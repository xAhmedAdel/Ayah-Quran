package com.ayah.quran.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One wallpaper generation+application attempt. Status is written only
 * after WallpaperManager confirms (or rejects) the operation.
 */
@Entity(
    tableName = "wallpaper_history",
    indices = [Index("appliedAt"), Index("surahId", "ayahNumber")],
)
data class WallpaperHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surahId: Int,
    val ayahNumber: Int,
    val templateId: String,
    // "SYSTEM" | "LOCK" | "BOTH"
    val destinations: String,
    // Epoch millis (UTC).
    val appliedAt: Long,
    // "SUCCESS" | "FAILED"
    val status: String,
    val failureReason: String?,
)
