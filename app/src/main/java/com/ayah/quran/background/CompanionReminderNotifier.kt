package com.ayah.quran.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.CompanionReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompanionReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun createChannel() {
        val localized = AppLocale.wrap(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.reminders_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localized.getString(R.string.reminders_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postReminder(type: CompanionReminderType): Boolean {
        if (!canPostNotifications()) return false
        createChannel()
        val localized = AppLocale.wrap(context)
        val (titleRes, bodyRes) = contentResourcesFor(type)
        val launchIntent = buildLaunchIntent(context, type)
        val pendingIntent = PendingIntent.getActivity(
            context,
            type.notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ayah_star)
            .setContentTitle(localized.getString(titleRes))
            .setContentText(localized.getString(bodyRes))
            .setStyle(NotificationCompat.BigTextStyle().bigText(localized.getString(bodyRes)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(type.notificationId, notification)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val CHANNEL_ID = "ayah_companion_reminders"
        const val EXTRA_COMPANION_ROUTE = "com.ayah.quran.extra.COMPANION_ROUTE"

        fun buildLaunchIntent(context: Context, type: CompanionReminderType): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_COMPANION_ROUTE, type.deepLinkRoute)
            }

        internal fun contentResourcesFor(type: CompanionReminderType): Pair<Int, Int> = when (type) {
            CompanionReminderType.MORNING_ADHKAR ->
                R.string.reminders_notif_morning_title to R.string.reminders_notif_morning_body
            CompanionReminderType.EVENING_ADHKAR ->
                R.string.reminders_notif_evening_title to R.string.reminders_notif_evening_body
            CompanionReminderType.DAILY_REFLECTION ->
                R.string.reminders_notif_reflection_title to R.string.reminders_notif_reflection_body
        }
    }
}
