package com.ayah.quran.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.BookmarkDao
import com.ayah.quran.core.database.dao.ReadingProgressDao
import com.ayah.quran.core.database.dao.SelectionHistoryDao
import com.ayah.quran.core.database.dao.SurahDao
import com.ayah.quran.core.database.dao.WallpaperHistoryDao
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import com.ayah.quran.core.database.entity.SelectionHistoryEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.core.database.entity.WallpaperHistoryEntity

/**
 * The single Room database.
 *
 * Schema history is exported to app/schemas (configured via
 * room.schemaLocation in app/build.gradle.kts) so future migrations can be
 * tested against real previous schemas.
 */
@Database(
    entities = [
        SurahEntity::class,
        AyahEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        WallpaperHistoryEntity::class,
        SelectionHistoryEntity::class,
    ],
    // Version history: 1 = initial Phase 1 schema; 2 = surahs.nameEnglish
    // added (Phase 2); 3 = wallpaper_history + selection_history (Phase 3).
    // Until the first release, destructive migration is acceptable: no user
    // database from an older version exists in the wild.
    version = 3,
    exportSchema = true,
)
abstract class AyahDatabase : RoomDatabase() {

    abstract fun surahDao(): SurahDao
    abstract fun ayahDao(): AyahDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun wallpaperHistoryDao(): WallpaperHistoryDao
    abstract fun selectionHistoryDao(): SelectionHistoryDao

    companion object {
        const val NAME = "ayah.db"
    }
}
