package com.ayah.quran.domain.model

import com.ayah.quran.core.wallpaper.WallpaperTemplate

/** One wallpaper generation+application attempt, for the history screen. */
data class WallpaperHistoryEntry(
    val id: Long,
    val surahId: Int,
    val ayahNumber: Int,
    val templateId: String,
    val destinations: WallpaperDestination,
    val appliedAtMillis: Long,
    val status: WallpaperApplyStatus,
    val failureReason: String? = null,
) {
    val templateName: String
        get() = WallpaperTemplate.byId(templateId).displayName
}

/** Outcome of an application attempt, as confirmed by Android. */
enum class WallpaperApplyStatus {
    SUCCESS,
    FAILED,
}
