package com.ayah.quran.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.BookmarkDao
import com.ayah.quran.core.database.dao.ReadingProgressDao
import com.ayah.quran.core.database.dao.SelectionHistoryDao
import com.ayah.quran.core.database.dao.SurahDao
import com.ayah.quran.core.database.dao.WallpaperHistoryDao
import com.ayah.quran.core.database.dao.companion.AdhkarDao
import com.ayah.quran.core.database.dao.companion.HadithDao
import com.ayah.quran.core.database.dao.companion.TasbihDao
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import com.ayah.quran.core.database.entity.SelectionHistoryEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.core.database.entity.WallpaperHistoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrCategoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrDailyProgressEntity
import com.ayah.quran.core.database.entity.companion.DhikrFavoriteEntity
import com.ayah.quran.core.database.entity.companion.DhikrItemEntity
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithEntity
import com.ayah.quran.core.database.entity.companion.HadithFtsEntity
import com.ayah.quran.core.database.entity.companion.HadithUserStateEntity
import com.ayah.quran.core.database.entity.companion.QuranicDuaEntity
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import com.ayah.quran.core.database.entity.companion.TasbihDailyLogEntity

@Database(
    entities = [
        SurahEntity::class,
        AyahEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        WallpaperHistoryEntity::class,
        SelectionHistoryEntity::class,
        DhikrCategoryEntity::class,
        DhikrItemEntity::class,
        QuranicDuaEntity::class,
        DhikrDailyProgressEntity::class,
        DhikrFavoriteEntity::class,
        TasbihCounterEntity::class,
        TasbihDailyLogEntity::class,
        HadithCollectionEntity::class,
        HadithEntity::class,
        HadithFtsEntity::class,
        HadithUserStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AyahDatabase : RoomDatabase() {

    abstract fun surahDao(): SurahDao
    abstract fun ayahDao(): AyahDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun wallpaperHistoryDao(): WallpaperHistoryDao
    abstract fun selectionHistoryDao(): SelectionHistoryDao

    abstract fun adhkarDao(): AdhkarDao
    abstract fun tasbihDao(): TasbihDao
    abstract fun hadithDao(): HadithDao

    companion object {
        const val NAME = "ayah.db"
    }
}
