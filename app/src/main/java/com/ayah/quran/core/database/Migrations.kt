package com.ayah.quran.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AyahMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `surahs` ADD COLUMN `nameEnglish` TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallpaper_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surahId` INTEGER NOT NULL, " +
                    "`ayahNumber` INTEGER NOT NULL, `templateId` TEXT NOT NULL, `destinations` TEXT NOT NULL, " +
                    "`appliedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `failureReason` TEXT)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_wallpaper_history_appliedAt` " +
                    "ON `wallpaper_history` (`appliedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_wallpaper_history_surahId_ayahNumber` " +
                    "ON `wallpaper_history` (`surahId`, `ayahNumber`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `selection_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `surahId` INTEGER NOT NULL, " +
                    "`ayahNumber` INTEGER NOT NULL, `globalId` INTEGER NOT NULL, " +
                    "`selectedAt` INTEGER NOT NULL, `mode` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_selection_history_selectedAt` " +
                    "ON `selection_history` (`selectedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_selection_history_globalId` " +
                    "ON `selection_history` (`globalId`)",
            )
        }
    }

    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dhikr_categories` (" +
                    "`id` TEXT NOT NULL, `sectionType` TEXT NOT NULL, `titleAr` TEXT NOT NULL, " +
                    "`titleEn` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `recommendedWindow` TEXT, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dhikr_items` (" +
                    "`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `itemType` TEXT NOT NULL, " +
                    "`sortOrder` INTEGER NOT NULL, `titleAr` TEXT NOT NULL, `titleEn` TEXT NOT NULL, " +
                    "`textAr` TEXT NOT NULL, `textEn` TEXT, `virtueAr` TEXT, `virtueEn` TEXT, " +
                    "`sourceAr` TEXT NOT NULL, `sourceEn` TEXT NOT NULL, `repeatCount` INTEGER NOT NULL, " +
                    "`surahId` INTEGER, `startAyah` INTEGER, `endAyah` INTEGER, " +
                    "`searchTextNormalized` TEXT NOT NULL, `datasetVersion` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `dhikr_categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dhikr_items_categoryId_sortOrder` " +
                    "ON `dhikr_items` (`categoryId`, `sortOrder`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quranic_duas` (" +
                    "`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `surahId` INTEGER NOT NULL, " +
                    "`startAyah` INTEGER NOT NULL, `endAyah` INTEGER NOT NULL, `titleAr` TEXT NOT NULL, " +
                    "`titleEn` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `searchTextNormalized` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quranic_duas_categoryId_sortOrder` " +
                    "ON `quranic_duas` (`categoryId`, `sortOrder`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quranic_duas_surahId_startAyah` " +
                    "ON `quranic_duas` (`surahId`, `startAyah`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dhikr_daily_progress` (" +
                    "`dayKey` TEXT NOT NULL, `itemId` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                    "`currentCount` INTEGER NOT NULL, `targetCount` INTEGER NOT NULL, " +
                    "`completed` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`dayKey`, `itemId`))",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_dhikr_daily_progress_dayKey_categoryId` " +
                    "ON `dhikr_daily_progress` (`dayKey`, `categoryId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dhikr_favorites` (" +
                    "`itemId` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`itemId`), " +
                    "FOREIGN KEY(`itemId`) REFERENCES `dhikr_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasbih_counters` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `stableKey` TEXT, " +
                    "`titleAr` TEXT NOT NULL, `titleEn` TEXT NOT NULL, `dhikrTextAr` TEXT NOT NULL, " +
                    "`targetCount` INTEGER NOT NULL, `currentCount` INTEGER NOT NULL, " +
                    "`completedRounds` INTEGER NOT NULL, `lifetimeCount` INTEGER NOT NULL, " +
                    "`isCustom` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_tasbih_counters_stableKey` " +
                    "ON `tasbih_counters` (`stableKey`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tasbih_counters_sortOrder` " +
                    "ON `tasbih_counters` (`sortOrder`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `tasbih_daily_log` (" +
                    "`dayKey` TEXT NOT NULL, `counterId` INTEGER NOT NULL, `countTotal` INTEGER NOT NULL, " +
                    "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`dayKey`, `counterId`), " +
                    "FOREIGN KEY(`counterId`) REFERENCES `tasbih_counters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_tasbih_daily_log_counterId` " +
                    "ON `tasbih_daily_log` (`counterId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hadith_collections` (" +
                    "`id` TEXT NOT NULL, `titleAr` TEXT NOT NULL, `titleEn` TEXT NOT NULL, " +
                    "`descriptionAr` TEXT NOT NULL, `descriptionEn` TEXT NOT NULL, " +
                    "`sourceAttribution` TEXT NOT NULL, `datasetVersion` TEXT NOT NULL, " +
                    "`isBundled` INTEGER NOT NULL, `isDownloaded` INTEGER NOT NULL, " +
                    "`hadithCount` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hadith_items` (" +
                    "`id` TEXT NOT NULL, `collectionId` TEXT NOT NULL, `chapterTitleAr` TEXT NOT NULL, " +
                    "`chapterTitleEn` TEXT NOT NULL, `hadithNumber` TEXT NOT NULL, `titleAr` TEXT NOT NULL, " +
                    "`titleEn` TEXT NOT NULL, `textAr` TEXT NOT NULL, `textEn` TEXT, " +
                    "`gradeAr` TEXT NOT NULL, `gradeEn` TEXT NOT NULL, `graderAttribution` TEXT NOT NULL, " +
                    "`referenceAr` TEXT NOT NULL, `referenceEn` TEXT NOT NULL, " +
                    "`explanationAr` TEXT, `explanationEn` TEXT, `sourceVersion` TEXT NOT NULL, " +
                    "`searchTextNormalized` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`collectionId`) REFERENCES `hadith_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_hadith_items_collectionId_sortOrder` " +
                    "ON `hadith_items` (`collectionId`, `sortOrder`)",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `hadith_fts` USING FTS4(" +
                    "`titleAr` TEXT NOT NULL, `titleEn` TEXT NOT NULL, `chapterTitleAr` TEXT NOT NULL, " +
                    "`chapterTitleEn` TEXT NOT NULL, `searchTextNormalized` TEXT NOT NULL, " +
                    "tokenize=unicode61, content=`hadith_items`)",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_hadith_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `hadith_items` BEGIN DELETE FROM `hadith_fts` WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_hadith_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `hadith_items` BEGIN DELETE FROM `hadith_fts` WHERE `docid`=OLD.`rowid`; END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_hadith_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `hadith_items` BEGIN INSERT INTO `hadith_fts`(" +
                    "`docid`, `titleAr`, `titleEn`, `chapterTitleAr`, `chapterTitleEn`, `searchTextNormalized`) " +
                    "VALUES (NEW.`rowid`, NEW.`titleAr`, NEW.`titleEn`, NEW.`chapterTitleAr`, NEW.`chapterTitleEn`, NEW.`searchTextNormalized`); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_hadith_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `hadith_items` BEGIN INSERT INTO `hadith_fts`(" +
                    "`docid`, `titleAr`, `titleEn`, `chapterTitleAr`, `chapterTitleEn`, `searchTextNormalized`) " +
                    "VALUES (NEW.`rowid`, NEW.`titleAr`, NEW.`titleEn`, NEW.`chapterTitleAr`, NEW.`chapterTitleEn`, NEW.`searchTextNormalized`); END",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `hadith_user_state` (" +
                    "`hadithId` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                    "`favoritedAtMillis` INTEGER, `lastReadAtMillis` INTEGER, PRIMARY KEY(`hadithId`), " +
                    "FOREIGN KEY(`hadithId`) REFERENCES `hadith_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_hadith_user_state_isFavorite` " +
                    "ON `hadith_user_state` (`isFavorite`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_hadith_user_state_lastReadAtMillis` " +
                    "ON `hadith_user_state` (`lastReadAtMillis`)",
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
    )
}
