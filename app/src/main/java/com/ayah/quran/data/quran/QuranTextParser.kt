package com.ayah.quran.data.quran

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of parsing the raw Tanzil dataset files.
 *
 * Pure data holder — parsing performs no transformations of Quranic text.
 */
data class ParsedQuran(
    val surahs: List<ParsedSurah>,
    // Index-aligned with the canonical global ayah number (1..6236).
    val ayahs: List<ParsedAyah>,
    val juzBoundaries: List<JuzBoundary>,
)

data class ParsedSurah(
    val id: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val nameEnglish: String,
    val revelationType: String, // "Makkan" | "Madinan", as delivered by Tanzil
    val ayahCount: Int,
    val rukus: Int,
    // 0-based global index of the surah's first ayah, from quran-data.xml.
    val startGlobalIndex: Int,
)

data class ParsedAyah(
    // Canonical global verse number (1..6236).
    val globalId: Int,
    val surahId: Int,
    val ayahNumber: Int,
    // Uthmani text, byte-for-byte as delivered by the dataset.
    val textUthmani: String,
    val juz: Int,
    val page: Int,
)

data class JuzBoundary(
    val juz: Int,
    val surahId: Int,
    val ayahNumber: Int,
)

/**
 * Parser for the bundled Tanzil dataset files.
 *
 * Strictness contract: any structural anomaly (wrong counts, out-of-order
 * references, empty text, missing fields) throws [QuranParseException] and the
 * import aborts. Text is never trimmed, normalized, or re-ordered; only the
 * record separators around it are handled.
 */
/**
 * No @Inject on purpose: default parameters generate a synthetic no-arg
 * constructor that Dagger would treat as a second injected constructor.
 * Provided via DI in DatabaseModule instead.
 */
