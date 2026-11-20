package com.ayah.quran.data.companion

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionDaoInstrumentedTest {

    private lateinit var db: AyahDatabase
    private lateinit var adhkarRepo: AdhkarRepositoryImpl
    private lateinit var tasbihRepo: TasbihRepositoryImpl
    private lateinit var hadithRepo: HadithRepositoryImpl
    private val parser = CompanionContentParser()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        adhkarRepo = AdhkarRepositoryImpl(db, db.adhkarDao(), db.ayahDao(), db.surahDao())
        tasbihRepo = TasbihRepositoryImpl(db, db.tasbihDao())
        hadithRepo = HadithRepositoryImpl(db, db.hadithDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun readAppAsset(path: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun bundledAssetsImportSearchPersistAndSurviveReimportIdempotently() = runBlocking {
        val adhkar = parser.parseAdhkar(readAppAsset(CompanionContentImporter.ADHKAR_ASSET))
        val quranicDuas = parser.parseQuranicDuas(readAppAsset(CompanionContentImporter.QURANIC_DUAS_ASSET))
        val hadith = parser.parseHadith(readAppAsset(CompanionContentImporter.HADITH_ASSET))

        db.adhkarDao().upsertCategories(adhkar.categories)
        db.adhkarDao().upsertItems(adhkar.items)
        db.adhkarDao().upsertQuranicDuas(quranicDuas.items)
        db.hadithDao().upsertCollections(hadith.collections)
        db.hadithDao().upsertHadiths(hadith.items)

        assertEquals(adhkar.categories.size, db.adhkarDao().categoryCount())
        assertEquals(adhkar.items.size, db.adhkarDao().itemCount())
        assertEquals(40, db.adhkarDao().quranicDuaCount())
        assertEquals(6, db.hadithDao().collectionCount())
        assertEquals(hadith.items.size, db.hadithDao().hadithCount())

        val arHadithHits = hadithRepo.search("النيات")
        assertTrue("Expected Arabic FTS4 match for النيات", arHadithHits.isNotEmpty())
        val enHadithHits = hadithRepo.search("Quran")
        assertTrue("Expected English FTS4 match for Quran", enHadithHits.isNotEmpty())

        val adhkarHits = adhkarRepo.searchItems("اللهم انت ربي")
        assertTrue("Expected diacritic-insensitive Adhkar search hit", adhkarHits.isNotEmpty())

        val firstMorning = adhkar.items.first { it.categoryId == "morning" }
        val dayKey = "2026-11-20"
        adhkarRepo.incrementProgress(dayKey, firstMorning.id)
        adhkarRepo.toggleFavorite(firstMorning.id)

        val sampleHadithId = hadith.items.first().id
        hadithRepo.toggleFavorite(sampleHadithId)
        hadithRepo.recordRead(sampleHadithId)

        db.adhkarDao().upsertCategories(adhkar.categories)
        db.adhkarDao().upsertItems(adhkar.items)
        db.hadithDao().upsertCollections(hadith.collections)
        db.hadithDao().upsertHadiths(hadith.items)

        val persistedProgress = db.adhkarDao().getProgress(dayKey, firstMorning.id)
        assertNotNull(persistedProgress)
        assertEquals(1, persistedProgress!!.currentCount)
        assertTrue(firstMorning.id in adhkarRepo.observeFavoriteIds().first())

        val favHadiths = hadithRepo.observeFavorites().first()
        assertTrue(favHadiths.any { it.id == sampleHadithId })
        val recentHadiths = hadithRepo.observeRecentlyRead().first()
        assertTrue(recentHadiths.any { it.id == sampleHadithId })

        val daily1 = hadithRepo.dailyHadithId(20_000L)
        val daily2 = hadithRepo.dailyHadithId(20_000L)
        assertNotNull(daily1)
        assertEquals(daily1, daily2)
    }

    @Test
    fun quranicDuasResolveLiveUthmaniTextFromAyahTable() = runBlocking {
        db.surahDao().insertAll(
            listOf(
                SurahEntity(
                    id = 2,
                    nameArabic = "البقرة",
                    nameTransliterated = "Al-Baqarah",
                    nameEnglish = "The Cow",
                    revelationType = "medinan",
                    ayahCount = 286,
                ),
            ),
        )
        db.ayahDao().insertAll(
            listOf(
                AyahEntity(
                    id = 208,
                    surahId = 2,
                    ayahNumber = 201,
                    textUthmani = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً",
                    juz = 2,
                    page = 31,
                ),
            ),
        )
        val quranicDuas = parser.parseQuranicDuas(readAppAsset(CompanionContentImporter.QURANIC_DUAS_ASSET))
        db.adhkarDao().upsertQuranicDuas(quranicDuas.items)

        val resolved = adhkarRepo.observeQuranicDuas().first()
        val rabbana = resolved.firstOrNull { it.pointer.surahId == 2 && it.pointer.startAyah == 201 }
        assertNotNull(rabbana)
        assertEquals("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً", rabbana!!.textUthmani)
        assertEquals("البقرة", rabbana.surahNameArabic)
    }

    @Test
    fun tasbihCounterSupportsIncrementRoundRolloverUndoResetAndCustomCrud() = runBlocking {
        val now = System.currentTimeMillis()
        val id = db.tasbihDao().insertCounter(
            TasbihCounterEntity(
                stableKey = "test_preset",
                titleAr = "سبحان الله",
                titleEn = "SubhanAllah",
                dhikrTextAr = "سُبْحَانَ اللَّهِ",
                targetCount = 3,
                currentCount = 0,
                completedRounds = 0,
                lifetimeCount = 0,
                isCustom = false,
                sortOrder = 1,
                updatedAtMillis = now,
            ),
        )
        val dayKey = "2026-11-20"
        assertFalse(tasbihRepo.increment(id, dayKey))
        assertFalse(tasbihRepo.increment(id, dayKey))
        tasbihRepo.undo(id, dayKey)
        assertEquals(1, db.tasbihDao().getById(id)!!.currentCount)

        assertFalse(tasbihRepo.increment(id, dayKey))
        assertTrue(tasbihRepo.increment(id, dayKey))
        val afterRound = db.tasbihDao().getById(id)!!
        assertEquals(0, afterRound.currentCount)
        assertEquals(1, afterRound.completedRounds)
        assertEquals(3L, afterRound.lifetimeCount)
        assertEquals(3, tasbihRepo.observeDayTotal(dayKey).first())

        tasbihRepo.increment(id, dayKey)
        tasbihRepo.reset(id)
        assertEquals(0, db.tasbihDao().getById(id)!!.currentCount)

        val customId = tasbihRepo.createCustomCounter("ورد خاص", "Custom Wird", "أستغفر الله العظيم", 50)
        assertEquals(2, db.tasbihDao().counterCount())
        tasbihRepo.deleteCustomCounter(customId)
        assertEquals(1, db.tasbihDao().counterCount())
    }
}
