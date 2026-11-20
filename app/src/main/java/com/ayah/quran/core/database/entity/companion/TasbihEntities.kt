package com.ayah.quran.core.database.entity.companion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasbih_counters",
    indices = [
        Index(value = ["stableKey"], unique = true),
        Index(value = ["sortOrder"]),
    ],
)
data class TasbihCounterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableKey: String?,
    val titleAr: String,
    val titleEn: String,
    val dhikrTextAr: String,
    val targetCount: Int,
    val currentCount: Int,
    val completedRounds: Int,
    val lifetimeCount: Long,
    val isCustom: Boolean,
    val sortOrder: Int,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "tasbih_daily_log",
    primaryKeys = ["dayKey", "counterId"],
    foreignKeys = [
        ForeignKey(
            entity = TasbihCounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["counterId"]),
    ],
)
data class TasbihDailyLogEntity(
    val dayKey: String,
    val counterId: Long,
    val countTotal: Int,
    val updatedAtMillis: Long,
)