class QuranTextParser(
    // Canonical counts; overridable ONLY to exercise line-level validation
    // in unit tests with miniature datasets. Production always uses defaults.
    private val expectedSurahs: Int = EXPECTED_SURAHS,
    private val expectedAyahs: Int = EXPECTED_AYAHS,
    private val expectedJuz: Int = EXPECTED_JUZ,
    private val expectedPages: Int = EXPECTED_PAGES,
) {

    fun parse(
        ayahTextLines: List<String>,
        surahMetadataLines: List<String>,
        ayahMetricsLines: List<String>,
    ): ParsedQuran {
        val surahs = parseSurahMetadata(surahMetadataLines)
        val juzBoundaries = parseJuzBoundaries(surahMetadataLines)
        val metricsByReference = parseAyahMetrics(dataLines(ayahMetricsLines, "ayah metrics"))
        val ayahs = parseAyahText(dataLines(ayahTextLines, "ayah text"), surahs, metricsByReference)

        validate(surahs, ayahs, juzBoundaries)
        return ParsedQuran(surahs, ayahs, juzBoundaries)
    }

    /**
     * Removes blank lines and `#` comment lines. The bundled Tanzil file
     * keeps its copyright block (required by the CC-BY terms); the parser
     * skips it without ever altering the ayah lines themselves.
     */
    private fun dataLines(lines: List<String>, label: String): List<String> =
        lines.filterIndexed { _, line -> line.isNotBlank() && !line.startsWith("#") }
            .also { parsed ->
                if (parsed.isEmpty()) fail("$label contained no data lines")
            }

    // ---- surah metadata (quran-data.xml) ----

    private fun parseSurahMetadata(lines: List<String>): List<ParsedSurah> {
        val suras = lines.mapNotNull { line -> SURA_TAG.find(line)?.let { it to parseSuraAttributes(it.value) } }
        if (suras.size != expectedSurahs) {
            fail("expected $expectedSurahs <sura> entries, found ${suras.size}")
        }
        return suras.mapIndexedNotNull { position, (match, attributes) ->
            val id = attributes["index"]?.toIntOrNull() ?: fail("missing surah index in: ${match.value}")
            if (id != position + 1) fail("surah index out of order: $id at position ${position + 1}")
            val ayahCount = attributes["ayas"]?.toIntOrNull() ?: fail("missing ayas count for surah $id")
            ParsedSurah(
                id = id,
                nameArabic = attributes["name"] ?: fail("missing Arabic name for surah $id"),
                nameTransliterated = attributes["tname"] ?: fail("missing transliterated name for surah $id"),
                nameEnglish = attributes["ename"] ?: fail("missing English name for surah $id"),
                revelationType = attributes["type"] ?: fail("missing revelation type for surah $id"),
                ayahCount = ayahCount,
                rukus = attributes["rukus"]?.toIntOrNull() ?: fail("missing rukus for surah $id"),
                startGlobalIndex = attributes["start"]?.toIntOrNull() ?: fail("missing start index for surah $id"),
            )
        }
    }

    private fun parseJuzBoundaries(lines: List<String>): List<JuzBoundary> {
        val boundaries = lines.mapNotNull { line ->
            JUZ_TAG.find(line)?.groups?.let { groups ->
                JuzBoundary(
                    juz = groups["juz"]?.value?.toIntOrNull() ?: fail("missing juz index in: $line"),
                    surahId = groups["sura"]?.value?.toIntOrNull() ?: fail("missing juz surah in: $line"),
                    ayahNumber = groups["aya"]?.value?.toIntOrNull() ?: fail("missing juz ayah in: $line"),
                )
            }
        }
        if (boundaries.map { it.juz } != (1..expectedJuz).toList()) {
            fail("juz boundaries are not exactly 1..$expectedJuz in order: ${boundaries.map { it.juz }}")
        }
        return boundaries
    }

    // ---- ayah metrics (page/juz per ayah, from the Madani mushaf layout) ----

    private fun parseAyahMetrics(lines: List<String>): Map<Pair<Int, Int>, AyahMetrics> {
        val metrics = lines.mapIndexed { position, line ->
            val parts = line.split('|')
            if (parts.size != 4) fail("ayah metrics line $position malformed: ${line.take(60)}")
            val surahId = parts[0].toIntOrNull() ?: fail("bad surah id in metrics line $position")
            val ayahNumber = parts[1].toIntOrNull() ?: fail("bad ayah number in metrics line $position")
            val juz = parts[2].toIntOrNull() ?: fail("bad juz in metrics line $position")
            val page = parts[3].toIntOrNull() ?: fail("bad page in metrics line $position")
            (surahId to ayahNumber) to AyahMetrics(juz, page)
        }
        if (metrics.size != expectedAyahs) {
            fail("expected $expectedAyahs ayah metric lines, found ${metrics.size}")
        }
        return metrics.toMap().also { map ->
            if (map.size != metrics.size) fail("duplicate surah|ayah references in ayah metrics")
        }
    }

    // ---- ayah text (quran-uthmani.txt) ----

    private fun parseAyahText(
        lines: List<String>,
        surahs: List<ParsedSurah>,
        metrics: Map<Pair<Int, Int>, AyahMetrics>,
    ): List<ParsedAyah> {
        val ayahCounts = surahs.associate { it.id to it.ayahCount }
        val expectedTotal = ayahCounts.values.sum()
        if (expectedTotal != expectedAyahs) {
            fail("surah ayah counts sum to $expectedTotal, expected $expectedAyahs")
        }

        val parsed = ArrayList<ParsedAyah>(expectedAyahs)
        // Global verse numbers are derived from the XML start indices
        // (start is a 0-based global index), tying both files together.
        val startBySurah = surahs.associate { it.id to it.startGlobalIndex }
        for ((position, line) in lines.withIndex()) {
            val parts = line.split('|')
            if (parts.size != 3) fail("ayah text line ${position + 1} malformed: ${line.take(60)}")
            val surahId = parts[0].toIntOrNull() ?: fail("bad surah id in text line ${position + 1}")
            val ayahNumber = parts[1].toIntOrNull() ?: fail("bad ayah number in text line ${position + 1}")
            val text = parts[2]
            if (text.isEmpty()) fail("empty Quranic text at line ${position + 1} ($surahId:$ayahNumber)")

            val last = parsed.lastOrNull()
            val expectedAyah: Int
            val expectedSurah: Int
            if (last == null) {
                expectedAyah = 1
                expectedSurah = 1
            } else if (last.ayahNumber < (ayahCounts[last.surahId] ?: fail("unknown surah ${last.surahId}"))) {
                expectedAyah = last.ayahNumber + 1
                expectedSurah = last.surahId
            } else {
                expectedAyah = 1
                expectedSurah = last.surahId + 1
            }
            if (surahId != expectedSurah || ayahNumber != expectedAyah) {
                fail(
                    "ayah sequence broken at line ${position + 1}: got $surahId:$ayahNumber, " +
                        "expected $expectedSurah:$expectedAyah",
                )
            }

            val ayahMetrics = metrics[surahId to ayahNumber]
                ?: fail("no juz/page metric for $surahId:$ayahNumber")

            parsed += ParsedAyah(
                globalId = (startBySurah[surahId] ?: fail("missing start for surah $surahId")) + ayahNumber,
                surahId = surahId,
                ayahNumber = ayahNumber,
                textUthmani = text,
                juz = ayahMetrics.juz,
                page = ayahMetrics.page,
            )
        }
        if (parsed.size != expectedAyahs) {
            fail("parsed ${parsed.size} ayahs, expected $expectedAyahs")
        }

        // Global ids must be exactly 1..N (sequence + start indices verified).
        if (parsed.map { it.globalId } != (1..expectedAyahs).toList()) {
            fail("global ayah ids are not exactly 1..$expectedAyahs")
        }
        return parsed
    }

    // ---- structural validation over the whole dataset ----

    private fun validate(
        surahs: List<ParsedSurah>,
        ayahs: List<ParsedAyah>,
        juzBoundaries: List<JuzBoundary>,
    ) {
        if (surahs.size != expectedSurahs) fail("surah count ${surahs.size}")
        if (ayahs.size != expectedAyahs) fail("ayah count ${ayahs.size}")

        val ayahCountBySurah = surahs.associate { it.id to it.ayahCount }
        val actualCounts = ayahs.groupingBy { it.surahId }.eachCount()
        if (actualCounts != ayahCountBySurah) {
            fail("per-surah ayah counts disagree between metadata and text")
        }

        for (surah in surahs) {
            val type = surah.revelationType.lowercase()
            if (type != "makkan" && type != "madinan") {
                fail("unknown revelation type '${surah.revelationType}' for surah ${surah.id}")
            }
        }

        val pages = ayahs.map { it.page }
        if (pages.min() < 1 || pages.max() > expectedPages) fail("pages outside 1..$expectedPages")
        val juzs = ayahs.map { it.juz }
        if (juzs.min() < 1 || juzs.max() > expectedJuz) fail("juz outside 1..$expectedJuz")

        for (boundary in juzBoundaries) {
            val matches = ayahs.any {
                it.surahId == boundary.surahId && it.ayahNumber == boundary.ayahNumber && it.juz == boundary.juz
            }
            if (!matches) fail("juz boundary ${boundary.juz} (${boundary.surahId}:${boundary.ayahNumber}) not found")
        }

        if (expectedSurahs == EXPECTED_SURAHS) {
            // Canonical-dataset-only spot checks.
            if (ayahCountBySurah[1] != 7) fail("Al-Fatiha must have 7 ayahs")
            if (ayahCountBySurah[2] != 286) fail("Al-Baqarah must have 286 ayahs")
        }
    }

    private fun parseSuraAttributes(tag: String): Map<String, String> {
        val attributes = ATTRIBUTE_REGEX.findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }
        if (attributes.isEmpty()) fail("no attributes found in: ${tag.take(60)}")
        return attributes
    }

    private fun fail(message: String): Nothing = throw QuranParseException(message)

    data class AyahMetrics(val juz: Int, val page: Int)

    companion object {
        const val EXPECTED_SURAHS = 114
        const val EXPECTED_AYAHS = 6236
        const val EXPECTED_JUZ = 30
        const val EXPECTED_PAGES = 604

        private val SURA_TAG = Regex("<sura [^>]*/>")
        private val JUZ_TAG = Regex(
            "<juz index=\"(?<juz>\\d+)\" sura=\"(?<sura>\\d+)\" aya=\"(?<aya>\\d+)\"\\s*/>",
        )
        private val ATTRIBUTE_REGEX = Regex("(\\w+)=\"([^\"]*)\"")
    }
}

/** Thrown when the dataset violates any structural expectation. Import aborts. */
class QuranParseException(message: String) : IllegalStateException(message)
