package com.ayah.quran.domain.model.audio

import com.ayah.quran.domain.model.prayer.AdhanCategory

/** Quran recitation entry — one surah audio file from one reciter. */
data class QuranRecitation(
    /** Stable unique identifier. */
    val id: String,
    /** Surah number (1-114). */
    val surahNumber: Int,
    /** Surah Arabic name. */
    val surahNameAr: String,
    /** Surah English name. */
    val surahNameEn: String,
    /** Reciter Arabic name. */
    val reciterNameAr: String,
    /** Reciter English name. */
    val reciterNameEn: String,
    /** Recitation style (Murattal / Mujawwad). */
    val style: String,
    /** Verified license short name. */
    val license: String,
    /** Required attribution. */
    val attribution: String,
    /** Original source page URL. */
    val sourceUrl: String,
    /** HTTPS download URL. */
    val downloadUrl: String,
    /** Expected file size in bytes (from Commons metadata). */
    val expectedSizeBytes: Long,
    val audioQuality: String,
) {
    /** Stable file name for downloaded audio. */
    val fileName: String get() = "$id.mp3"

    /** Duration formatted as m:ss. */
    val durationFormatted: String
        get() {
            val totalSec = expectedSizeBytes / 1000 // approximate
            return "$totalSec"
        }
}

/** Playback mode for Quran audio. */
enum class QuranPlaybackMode {
    CONTINUOUS,
    REPEAT_AYAH,
    REPEAT_SURAH,
    OFF,
}

/** Playback state for the Quran audio player. */
data class QuranPlaybackState(
    val recitation: QuranRecitation? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val currentAyah: Int? = null,
    val playbackMode: QuranPlaybackMode = QuranPlaybackMode.CONTINUOUS,
    val playbackSpeed: Float = 1.0f,
)
