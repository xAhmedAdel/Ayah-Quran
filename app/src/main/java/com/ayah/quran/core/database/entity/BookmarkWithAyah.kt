package com.ayah.quran.core.database.entity

/**
 * Projection POJO for the bookmark list query: the bookmark row joined with
 * its verse text and surah names. Column names are mapped by Room to the
 * aliased columns of [com.ayah.quran.core.database.dao.BookmarkDao].
 */
data class BookmarkWithAyah(
    val bookmarkId: Long,
    val surahId: Int,
    val ayahNumber: Int,
    val createdAtMillis: Long,
    val textUthmani: String,
    val surahNameArabic: String,
    val surahNameTransliterated: String,
)
