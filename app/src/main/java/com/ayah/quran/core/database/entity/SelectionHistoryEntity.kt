package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent record of which verses the wallpaper engine has selected, used
 * for recent-ayah avoidance and (trimmed on insert) kept to a small ring.
 * [globalId] is the canonical global verse number from the ayahs table.
 */
@Entity(
    tableName = "selection_history",
    indices = [Index("selectedAt"), Index("globalId")],
)
data class SelectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahId: Int,
    val ayahNumber: Int,
    val globalId: Long,
    // Epoch millis (UTC).
    val selectedAt: Long,
    // SelectionMode name that produced this selection.
    val mode: String,
)
