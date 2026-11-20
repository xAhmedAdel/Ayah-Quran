package com.ayah.quran.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            AyahMigrations.MIGRATION_3_4,
        )
        migratedSqlDb.close()

        val roomDb = Room.databaseBuilder(context, AyahDatabase::class.java, dbName)
            .addMigrations(AyahMigrations.MIGRATION_3_4)
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
}
