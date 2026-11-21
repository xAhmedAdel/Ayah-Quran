package com.ayah.quran.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ayah.quran.data.prayer.AlarmPrayerAlarmScheduler
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var manager: PrayerTimeManager
    @Inject lateinit var notifications: PrayerNotifications
    @Inject lateinit var occurrenceGuard: PrayerAlarmOccurrenceGuard
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmPrayerAlarmScheduler.ACTION_PRAYER_ALARM) return
        val prayer = intent
            .getStringExtra(AlarmPrayerAlarmScheduler.EXTRA_PRAYER)
            ?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
            ?: return
        val dayKey = AlarmOccurrenceKey.normalizeDayKey(
            intent.getStringExtra(AlarmPrayerAlarmScheduler.EXTRA_DAY_KEY),
        )
        val scheduledAtMillis = intent.getLongExtra(
            AlarmPrayerAlarmScheduler.EXTRA_SCHEDULED_AT,
            0L,
        )

        val correlationId = "$prayer-" +
            java.text.SimpleDateFormat("yyyy-MM-dd-HH:mm", java.util.Locale.US)
                .format(java.util.Date(scheduledAtMillis))
        PrayerAlarmLog.receiverEntered(correlationId, prayer.name)

        if (scheduledAtMillis > 0) {
            PrayerAlarmLog.receiverDelay(correlationId, scheduledAtMillis, System.currentTimeMillis())
        }

        val result = goAsync()
        val scope = if (::appScope.isInitialized) {
            appScope
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            handleAlarm(
                prayer = prayer,
                dayKey = dayKey,
                scheduledAtMillis = scheduledAtMillis,
                correlationId = correlationId,
                preferencesRepository = preferencesRepository,
                occurrenceGuard = occurrenceGuard,
                manager = manager,
                onDispatch = { mode, voiceId ->
                    dispatchPrayerAlert(context, prayer, mode, voiceId, correlationId)
                },
                onFinish = { result.finish() },
            )
        }
    }

    private fun dispatchPrayerAlert(
        context: Context,
        prayer: Prayer,
        mode: AdhanMode,
        voiceId: String,
        correlationId: String,
    ) {
        when (mode) {
            AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT -> {
                notifications.post(prayer, mode)
                PrayerAlarmLog.notificationPosted(
                    correlationId,
                    if (mode == AdhanMode.NOTIFICATION_SOUND) {
                        PrayerNotifications.CHANNEL_ALERT
                    } else {
                        PrayerNotifications.CHANNEL_SILENT
                    },
                )
            }

            AdhanMode.FULL_ADHAN -> {
                val serviceIntent = Intent(context, AdhanPlaybackService::class.java)
                    .putExtra(AdhanPlaybackService.EXTRA_PRAYER, prayer.name)
                    .putExtra(AdhanPlaybackService.EXTRA_TEST, false)
                runCatching {
                    context.startForegroundService(serviceIntent)
                    PrayerAlarmLog.playbackStarted(correlationId, voiceId)
                }.onFailure { e ->
                    PrayerAlarmLog.playbackFallback(
                        correlationId,
                        "startForegroundService failed: ${e.message}",
                    )
                    notifications.post(prayer, AdhanMode.SILENT)
                }
            }

            AdhanMode.DISABLED -> {
                PrayerAlarmLog.notificationSuppressed(correlationId, "DISABLED")
            }
        }
    }

    companion object {
        private const val ALARM_TIMEOUT_MILLIS = 8_000L

        internal suspend fun handleAlarm(
            prayer: Prayer,
            dayKey: String?,
            scheduledAtMillis: Long,
            correlationId: String,
            preferencesRepository: PrayerPreferencesRepository,
            occurrenceGuard: PrayerAlarmOccurrenceGuard,
            manager: PrayerTimeManager,
            onDispatch: (AdhanMode, String) -> Unit,
            onFinish: () -> Unit,
            clock: Clock = Clock.systemDefaultZone(),
        ) {
            try {
                withTimeout(ALARM_TIMEOUT_MILLIS) {
                    val prefs = preferencesRepository.preferences.first()
                    val mode = prefs.adhanConfig.modeOf(prayer)
                    val voiceId = prefs.voiceSelection.voiceOf(prayer)
                    PrayerAlarmLog.notificationSuppressed(
                        correlationId,
                        "mode=${mode.name} voice=$voiceId",
                    )

                    val nowMillis = clock.millis()
                    val zone = prefs.zone()
                    val retentionCutoff = Instant.ofEpochMilli(nowMillis)
                        .atZone(zone)
                        .toLocalDate()
                        .minusDays(PrayerAlarmOccurrenceGuard.RETENTION_DAYS)
                    val normalizedDayKey = AlarmOccurrenceKey.normalizeDayKey(dayKey)
                    val occurrenceKey = AlarmOccurrenceKey.build(
                        prayer = prayer,
                        dayKey = normalizedDayKey,
                        scheduledAtMillis = scheduledAtMillis,
                        fallbackNowMillis = nowMillis,
                    )

                    if (occurrenceGuard.tryMarkFired(occurrenceKey, retentionCutoff)) {
                        try {
                            onDispatch(mode, voiceId)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            Log.e(
                                PrayerAlarmLog.TAG,
                                "$correlationId | DISPATCH_ERROR | ${e.message}",
                                e,
                            )
                        }
                    }
                    manager.onAlarmFiredNow(
                        firedPrayer = prayer,
                        firedDayKey = normalizedDayKey,
                        scheduledAtMillis = scheduledAtMillis,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(PrayerAlarmLog.TAG, "$correlationId | RECEIVER_ERROR | ${e.message}", e)
            } finally {
                onFinish()
            }
        }
    }
}
