package com.ayah.quran.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.audio.AyahPlaybackState
import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.data.audio.QuranAudioDownloadRepository
import com.ayah.quran.data.audio.QuranAudioState
import com.ayah.quran.data.audio.QuranReciterEdition
import com.ayah.quran.data.audio.QuranRepeatRange
import com.ayah.quran.data.audio.QuranSleepMode
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.Reciter
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.QuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaybackPanelState(
    val playbackState: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val selectedSurahNumber: Int = 1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
)

data class SurahAudioRow(
    val surah: Int,
    val nameArabic: String,
    val nameTransliterated: String = "",
    val ayahCount: Int,
    val downloadState: QuranAudioState = QuranAudioState.NotDownloaded,
    val downloadedAyahs: Int = 0,
    val isCurrent: Boolean = false,
    val isPlaying: Boolean = false,
) {
    val isDownloaded: Boolean
        get() = downloadState == QuranAudioState.Downloaded ||
            (ayahCount > 0 && downloadedAyahs >= ayahCount)
}

data class AudioUiState(
    val reciters: List<Reciter> = emptyList(),
    val isLoadingReciters: Boolean = true,
    val playback: PlaybackPanelState = PlaybackPanelState(),
    val quranReciters: List<QuranReciterEdition> = emptyList(),
    val selectedEditionId: String? = null,
    val surahRows: List<SurahAudioRow> = emptyList(),
    val ayahPlayback: AyahPlaybackState = AyahPlaybackState(),
)

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val preferencesRepository: AudioPreferencesRepository,
    private val quranRepository: QuranRepository,
    private val downloadRepository: QuranAudioDownloadRepository,
    private val ayahPlayer: ExoPlayerAyahPlaylistPlayer,
) : ViewModel() {

    private val _selectedEdition = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                if (_selectedEdition.value == null && prefs.selectedReciterId != null) {
                    _selectedEdition.value = prefs.selectedReciterId
                }
            }
        }
    }

    private val downloadsSlice = combine(
        downloadRepository.states,
        downloadRepository.downloadedCounts,
    ) { states, counts ->
        states to counts
    }

    val uiState: StateFlow<AudioUiState> = combine(
        quranRepository.observeSurahs(),
        downloadsSlice,
        ayahPlayer.state,
        _selectedEdition,
        preferencesRepository.preferences,
    ) { surahs, (downloadStates, downloadedCounts), ayahPlayback, selectedEdition, prefs ->
        val edition = QuranAudioCatalog.byEdition(
            selectedEdition ?: prefs.selectedReciterId,
        )
        AudioUiState(
            isLoadingReciters = false,
            playback = PlaybackPanelState(
                repeatMode = prefs.repeatMode,
                playbackSpeed = prefs.playbackSpeed,
            ),
            quranReciters = QuranAudioCatalog.reciters,
            selectedEditionId = edition.editionId,
            surahRows = buildSurahRows(
                surahs = surahs,
                edition = edition,
                downloadStates = downloadStates,
                downloadedCounts = downloadedCounts,
                ayahPlayback = ayahPlayback,
            ),
            ayahPlayback = ayahPlayback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AudioUiState(isLoadingReciters = true),
    )

    private fun buildSurahRows(
        surahs: List<Surah>,
        edition: QuranReciterEdition,
        downloadStates: Map<String, QuranAudioState>,
        downloadedCounts: Map<String, Int>,
        ayahPlayback: AyahPlaybackState,
    ): List<SurahAudioRow> = surahs.map { surah ->
        val key = "${edition.editionId}:${surah.id}"
        val state = downloadStates[key]
        val downloaded = (downloadedCounts[key] ?: 0).coerceAtMost(surah.ayahCount)
        val isCurrent = !ayahPlayback.isIdle &&
            ayahPlayback.surah == surah.id &&
            ayahPlayback.editionId == edition.editionId
        SurahAudioRow(
            surah = surah.id,
            nameArabic = surah.nameArabic,
            nameTransliterated = surah.nameTransliterated,
            ayahCount = surah.ayahCount,
            downloadState = state ?: if (surah.ayahCount > 0 && downloaded >= surah.ayahCount) {
                QuranAudioState.Downloaded
            } else {
                QuranAudioState.NotDownloaded
            },
            downloadedAyahs = downloaded,
            isCurrent = isCurrent,
            isPlaying = isCurrent && ayahPlayback.isPlaying,
        )
    }

    fun selectReciter(editionId: String) {
        _selectedEdition.value = editionId
        viewModelScope.launch {
            preferencesRepository.update { it.copy(selectedReciterId = editionId) }
        }
        ayahPlayer.stop()
    }

    fun playSurah(surah: Int) {
        val edition = QuranAudioCatalog.byEdition(_selectedEdition.value)
        ayahPlayer.play(edition, surah)
    }

    fun pauseAyah() = ayahPlayer.pause()

    fun resumeAyah() = ayahPlayer.resume()

    fun stopAyah() = ayahPlayer.stop()

    fun seekAyah(index: Int) = ayahPlayer.seekToAyah(index)

    fun toggleRepeatAyah() = ayahPlayer.toggleRepeatAyah()

    fun setSleepTimer(mode: QuranSleepMode, minutes: Int = 0) =
        ayahPlayer.setSleepTimer(mode, minutes)

    fun setRepeatRange(range: QuranRepeatRange?) =
        ayahPlayer.setRepeatRange(range)

    fun resumeLast() = ayahPlayer.resumeLast()

    fun seekNextAyah() = ayahPlayer.seekNextAyah()

    fun seekPreviousAyah() = ayahPlayer.seekPreviousAyah()

    fun downloadSurah(surah: Int) {
        val edition = QuranAudioCatalog.byEdition(_selectedEdition.value)
        downloadRepository.downloadSurah(edition, surah)
    }

    fun cancelDownload(surah: Int) {
        val edition = QuranAudioCatalog.byEdition(_selectedEdition.value)
        downloadRepository.cancelDownload(edition.editionId, surah)
    }

    fun deleteSurah(surah: Int) {
        val edition = QuranAudioCatalog.byEdition(_selectedEdition.value)
        downloadRepository.deleteSurah(edition.editionId, surah)
    }
}
