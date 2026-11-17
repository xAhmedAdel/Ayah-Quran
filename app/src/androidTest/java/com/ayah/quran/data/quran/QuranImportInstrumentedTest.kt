package com.ayah.quran.data.quran

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.domain.model.QuranImportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real importer against the REAL bundled assets into an in-memory
 * Room database on the device, then validates the Madani-mushaf invariants
 * on what actually landed in the database.
 */
@RunWith(AndroidJUnit4::class)
class QuranImportInstrumentedTest {

    private lateinit var database: AyahDatabase
    private lateinit var importer: QuranImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = QuranImporter(
            context = context,
            database = database,
            parser = QuranTextParser(),
            appScope = CoroutineScope(Dispatchers.IO),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importCompletesWithVerifiedCounts() = runBlocking {
        importer.importIfNeeded()

        assertEquals(
            QuranImportState.Completed,
            importer.importState.value,
        )
        assertEquals(114, database.surahDao().count())
        assertEquals(6236, database.ayahDao().count())
    }

    @Test
    fun importIsIdempotent() = runBlocking {
        importer.importIfNeeded()
        val surahCountAfterFirst = database.surahDao().count()
        val ayahCountAfterFirst = database.ayahDao().count()

        importer.importIfNeeded()

        assertEquals(
            "second import must not duplicate rows",
            surahCountAfterFirst,
            database.surahDao().count(),
        )
        assertEquals(ayahCountAfterFirst, database.ayahDao().count())
    }

    @Test
    fun importedAyahIdsAreContinuousAndTextIsPresent() = runBlocking {
        importer.importIfNeeded()
        val range = database.ayahDao().idRangeAndCount()
        assertEquals(1, range.minId)
        assertEquals(6236, range.maxId)
        assertEquals(6236, range.count)

        // Spot-check that Quranic text actually landed (non-empty everywhere
        // is verified by the importer's validation; here we verify access).
        val fatiha = database.ayahDao().observeSurahAyahs(1).first()
        assertEquals(7, fatiha.size)
        assertTrue(fatiha.all { it.textUthmani.isNotEmpty() })
    }

    @Test
    fun surahReferencesResolve() = runBlocking {
        importer.importIfNeeded()
        val baqarah = database.surahDao().getSurah(2)
        assertEquals(286, baqarah?.ayahCount)
        assertEquals("Al-Baqara", baqarah?.nameTransliterated)

        val kursi = database.ayahDao().getAyah(2, 255)
        assertEquals(42, kursi?.page)
        assertEquals(3, kursi?.juz)
    }
}
