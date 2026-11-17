package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.repository.WallpaperSchedulingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the honest WorkManager scheduling status. */
class ObserveWallpaperScheduleStatusUseCase @Inject constructor(
    private val repository: WallpaperSchedulingRepository,
) {
    operator fun invoke(): Flow<WallpaperScheduleStatus> = repository.observeStatus()
}

/** Enables or re-configures periodic wallpaper updates. */
class EnableWallpaperUpdatesUseCase @Inject constructor(
    private val repository: WallpaperSchedulingRepository,
) {
    suspend operator fun invoke(intervalMinutes: Int) = repository.enable(intervalMinutes)
}

/** Cancels periodic wallpaper updates (settings are preserved). */
class PauseWallpaperUpdatesUseCase @Inject constructor(
    private val repository: WallpaperSchedulingRepository,
) {
    suspend operator fun invoke() = repository.pause()
}
