package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmarked verse. The composite unique index makes (surah, ayah)
 * bookmarkable exactly once; the foreign key cascades deletion if the verse
 * table is ever re-imported.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["surahId", "ayahNumber"],
            childColumns = ["surahId", "ayahNumber"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["surahId", "ayahNumber"], unique = true),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahId: Int,
    val ayahNumber: Int,
    // Epoch millis (UTC).
    val createdAt: Long,
)
