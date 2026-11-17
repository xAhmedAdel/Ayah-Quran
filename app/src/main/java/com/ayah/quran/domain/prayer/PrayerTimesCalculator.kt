package com.ayah.quran.domain.prayer

import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure prayer-time computation contract. Implementations wrap a calculation
 * library (adhan-java); no I/O and no network — everything is local math.
 */
interface PrayerTimesCalculator {

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        timezoneId: ZoneId,
        method: CalculationMethod,
        asrMethod: AsrMethod,
        highLatitudeRule: HighLatitudeRulePref,
        adjustments: PrayerAdjustments,
    ): DayPrayerTimes

    companion object {
        /** Docs tolerance vs. reference services (Aladhan): ±2 minutes. */
        const val REFERENCE_TOLERANCE_MINUTES = 2
    }
}

/** Formats/derives helpers shared by UI and scheduling. */
object PrayerTimesFormatting {

    /** "17:38" style HH:mm (24h) — the UI renders localized digits itself. */
    fun formatHHmm(time: LocalDateTime): String =
        "%02d:%02d".format(time.hour, time.minute)
}
