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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var manager: PrayerTimeManager
    @Inject lateinit var notifications: PrayerNotifications
    @Inject lateinit var occurrenceGuard: PrayerAlarmOccurrenceGuard
    @Inject lateinit var wakeLock: PrayerAlarmWakeLock
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
        launchAlarmWork(
            scope = scope,
            correlationId = correlationId,
            onFinish = { result.finish() },
        ) { finishOnce ->
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
                onFinish = finishOnce,
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
        val activeWakeLock = if (::wakeLock.isInitialized) wakeLock else null
        routePrayerAlert(
            mode = mode,
            voiceId = voiceId,
            correlationId = correlationId,
            wakeLock = activeWakeLock,
            onPostNotification = { notifyMode ->
                notifications.post(prayer, notifyMode)
            },
            onStartFullAdhanService = { tokenId ->
                val serviceIntent = Intent(context, AdhanPlaybackService::class.java)
                    .putExtra(AdhanPlaybackService.EXTRA_PRAYER, prayer.name)
                    .putExtra(AdhanPlaybackService.EXTRA_TEST, false)
                    .apply {
                        if (tokenId > PrayerAlarmWakeLock.INVALID_TOKEN_ID) {
                            putExtra(AdhanPlaybackService.EXTRA_WAKE_LOCK_TOKEN, tokenId)
                        }
                    }
                context.startForegroundService(serviceIntent)
            },
            onFallbackAlert = { fallbackMode ->
                notifications.post(prayer, fallbackMode)
            },
        )
    }

    companion object {
        private const val ALARM_TIMEOUT_MILLIS = 8_000L

        internal fun launchAlarmWork(
            scope: CoroutineScope,
            correlationId: String,
            onFinish: () -> Unit,
            block: suspend (finishOnce: () -> Unit) -> Unit,
        ) {
            val finished = AtomicBoolean(false)
            val finishOnce: () -> Unit = {
                if (finished.compareAndSet(false, true)) {
                    runCatching { onFinish() }
                        .onFailure { e ->
                            Log.w(
                                PrayerAlarmLog.TAG,
                                "$correlationId | FINISH_ERROR | ${e.javaClass.simpleName}",
                            )
                        }
                }
            }
            if (!scope.isActive) {
                finishOnce()
                return
            }
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                Log.e(
                    PrayerAlarmLog.TAG,
                    "$correlationId | LAUNCH_ERROR | ${throwable.javaClass.simpleName}",
                    throwable,
                )
                finishOnce()
            }
            try {
                val job = scope.launch(exceptionHandler) {
                    try {
                        block(finishOnce)
                    } finally {
                        finishOnce()
                    }
                }
                job.invokeOnCompletion { finishOnce() }
            } catch (t: Throwable) {
                finishOnce()
                if (t is Error) throw t
            }
        }

        internal fun routePrayerAlert(
            mode: AdhanMode,
            voiceId: String,
            correlationId: String,
            wakeLock: PrayerAlarmWakeLock?,
            onPostNotification: (AdhanMode) -> Boolean,
            onStartFullAdhanService: (tokenId: Long) -> Unit,
            onFallbackAlert: (AdhanMode) -> Boolean,
            onLogNotificationPosted: (String) -> Unit = { channel ->
                PrayerAlarmLog.notificationPosted(correlationId, channel)
            },
            onLogNotificationSuppressed: (String) -> Unit = { reason ->
                PrayerAlarmLog.notificationSuppressed(correlationId, reason)
            },
        ) {
            when (mode) {
                AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT -> {
                    val channel = PrayerNotifications.channelIdFor(mode)
                    val posted = onPostNotification(mode)
                    if (posted) {
                        onLogNotificationPosted(channel)
                    } else {
                        onLogNotificationSuppressed("POST_NOTIFICATIONS_DENIED channel=$channel")
                    }
                }

                AdhanMode.FULL_ADHAN -> {
                    dispatchFullAdhan(
                        voiceId = voiceId,
                        correlationId = correlationId,
                        wakeLock = wakeLock,
                        startService = onStartFullAdhanService,
                        onFallbackAlert = onFallbackAlert,
                        onLogNotificationPosted = onLogNotificationPosted,
                        onLogNotificationSuppressed = onLogNotificationSuppressed,
                    )
                }

                AdhanMode.DISABLED -> {
                    onLogNotificationSuppressed("DISABLED")
                }
            }
        }

        internal fun dispatchFullAdhan(
            voiceId: String,
            correlationId: String,
            wakeLock: PrayerAlarmWakeLock?,
            startService: (tokenId: Long) -> Unit,
            onFallbackAlert: (AdhanMode) -> Boolean,
            onLogNotificationPosted: (String) -> Unit = { channel ->
                PrayerAlarmLog.notificationPosted(correlationId, channel)
            },
            onLogNotificationSuppressed: (String) -> Unit = { reason ->
                PrayerAlarmLog.notificationSuppressed(correlationId, reason)
            },
        ): Boolean {
            val lease = wakeLock?.acquireHandover()
            val tokenId = lease?.tokenId ?: PrayerAlarmWakeLock.INVALID_TOKEN_ID
            try {
                startService(tokenId)
            } catch (e: Error) {
                lease?.release()
                throw e
            } catch (t: Throwable) {
                lease?.release()
                val fallbackMode = PrayerNotifications.FULL_ADHAN_FALLBACK_MODE
                val fallbackChannel = PrayerNotifications.channelIdFor(fallbackMode)
                PrayerAlarmLog.playbackFallback(
                    correlationId,
                    "startForegroundService failed: ${t.message}; fallback=${fallbackMode.name}",
                )
                val posted = try {
                    onFallbackAlert(fallbackMode)
                } catch (e: Error) {
                    throw e
                } catch (postError: Throwable) {
                    Log.e(
                        PrayerAlarmLog.TAG,
                        "$correlationId | FALLBACK_POST_ERROR | ${postError.javaClass.simpleName}",
                        postError,
                    )
                    false
                }
                if (posted) {
                    onLogNotificationPosted(fallbackChannel)
                } else {
                    onLogNotificationSuppressed("FALLBACK_NOT_POSTED channel=$fallbackChannel")
                }
                return posted
            }
            runCatching {
                PrayerAlarmLog.playbackStarted(correlationId, voiceId)
            }
            return true
        }

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
