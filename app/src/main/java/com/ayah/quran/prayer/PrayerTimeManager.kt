package com.ayah.quran.prayer

import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerCountdown
import com.ayah.quran.domain.prayer.NextPrayerCalculator
import com.ayah.quran.domain.prayer.PrayerAlarm
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central prayer-time orchestrator (app-scoped):
 *
 * - exposes preferences + the derived "now" state for the UI,
 * - keeps EXACTLY ONE AlarmManager alarm armed (the next prayer); every
 *   fire / settings change / timezone change / boot re-arms the next one —
 *   duplicate alarms are structurally impossible (single fixed
 *   PendingIntent + reconcile mutex).
 *
 * All recomputation paths funnel through [reconcile].
 */
@Singleton
class PrayerTimeManager @Inject constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
    private val calculator: PrayerTimesCalculator,
    private val scheduler: PrayerAlarmScheduler,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconcileMutex = Mutex()

    private val _preferences = MutableStateFlow(defaultPreferences())
    val preferences: StateFlow<PrayerPreferences> = _preferences.asStateFlow()

    /** The derived daily times for the currently selected location/day. */
    private val _todayTimes = MutableStateFlow<DayPrayerTimes?>(null)
    val todayTimes: StateFlow<DayPrayerTimes?> = _todayTimes.asStateFlow()

    private val _tomorrowFajr = MutableStateFlow<LocalDateTime?>(null)
    val tomorrowFajr: StateFlow<LocalDateTime?> = _tomorrowFajr.asStateFlow()

    /** True when the OS granted an exact alarm for the current schedule. */
    private val _exactAlarmGranted = MutableStateFlow(true)
    val exactAlarmGranted: StateFlow<Boolean> = _exactAlarmGranted.asStateFlow()

    /** Recompute state and re-arm the next alarm. Safe to call repeatedly. */
    fun reconcile() {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            _preferences.value = prefs
            reconcileMutex.withLock {
                val location = prefs.location
                if (location == null) {
                    _todayTimes.value = null
                    _tomorrowFajr.value = null
                    scheduler.cancel()
                    return@withLock
                }
                val zone = ZoneId.of(location.timezoneId)
                val today = LocalDate.now(zone)
                val todayTimes = calculator.calculate(
                    date = today,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timezoneId = zone,
                    method = prefs.method,
                    asrMethod = prefs.asrMethod,
                    highLatitudeRule = prefs.highLatitudeRule,
                    adjustments = prefs.adjustments,
                )
                val tomorrowTimes = calculator.calculate(
                    date = today.plusDays(1),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timezoneId = zone,
                    method = prefs.method,
                    asrMethod = prefs.asrMethod,
                    highLatitudeRule = prefs.highLatitudeRule,
                    adjustments = prefs.adjustments,
                )
                _todayTimes.value = todayTimes
                _tomorrowFajr.value = tomorrowTimes.fajr

                val next = nextAlarm(todayTimes, tomorrowTimes.fajr, zone)
                if (next == null) {
                    scheduler.cancel()
                } else {
                    _exactAlarmGranted.value = scheduler.schedule(next)
                }
            }
        }
    }

    /**
     * Called by [com.ayah.quran.prayer.PrayerAlarmReceiver] when an alarm
     * fires. The caller handles notify/play; this re-arms the next prayer.
     */
    fun onAlarmFired() {
        reconcile()
    }

    /** Pure next-alarm selection (first upcoming prayer; Isha → tomorrow Fajr). */
    fun nextAlarm(
        today: DayPrayerTimes,
        tomorrowFajr: LocalDateTime,
        zone: ZoneId,
    ): PrayerAlarm? {
        val now = LocalDateTime.now(zone)
        val countdown = NextPrayerCalculator.compute(today, tomorrowFajr, now)
        val nextDate = countdown.nextTime.toLocalDate()
        val dayKey = if (nextDate != today.date) {
            nextDate.toString()
        } else {
            today.date.toString()
        }
        val triggerAt = countdown.nextTime
            .toInstant(zone.rules.getOffset(countdown.nextTime))
            .toEpochMilli()
        return PrayerAlarm(
            prayer = countdown.next,
            triggerAtMillis = triggerAt,
            dayKey = dayKey,
        )
    }

    /** Live countdown for the UI (recomputed per tick by the ViewModel). */
    fun countdown(today: DayPrayerTimes, tomorrowFajr: LocalDateTime, zone: ZoneId): PrayerCountdown =
        NextPrayerCalculator.compute(today, tomorrowFajr, LocalDateTime.now(zone))

    private fun defaultPreferences() = PrayerPreferences(
        location = null,
        autoLocationEnabled = false,
        method = com.ayah.quran.domain.model.prayer.CalculationMethod.EGYPTIAN,
        asrMethod = com.ayah.quran.domain.model.prayer.AsrMethod.STANDARD,
        highLatitudeRule = com.ayah.quran.domain.model.prayer.HighLatitudeRulePref.ANGLE_BASED,
        adjustments = com.ayah.quran.domain.model.prayer.PrayerAdjustments(),
        adhanConfig = com.ayah.quran.domain.model.prayer.PrayerAdhanConfig(),
        voiceSelection = com.ayah.quran.domain.model.prayer.PrayerVoiceSelection(),
        hijriAdjustmentDays = 0,
        customAdhanAudioUri = null,
    )
}

/** Convenience: milliseconds remaining, clamped ≥ 0 for late ticks. */
fun PrayerCountdown.remainingClamped(now: LocalDateTime): Long =
    maxOf(0L, Duration.between(now, nextTime).toMillis())

/** ZoneId of the current preferences (helper for receivers/UI). */
fun PrayerPreferences.zone(): ZoneId =
    ZoneId.of(location?.timezoneId ?: ZoneId.systemDefault().id)
