package com.ayah.quran.domain.model.audio

/**
 * Where an [AudioTrack]'s audio lives. No Android Uri: [source] is a plain
 * string (absolute file path or https URL) interpreted by the data layer.
 */
enum class AudioSourceType {
    /** No resolvable source yet — the track is not playable. */
    NONE,

    /** A local file already present on the device (future offline audio). */
    LOCAL_FILE,

    /** A remote https stream (future licensed catalog). */
    REMOTE_URL,
}

/**
 * One playable recitation track (surah-level by convention; an optional
 * ayah number is reserved for future per-ayah catalogs — per-ayah timing
 * data is NOT assumed to exist).
 */
data class AudioTrack(
    /** Stable unique id within its reciter's catalog. */
    val id: String,
    val reciterId: String,
    /** Canonical surah number (1..114), matching the imported Quran dataset. */
    val surahNumber: Int,
    /** Optional ayah reference for future ayah-scoped catalogs. */
    val ayahNumber: Int? = null,
    val source: String? = null,
    val sourceType: AudioSourceType = AudioSourceType.NONE,
    val durationMs: Long? = null,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
) {
    /** A track is playable only when its source is present and typed. */
    val hasPlayableSource: Boolean
        get() = sourceType != AudioSourceType.NONE && !source.isNullOrBlank()
}
