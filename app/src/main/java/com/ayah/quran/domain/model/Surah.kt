package com.ayah.quran.domain.model

/** Where a surah was revealed. */
enum class RevelationType {
    MECCAN,
    MEDINAN,
}

/**
 * A surah (chapter) of the Quran.
 *
 * [id] is the canonical surah number (1..114) in the order of the Madani mushaf.
 */
data class Surah(
    val id: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val nameEnglish: String,
    val revelationType: RevelationType,
    val ayahCount: Int,
)
