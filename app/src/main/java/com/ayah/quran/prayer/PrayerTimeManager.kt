package com.ayah.quran.prayer

import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerCountdown
import com.ayah.quran.domain.prayer.NextPrayerCalculator
import com.ayah.quran.domain.prayer.PrayerAlarm
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrayerTimeManager internal constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
    private val calculator: PrayerTimesCalculator,
    private val scheduler: PrayerAlarmScheduler,
    private val scope: CoroutineScope,
    private val clock: Clock,
) {

    @Inject
    constructor(
        preferencesRepository: PrayerPreferencesRepository,
        calculator: PrayerTimesCalculator,
        scheduler: PrayerAlarmScheduler,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        preferencesRepository = preferencesRepository,
        calculator = calculator,
        scheduler = scheduler,
        scope = scope,
        clock = Clock.systemDefaultZone(),
    )

    internal constructor(
        preferencesRepository: PrayerPreferencesRepository,
        calculator: PrayerTimesCalculator,
        scheduler: PrayerAlarmScheduler,
        clock: Clock = Clock.systemDefaultZone(),
    ) : this(
        preferencesRepository = preferencesRepository,
        calculator = calculator,
        scheduler = scheduler,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        clock = clock,
    )

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

    fun reconcile() {
        scope.launch {
            reconcileNow()
        }
    }

    suspend fun reconcileNow() {
        reconcileInternal(
            firedPrayer = null,
            firedDayKey = null,
            scheduledAtMillis = 0L,
        )
    }

    fun onAlarmFired() {
        reconcile()
    }

    suspend fun onAlarmFiredNow(
        firedPrayer: Prayer? = null,
        firedDayKey: String? = null,
        scheduledAtMillis: Long = 0L,
    ) {
        reconcileInternal(
            firedPrayer = firedPrayer,
            firedDayKey = firedDayKey,
            scheduledAtMillis = scheduledAtMillis,
        )
    }

    private suspend fun reconcileInternal(
        firedPrayer: Prayer?,
        firedDayKey: String?,
        scheduledAtMillis: Long,
    ) {
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
            val nowMillis = clock.millis()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
            val today = now.toLocalDate()
            val todayTimes = calculateDay(today, location, zone, prefs)
            val tomorrow = today.plusDays(1)
            val tomorrowTimes = calculateDay(tomorrow, location, zone, prefs)
            _todayTimes.value = todayTimes
            _tomorrowFajr.value = tomorrowTimes.fajr

            val allToday = listOf(
                todayTimes.fajr to "FAJR",
                todayTimes.dhuhr to "DHUHR",
                todayTimes.asr to "ASR",
                todayTimes.maghrib to "MAGHRIB",
                todayTimes.isha to "ISHA",
            )
            val overdue = allToday.filter { (time, _) -> time.isBefore(now) }
            if (overdue.isNotEmpty()) {
                android.util.Log.w(
                    com.ayah.quran.prayer.PrayerAlarmLog.TAG,
                    "RECONCILE | overdue prayers detected: " +
                        overdue.joinToString(", ") { (time, name) -> "$name at $time" },
                )
            }

            val normalizedFiredDayKey = AlarmOccurrenceKey.normalizeDayKey(firedDayKey)
            val firedDate = normalizedFiredDayKey?.let { LocalDate.parse(it) }
            val candidateDays = mutableListOf(todayTimes, tomorrowTimes)
            if (firedDate != null && !firedDate.isBefore(tomorrow)) {
                candidateDays += calculateDay(today.plusDays(2), location, zone, prefs)
            }
            var next = selectNextOccurrence(
                days = candidateDays,
                zone = zone,
                nowMillis = nowMillis,
                firedPrayer = firedPrayer,
                firedDayKey = normalizedFiredDayKey,
                scheduledAtMillis = scheduledAtMillis,
            )
            if (next == null && candidateDays.size == 2) {
                val dayAfterTomorrow = calculateDay(today.plusDays(2), location, zone, prefs)
                next = selectNextOccurrence(
                    days = listOf(dayAfterTomorrow),
                    zone = zone,
                    nowMillis = nowMillis,
                    firedPrayer = firedPrayer,
                    firedDayKey = normalizedFiredDayKey,
                    scheduledAtMillis = scheduledAtMillis,
                )
            }
            if (next == null) {
                android.util.Log.w(
                    PrayerAlarmLog.TAG,
                    "RECONCILE | no upcoming prayer occurrence found; cancelling alarm",
                )
                scheduler.cancel()
            } else {
                _exactAlarmGranted.value = scheduler.schedule(next)
            }
        }
    }

    private fun calculateDay(
        date: LocalDate,
        location: com.ayah.quran.domain.model.prayer.PrayerLocation,
        zone: ZoneId,
        prefs: PrayerPreferences,
    ): DayPrayerTimes = calculator.calculate(
        date = date,
        latitude = location.latitude,
        longitude = location.longitude,
        timezoneId = zone,
        method = prefs.method,
        asrMethod = prefs.asrMethod,
        highLatitudeRule = prefs.highLatitudeRule,
        adjustments = prefs.adjustments,
    )

    private fun selectNextOccurrence(
        days: List<DayPrayerTimes>,
        zone: ZoneId,
        nowMillis: Long,
        firedPrayer: Prayer?,
        firedDayKey: String?,
        scheduledAtMillis: Long,
    ): PrayerAlarm? {
        for (day in days) {
            val dayKey = day.date.toString()
            val prayers = listOf(
                Prayer.FAJR to day.fajr,
                Prayer.DHUHR to day.dhuhr,
                Prayer.ASR to day.asr,
                Prayer.MAGHRIB to day.maghrib,
                Prayer.ISHA to day.isha,
            )
            for ((prayer, time) in prayers) {
                val triggerAtMillis = time
                    .toInstant(zone.rules.getOffset(time))
                    .toEpochMilli()
                val candidate = PrayerAlarm(
                    prayer = prayer,
                    triggerAtMillis = triggerAtMillis,
                    dayKey = dayKey,
                )
                if (candidate.triggerAtMillis <= nowMillis) continue
                if (isFiredOrSupersededOccurrence(candidate, firedPrayer, firedDayKey, scheduledAtMillis)) {
                    continue
                }
                return candidate
            }
        }
        return null
    }

    private fun isFiredOrSupersededOccurrence(
        candidate: PrayerAlarm,
        firedPrayer: Prayer?,
        firedDayKey: String?,
        scheduledAtMillis: Long,
    ): Boolean {
        if (firedPrayer == null) return false
        if (candidate.prayer != firedPrayer) return false
        if (firedDayKey == null && scheduledAtMillis <= 0L) return false
        if (firedDayKey != null && candidate.dayKey != firedDayKey) return false
        return scheduledAtMillis <= 0L || candidate.triggerAtMillis <= scheduledAtMillis
    }

    /** Pure next-alarm selection (first upcoming prayer; Isha → tomorrow Fajr). */
    fun nextAlarm(
        today: DayPrayerTimes,
        tomorrowFajr: LocalDateTime,
        zone: ZoneId,
        now: LocalDateTime = LocalDateTime.now(clock.withZone(zone)),
    ): PrayerAlarm? {
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
        NextPrayerCalculator.compute(today, tomorrowFajr, LocalDateTime.now(clock.withZone(zone)))

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
