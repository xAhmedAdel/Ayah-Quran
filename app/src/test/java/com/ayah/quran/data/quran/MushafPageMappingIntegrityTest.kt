package com.ayah.quran.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Validates the per-ayah Madinah mushaf page mapping (604 pages) that backs
 * the authentic mushaf reader.
 *
 * The page boundaries are the bundled `ayah-metrics.txt` mapping (quran.com
 * v4 API, KFGQPC Madani layout) — the SAME data imported into Room. These
 * tests prove the mapping covers every page and every ayah exactly once, in
 * canonical order, with the authentic boundary landmarks (page 1 = Al-Fatiha,
 * page 604 ends with An-Nas). No page boundaries are invented here.
 */
class MushafPageMappingIntegrityTest {

    private data class Row(val surahId: Int, val ayahNumber: Int, val page: Int)

    private lateinit var rows: List<Row>

    @Before
    fun setUp() {
        val file = listOf(
            File("src/main/assets/quran/ayah-metrics.txt"),
            File("app/src/main/assets/quran/ayah-metrics.txt"),
        ).firstOrNull { it.exists() } ?: error("ayah-metrics.txt not found")

        rows = file.readLines().mapIndexed { index, line ->
            val parts = line.trim().split('|')
            assertEquals("malformed metrics line $index", 4, parts.size)
            Row(parts[0].toInt(), parts[1].toInt(), parts[3].toInt())
        }
    }

    @Test
    fun `every ayah of the Quran appears exactly once`() {
        assertEquals(6236, rows.size)
        assertEquals(6236, rows.map { it.surahId to it.ayahNumber }.toSet().size)
    }

    @Test
    fun `page numbers cover exactly 1 through 604`() {
        val pages = rows.map { it.page }.toSet()
        assertEquals((1..604).toSet(), pages)
    }

    @Test
    fun `pages are monotonic in canonical reading order`() {
        val violations = rows.zipWithNext().count { (a, b) -> b.page < a.page }
        assertEquals(0, violations)
    }

    @Test
    fun `every page has at least one ayah`() {
        val counts = rows.groupingBy { it.page }.eachCount()
        assertEquals(604, counts.size)
        assertTrue(counts.values.all { it >= 1 })
    }

    // ---- authentic landmark boundaries ----

    @Test
    fun `page 1 is exactly Surah Al-Fatiha`() {
        val page1 = rows.filter { it.page == 1 }
        assertEquals((1..7).toList(), page1.map { it.ayahNumber })
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 1), page1.map { it.surahId })
    }

    @Test
    fun `page 2 starts with Al-Baqarah ayah 1`() {
        val first = rows.first { it.page == 2 }
        assertEquals(2 to 1, first.surahId to first.ayahNumber)
    }

    @Test
    fun `page 604 ends with An-Nas`() {
        val page604 = rows.filter { it.page == 604 }
        val last = page604.last()
        assertEquals(114 to 6, last.surahId to last.ayahNumber)
        assertTrue(page604.any { it.surahId == 112 && it.ayahNumber == 1 })
    }

    @Test
    fun `every ayah's page matches the imported invariant range`() {
        assertTrue(rows.all { it.page in 1..604 })
    }
}
