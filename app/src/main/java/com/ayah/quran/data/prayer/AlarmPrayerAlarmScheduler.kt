package com.ayah.quran.data.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.prayer.PrayerAlarm
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.prayer.PrayerAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager-backed scheduler. WorkManager is deliberately NOT used for
 * prayer firing — only exact (or best-effort windowed) alarms meet the
 * prayer-time accuracy requirement.
 */
@Singleton
class AlarmPrayerAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : PrayerAlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun schedule(alarm: PrayerAlarm): Boolean {
        val exact = canScheduleExact()
        val pending = pendingIntent(alarm.prayer, alarm.dayKey)
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAtMillis,
                pending,
            )
        } else {
            // Exact alarms not granted (user/OS restriction): best-effort
            // window within ±10 min. Documented limitation — the in-app
            // Advanced section guides the user to the exact-alarm setting.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                alarm.triggerAtMillis,
                10 * 60 * 1000L,
                pending,
            )
        }
        return exact
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(Prayer.FAJR, dayKey = "cancel"))
    }

    private fun pendingIntent(prayer: Prayer, dayKey: String): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER, prayer.name)
            putExtra(EXTRA_DAY_KEY, dayKey)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_PRAYER_ALARM = "com.ayah.quran.PRAYER_ALARM"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_DAY_KEY = "day_key"
        const val REQUEST_CODE = 4711
    }
}
