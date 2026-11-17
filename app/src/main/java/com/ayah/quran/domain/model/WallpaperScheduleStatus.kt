package com.ayah.quran.domain.model

/**
 * Honest scheduling status. WorkManager periodic work is inexact — the
 * interval is an estimate, never a guaranteed execution minute.
 */
data class WallpaperScheduleStatus(
    val isScheduled: Boolean,
    val intervalMinutes: Int?,
)
