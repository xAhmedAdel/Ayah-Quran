package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.WallpaperScheduleStatus
import kotlinx.coroutines.flow.Flow

/**
 * Automatic wallpaper updates via WorkManager unique periodic work.
 * Scheduling survives process death and device restarts (WorkManager
 * persists its own database); pause simply cancels the unique work.
 */
interface WallpaperSchedulingRepository {

    /**
     * Enables (or re-configures) periodic updates. [intervalMinutes] must be
     * one of the supported intervals (30/60/180/360/720/1440).
     */
    suspend fun enable(intervalMinutes: Int)

    /** Cancels the periodic work without touching user settings. */
    suspend fun pause()

    /** Current scheduling status as reported by WorkManager. */
    fun observeStatus(): Flow<WallpaperScheduleStatus>
}
