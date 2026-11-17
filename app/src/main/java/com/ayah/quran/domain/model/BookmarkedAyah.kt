package com.ayah.quran.domain.model

/** A bookmark joined with its verse text and surah names, for list display. */
data class BookmarkedAyah(
    val bookmarkId: Long,
    val surahId: Int,
    val ayahNumber: Int,
    val createdAtMillis: Long,
    val textUthmani: String,
    val surahNameArabic: String,
    val surahNameTransliterated: String,
)
