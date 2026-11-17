package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperHistoryEntry
import com.ayah.quran.domain.repository.WallpaperRepository
import javax.inject.Inject

/** Re-applies a previous wallpaper entry with the current settings. */
class ReapplyWallpaperFromHistoryUseCase @Inject constructor(
    private val repository: WallpaperRepository,
) {
    suspend operator fun invoke(entry: WallpaperHistoryEntry): WallpaperApplyStatus =
        repository.reapplyFromHistory(entry)
}
