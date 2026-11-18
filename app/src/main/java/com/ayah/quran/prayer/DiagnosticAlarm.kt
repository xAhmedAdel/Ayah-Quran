package com.ayah.quran.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic alarm (Phase 14): a REAL AlarmManager exact/windowed alarm fired
 * ~60 seconds out, producing a clear test notification. Entirely separate
 * from the prayer alarm (own action, own request code) — scheduling,
 * cancelling or firing a diagnostic never touches the prayer schedule.
 */
@Singleton
class DiagnosticAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Arms a one-shot diagnostic alarm ~[delayMs] from now. True = exact. */
    fun scheduleIn(delayMs: Long = 60_000): Boolean {
        val pending = pendingIntent()
        val triggerAt = System.currentTimeMillis() + delayMs
        val exact = canScheduleExact()
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10_000, pending)
        }
        return exact
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, DiagnosticAlarmReceiver::class.java).apply {
            action = ACTION_DIAGNOSTIC_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_DIAGNOSTIC_ALARM = "com.ayah.quran.DIAGNOSTIC_ALARM"
        private const val REQUEST_CODE = 4712
    }
}

/** Fires the diagnostic alarm: posts a clear test notification. */
@AndroidEntryPoint
class DiagnosticAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var notifications: PrayerNotifications
    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DiagnosticAlarmScheduler.ACTION_DIAGNOSTIC_ALARM) return
        val prefs = runBlocking { preferencesRepository.preferences.first() }
        // Same channel behavior as real prayer alerts, clearly test-labeled.
        val mode = when {
            prefs.adhanConfig.fajr == com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND ||
                prefs.adhanConfig.dhuhr == com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND ||
                prefs.adhanConfig.asr == com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND ||
                prefs.adhanConfig.maghrib == com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND ||
                prefs.adhanConfig.isha == com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND ->
                com.ayah.quran.domain.model.prayer.AdhanMode.NOTIFICATION_SOUND

            else -> com.ayah.quran.domain.model.prayer.AdhanMode.SILENT
        }
        notifications.postDiagnostic(mode)
    }
}
