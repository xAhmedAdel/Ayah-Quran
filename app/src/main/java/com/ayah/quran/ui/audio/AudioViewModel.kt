package com.ayah.quran.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.audio.AyahPlaybackState
import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.data.audio.QuranAudioDownloadRepository
import com.ayah.quran.data.audio.QuranAudioState
import com.ayah.quran.data.audio.QuranReciterEdition
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.Reciter
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.AudioRepository
import com.ayah.quran.domain.repository.QuranRepository
import com.ayah.quran.domain.repository.ReciterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Playback-foundation panel state — reflects ACTUAL player state only. */
data class PlaybackPanelState(
    val playbackState: PlaybackState = PlaybackState.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val selectedSurahNumber: Int = 1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
)

/** One surah row in the Phase 17 recitation list. */
data class SurahAudioRow(
    val surah: Int,
    val nameArabic: String,
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
    // Phase 5 legacy surface (kept for the old playback panel).
    val reciters: List<Reciter> = emptyList(),
    val isLoadingReciters: Boolean = true,
    val playback: PlaybackPanelState = PlaybackPanelState(),
    // Phase 17 Quran recitations.
    val quranReciters: List<QuranReciterEdition> = emptyList(),
    val selectedEditionId: String? = null,
    val surahRows: List<SurahAudioRow> = emptyList(),
    val ayahPlayback: AyahPlaybackState = AyahPlaybackState(),
)

/**
 * Audio tab (Phase 17): verified reciter editions, per-ayah offline
 * downloads with resumable progress, and per-ayah playlist playback
 * (ayah highlight + repeat-ayah live in [AyahPlaybackState]).
 */
@HiltViewModel
class AudioViewModel @Inject constructor(
    reciterRepository: ReciterRepository,
    private val audioRepository: AudioRepository,
    private val preferencesRepository: AudioPreferencesRepository,
    private val quranRepository: QuranRepository,
    private val downloadRepository: QuranAudioDownloadRepository,
    private val ayahPlayer: ExoPlayerAyahPlaylistPlayer,
) : ViewModel() {

    private val _selectedEdition = MutableStateFlow<String?>(null)

    init {
        // Restore the persisted reciter once (catalog default until then).
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                if (_selectedEdition.value == null && prefs.selectedReciterId != null) {
                    _selectedEdition.value = prefs.selectedReciterId
                }
            }
        }
    }

    private val legacyState = combine(
        reciterRepository.observeReciters(),
        audioRepository.playbackState,
        audioRepository.positionMs,
        audioRepository.durationMs,
        preferencesRepository.preferences,
    ) { reciters, playbackState, positionMs, durationMs, prefs ->
        Triple(reciters, playbackState, Triple(positionMs, durationMs, prefs))
    }

    private val phase17State = combine(
        quranRepository.observeSurahs(),
        downloadRepository.states,
        ayahPlayer.state,
        _selectedEdition,
    ) { surahs, downloadStates, ayahPlayback, selectedEdition ->
        Triple(surahs, downloadStates, ayahPlayback) to selectedEdition
    }

    val uiState: StateFlow<AudioUiState> = combine(
        legacyState,
        phase17State,
    ) { (reciters, playbackState, posDurPrefs),
        (surahsAndDownloads, selectedEdition) ->
        val (surahs, downloadStates, ayahPlayback) = surahsAndDownloads
        val (positionMs, durationMs, prefs) = posDurPrefs
        val edition = QuranAudioCatalog.byEdition(
            selectedEdition ?: prefs.selectedReciterId,
        )
        AudioUiState(
            reciters = reciters,
            isLoadingReciters = false,
            playback = PlaybackPanelState(
                playbackState = playbackState,
                positionMs = positionMs,
                durationMs = durationMs,
                repeatMode = prefs.repeatMode,
                playbackSpeed = prefs.playbackSpeed,
            ),
            quranReciters = QuranAudioCatalog.reciters,
            selectedEditionId = edition.editionId,
            surahRows = buildSurahRows(surahs, edition, downloadStates, ayahPlayback),
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
        ayahPlayback: AyahPlaybackState,
    ): List<SurahAudioRow> = surahs.map { surah ->
        val state = downloadStates["${edition.editionId}:${surah.id}"]
        val downloaded = downloadRepository.downloadedAyahCount(
            edition.editionId, surah.id, surah.ayahCount,
        )
        val isCurrent = !ayahPlayback.isIdle &&
            ayahPlayback.surah == surah.id &&
            ayahPlayback.editionId == edition.editionId
        SurahAudioRow(
            surah = surah.id,
            nameArabic = surah.nameArabic,
            ayahCount = surah.ayahCount,
            downloadState = state ?: if (downloaded >= surah.ayahCount) {
                QuranAudioState.Downloaded
            } else {
                QuranAudioState.NotDownloaded
            },
            downloadedAyahs = downloaded,
            isCurrent = isCurrent,
            isPlaying = isCurrent && ayahPlayback.isPlaying,
        )
    }

    // ---- Phase 17 actions ----

    fun selectReciter(editionId: String) {
        _selectedEdition.value = editionId
        viewModelScope.launch {
            preferencesRepository.update { it.copy(selectedReciterId = editionId) }
        }
        // Switching reciter stops playback of the previous edition.
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

    // ---- Phase 5 legacy panel actions (kept; panel still binds to them) ----

    fun playPause() {
        viewModelScope.launch {
            when (audioRepository.playbackState.value) {
                is PlaybackState.Playing -> audioRepository.pause()
                else -> audioRepository.play()
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        viewModelScope.launch {
            audioRepository.setRepeatMode(mode)
            preferencesRepository.update { it.copy(repeatMode = mode) }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            audioRepository.setPlaybackSpeed(speed)
            preferencesRepository.update { it.copy(playbackSpeed = speed) }
        }
    }
}
