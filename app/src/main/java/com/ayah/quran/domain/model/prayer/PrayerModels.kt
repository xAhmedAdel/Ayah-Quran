package com.ayah.quran.domain.model.prayer

import java.time.LocalDate
import java.time.LocalDateTime

/** The five obligatory prayers plus sunrise (sunrise is NOT a prayer). */
enum class Prayer {
    FAJR,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
}

/** One day's calculated times in the location's timezone. */
data class DayPrayerTimes(
    val date: LocalDate,
    val fajr: LocalDateTime,
    val sunrise: LocalDateTime,
    val dhuhr: LocalDateTime,
    val asr: LocalDateTime,
    val maghrib: LocalDateTime,
    val isha: LocalDateTime,
) {
    fun timeOf(prayer: Prayer): LocalDateTime = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

/** Supported calculation methods, mapped onto adhan-java's implementations. */
enum class CalculationMethod {
    EGYPTIAN,
    MUSLIM_WORLD_LEAGUE,
    UMM_AL_QURA,
    KARACHI,
    ISNA,
    DUBAI,
    KUWAIT,
    QATAR,
    SINGAPORE,
    MOONSIGHTING_COMMITTEE,
}

enum class AsrMethod { STANDARD, HANAFI }

enum class HighLatitudeRulePref { MIDDLE_OF_NIGHT, SEVENTH_OF_NIGHT, ANGLE_BASED }

/** Manual per-prayer minute offsets (−120..120). */
data class PrayerAdjustments(
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

/** A user-selected location (manual city or cached device fix). */
data class PrayerLocation(
    val cityAr: String,
    val cityEn: String,
    val countryAr: String,
    val countryEn: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String,
)

/** What happens when a prayer time arrives. */
enum class AdhanMode {
    /** Play the selected adhan audio (service + full controls). */
    FULL_ADHAN,
    /** Short notification-channel sound, no audio playback. */
    NOTIFICATION_SOUND,
    /** Silent notification only. */
    SILENT,
    /** Nothing at all. */
    DISABLED,
}

/** Per-prayer adhan configuration. */
data class PrayerAdhanConfig(
    val fajr: AdhanMode = AdhanMode.NOTIFICATION_SOUND,
    val dhuhr: AdhanMode = AdhanMode.NOTIFICATION_SOUND,
    val asr: AdhanMode = AdhanMode.NOTIFICATION_SOUND,
    val maghrib: AdhanMode = AdhanMode.NOTIFICATION_SOUND,
    val isha: AdhanMode = AdhanMode.NOTIFICATION_SOUND,
) {
    fun modeOf(prayer: Prayer): AdhanMode = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    fun withMode(prayer: Prayer, mode: AdhanMode): PrayerAdhanConfig = when (prayer) {
        Prayer.FAJR -> copy(fajr = mode)
        Prayer.DHUHR -> copy(dhuhr = mode)
        Prayer.ASR -> copy(asr = mode)
        Prayer.MAGHRIB -> copy(maghrib = mode)
        Prayer.ISHA -> copy(isha = mode)
    }
}

/** Manual Hijri day correction (−2..+2) for regional moon-sighting conventions. */
data class HijriAdjustment(val days: Int = 0)

/** Where the next-prayer state machine reports to the UI. */
data class PrayerCountdown(
    val current: Prayer?,
    val next: Prayer,
    val nextTime: LocalDateTime,
    val durationMillis: Long,
)
