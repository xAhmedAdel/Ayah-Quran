package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import java.util.Date
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prayer-time computation backed by [adhan-java](https://github.com/BatoulApps/adhan-java)
 * (com.batoulapps.adhan:adhan:1.2.1, MIT). All math runs locally — no network.
 *
 * Mapping notes:
 * - ISNA → adhan's NORTH_AMERICA (same convention).
 * - MOONSIGHTING_COMMITTEE → adhan's MOON_SIGHTING_COMMITTEE.
 * - adhan returns epoch instants; they are converted to the location's zone.
 */
@Singleton
class AdhanPrayerTimesCalculator @Inject constructor() : PrayerTimesCalculator {

    override fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        timezoneId: ZoneId,
        method: CalculationMethod,
        asrMethod: AsrMethod,
        highLatitudeRule: HighLatitudeRulePref,
        adjustments: PrayerAdjustments,
    ): DayPrayerTimes {
        val parameters = parametersOf(method, asrMethod, highLatitudeRule, adjustments)
        val times = PrayerTimes(
            Coordinates(latitude, longitude),
            com.batoulapps.adhan.data.DateComponents(date.year, date.monthValue, date.dayOfMonth),
            parameters,
        )
        fun local(at: Date): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(at.time), timezoneId)

        return DayPrayerTimes(
            date = date,
            fajr = local(times.fajr),
            sunrise = local(times.sunrise),
            dhuhr = local(times.dhuhr),
            asr = local(times.asr),
            maghrib = local(times.maghrib),
            isha = local(times.isha),
        )
    }

    private fun parametersOf(
        method: CalculationMethod,
        asrMethod: AsrMethod,
        highLatitudeRule: HighLatitudeRulePref,
        adjustments: PrayerAdjustments,
    ): CalculationParameters {
        val parameters = when (method) {
            CalculationMethod.EGYPTIAN -> com.batoulapps.adhan.CalculationMethod.EGYPTIAN.getParameters()
            CalculationMethod.MUSLIM_WORLD_LEAGUE -> com.batoulapps.adhan.CalculationMethod.MUSLIM_WORLD_LEAGUE.getParameters()
            CalculationMethod.UMM_AL_QURA -> com.batoulapps.adhan.CalculationMethod.UMM_AL_QURA.getParameters()
            CalculationMethod.KARACHI -> com.batoulapps.adhan.CalculationMethod.KARACHI.getParameters()
            CalculationMethod.ISNA -> com.batoulapps.adhan.CalculationMethod.NORTH_AMERICA.getParameters()
            CalculationMethod.DUBAI -> com.batoulapps.adhan.CalculationMethod.DUBAI.getParameters()
            CalculationMethod.KUWAIT -> com.batoulapps.adhan.CalculationMethod.KUWAIT.getParameters()
            CalculationMethod.QATAR -> com.batoulapps.adhan.CalculationMethod.QATAR.getParameters()
            CalculationMethod.SINGAPORE -> com.batoulapps.adhan.CalculationMethod.SINGAPORE.getParameters()
            CalculationMethod.MOONSIGHTING_COMMITTEE -> com.batoulapps.adhan.CalculationMethod.MOON_SIGHTING_COMMITTEE.getParameters()
        }
        parameters.madhab = when (asrMethod) {
            AsrMethod.STANDARD -> Madhab.SHAFI
            AsrMethod.HANAFI -> Madhab.HANAFI
        }
        parameters.highLatitudeRule = when (highLatitudeRule) {
            HighLatitudeRulePref.MIDDLE_OF_NIGHT -> HighLatitudeRule.MIDDLE_OF_THE_NIGHT
            HighLatitudeRulePref.SEVENTH_OF_NIGHT -> HighLatitudeRule.SEVENTH_OF_THE_NIGHT
            HighLatitudeRulePref.ANGLE_BASED -> HighLatitudeRule.TWILIGHT_ANGLE
        }
        parameters.adjustments.fajr = adjustments.fajr
        parameters.adjustments.dhuhr = adjustments.dhuhr
        parameters.adjustments.asr = adjustments.asr
        parameters.adjustments.maghrib = adjustments.maghrib
        parameters.adjustments.isha = adjustments.isha
        return parameters
    }
}
