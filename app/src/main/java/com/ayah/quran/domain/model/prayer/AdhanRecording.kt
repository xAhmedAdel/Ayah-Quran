package com.ayah.quran.domain.model.prayer

/** Library category for an adhan recording (Phase 15). */
enum class AdhanCategory {
    /** Bundled in the APK (Phase 13 recordings). */
    BUNDLED,
    MAKKAH,
    MADINAH,
    FAMOUS,
    EGYPTIAN,
    REGIONS,
}

/**
 * One adhan recording — either BUNDLED in the APK or a REMOTE entry the user
 * can download (Phase 15). Remote entries carry their verified source URL,
 * expected size (from the source catalog metadata) and — when pre-computed —
 * the expected SHA-256. Audio is never re-encoded or altered.
 */
data class AdhanRecording(
    /** Stable identifier (== res/raw resource name for bundled entries). */
    val id: String,
    val displayNameAr: String,
    val displayNameEn: String,
    val descriptionAr: String?,
    val descriptionEn: String?,
    /** Duration in whole seconds, from the source catalog metadata. */
    val durationSeconds: Int,
    /** Verified license short name, e.g. "CC0 1.0", "CC BY-SA 4.0", "Public domain". */
    val license: String,
    /** Required attribution line (rendered in Settings → Licenses). */
    val attribution: String,
    /** Original source page URL. */
    val sourceUrl: String,
    val category: AdhanCategory = AdhanCategory.BUNDLED,
    val muadhinNameAr: String? = null,
    val muadhinNameEn: String? = null,
    val mosqueCityAr: String? = null,
    val mosqueCityEn: String? = null,
    /** True when the audio ships inside the APK (no download needed). */
    val isBundled: Boolean = true,
    /** HTTPS download URL for remote entries. */
    val downloadUrl: String? = null,
    /** File extension WITHOUT the dot (ogg / mp3 / wav …). */
    val fileExtension: String = "ogg",
    /** Exact size from the source catalog metadata (download validation). */
    val expectedSizeBytes: Long? = null,
    /**
     * Expected SHA-256. When null (not yet pre-computed), the download
     * manager computes and PERSISTS the hash on first download and verifies
     * against it afterwards; size + audio-magic checks always apply.
     */
    val expectedSha256: String? = null,
    /** Human-readable quality note, e.g. "OGG Vorbis". */
    val audioQuality: String? = null,
)
