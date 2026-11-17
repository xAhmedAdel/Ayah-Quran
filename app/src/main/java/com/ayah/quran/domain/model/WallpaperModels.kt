package com.ayah.quran.domain.model

/** Where a generated wallpaper is applied. */
enum class WallpaperDestination {
    SYSTEM,
    LOCK,
    BOTH,
}

/** How the next wallpaper ayah is chosen. */
enum class SelectionMode {
    /** Random ayah from all 6,236 verified Quran ayahs. */
    RANDOM,

    /** Random ayah from the user's selected collection. */
    RANDOM_FROM_SELECTION,

    /** Sequential rotation through the user's selected collection. */
    SEQUENTIAL_FROM_SELECTION,

    /** Deterministic daily verse from the entire Quran. */
    DAILY,
}

/** Vertical placement of the verse block inside the safe area. */
enum class TextVerticalPosition {
    TOP,
    CENTER,
    BOTTOM,
}

/** A single ayah the user has selected for wallpaper rotation. */
data class SelectedAyah(
    val surahId: Int,
    val ayahNumber: Int,
) {
    /** Stable id in "surahId:ayahNumber" format. */
    val stableId: String get() = "$surahId:$ayahNumber"

    companion object {
        fun fromStableId(id: String): SelectedAyah? {
            val parts = id.split(":")
            if (parts.size != 2) return null
            val surah = parts[0].toIntOrNull() ?: return null
            val ayah = parts[1].toIntOrNull() ?: return null
            if (surah < 1 || surah > 114 || ayah < 1) return null
            return SelectedAyah(surahId = surah, ayahNumber = ayah)
        }
    }
}
