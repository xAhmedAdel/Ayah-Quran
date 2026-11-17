package com.ayah.quran.domain.prayer

import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerCountdown
import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure "current/next prayer + countdown" state machine.
 *
 * - After Isha (and before midnight) the NEXT prayer is tomorrow's Fajr.
 * - `current` is the most recent prayer that has passed today (null before Fajr).
 * - Countdown duration is always positive, measured against `now`.
 */
object NextPrayerCalculator {

    fun compute(
        today: DayPrayerTimes,
        tomorrowFajr: LocalDateTime,
        now: LocalDateTime,
    ): PrayerCountdown {
        val order = listOf(
            Prayer.FAJR to today.fajr,
            Prayer.DHUHR to today.dhuhr,
            Prayer.ASR to today.asr,
            Prayer.MAGHRIB to today.maghrib,
            Prayer.ISHA to today.isha,
        )
        val next = order.firstOrNull { now.isBefore(it.second) }
        return if (next != null) {
            val previous = order.takeWhile { it != next }.lastOrNull()
            PrayerCountdown(
                current = previous?.first,
                next = next.first,
                nextTime = next.second,
                durationMillis = Duration.between(now, next.second).toMillis(),
            )
        } else {
            // After Isha: next is tomorrow's Fajr (handles midnight rollover).
            PrayerCountdown(
                current = Prayer.ISHA,
                next = Prayer.FAJR,
                nextTime = tomorrowFajr,
                durationMillis = Duration.between(now, tomorrowFajr).toMillis(),
            )
        }
    }
}
