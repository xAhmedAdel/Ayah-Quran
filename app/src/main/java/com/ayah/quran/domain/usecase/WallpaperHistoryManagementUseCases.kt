package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.WallpaperRepository
import javax.inject.Inject

/** Deletes one wallpaper history entry (metadata only). */
class DeleteWallpaperHistoryEntryUseCase @Inject constructor(
    private val repository: WallpaperRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteHistoryEntry(id)
}

/** Clears all wallpaper history entries (metadata only). */
class ClearWallpaperHistoryUseCase @Inject constructor(
    private val repository: WallpaperRepository,
) {
    suspend operator fun invoke() = repository.clearHistory()
}
