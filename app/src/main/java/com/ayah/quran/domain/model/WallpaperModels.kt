package com.ayah.quran.domain.model

/** Where a generated wallpaper is applied. */
enum class WallpaperDestination {
    SYSTEM,
    LOCK,
    BOTH,
}

/** How the next wallpaper ayah is chosen. */
enum class SelectionMode {
    RANDOM,
    SEQUENTIAL,
    DAILY,
    SURAH, // random within a user-chosen surah
    AYAH, // a fixed user-chosen verse
}

/** Vertical placement of the verse block inside the safe area. */
enum class TextVerticalPosition {
    TOP,
    CENTER,
    BOTTOM,
}
