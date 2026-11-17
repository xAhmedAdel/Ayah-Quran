package com.ayah.quran.domain.repository

import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperDestination
import kotlinx.coroutines.flow.Flow

/** User-configurable wallpaper preferences, persisted in DataStore. */
data class WallpaperSettings(
    val autoUpdateEnabled: Boolean = false,
    // PeriodicWorkRequest minimum is 15 minutes; Ayah offers 30 min and up.
    val intervalMinutes: Int = 60,
    val destinations: WallpaperDestination = WallpaperDestination.BOTH,
    val templateId: String = WallpaperTemplate.EMERALD_LUXURY_ID,
    // Multiplier on the template's base text size (0.6..1.6).
    val fontScale: Float = 1.0f,
    val verticalPosition: TextVerticalPosition = TextVerticalPosition.CENTER,
    val selectionMode: SelectionMode = SelectionMode.RANDOM,
    val fixedSurahId: Int = 1,
    val fixedAyahNumber: Int = 1,
)

interface WallpaperSettingsRepository {

    /** Emits the current settings and every subsequent change. */
    val settings: Flow<WallpaperSettings>

    /** Atomically applies a transformation to the stored settings. */
    suspend fun update(transform: (WallpaperSettings) -> WallpaperSettings)
}
