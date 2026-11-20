package com.ayah.quran.core.database.entity.companion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dhikr_categories")
data class DhikrCategoryEntity(
    @PrimaryKey val id: String,
    val sectionType: String,
    val titleAr: String,
    val titleEn: String,
    val sortOrder: Int,
    val recommendedWindow: String?,
)

@Entity(
    tableName = "dhikr_items",
    foreignKeys = [
        ForeignKey(
            entity = DhikrCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["categoryId", "sortOrder"]),
    ],
)
data class DhikrItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val itemType: String,
    val sortOrder: Int,
    val titleAr: String,
    val titleEn: String,
    val textAr: String,
    val textEn: String?,
    val virtueAr: String?,
    val virtueEn: String?,
    val sourceAr: String,
    val sourceEn: String,
    val repeatCount: Int,
    val surahId: Int?,
    val startAyah: Int?,
    val endAyah: Int?,
    val searchTextNormalized: String,
    val datasetVersion: String,
)

@Entity(
    tableName = "quranic_duas",
    indices = [
        Index(value = ["categoryId", "sortOrder"]),
        Index(value = ["surahId", "startAyah"]),
    ],
)
data class QuranicDuaEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val surahId: Int,
    val startAyah: Int,
    val endAyah: Int,
    val titleAr: String,
    val titleEn: String,
    val sortOrder: Int,
    val searchTextNormalized: String,
)

@Entity(
    tableName = "dhikr_daily_progress",
    primaryKeys = ["dayKey", "itemId"],
    indices = [
        Index(value = ["dayKey", "categoryId"]),
    ],
)
data class DhikrDailyProgressEntity(
    val dayKey: String,
    val itemId: String,
    val categoryId: String,
    val currentCount: Int,
    val targetCount: Int,
    val completed: Boolean,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "dhikr_favorites",
    foreignKeys = [
        ForeignKey(
            entity = DhikrItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DhikrFavoriteEntity(
    @PrimaryKey val itemId: String,
    val createdAtMillis: Long,
)
