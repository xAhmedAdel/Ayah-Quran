package com.ayah.quran.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ayah.quran.data.prayer.AlarmPrayerAlarmScheduler
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Fired by AlarmManager at prayer time. Dispatches per the prayer's adhan
 * mode (notification / silent / full adhan service) and re-arms the NEXT
 * prayer alarm. Duplicate protection: a single fixed PendingIntent is armed
 * at any time, plus a same-minute in-memory guard for OEM double delivery.
 */
@AndroidEntryPoint
class PrayerAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var manager: PrayerTimeManager
    @Inject lateinit var notifications: PrayerNotifications

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmPrayerAlarmScheduler.ACTION_PRAYER_ALARM) return
        val prayer = intent
            .getStringExtra(AlarmPrayerAlarmScheduler.EXTRA_PRAYER)
            ?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
            ?: return

        val result = goAsync()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        scope.launch {
            try {
                val prefs = preferencesRepository.preferences.first()
                val mode = prefs.adhanConfig.modeOf(prayer)
                if (FiredAlarmGuard.mark(prayer, prefs.adhanConfig)) {
                    when (mode) {
                        AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT ->
                            notifications.post(prayer, mode)

                        AdhanMode.FULL_ADHAN -> {
                            val intent_playback = Intent(context, AdhanPlaybackService::class.java)
                                .putExtra(AdhanPlaybackService.EXTRA_PRAYER, prayer.name)
                            runCatching {
                                context.startForegroundService(intent_playback)
                            }
                            // The service posts its own notification; if the
                            // OS blocks the foreground start, still notify.
                            if (!notifications.canNotify()) Unit
                        }

                        AdhanMode.DISABLED -> Unit
                    }
                }
                manager.onAlarmFired()
            } finally {
                result.finish()
            }
        }
    }

    /** True exactly once per prayer/dayKey per minute (OEM double-delivery guard). */
    private object FiredAlarmGuard {
        private val last = mutableMapOf<String, Long>()

        fun mark(prayer: Prayer, config: com.ayah.quran.domain.model.prayer.PrayerAdhanConfig): Boolean {
            val nowMinute = System.currentTimeMillis() / 60_000
            val key = "${prayer.name}|${config.modeOf(prayer).name}"
            synchronized(last) {
                if (last[key] == nowMinute) return false
                last[key] = nowMinute
                return true
            }
        }
    }
}
