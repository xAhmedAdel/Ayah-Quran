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
import javax.inject.Inject

@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var manager: PrayerTimeManager
    @Inject lateinit var notifications: PrayerNotifications
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmPrayerAlarmScheduler.ACTION_PRAYER_ALARM) return
        val prayer = intent
            .getStringExtra(AlarmPrayerAlarmScheduler.EXTRA_PRAYER)
            ?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
            ?: return
        val scheduledAtMillis = intent.getLongExtra(
            com.ayah.quran.data.prayer.AlarmPrayerAlarmScheduler.EXTRA_SCHEDULED_AT,
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
            try {
                withTimeout(8_000L) {
                    val prefs = preferencesRepository.preferences.first()
                    val mode = prefs.adhanConfig.modeOf(prayer)
                    val voiceId = prefs.voiceSelection.voiceOf(prayer)
                    PrayerAlarmLog.notificationSuppressed(
                        correlationId,
                        "mode=${mode.name} voice=$voiceId",
                    )

                    if (FiredAlarmGuard.mark(prayer, mode)) {
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
                    manager.onAlarmFiredNow()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(PrayerAlarmLog.TAG, "$correlationId | RECEIVER_ERROR | ${e.message}", e)
            } finally {
                result.finish()
            }
        }
    }

    /** True exactly once per prayer/mode per minute (OEM double-delivery guard). */
    private object FiredAlarmGuard {
        private val last = mutableMapOf<String, Long>()

        fun mark(prayer: Prayer, mode: AdhanMode): Boolean {
            val nowMinute = System.currentTimeMillis() / 60_000
            val key = "${prayer.name}|${mode.name}"
            synchronized(last) {
                if (last[key] == nowMinute) return false
                last[key] = nowMinute
                return true
            }
        }
    }
}
