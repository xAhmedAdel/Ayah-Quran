package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.WallpaperHistoryEntry
import com.ayah.quran.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the wallpaper application history, newest first. */
class ObserveWallpaperHistoryUseCase @Inject constructor(
    private val repository: WallpaperRepository,
) {
    operator fun invoke(): Flow<List<WallpaperHistoryEntry>> = repository.observeHistory()
}
