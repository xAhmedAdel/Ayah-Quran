package com.ayah.quran.prayer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ayah.quran.R
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Prayer-time notifications: three channels (full adhan playback service,
 * short notification sound, silent). Notification sound behavior lives in
 * the channel; full adhan audio is played by the service, never by the
 * notification (they are kept separate per spec).
 */
@Singleton
class PrayerNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannels() {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                PrayerNotificationsChannels.PLAYBACK,
                context.getString(R.string.prayer_channel_playback),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(null, null) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PrayerNotificationsChannels.ALERT,
                context.getString(R.string.prayer_channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // Platform default notification sound (short alert), separate
                // from full adhan playback by design.
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                PrayerNotificationsChannels.SILENT,
                context.getString(R.string.prayer_channel_silent),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setSound(null, null); enableVibration(false) },
        )
    }

    /** Can we post a notification right now (API 33+ permission)? */
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The prayer-time notification ("حان الآن وقت صلاة …"). */
    fun prayerNotification(prayer: Prayer, mode: AdhanMode): Notification {
        val title = context.getString(R.string.prayer_notification_title, prayerName(prayer))
        val channelId = when (mode) {
            AdhanMode.NOTIFICATION_SOUND -> CHANNEL_ALERT
            else -> CHANNEL_SILENT
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    /** Clear-labeled TEST notification on the SAME alert channel. */
    fun postTest() {
        createChannels()
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, PrayerNotificationsChannels.ALERT)
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setContentTitle(context.getString(R.string.prayer_test_notification_title))
            .setContentText(context.getString(R.string.prayer_test_notification_body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, notification)
    }

    /** Diagnostic-labeled notification on the SAME channel a real prayer uses. */
    fun postDiagnostic(mode: AdhanMode) {
        createChannels()
        if (!canNotify()) return
        val channelId = when (mode) {
            AdhanMode.NOTIFICATION_SOUND -> PrayerNotificationsChannels.ALERT
            else -> PrayerNotificationsChannels.SILENT
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setContentTitle(context.getString(R.string.prayer_diagnostic_alarm_title))
            .setContentText(context.getString(R.string.prayer_diagnostic_alarm_body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DIAGNOSTIC, notification)
    }

    fun post(prayer: Prayer, mode: AdhanMode) {
        if (!canNotify()) return
        createChannels()
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_PRAYER, prayerNotification(prayer, mode))
    }

    fun postStatus(id: Int, notification: Notification) {
        if (!canNotify()) return
        createChannels()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Localized prayer name for notifications/UI. */
    fun prayerName(prayer: Prayer): String = context.getString(
        when (prayer) {
            Prayer.FAJR -> R.string.prayer_name_fajr
            Prayer.DHUHR -> R.string.prayer_name_dhuhr
            Prayer.ASR -> R.string.prayer_name_asr
            Prayer.MAGHRIB -> R.string.prayer_name_maghrib
            Prayer.ISHA -> R.string.prayer_name_isha
        },
    )

    companion object {
        val CHANNEL_PLAYBACK = PrayerNotificationsChannels.PLAYBACK
        val CHANNEL_ALERT = PrayerNotificationsChannels.ALERT
        val CHANNEL_SILENT = PrayerNotificationsChannels.SILENT
        const val NOTIFICATION_ID_PRAYER = 6001
        const val NOTIFICATION_ID_PLAYBACK = 6002
        const val NOTIFICATION_ID_TEST = 6010
        const val NOTIFICATION_ID_DIAGNOSTIC = 6011
    }
}

/** True when this prayer's config should trigger the audio service. */
fun PrayerPreferences.shouldPlayFullAdhan(prayer: Prayer): Boolean =
    adhanConfig.modeOf(prayer) == AdhanMode.FULL_ADHAN

/** True when a (sound or silent) notification should be posted. */
fun PrayerPreferences.shouldNotify(prayer: Prayer): Boolean =
    adhanConfig.modeOf(prayer) in setOf(AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT)
