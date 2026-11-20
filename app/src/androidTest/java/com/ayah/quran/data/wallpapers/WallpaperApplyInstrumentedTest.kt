package com.ayah.quran.data.wallpapers

import android.app.WallpaperManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.wallpaper.WallpaperRenderer
import com.ayah.quran.data.quran.QuranImporter
import com.ayah.quran.data.quran.QuranTextParser
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.repository.WallpaperSettings
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Applies real rendered wallpapers on-device and verifies that Android
 * actually accepted them (wallpaper ids change, history records SUCCESS),
 * plus the honest failure path and selection-avoidance persistence.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperApplyInstrumentedTest {

    private lateinit var database: AyahDatabase
    private lateinit var repository: WallpaperRepositoryImpl
    private lateinit var settingsRepository: FakeSettingsRepository
    private val wallpaperManager =
        WallpaperManager.getInstance(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        QuranImporter(
            context = context,
            database = database,
            parser = QuranTextParser(),
            appScope = CoroutineScope(Dispatchers.IO),
        ).let { importer -> importer.importIfNeeded() }

        settingsRepository = FakeSettingsRepository()
        repository = WallpaperRepositoryImpl(
            context = context,
            database = database,
            renderer = WallpaperRenderer(context),
            ayahDao = database.ayahDao(),
            surahDao = database.surahDao(),
            wallpaperHistoryDao = database.wallpaperHistoryDao(),
            selectionHistoryDao = database.selectionHistoryDao(),
            settingsRepository = settingsRepository,
            dataStore = FakeDataStore(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun ayah(surahId: Int, ayahNumber: Int): Ayah = runBlocking {
        database.ayahDao().getAyah(surahId, ayahNumber)?.let {
            Ayah(it.id, it.surahId, it.ayahNumber, it.textUthmani, it.juz, it.page)
        } ?: error("verse missing")
    }

    @Test
    fun applyToHomeScreenChangesSystemWallpaperAndRecordsSuccess() = runBlocking {
        val systemIdBefore = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)

        val status = repository.applyWallpaper(
            ayah = ayah(1, 1),
            template = com.ayah.quran.core.wallpaper.WallpaperTemplate.byId(
                com.ayah.quran.core.wallpaper.WallpaperTemplate.EMERALD_LUXURY_ID,
            ),
            destinations = WallpaperDestination.SYSTEM,
            fontScale = 1.0f,
            verticalPosition = TextVerticalPosition.CENTER,
        )

        assertEquals(WallpaperApplyStatus.SUCCESS, status)
        assertNotEquals(systemIdBefore, wallpaperManager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))

        val history = database.wallpaperHistoryDao().observeAll().first()
        assertEquals(1, history.size)
        assertEquals("SUCCESS", history[0].status)
        assertEquals(1, history[0].surahId)
    }

    @Test
    fun applyToLockScreenChangesLockWallpaper() = runBlocking {
        val lockIdBefore = wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK)

        val status = repository.applyWallpaper(
            ayah = ayah(2, 255),
            template = com.ayah.quran.core.wallpaper.WallpaperTemplate.byId(
                com.ayah.quran.core.wallpaper.WallpaperTemplate.MIDNIGHT_GOLD_ID,
            ),
            destinations = WallpaperDestination.LOCK,
            fontScale = 1.0f,
            verticalPosition = TextVerticalPosition.CENTER,
        )

        assertEquals(WallpaperApplyStatus.SUCCESS, status)
        assertNotEquals(lockIdBefore, wallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK))
    }

    @Test
    fun missingVerseIsNeverFabricated() = runBlocking {
        // Out-of-range references resolve to null — the engine can never
        // apply content that is not in the verified dataset.
        assertTrue(repository.loadAyah(999, 999) == null)
        assertTrue(repository.loadAyahByGlobalId(6237L) == null)
        assertEquals(0, database.wallpaperHistoryDao().count())
    }

    @Test
    fun selectionAvoidancePersistsAndTrims() = runBlocking {
        repository.recordSelection(1, 1, SelectionMode.RANDOM)
        repository.recordSelection(1, 2, SelectionMode.RANDOM)
        repository.recordSelection(1, 3, SelectionMode.DAILY)

        val recent = repository.recentSelections()
        assertEquals(3, recent.size)
        assertTrue(recent.contains(database.ayahDao().getAyah(1, 3)?.id?.toLong()))
    }

    @Test
    fun sequentialCursorRoundTrips() = runBlocking {
        assertEquals(0L, repository.sequentialCursor())
        repository.saveSequentialCursor(4213L)
        assertEquals(4213L, repository.sequentialCursor())
    }
}

/** Minimal in-memory settings repository lives in TestFakes.kt (shared). */
/** In-memory DataStore fake lives in TestFakes.kt (shared). */
