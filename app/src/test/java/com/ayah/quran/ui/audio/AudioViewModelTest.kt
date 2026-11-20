package com.ayah.quran.ui.audio

import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.data.audio.QuranAudioDownloadRepository
import com.ayah.quran.data.audio.QuranAudioState
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferences
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiStateUsesCachedDownloadedCountsWithoutCreatingAudioDirAndPersistsReciterPreference() = runTest(mainDispatcher) {
        val audioDir = File(tempFolder.root, "vm_audio_dir")
        val prefsFlow = MutableStateFlow(
            AudioPreferences(
                selectedReciterId = "ar.alafasy",
                repeatMode = RepeatMode.OFF,
                playbackSpeed = 1.0f,
            ),
        )
        val fakePrefs = object : AudioPreferencesRepository {
            override val preferences: Flow<AudioPreferences> = prefsFlow
            override suspend fun update(transform: (AudioPreferences) -> AudioPreferences) {
                prefsFlow.value = transform(prefsFlow.value)
            }
        }
        val surahs = listOf(
            Surah(1, "الفاتحة", "Al-Fatihah", "The Opener", RevelationType.MECCAN, 7),
            Surah(108, "الكوثر", "Al-Kawthar", "The Abundance", RevelationType.MECCAN, 3),
        )
        val fakeQuran = object : QuranRepository {
            override val importState: Flow<QuranImportState> = flowOf(QuranImportState.Completed)
            override suspend fun importIfNeeded() = Unit
            override fun observeSurahs(): Flow<List<Surah>> = flowOf(surahs)
            override fun searchSurahs(query: String): Flow<List<Surah>> = flowOf(surahs)
            override suspend fun getSurah(surahId: Int): Surah? = surahs.firstOrNull { it.id == surahId }
            override fun observeAyahs(surahId: Int): Flow<List<Ayah>> = flowOf(emptyList())
            override suspend fun getAyah(surahId: Int, ayahNumber: Int): Ayah? = null
            override suspend fun ayahsOnPage(page: Int): List<Ayah> = emptyList()
            override suspend fun pageForSurahStart(surahId: Int): Int? = null
            override suspend fun pageForAyah(surahId: Int, ayahNumber: Int): Int? = null
        }

        val downloadRepo = QuranAudioDownloadRepository(
            audioDirProvider = { audioDir },
            client = OkHttpClient(),
            scope = backgroundScope,
            ioDispatcher = mainDispatcher,
            minAyahBytes = 1_000L,
            autoScanOnInit = false,
        )
        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = downloadRepo,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = mainDispatcher,
            stopControllerAction = {},
            stopServiceAction = {},
            autoConnect = false,
        )

        val vm = AudioViewModel(
            preferencesRepository = fakePrefs,
            quranRepository = fakeQuran,
            downloadRepository = downloadRepo,
            ayahPlayer = player,
        )

        val collectJob = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(audioDir.exists())
        assertEquals("ar.alafasy", vm.uiState.value.selectedEditionId)
        assertEquals(QuranAudioCatalog.reciters.size, vm.uiState.value.quranReciters.size)
        assertEquals(2, vm.uiState.value.surahRows.size)
        assertEquals(0, vm.uiState.value.surahRows.first { it.surah == 108 }.downloadedAyahs)
        assertFalse(vm.uiState.value.surahRows.first { it.surah == 108 }.isDownloaded)

        val surah108Dir = File(File(audioDir, "ar.alafasy"), "108").apply { mkdirs() }
        for (ayah in 1..3) {
            File(surah108Dir, "$ayah.mp3").writeBytes(ByteArray(2048) { 7 })
        }
        downloadRepo.refreshDownloadedCounts()
        advanceUntilIdle()

        val row108 = vm.uiState.value.surahRows.first { it.surah == 108 }
        assertEquals(3, row108.downloadedAyahs)
        assertTrue(row108.isDownloaded)
        assertEquals(QuranAudioState.Downloaded, row108.downloadState)

        vm.selectReciter("ar.husary")
        advanceUntilIdle()
        assertEquals("ar.husary", vm.uiState.value.selectedEditionId)
        assertEquals("ar.husary", prefsFlow.value.selectedReciterId)
        assertFalse(vm.uiState.value.surahRows.first { it.surah == 108 }.isDownloaded)

        collectJob.cancel()
    }
}
