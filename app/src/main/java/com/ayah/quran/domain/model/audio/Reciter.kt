package com.ayah.quran.domain.model.audio

/**
 * A Quran reciter offered by the app.
 *
 * Instances exist ONLY for reciters whose recitations are licensed for
 * redistribution (with attribution requirements recorded here). There is no
 * fabricated catalog: an empty licensed catalog yields an empty reciter list.
 *
 * Free of Android types by design (Clean Architecture domain layer).
 */
data class Reciter(
    /** Stable unique id (used by tracks and persisted preferences). */
    val id: String,
    val nameArabic: String,
    val nameEnglish: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    /** Required attribution line, shown in the UI when the reciter is listed. */
    val attribution: String? = null,
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    /** Whether the reciter's audio sources are currently resolvable. */
    val isAvailable: Boolean,
)
