package com.ayah.quran.data.companion

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.di.createPreferencesCorruptionHandler
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.core.database.entity.companion.DhikrCategoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrItemEntity
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithEntity
import com.ayah.quran.core.database.entity.companion.QuranicDuaEntity
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.CompanionImportState
import com.ayah.quran.domain.model.companion.DhikrItemType
import com.ayah.quran.domain.model.companion.DhikrSectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CompanionColdStartAndTransactionTest {

    private lateinit var db: AyahDatabase
    private lateinit var adhkarRepo: AdhkarRepositoryImpl
    private lateinit var tasbihRepo: TasbihRepositoryImpl
    private lateinit var hadithRepo: HadithRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        adhkarRepo = AdhkarRepositoryImpl(db, db.adhkarDao(), db.ayahDao(), db.surahDao())
        tasbihRepo = TasbihRepositoryImpl(db, db.tasbihDao())
        hadithRepo = HadithRepositoryImpl(db, db.hadithDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun quranicDuasColdStartRaceResolvesWhenQuranImportCompletesWithoutRestart() = runBlocking {
        db.adhkarDao().upsertQuranicDuas(
            listOf(
                QuranicDuaEntity(
                    id = "rabbana_01",
                    categoryId = "quranic_rabbana",
                    surahId = 2,
                    startAyah = 201,
                    endAyah = 201,
                    titleAr = "ربنا آتنا في الدنيا حسنة",
                    titleEn = "Our Lord, grant us good",
                    sortOrder = 1,
                    searchTextNormalized = ArabicTextNormalizer.normalize("ربنا آتنا"),
                ),
            ),
        )

        val initial = adhkarRepo.observeQuranicDuas().first()
        assertTrue("Expected empty before Quran import completes", initial.isEmpty())

        val resolvedDeferred = async {
            withTimeout(5_000L) {
                adhkarRepo.observeQuranicDuas().first { it.isNotEmpty() }
            }
        }

        launch(Dispatchers.IO) {
            db.surahDao().insertAll(
                listOf(
                    SurahEntity(
                        id = 2,
                        nameArabic = "البقرة",
                        nameTransliterated = "Al-Baqarah",
                        nameEnglish = "The Cow",
                        revelationType = "madinan",
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
        }

        val resolved = resolvedDeferred.await()
        assertEquals(1, resolved.size)
        assertEquals("rabbana_01", resolved.first().pointer.id)
        assertEquals("البقرة", resolved.first().surahNameArabic)
        assertEquals("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً", resolved.first().textUthmani)
    }

    @Test
    fun concurrentTransactionsPreserveAdhkarHadithAndTasbihConsistency() = runBlocking {
        db.adhkarDao().upsertCategories(
            listOf(
                DhikrCategoryEntity(
                    id = "morning",
                    sectionType = DhikrSectionType.entries.first().name,
                    titleAr = "الصباح",
                    titleEn = "Morning",
                    sortOrder = 1,
                    recommendedWindow = "MORNING",
                ),
            ),
        )
        db.adhkarDao().upsertItems(
            listOf(
                DhikrItemEntity(
                    id = "m_1",
                    categoryId = "morning",
                    itemType = DhikrItemType.DHIKR.name,
                    sortOrder = 1,
                    titleAr = "ذكر",
                    titleEn = "Dhikr",
                    textAr = "سبحان الله",
                    textEn = null,
                    virtueAr = null,
                    virtueEn = null,
                    sourceAr = "مسلم",
                    sourceEn = "Muslim",
                    repeatCount = 20,
                    surahId = null,
                    startAyah = null,
                    endAyah = null,
                    searchTextNormalized = "سبحان الله",
                    datasetVersion = "v1",
                ),
            ),
        )

        val incrementJobs = (1..12).map {
            async(Dispatchers.IO) {
                adhkarRepo.incrementProgress("2026-11-20", "m_1")
            }
        }
        incrementJobs.awaitAll()
        val progress = adhkarRepo.observeDailyProgress("2026-11-20").first()
        assertEquals(12, progress.first().currentCount)

        db.hadithDao().upsertCollections(
            listOf(
                HadithCollectionEntity(
                    id = "nawawi40",
                    titleAr = "الأربعون",
                    titleEn = "Nawawi",
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
        db.hadithDao().upsertHadiths(
            listOf(
                HadithEntity(
                    id = "h_1",
                    collectionId = "nawawi40",
                    chapterTitleAr = "باب",
                    chapterTitleEn = "Chapter",
                    hadithNumber = "1",
                    titleAr = "حديث",
                    titleEn = "Hadith",
                    textAr = "إنما الأعمال بالنيات",
                    textEn = null,
                    gradeAr = "صحيح",
                    gradeEn = "Sahih",
                    graderAttribution = "Bukhari",
                    referenceAr = "البخاري",
                    referenceEn = "Bukhari",
                    explanationAr = null,
                    explanationEn = null,
                    sourceVersion = "v1",
                    searchTextNormalized = "انما الاعمال بالنيات",
                    sortOrder = 1,
                ),
            ),
        )
        val favJob = async(Dispatchers.IO) { hadithRepo.toggleFavorite("h_1") }
        val readJob = async(Dispatchers.IO) { hadithRepo.recordRead("h_1") }
        awaitAll(favJob, readJob)
        val hadith = hadithRepo.observeById("h_1").first()!!
        assertTrue(hadith.isFavorite)
        assertTrue(hadith.lastReadAtMillis != null)

        val customIds = (1..4).map { idx ->
            async(Dispatchers.IO) {
                tasbihRepo.createCustomCounter(
                    titleAr = "ذكر $idx",
                    titleEn = "Dhikr $idx",
                    dhikrTextAr = "سبحان الله $idx",
                    targetCount = 33,
                )
            }
        }.awaitAll()
        assertEquals(4, customIds.toSet().size)
        val counters = tasbihRepo.observeCounters().first()
        assertEquals(4, counters.size)
        tasbihRepo.reset(customIds.first())
        tasbihRepo.deleteCustomCounter(customIds.last())
        assertEquals(3, tasbihRepo.observeCounters().first().size)
    }

    @Test
    fun companionImporterCompletesEvenWhenDataStoreThrowsIoOrCorruption() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failingDataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                throw CorruptionException("corrupt preferences")
            }

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("read-only storage")
            }
        }
        val importer = CompanionContentImporter(
            context = context,
            database = db,
            parser = CompanionContentParser(),
            dataStore = failingDataStore,
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        importer.importIfNeeded()

        assertEquals(CompanionImportState.Completed, importer.importState.value)
        assertTrue(db.adhkarDao().itemCount() >= 100)
        assertTrue(db.adhkarDao().quranicDuaCount() == 40)
        assertTrue(db.hadithDao().hadithCount() >= 200)
        assertTrue(db.tasbihDao().counterCount() >= 7)
    }

    @Test
    fun androidFileBackedDataStoreRecoversCorruptedFileWithHandler() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val corruptFile = java.io.File(context.cacheDir, "test_corrupt_${System.nanoTime()}.preferences_pb")
        corruptFile.writeBytes(byteArrayOf(0x00, 0xFF.toByte(), 0x7F, 0x01, 0x02))
        val key = stringPreferencesKey("theme_mode")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = createPreferencesCorruptionHandler(),
                scope = scope,
                produceFile = { corruptFile },
            )
            assertEquals(null, store.data.first()[key])
            store.edit { it[key] = "DARK" }
            assertEquals("DARK", store.data.first()[key])
        } finally {
            corruptFile.delete()
        }
    }
}
