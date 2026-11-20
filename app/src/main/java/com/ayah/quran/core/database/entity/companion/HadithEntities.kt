package com.ayah.quran.core.database.entity.companion

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "hadith_collections")
data class HadithCollectionEntity(
    @PrimaryKey val id: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val sourceAttribution: String,
    val datasetVersion: String,
    val isBundled: Boolean,
    val isDownloaded: Boolean,
    val hadithCount: Int,
    val sortOrder: Int,
)

@Entity(
    tableName = "hadith_items",
    foreignKeys = [
        ForeignKey(
            entity = HadithCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["collectionId", "sortOrder"]),
    ],
)
data class HadithEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val chapterTitleAr: String,
    val chapterTitleEn: String,
    val hadithNumber: String,
    val titleAr: String,
    val titleEn: String,
    val textAr: String,
    val textEn: String?,
    val gradeAr: String,
    val gradeEn: String,
    val graderAttribution: String,
    val referenceAr: String,
    val referenceEn: String,
    val explanationAr: String?,
    val explanationEn: String?,
    val sourceVersion: String,
    val searchTextNormalized: String,
    val sortOrder: Int,
)

@Fts4(
    contentEntity = HadithEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "hadith_fts")
data class HadithFtsEntity(
    val titleAr: String,
    val titleEn: String,
    val chapterTitleAr: String,
    val chapterTitleEn: String,
    val searchTextNormalized: String,
)

@Entity(
    tableName = "hadith_user_state",
    foreignKeys = [
        ForeignKey(
            entity = HadithEntity::class,
            parentColumns = ["id"],
            childColumns = ["hadithId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["isFavorite"]),
        Index(value = ["lastReadAtMillis"]),
    ],
)
data class HadithUserStateEntity(
    @PrimaryKey val hadithId: String,
    val isFavorite: Boolean,
    val favoritedAtMillis: Long?,
    val lastReadAtMillis: Long?,
)
