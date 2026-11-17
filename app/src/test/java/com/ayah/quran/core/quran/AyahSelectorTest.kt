package com.ayah.quran.core.quran

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AyahSelectorTest {

    private val selector = AyahSelector()
    private val pool = (1L..6236L).toList()
    private val seededRandom = Random(42)

    @Test
    fun `random selection always stays inside the pool`() {
        repeat(200) {
            val selected = selector.selectRandom(pool, emptySet(), seededRandom)
            assertTrue("selected $selected outside pool", selected in 1L..6236L)
        }
    }

    @Test
    fun `random selection avoids recent verses when alternatives exist`() {
        val recent = setOf(1L, 2L, 3L)
        repeat(200) {
            val selected = selector.selectRandom(pool, recent, seededRandom)
            assertTrue("selected $selected was in the recent set", selected !in recent)
        }
    }

    @Test
    fun `random selection falls back to full pool when everything is recent`() {
        val wholePool = setOf(1L..10L).flatMap { it.asSequence() }.toSet()
        repeat(50) {
            val selected = selector.selectRandom((1L..10L).toList(), wholePool, seededRandom)
            assertTrue(selected in 1L..10L)
        }
    }

    @Test
    fun `sequential selection walks the pool in order and wraps`() {
        var cursor = 0L
        val visited = mutableListOf<Long>()
        // More selections than pool entries to prove wrap-around.
        repeat(pool.size + 3) {
            val (selected, updated) = selector.selectSequential(cursor, pool.size)
            visited += selected
            cursor = updated
        }
        assertEquals((1L..6236L).toList() + listOf(1L, 2L, 3L), visited)
    }

    @Test
    fun `daily selection is deterministic for a day and varies across days`() {
        val day = 20_000L
        val forDay = selector.selectDaily(day, pool.size)
        assertEquals(forDay, selector.selectDaily(day, pool.size))

        // Across many days the selector must cover different verses.
        val distinct = (0L until 60L).map { selector.selectDaily(day + it, pool.size) }.toSet()
        assertTrue("daily selector produced only ${distinct.size} distinct verses over 60 days", distinct.size > 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty pool is rejected`() {
        selector.selectRandom(emptyList(), emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero-size sequential pool is rejected`() {
        selector.selectSequential(0, 0)
    }
}
