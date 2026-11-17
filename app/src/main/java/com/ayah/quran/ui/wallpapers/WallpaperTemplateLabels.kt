package com.ayah.quran.ui.wallpapers

import androidx.annotation.StringRes
import com.ayah.quran.R
import com.ayah.quran.core.wallpaper.WallpaperTemplate

/**
 * Maps templates to localized display labels. The template itself stays
 * structured data (English [WallpaperTemplate.displayName] is a fallback
 * identifier, never shown in the UI).
 */
@StringRes
fun WallpaperTemplate.labelRes(): Int = labelResForId(id)

@StringRes
fun labelResForId(templateId: String): Int = when (templateId) {
    WallpaperTemplate.EMERALD_LUXURY_ID -> R.string.wallpapers_template_emerald_luxury
    WallpaperTemplate.MINIMAL_CREAM_ID -> R.string.wallpapers_template_minimal_cream
    WallpaperTemplate.MIDNIGHT_GOLD_ID -> R.string.wallpapers_template_midnight_gold
    WallpaperTemplate.ISLAMIC_ARCHITECTURE_ID -> R.string.wallpapers_template_islamic_architecture
    WallpaperTemplate.GEOMETRIC_ART_ID -> R.string.wallpapers_template_geometric_art
    else -> R.string.wallpapers_template_emerald_luxury
}
