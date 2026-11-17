package com.ayah.quran.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Validates the REAL bundled dataset (no fixtures): structural Madani-mushaf
 * invariants plus a full cross-check of all 6,236 ayahs against an
 * independent second lineage (quran.com text_uthmani, KFGQPC Madani layout),
 * stored under src/test/resources. Differences may only come from the three
 * documented encoding conventions (tatweel placement, optional waqf marks,
 * bismillah placement); word content must be identical.
 */
class QuranDatasetIntegrityTest {

    private lateinit var parsed: ParsedQuran

    @Before
    fun setUp() {
        val parser = QuranTextParser()
        parsed = parser.parse(
            ayahTextLines = readAssetLines("quran-uthmani.txt"),
            surahMetadataLines = readAssetLines("quran-data.xml"),
            ayahMetricsLines = readAssetLines("ayah-metrics.txt"),
        )
    }

    // ---- structure ----

    @Test
    fun `contains exactly 114 surahs with sequential ids`() {
        assertEquals(114, parsed.surahs.size)
        assertEquals((1..114).toList(), parsed.surahs.map { it.id })
    }

    @Test
    fun `contains exactly 6236 ayahs with continuous global ids`() {
        assertEquals(6236, parsed.ayahs.size)
        assertEquals((1..6236).toList(), parsed.ayahs.map { it.globalId })
    }

    @Test
    fun `per-surah ayah counts match the metadata`() {
        val counts = parsed.ayahs.groupingBy { it.surahId }.eachCount()
        val expected = parsed.surahs.associate { it.id to it.ayahCount }
        assertEquals(expected, counts)
    }

    @Test
    fun `al-Fatiha has 7 ayahs and al-Baqarah has 286`() {
        assertEquals(7, parsed.surahs.first { it.id == 1 }.ayahCount)
        assertEquals(286, parsed.surahs.first { it.id == 2 }.ayahCount)
    }

    @Test
    fun `no ayah has empty text`() {
        val empty = parsed.ayahs.filter { it.textUthmani.isBlank() }
        assertEquals(emptyList<ParsedAyah>(), empty)
    }

    @Test
    fun `pages cover exactly 1 to 604 and juz exactly 1 to 30`() {
        val pages = parsed.ayahs.map { it.page }.toSortedSet()
        val juzs = parsed.ayahs.map { it.juz }.toSortedSet()
        assertEquals((1..604).toSet(), pages)
        assertEquals((1..30).toSet(), juzs)
    }

    @Test
    fun `juz boundaries in metadata match ayah juz assignments`() {
        for (boundary in parsed.juzBoundaries) {
            val ayah = parsed.ayahs.first {
                it.surahId == boundary.surahId && it.ayahNumber == boundary.ayahNumber
            }
            assertEquals("juz ${boundary.juz} must start at ${boundary.surahId}:${boundary.ayahNumber}", boundary.juz, ayah.juz)
        }
    }

    @Test
    fun `reference spot checks - Ayat al-Kursi on page 42 juz 3`() {
        val kursi = parsed.ayahs.first { it.surahId == 2 && it.ayahNumber == 255 }
        assertEquals(42, kursi.page)
        assertEquals(3, kursi.juz)
    }

    @Test
    fun `revelation types are only Makkan or Madinan`() {
        parsed.surahs.forEach { surah ->
            assertTrue(
                "unexpected revelation type ${surah.revelationType} in surah ${surah.id}",
                surah.revelationType.lowercase() in setOf("makkan", "madinan"),
            )
        }
    }

    // ---- content cross-check against the second lineage ----

    @Test
    fun `every ayah is content-identical to the independent quran com dataset`() {
        val reference = loadQuranComReference()
        assertEquals(6236, reference.size)
        assertEquals(parsed.ayahs.map { it.surahId to it.ayahNumber }.toSet(), reference.keys.toSet())

        val bismillahStandard = parsed.ayahs.first { it.globalId == 1 }.textUthmani

        var exactMatches = 0
        var explainedByConventions = 0
        val unexplained = mutableListOf<Pair<Int, Int>>()

        for (ayah in parsed.ayahs) {
            val expected = reference.getValue(ayah.surahId to ayahNumberPair(ayah))
            if (ayah.textUthmani == expected) {
                exactMatches++
                continue
            }

            // Tanzil prefixes the bismillah to the FIRST ayah of every surah
            // except Al-Fatiha and At-Tawbah (Tanzil's documented convention;
            // surahs 95/97 use the shadda variant which the word-prefix rule
            // covers).
            val tanzilSide = if (ayah.surahId != 1 && ayah.surahId != 9 && ayah.ayahNumber == 1) {
                stripBismillahPrefix(ayah.textUthmani, bismillahStandard)
            } else {
                ayah.textUthmani
            }

            val normalizedTanzil = normalizeForComparison(tanzilSide)
            val normalizedReference = normalizeForComparison(expected)
            if (normalizedTanzil == normalizedReference) {
                explainedByConventions++
            } else {
                unexplained += ayah.surahId to ayah.ayahNumber
            }
        }

        assertEquals(6236, exactMatches + explainedByConventions)
        assertTrue(
            "unexplained content differences at: ${unexplained.take(10)}",
            unexplained.isEmpty(),
        )
    }

    // ---- helpers ----

    private fun ayahNumberPair(ayah: ParsedAyah) = ayah.ayahNumber

    private fun stripBismillahPrefix(text: String, bismillahStandard: String): String {
        if (text.startsWith("$bismillahStandard ")) {
            return text.removePrefix("$bismillahStandard ")
        }
        // Variant bismillah (surahs 95/97): first four words, per Tanzil.
        val words = text.split(' ')
        return if (words.size > 4) words.drop(4).joinToString(" ") else text
    }

    /** Strips only DOCUMENTED encoding conventions, never diacritics. */
    private fun normalizeForComparison(text: String): String {
        // U+0640 tatweel: quran.com inserts it before the dagger alef.
        var result = text.replace(TATWEEL, "")
        // Optional waqf/section demonstration marks present only in the
        // quran.com encoding: U+06D6..U+06DC, U+06DE, U+06E9.
        for (mark in QURAN_COM_ONLY_MARKS) {
            result = result.replace(mark.toString(), "")
        }
        return result.split(Regex("\\s+")).joinToString(" ").trim()
    }

    private companion object {
        const val TATWEEL = "\u0640"
        val QURAN_COM_ONLY_MARKS = charArrayOf(
            '\u06D6', // small high sad-lam-alef maksura
            '\u06D7', // small high qaf-lam-alef maksura
            '\u06D8', // small high meem
            '\u06D9', // small high lam-alef
            '\u06DA', // small high jeem
            '\u06DB', // small high three dots
            '\u06DC', // small high rounded zero
            '\u06DE', // start of rub el hizb
            '\u06E9', // place of sajdah
        )
    }

    private fun loadQuranComReference(): Map<Pair<Int, Int>, String> {
        val file = File("src/test/resources/quran/quran-com-verses.tsv")
            .takeIf { it.exists() } ?: File("app/src/test/resources/quran/quran-com-verses.tsv")
        return file.readLines().filter { it.isNotBlank() }.associate { line ->
            val parts = line.split('|')
            val key = parts[0].toInt() to parts[1].toInt()
            key to parts.drop(4).joinToString("|")
        }
    }

    private fun readAssetLines(name: String): List<String> {
        val candidates = listOf(
            File("src/main/assets/quran/$name"),
            File("app/src/main/assets/quran/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("dataset file not found: $name (tried ${candidates.map { it.path }})")
        return file.readLines()
    }
}
