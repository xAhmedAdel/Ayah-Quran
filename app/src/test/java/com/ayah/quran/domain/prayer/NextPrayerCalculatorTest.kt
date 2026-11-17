package com.ayah.quran.domain.prayer

import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pure state-machine tests for current/next-prayer selection: mid-day,
 * midnight rollover (next = tomorrow Fajr), and the "current" pointer.
 * Times use a fixed ISO day — no device clock dependence.
 */
class NextPrayerCalculatorTest {

    private fun day(
        date: LocalDate = LocalDate.of(2026, 11, 17),
    ): DayPrayerTimes = DayPrayerTimes(
        date = date,
        fajr = date.atTime(5, 0),
        sunrise = date.atTime(6, 30),
        dhuhr = date.atTime(12, 0),
        asr = date.atTime(15, 0),
        maghrib = date.atTime(17, 30),
        isha = date.atTime(19, 0),
    )

    private fun tomorrowFajr() = LocalDate.of(2026, 11, 18).atTime(5, 0)

    @Test
    fun `before fajr the next prayer is fajr with no current`() {
        val now = LocalDateTime.of(2026, 11, 17, 3, 0)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.FAJR, result.next)
        assertNull(result.current)
        assertEquals(2 * 3_600_000L, result.durationMillis)
    }

    @Test
    fun `mid-morning points to dhuhr with fajr current`() {
        val now = LocalDateTime.of(2026, 11, 17, 9, 30)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.DHUHR, result.next)
        assertEquals(Prayer.FAJR, result.current)
        assertEquals(2 * 3_600_000L + 30 * 60_000L, result.durationMillis)
    }

    @Test
    fun `between maghrib and isha points to isha`() {
        val now = LocalDateTime.of(2026, 11, 17, 18, 0)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.ISHA, result.next)
        assertEquals(Prayer.MAGHRIB, result.current)
    }

    @Test
    fun `after isha rolls over to tomorrow fajr with isha current`() {
        val now = LocalDateTime.of(2026, 11, 17, 23, 30)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.FAJR, result.next)
        assertEquals(Prayer.ISHA, result.current)
        assertEquals(tomorrowFajr(), result.nextTime)
        assertEquals(5 * 3_600_000L + 30 * 60_000L, result.durationMillis)
    }

    @Test
    fun `exactly at fajr time the prayer has arrived and dhuhr becomes next`() {
        val now = LocalDateTime.of(2026, 11, 17, 5, 0)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.DHUHR, result.next)
        assertEquals(Prayer.FAJR, result.current)
        assertEquals(7 * 3_600_000L, result.durationMillis)
    }

    @Test
    fun `exact prayer boundary switches current and next`() {
        val now = LocalDateTime.of(2026, 11, 17, 12, 0)
        val result = NextPrayerCalculator.compute(day(), tomorrowFajr(), now)
        assertEquals(Prayer.ASR, result.next)
        assertEquals(Prayer.DHUHR, result.current)
    }
}
