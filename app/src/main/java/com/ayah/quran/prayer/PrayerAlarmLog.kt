package com.ayah.quran.prayer

import android.util.Log

/**
 * Structured logging for the prayer alarm pipeline (Phase 16).
 * Every stage logs with the same tag and a correlation ID so the entire
 * lifecycle of one scheduled prayer event can be traced in `adb logcat`.
 *
 * Format: `PrayerAlarm | <correlation-id> | <stage> | <detail>`
 *
 * Correlation ID format: `<prayer-name>-<yyyy-MM-dd-HH:mm>` — stable across
 * scheduling, firing, notification, and next-prayer re-arm.
 */
object PrayerAlarmLog {

    const val TAG = "PrayerAlarm"

    fun scheduling(correlationId: String, triggerAtMillis: Long, exact: Boolean) {
        val when_ = java.util.Date(triggerAtMillis).toString()
        Log.d(TAG, "$correlationId | SCHEDULE | trigger=$triggerAtMillis ($when_) | exact=$exact")
    }

    fun schedulingFailed(correlationId: String, reason: String) {
        Log.e(TAG, "$correlationId | SCHEDULE_FAILED | $reason")
    }

    fun receiverEntered(correlationId: String, prayer: String) {
        Log.d(TAG, "$correlationId | RECEIVER_ENTER | prayer=$prayer | processAlive=${android.os.Process.myPid()}")
    }

    fun receiverDelay(correlationId: String, scheduledAtMillis: Long, deliveredAtMillis: Long) {
        val delayMs = deliveredAtMillis - scheduledAtMillis
        val delayMin = delayMs / 60_000
        val level = if (delayMs > 60_000) Log.WARN else Log.DEBUG
        Log.println(level, TAG, "$correlationId | DELIVERY_DELAY | ${delayMs}ms (${delayMin}min)")
    }

    fun notificationPosted(correlationId: String, channelId: String) {
        Log.d(TAG, "$correlationId | NOTIFICATION_POSTED | channel=$channelId")
    }

    fun notificationSuppressed(correlationId: String, reason: String) {
        Log.w(TAG, "$correlationId | NOTIFICATION_SUPPRESSED | $reason")
    }

    fun playbackStarted(correlationId: String, voiceId: String) {
        Log.d(TAG, "$correlationId | PLAYBACK_STARTED | voice=$voiceId")
    }

    fun playbackFallback(correlationId: String, reason: String) {
        Log.w(TAG, "$correlationId | PLAYBACK_FALLBACK | $reason")
    }

    fun nextPrayerArmed(correlationId: String, nextPrayer: String, atMillis: Long) {
        Log.d(TAG, "$correlationId | NEXT_ARMED | next=$nextPrayer at=$atMillis")
    }

    fun cancelled(correlationId: String) {
        Log.d(TAG, "$correlationId | CANCELLED")
    }

    fun reconcileStarted(reason: String) {
        Log.d(TAG, "RECONCILE | reason=$reason")
    }

    fun reconcileNoLocation() {
        Log.w(TAG, "RECONCILE | no location — alarm cancelled")
    }
}
