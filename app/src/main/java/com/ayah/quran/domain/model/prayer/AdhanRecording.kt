package com.ayah.quran.domain.model.prayer

/**
 * One bundled adhan recording with its full provenance. Metadata only —
 * playback logic lives in the preview controller / playback service.
 *
 * Licensing is part of the data model on purpose: every recording ships with
 * its verified redistribution terms and attribution (see
 * docs/ADHAN-AUDIO-LICENSES.md).
 */
data class AdhanRecording(
    /** Stable identifier (== res/raw resource name). */
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
)
