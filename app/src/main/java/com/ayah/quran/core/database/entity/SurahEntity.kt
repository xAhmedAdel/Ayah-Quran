package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A surah row. Mirrors [com.ayah.quran.domain.model.Surah].
 *
 * [revelationType] is stored as a lowercase string ("meccan"/"medinan")
 * instead of a Room TypeConverter to keep the schema trivially inspectable.
 */
@Entity(tableName = "surahs")
data class SurahEntity(
    // Canonical surah number 1..114.
    @PrimaryKey val id: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val nameEnglish: String,
    val revelationType: String,
    val ayahCount: Int,
)
