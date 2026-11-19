package com.ayah.quran.ui.settings

import app.cash.turbine.test
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerLocation
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection
import com.ayah.quran.domain.repository.AudioPreferences
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.repository.ReaderSettings
import com.ayah.quran.domain.repository.WallpaperSchedulingRepository
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import com.ayah.quran.domain.usecase.ObserveWallpaperScheduleStatusUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHubViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val readerRepo = mockk<ReaderPreferencesRepository>(relaxed = true)
    private val audioRepo = mockk<AudioPreferencesRepository>(relaxed = true)
    private val prayerRepo = mockk<PrayerPreferencesRepository>(relaxed = true)
    private val wallpaperRepo = mockk<WallpaperSchedulingRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `summary combines reader, audio, prayer city, and wallpaper status`() = runTest(
        testDispatcher.scheduler,
    ) {
        val reciter = QuranAudioCatalog.reciters.last()
        every { readerRepo.settings } returns MutableStateFlow(
            ReaderSettings(fontScale = 1.25f, readingMode = ReadingMode.CONTINUOUS),
        )
        every { audioRepo.preferences } returns MutableStateFlow(
            AudioPreferences(
                selectedReciterId = reciter.editionId,
                repeatMode = RepeatMode.OFF,
                playbackSpeed = 1.5f,
            ),
        )
        every { prayerRepo.preferences } returns MutableStateFlow(
            samplePrayerPrefs(
                PrayerLocation(
                    cityAr = "القاهرة",
                    cityEn = "Cairo",
                    countryAr = "مصر",
                    countryEn = "Egypt",
                    latitude = 30.0444,
                    longitude = 31.2357,
                    timezoneId = "Africa/Cairo",
                ),
            ),
        )
        every { wallpaperRepo.observeStatus() } returns MutableStateFlow(
            WallpaperScheduleStatus(isScheduled = true, intervalMinutes = 180),
        )

        val vm = createViewModel()
        vm.summary.test {
            awaitItem()
            val item = awaitItem()
            assertEquals(ReadingMode.CONTINUOUS, item.readingMode)
            assertEquals(125, item.fontScalePercent)
            assertEquals(reciter.nameAr, item.reciterNameAr)
            assertEquals(reciter.nameEn, item.reciterNameEn)
            assertEquals(1.5f, item.playbackSpeed, 0.001f)
            assertEquals("القاهرة", item.prayerCityAr)
            assertEquals("Cairo", item.prayerCityEn)
            assertEquals(true, item.wallpaperSchedule.isScheduled)
            assertEquals(180, item.wallpaperSchedule.intervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `summary leaves prayer city null when location is unset`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { readerRepo.settings } returns MutableStateFlow(
            ReaderSettings(fontScale = 1.0f, readingMode = ReadingMode.MUSHAF),
        )
        every { audioRepo.preferences } returns MutableStateFlow(
            AudioPreferences(null, RepeatMode.OFF, 1.0f),
        )
        every { prayerRepo.preferences } returns MutableStateFlow(samplePrayerPrefs(null))
        every { wallpaperRepo.observeStatus() } returns MutableStateFlow(
            WallpaperScheduleStatus(isScheduled = false, intervalMinutes = null),
        )

        val vm = createViewModel()
        vm.summary.test {
            val item = awaitItem()
            assertNull(item.prayerCityAr)
            assertNull(item.prayerCityEn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setReadingMode delegates update to reader preferences repository`() = runTest(
        testDispatcher.scheduler,
    ) {
        val state = MutableStateFlow(ReaderSettings(1.0f, ReadingMode.MUSHAF))
        every { readerRepo.settings } returns state
        coEvery { readerRepo.update(any()) } answers {
            val transform = firstArg<(ReaderSettings) -> ReaderSettings>()
            state.value = transform(state.value)
        }
        every { audioRepo.preferences } returns MutableStateFlow(
            AudioPreferences(null, RepeatMode.OFF, 1.0f),
        )
        every { prayerRepo.preferences } returns MutableStateFlow(samplePrayerPrefs(null))
        every { wallpaperRepo.observeStatus() } returns MutableStateFlow(
            WallpaperScheduleStatus(false, null),
        )

        val vm = createViewModel()
        vm.setReadingMode(ReadingMode.CONTINUOUS)
        advanceUntilIdle()

        coVerify(exactly = 1) { readerRepo.update(any()) }
        assertEquals(ReadingMode.CONTINUOUS, state.value.readingMode)
    }

    private fun createViewModel(): SettingsHubViewModel = SettingsHubViewModel(
        readerPreferencesRepository = readerRepo,
        audioPreferencesRepository = audioRepo,
        prayerPreferencesRepository = prayerRepo,
        observeWallpaperScheduleStatus = ObserveWallpaperScheduleStatusUseCase(wallpaperRepo),
    )

    private fun samplePrayerPrefs(location: PrayerLocation?): PrayerPreferences = PrayerPreferences(
        location = location,
        autoLocationEnabled = false,
        method = CalculationMethod.EGYPTIAN,
        asrMethod = AsrMethod.STANDARD,
        highLatitudeRule = HighLatitudeRulePref.MIDDLE_OF_NIGHT,
        adjustments = PrayerAdjustments(),
        adhanConfig = PrayerAdhanConfig(),
        voiceSelection = PrayerVoiceSelection(),
        hijriAdjustmentDays = 0,
        customAdhanAudioUri = null,
    )
}
