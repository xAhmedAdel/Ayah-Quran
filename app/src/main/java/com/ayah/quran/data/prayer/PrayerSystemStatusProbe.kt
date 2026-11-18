package com.ayah.quran.data.prayer

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels
import com.ayah.quran.domain.model.prayer.VendorDetection
import com.ayah.quran.domain.model.prayer.VendorKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-side probe for the permission dashboard. Every call performs a
 * REAL check at read time — callers must refresh on resume so revoked
 * permissions flip the UI immediately. No caching of grant states.
 */
@Singleton
class PrayerSystemStatusProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** API < 33: notifications are granted at install. */
    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * The prayer-alert channel must EXIST and be non-blocked. App-level
     * enablement alone is NOT sufficient (spec §3): the user can disable a
     * single channel from system settings.
     */
    fun adhanChannelEnabled(): Boolean {
        val channels = listOf(PrayerNotificationsChannels.ALERT, PrayerNotificationsChannels.SILENT)
        val states = channels.map { id ->
            val channel = notificationManager.getNotificationChannel(id)
            channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        // Healthy when at least one usable prayer-notification channel exists.
        return states.any { it }
    }

    /** API < 31: exact alarms always allowed; 31+: user-granted special access. */
    fun exactAlarmsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun vendorKind(): VendorKind = VendorDetection.classify(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
    )
}
