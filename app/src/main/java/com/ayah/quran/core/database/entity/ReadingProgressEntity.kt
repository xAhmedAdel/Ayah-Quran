package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The user's last reading position. Single-row table (primary key is fixed
 * to [SINGLE_ROW_ID]); [com.ayah.quran.core.database.dao.ReadingProgressDao]
 * upserts it, so history is deliberately not kept.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val surahId: Int,
    val ayahNumber: Int,
    // Epoch millis (UTC).
    val updatedAt: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
