package com.ayah.quran.domain.model

/** A verse the user saved. [createdAtMillis] is epoch time (UTC). */
data class Bookmark(
    val id: Long,
    val surahId: Int,
    val ayahNumber: Int,
    val createdAtMillis: Long,
)

/** The verse the user last viewed, used for "Continue Reading". */
data class ReadingPosition(
    val surahId: Int,
    val ayahNumber: Int,
    val updatedAtMillis: Long,
)
