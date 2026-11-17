package com.ayah.quran.domain.prayer

import com.ayah.quran.domain.model.prayer.Prayer

/** One concrete upcoming prayer alarm. */
data class PrayerAlarm(
    val prayer: Prayer,
    val triggerAtMillis: Long,
    /** Local date of the prayer (yyyy-MM-dd) — dedupe key component. */
    val dayKey: String,
)

/**
 * Schedules the NEXT prayer alarm. The scheduler keeps exactly ONE live
 * alarm: firing (or any settings/location/time change) re-arms the next one.
 * This design makes duplicate alarms structurally impossible and reboot/
 * timezone reconciliation trivial (re-arm from persisted state).
 */
interface PrayerAlarmScheduler {

    /**
     * (Re)arms the alarm for [alarm], replacing any previously armed one.
     * Returns true when the OS granted an EXACT alarm; false means the
     * caller should proceed with the best-effort inexact window.
     */
    fun schedule(alarm: PrayerAlarm): Boolean

    /** Cancels the live alarm (e.g. adhan disabled globally). */
    fun cancel()

    /** True when the OS allows exact alarms (API 31+ restriction). */
    fun canScheduleExact(): Boolean
}
