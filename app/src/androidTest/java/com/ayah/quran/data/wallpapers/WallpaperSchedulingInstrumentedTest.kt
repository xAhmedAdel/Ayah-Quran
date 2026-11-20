package com.ayah.quran.data.wallpapers

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ayah.quran.background.WallpaperWorker
import com.ayah.quran.core.quran.AyahSelector
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.usecase.ApplyWallpaperAutomaticallyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scheduling lifecycle on the real WorkManager: enable → unique periodic
 * work enqueued; re-enable with a new interval → updated, not duplicated;
 * pause → cancelled. Plus one real automatic cycle (select → render →
 * apply → record) through the worker's use case.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperSchedulingInstrumentedTest {

    private lateinit var repository: WallpaperSchedulingRepositoryImpl
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsRepository = FakeSettingsRepository()
        repository = WallpaperSchedulingRepositoryImpl(context, settingsRepository)
        workManager = WorkManager.getInstance(context)
        // Start from a clean scheduling state.
        workManager.cancelUniqueWork(WallpaperWorker.UNIQUE_WORK_NAME)
    }

    @After
    fun tearDown() {
        workManager.cancelUniqueWork(WallpaperWorker.UNIQUE_WORK_NAME)
    }

    private suspend fun uniqueWorkInfos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(WallpaperWorker.UNIQUE_WORK_NAME).get()

    @Test
    fun enableEnqueuesUniquePeriodicWork() = runBlocking {
        repository.enable(60)

        val infos = uniqueWorkInfos()
        assertEquals("unique work must not duplicate", 1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)

        val status = repository.observeStatus().first()
        assertTrue(status.isScheduled)
        assertEquals(60, status.intervalMinutes)
    }

    @Test
    fun reEnableUpdatesIntervalWithoutDuplicating() = runBlocking {
        // Production always updates the saved settings before enabling; the
        // reported interval is the saved setting (WorkManager exposes no
        // periodic-interval accessor on WorkInfo).
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settingsRepository = FakeSettingsRepository()
        settingsRepository.update { it.copy(intervalMinutes = 60) }
        val localRepository = WallpaperSchedulingRepositoryImpl(context, settingsRepository)

        localRepository.enable(60)
        settingsRepository.update { it.copy(intervalMinutes = 1440) }
        localRepository.enable(1440)

        assertEquals("re-enabling must not duplicate unique work", 1, uniqueWorkInfos().size)
        assertTrue(uniqueWorkInfos()[0].state == WorkInfo.State.ENQUEUED)
        assertEquals(1440, localRepository.observeStatus().first().intervalMinutes)
    }

    @Test
    fun pauseCancelsTheUniqueWork() = runBlocking {
        repository.enable(30)
        assertTrue(repository.observeStatus().first().isScheduled)

        repository.pause()

        assertTrue(uniqueWorkInfos().all { it.state == WorkInfo.State.CANCELLED })
        assertFalse(repository.observeStatus().first().isScheduled)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedIntervalIsRejected() = runBlocking {
        repository.enable(17)
    }

    @Test
    fun oneAutomaticCycleAppliesAndRecordsSuccess() = runBlocking {
        // Drive a real automatic cycle (select → render → apply → record)
        // through the worker's use case using real dependencies.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = androidx.room.Room.inMemoryDatabaseBuilder(context, com.ayah.quran.core.database.AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            com.ayah.quran.data.quran.QuranImporter(
                context = context,
                database = database,
                parser = com.ayah.quran.data.quran.QuranTextParser(),
                appScope = CoroutineScope(Dispatchers.IO),
            ).importIfNeeded()

            val settingsRepository = FakeSettingsRepository()
            val wallpaperRepository = WallpaperRepositoryImpl(
                context = context,
                database = database,
                renderer = com.ayah.quran.core.wallpaper.WallpaperRenderer(context),
                ayahDao = database.ayahDao(),
                surahDao = database.surahDao(),
                wallpaperHistoryDao = database.wallpaperHistoryDao(),
                selectionHistoryDao = database.selectionHistoryDao(),
                settingsRepository = settingsRepository,
                dataStore = FakeDataStore(),
            )
            val useCase = ApplyWallpaperAutomaticallyUseCase(
                settingsRepository = settingsRepository,
                wallpaperRepository = wallpaperRepository,
                selector = AyahSelector(),
            )

            val status = useCase()
            assertEquals(WallpaperApplyStatus.SUCCESS, status)

            val history = database.wallpaperHistoryDao().observeAll().first()
            assertEquals(1, history.size)
            assertEquals("SUCCESS", history[0].status)
            assertTrue(wallpaperRepository.recentSelections().isNotEmpty())
        } finally {
            database.close()
        }
    }
}
