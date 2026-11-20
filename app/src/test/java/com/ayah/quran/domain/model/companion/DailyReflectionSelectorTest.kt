package com.ayah.quran.domain.model.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReflectionSelectorTest {

    @Test
    fun selectionIsDeterministicForSameDate() {
        val epochDay = 20_777L
        val first = DailyReflectionSelector.selectIndex(epochDay, 202)
        val second = DailyReflectionSelector.selectIndex(epochDay, 202)
        assertEquals(first, second)
        assertTrue(first in 0 until 202)
    }

    @Test
    fun consecutiveDaysNeverRepeatAcrossPoolSizes() {
        val poolSizes = listOf(2, 3, 17, 42, 202, 6236)
        val startDay = 20_000L
        poolSizes.forEach { size ->
            var previous = DailyReflectionSelector.selectIndex(startDay, size)
            for (offset in 1..365) {
                val current = DailyReflectionSelector.selectIndex(startDay + offset, size)
                assertTrue(current in 0 until size)
                assertNotEquals("Repeated on day offset $offset for poolSize=$size", previous, current)
                previous = current
            }
        }
    }

    @Test
    fun singleItemPoolAlwaysReturnsZero() {
        assertEquals(0, DailyReflectionSelector.selectIndex(20_000L, 1))
        assertEquals(0, DailyReflectionSelector.selectIndex(20_001L, 1))
    }
}
