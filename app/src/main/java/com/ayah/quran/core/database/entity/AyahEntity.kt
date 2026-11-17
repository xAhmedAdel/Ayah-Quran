package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Quranic verse row. Mirrors [com.ayah.quran.domain.model.Ayah].
 *
 * [id] is the global verse number (1..6236); (surahId, ayahNumber) is the
 * canonical reference and is additionally enforced unique. The foreign key
 * guarantees no verse can exist without its surah row.
 */
@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["id"],
            childColumns = ["surahId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["surahId", "ayahNumber"], unique = true),
        Index(value = ["page"]),
        Index(value = ["juz"]),
    ],
)
data class AyahEntity(
    @PrimaryKey val id: Int,
    val surahId: Int,
    val ayahNumber: Int,
    // Uthmani script, stored verbatim from the verified dataset. Never mutated.
    val textUthmani: String,
    val juz: Int,
    val page: Int,
)
