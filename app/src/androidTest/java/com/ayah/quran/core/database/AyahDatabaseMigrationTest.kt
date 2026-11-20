package com.ayah.quran.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithEntity
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import com.ayah.quran.core.text.ArabicTextNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AyahDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AyahDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4PreservesAllPopulatedUserDataAndCreatesCompanionSchema() = runBlocking {
        val dbName = "ayah-migration-3-4-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO surahs (id, nameArabic, nameTransliterated, nameEnglish, revelationType, ayahCount) " +
                    "VALUES (1, 'الفاتحة', 'Al-Fatihah', 'The Opening', 'meccan', 7)",
            )
            execSQL(
                "INSERT INTO ayahs (id, surahId, ayahNumber, textUthmani, juz, page) " +
                    "VALUES (1, 1, 1, 'بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ', 1, 1)",
            )
            execSQL(
                "INSERT INTO bookmarks (id, surahId, ayahNumber, createdAt) " +
                    "VALUES (10, 1, 1, 1700000000000)",
            )
            execSQL(
                "INSERT INTO reading_progress (id, surahId, ayahNumber, updatedAt) " +
                    "VALUES (1, 1, 1, 1700000001000)",
            )
            execSQL(
                "INSERT INTO wallpaper_history (id, surahId, ayahNumber, templateId, destinations, appliedAt, status, failureReason) " +
                    "VALUES (20, 1, 1, 'emerald_luxury', 'BOTH', 1700000002000, 'SUCCESS', NULL)",
            )
            execSQL(
                "INSERT INTO selection_history (id, surahId, ayahNumber, globalId, selectedAt, mode) " +
                    "VALUES (30, 1, 1, 1, 1700000003000, 'DAILY')",
            )
            close()
        }

        val migratedSqlDb = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            *AyahMigrations.ALL,
        )
        migratedSqlDb.close()

        val roomDb = Room.databaseBuilder(context, AyahDatabase::class.java, dbName)
            .addMigrations(*AyahMigrations.ALL)
            .build()
        try {
            assertEquals(1, roomDb.surahDao().count())
            assertEquals(1, roomDb.ayahDao().count())
            val savedAyah = roomDb.ayahDao().getAyah(1, 1)
            assertNotNull(savedAyah)
            assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ", savedAyah!!.textUthmani)

            val bookmarks = roomDb.bookmarkDao().observeBookmarkedAyahs().first()
            assertEquals(1, bookmarks.size)
            assertEquals(1, bookmarks.first().surahId)

            val progress = roomDb.readingProgressDao().observe().first()
            assertNotNull(progress)
            assertEquals(1, progress!!.surahId)
            assertEquals(1, progress.ayahNumber)

            val wallpaperHistory = roomDb.wallpaperHistoryDao().observeAll().first()
            assertEquals(1, wallpaperHistory.size)
            assertEquals("emerald_luxury", wallpaperHistory.first().templateId)

            val recentSelections = roomDb.selectionHistoryDao().recent(10)
            assertEquals(1, recentSelections.size)
            assertEquals(1L, recentSelections.first().globalId)

            roomDb.tasbihDao().insertCounter(
                TasbihCounterEntity(
                    stableKey = "subhanallah",
                    titleAr = "سبحان الله",
                    titleEn = "SubhanAllah",
                    dhikrTextAr = "سُبْحَانَ اللَّهِ",
                    targetCount = 33,
                    currentCount = 5,
                    completedRounds = 0,
                    lifetimeCount = 5,
                    isCustom = false,
                    sortOrder = 1,
                    updatedAtMillis = 1700000004000L,
                ),
            )
            assertEquals(1, roomDb.tasbihDao().counterCount())

            roomDb.hadithDao().upsertCollections(
                listOf(
                    HadithCollectionEntity(
                        id = "nawawi40",
                        titleAr = "الأربعون النووية",
                        titleEn = "40 Hadith Nawawi",
                        descriptionAr = "وصف",
                        descriptionEn = "Desc",
                        sourceAttribution = "Source",
                        datasetVersion = "v1",
                        isBundled = true,
                        isDownloaded = true,
                        hadithCount = 1,
                        sortOrder = 1,
                    ),
                ),
            )
            roomDb.hadithDao().upsertHadiths(
                listOf(
                    HadithEntity(
                        id = "nawawi_01",
                        collectionId = "nawawi40",
                        chapterTitleAr = "الأربعون النووية",
                        chapterTitleEn = "40 Hadith",
                        hadithNumber = "1",
                        titleAr = "إنما الأعمال بالنيات",
                        titleEn = "Actions by intentions",
                        textAr = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ",
                        textEn = "Actions are by intentions",
                        gradeAr = "صحيح",
                        gradeEn = "Sahih",
                        graderAttribution = "Al-Bukhari",
                        referenceAr = "البخاري",
                        referenceEn = "Bukhari",
                        explanationAr = null,
                        explanationEn = null,
                        sourceVersion = "v1",
                        searchTextNormalized = ArabicTextNormalizer.normalize("إنما الأعمال بالنيات intentions"),
                        sortOrder = 1,
                    ),
                ),
            )
            val ftsHits = roomDb.hadithDao().searchFts(ArabicTextNormalizer.buildFtsQuery("الأعمال"))
            assertTrue(ftsHits.any { it.hadith.id == "nawawi_01" })
            val normalizedHits = roomDb.hadithDao().searchNormalized(ArabicTextNormalizer.normalize("النيات"))
            assertTrue(normalizedHits.any { it.hadith.id == "nawawi_01" })
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate1To4PreservesBookmarksReadingProgressAndQuranData() = runBlocking {
        val dbName = "ayah-migration-1-4-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO surahs (id, nameArabic, nameTransliterated, revelationType, ayahCount) " +
                    "VALUES (1, 'الفاتحة', 'Al-Fatihah', 'meccan', 7)",
            )
            execSQL(
                "INSERT INTO ayahs (id, surahId, ayahNumber, textUthmani, juz, page) " +
                    "VALUES (1, 1, 1, 'بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ', 1, 1)",
            )
            execSQL(
                "INSERT INTO bookmarks (id, surahId, ayahNumber, createdAt) " +
                    "VALUES (10, 1, 1, 1700000000000)",
            )
            execSQL(
                "INSERT INTO reading_progress (id, surahId, ayahNumber, updatedAt) " +
                    "VALUES (1, 1, 1, 1700000001000)",
            )
            close()
        }

        val migratedSqlDb = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            *AyahMigrations.ALL,
        )
        migratedSqlDb.close()

        val roomDb = Room.databaseBuilder(context, AyahDatabase::class.java, dbName)
            .addMigrations(*AyahMigrations.ALL)
            .build()
        try {
            val surah = roomDb.surahDao().getSurah(1)
            assertNotNull(surah)
            assertEquals("الفاتحة", surah!!.nameArabic)
            assertEquals("", surah.nameEnglish)

            val savedAyah = roomDb.ayahDao().getAyah(1, 1)
            assertNotNull(savedAyah)
            assertEquals("بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ", savedAyah!!.textUthmani)

            val bookmarks = roomDb.bookmarkDao().observeBookmarkedAyahs().first()
            assertEquals(1, bookmarks.size)
            assertEquals(1, bookmarks.first().surahId)

            val progress = roomDb.readingProgressDao().observe().first()
            assertNotNull(progress)
            assertEquals(1, progress!!.surahId)
            assertEquals(1, progress.ayahNumber)

            assertTrue(roomDb.wallpaperHistoryDao().observeAll().first().isEmpty())
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun migrate2To4PreservesBookmarksReadingProgressAndQuranData() = runBlocking {
        val dbName = "ayah-migration-2-4-test.db"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO surahs (id, nameArabic, nameTransliterated, nameEnglish, revelationType, ayahCount) " +
                    "VALUES (2, 'البقرة', 'Al-Baqarah', 'The Cow', 'medinan', 286)",
            )
            execSQL(
                "INSERT INTO ayahs (id, surahId, ayahNumber, textUthmani, juz, page) " +
                    "VALUES (262, 2, 255, 'ٱللَّهُ لَا إِلَـٰهَ إِلَّا هُوَ', 3, 42)",
            )
            execSQL(
                "INSERT INTO bookmarks (id, surahId, ayahNumber, createdAt) " +
                    "VALUES (11, 2, 255, 1700000000000)",
            )
            execSQL(
                "INSERT INTO reading_progress (id, surahId, ayahNumber, updatedAt) " +
                    "VALUES (1, 2, 255, 1700000001000)",
            )
            close()
        }

        val migratedSqlDb = helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            *AyahMigrations.ALL,
        )
        migratedSqlDb.close()

        val roomDb = Room.databaseBuilder(context, AyahDatabase::class.java, dbName)
            .addMigrations(*AyahMigrations.ALL)
            .build()
        try {
            val surah = roomDb.surahDao().getSurah(2)
            assertNotNull(surah)
            assertEquals("The Cow", surah!!.nameEnglish)

            val bookmarks = roomDb.bookmarkDao().observeBookmarkedAyahs().first()
            assertEquals(1, bookmarks.size)
            assertEquals(2, bookmarks.first().surahId)
            assertEquals(255, bookmarks.first().ayahNumber)

            val progress = roomDb.readingProgressDao().observe().first()
            assertNotNull(progress)
            assertEquals(2, progress!!.surahId)
            assertEquals(255, progress.ayahNumber)
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun quranReImportPreservesBookmarksAndReadingProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val surah = SurahEntity(
                id = 1,
                nameArabic = "الفاتحة",
                nameTransliterated = "Al-Fatihah",
                nameEnglish = "The Opening",
                revelationType = "makkan",
                ayahCount = 7,
            )
            val ayah = AyahEntity(
                id = 1,
                surahId = 1,
                ayahNumber = 1,
                textUthmani = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                juz = 1,
                page = 1,
            )
            roomDb.surahDao().insertAll(listOf(surah))
            roomDb.ayahDao().insertAll(listOf(ayah))
            roomDb.bookmarkDao().insert(BookmarkEntity(surahId = 1, ayahNumber = 1, createdAt = 1700000000000L))
            roomDb.readingProgressDao().upsert(
                ReadingProgressEntity(id = 1, surahId = 1, ayahNumber = 1, updatedAt = 1700000001000L),
            )

            roomDb.surahDao().insertAll(listOf(surah.copy(nameEnglish = "The Opener")))
            roomDb.ayahDao().insertAll(listOf(ayah))

            assertEquals("The Opener", roomDb.surahDao().getSurah(1)?.nameEnglish)
            val bookmarksAfter = roomDb.bookmarkDao().observeBookmarkedAyahs().first()
            assertEquals(1, bookmarksAfter.size)
            assertEquals(1, bookmarksAfter.first().surahId)
            assertEquals(1, bookmarksAfter.first().ayahNumber)

            val progressAfter = roomDb.readingProgressDao().observe().first()
            assertNotNull(progressAfter)
            assertEquals(1, progressAfter!!.surahId)
            assertEquals(1, progressAfter.ayahNumber)
        } finally {
            roomDb.close()
        }
    }
}
