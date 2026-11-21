package com.ayah.quran.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RubElHizbCatalogIntegrityTest {

    private lateinit var parsedQuran: ParsedQuran
    private lateinit var rubs: List<com.ayah.quran.domain.model.RubElHizb>

    @Before
    fun setUp() {
        parsedQuran = QuranTextParser().parse(
            ayahTextLines = readAssetLines("quran-uthmani.txt"),
            surahMetadataLines = readAssetLines("quran-data.xml"),
            ayahMetricsLines = readAssetLines("ayah-metrics.txt"),
        )
        rubs = RubElHizbParser().parse(readAssetLines("rub-el-hizb.txt"))
    }

    @Test
    fun `contains exactly 240 rub entries numbered sequentially 1 to 240`() {
        assertEquals(240, rubs.size)
        assertEquals((1..240).toList(), rubs.map { it.rubNumber })
    }

    @Test
    fun `expected canonical boundary anchors match verified Madani layout`() {
        val first = rubs.first()
        assertEquals(1, first.rubNumber)
        assertEquals(1, first.hizbNumber)
        assertEquals(1, first.quarterInHizb)
        assertEquals(1, first.juzNumber)
        assertEquals(1, first.startSurahId)
        assertEquals(1, first.startAyahNumber)

        val rub106 = rubs[105]
        assertEquals(106, rub106.rubNumber)
        assertEquals(27, rub106.hizbNumber)
        assertEquals(2, rub106.quarterInHizb)
        assertEquals(14, rub106.juzNumber)
        assertEquals(15, rub106.startSurahId)
        assertEquals(49, rub106.startAyahNumber)

        val last = rubs.last()
        assertEquals(240, last.rubNumber)
        assertEquals(60, last.hizbNumber)
        assertEquals(4, last.quarterInHizb)
        assertEquals(30, last.juzNumber)
        assertEquals(100, last.startSurahId)
        assertEquals(9, last.startAyahNumber)
    }

    @Test
    fun `every rub references a real ayah in strict chronological order with no duplicates`() {
        val ayahByRef = parsedQuran.ayahs.associateBy { it.surahId to it.ayahNumber }
        val surahById = parsedQuran.surahs.associateBy { it.id }
        val seenRefs = mutableSetOf<Pair<Int, Int>>()
        var previousGlobalId = 0

        for (rub in rubs) {
            val surah = surahById[rub.startSurahId]
            assertTrue("invalid surah id ${rub.startSurahId} at rub ${rub.rubNumber}", surah != null)
            assertTrue(
                "invalid ayah ${rub.startSurahId}:${rub.startAyahNumber} at rub ${rub.rubNumber}",
                rub.startAyahNumber in 1..surah!!.ayahCount,
            )

            val ref = rub.startSurahId to rub.startAyahNumber
            assertTrue("duplicate rub start $ref at rub ${rub.rubNumber}", seenRefs.add(ref))

            val ayah = ayahByRef[ref]
            assertTrue("ayah $ref missing from Quran corpus", ayah != null)
            assertTrue(
                "rub ${rub.rubNumber} global id ${ayah!!.globalId} not strictly after $previousGlobalId",
                ayah.globalId > previousGlobalId,
            )
            previousGlobalId = ayah.globalId
        }
        assertEquals(240, seenRefs.size)
    }

    @Test
    fun `hizb, quarterInHizb, and juz associations match canonical Quran divisions`() {
        val ayahByRef = parsedQuran.ayahs.associateBy { it.surahId to it.ayahNumber }

        assertEquals((1..60).toSet(), rubs.map { it.hizbNumber }.toSet())
        val countsPerHizb = rubs.groupingBy { it.hizbNumber }.eachCount()
        assertTrue(countsPerHizb.values.all { it == 4 })

        val countsPerJuz = rubs.groupingBy { it.juzNumber }.eachCount()
        assertEquals((1..30).toSet(), countsPerJuz.keys)
        assertTrue(countsPerJuz.values.all { it == 8 })

        for (rub in rubs) {
            val expectedHizb = ((rub.rubNumber - 1) / 4) + 1
            val expectedQuarter = ((rub.rubNumber - 1) % 4) + 1
            val expectedJuz = ((rub.rubNumber - 1) / 8) + 1
            assertEquals(expectedHizb, rub.hizbNumber)
            assertEquals(expectedQuarter, rub.quarterInHizb)
            assertEquals(expectedJuz, rub.juzNumber)

            val ayah = ayahByRef.getValue(rub.startSurahId to rub.startAyahNumber)
            assertEquals(
                "rub ${rub.rubNumber} (${rub.startSurahId}:${rub.startAyahNumber}) juz mismatch",
                rub.juzNumber,
                ayah.juz,
            )
        }

        for (juzBoundary in parsedQuran.juzBoundaries) {
            val rubIndex = (juzBoundary.juz - 1) * 8
            val openingRub = rubs[rubIndex]
            assertEquals(juzBoundary.surahId, openingRub.startSurahId)
            assertEquals(juzBoundary.ayahNumber, openingRub.startAyahNumber)
        }
    }

    @Test
    fun `mid-surah rub boundaries match all printed U+06DE markers in quran com reference`() {
        val referenceFile = File("src/test/resources/quran/quran-com-verses.tsv")
            .takeIf { it.exists() } ?: File("app/src/test/resources/quran/quran-com-verses.tsv")
        val markedRefs = referenceFile.readLines()
            .filter { it.isNotBlank() && it.contains('\u06DE') }
            .map { line ->
                val parts = line.split('|')
                parts[0].toInt() to parts[1].toInt()
            }
            .toSet()

        val rubRefs = rubs.map { it.startSurahId to it.startAyahNumber }.toSet()
        val unmarkedRubs = rubRefs - markedRefs
        val unexpectedMarks = markedRefs - rubRefs

        assertEquals(199, markedRefs.size)
        assertEquals(emptySet<Pair<Int, Int>>(), unexpectedMarks)
        assertTrue("15:49 must carry the printed U+06DE marker", (15 to 49) in markedRefs)
        assertTrue(
            "every rub without an inline U+06DE mark must start at ayah 1 of a surah",
            unmarkedRubs.all { (_, ayah) -> ayah == 1 },
        )
        assertEquals(41, unmarkedRubs.size)
    }

    @Test
    fun `bundled rub asset sha256 matches documented checksum`() {
        val assetFile = resolveAssetFile("rub-el-hizb.txt")
        val digest = MessageDigest.getInstance("SHA-256").digest(assetFile.readBytes())
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals(
            "840104f1947e3585f2067f5a70bd5790fdcc85575b2f50889d4d9a3eaab78cf4",
            hex,
        )
    }

    @Test
    fun `parser rejects malformed, duplicate, out-of-order, and incomplete inputs`() {
        val parser = RubElHizbParser(expectedRubCount = 2)

        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|1|1"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|1|1", "3|2|26"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|2|26", "2|2|26"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|2|44", "2|2|26"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|0|1", "2|2|26"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|1|0", "2|2|26"))
        }
        assertThrows(QuranParseException::class.java) {
            parser.parse(listOf("1|1", "2|2|26"))
        }
    }

    private fun resolveAssetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/quran/$name"),
            File("app/src/main/assets/quran/$name"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("dataset file not found: $name (tried ${candidates.map { it.path }})")
    }

    private fun readAssetLines(name: String): List<String> = resolveAssetFile(name).readLines()
}
