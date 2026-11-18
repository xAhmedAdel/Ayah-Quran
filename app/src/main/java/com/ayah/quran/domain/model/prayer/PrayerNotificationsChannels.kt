package com.ayah.quran.domain.model.prayer

/**
 * Notification channel ids used by the prayer system. Kept in the domain so
 * the status probe, the notification builder and the settings UI reference
 * ONE source of truth.
 */
object PrayerNotificationsChannels {
    const val PLAYBACK = "prayer_adhan_playback"
    const val ALERT = "prayer_time_alert"
    const val SILENT = "prayer_time_silent"
}
