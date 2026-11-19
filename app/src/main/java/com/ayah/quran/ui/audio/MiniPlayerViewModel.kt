package com.ayah.quran.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.audio.AyahPlaybackState
import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.data.audio.QuranRepeatRange
import com.ayah.quran.data.audio.QuranSleepMode
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.QuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mini player + Now Playing screen shared state. */
data class MiniPlayerUiState(
    val playback: AyahPlaybackState = AyahPlaybackState(),
    val surahNameAr: String = "",
    val reciterNameAr: String = "",
    /** True while playback is blocked by an active adhan (policy notice). */
    val blockedByAdhan: Boolean = false,
    /** Persisted playback speed (drives the Now Playing speed chips). */
    val playbackSpeed: Float = 1.0f,
) {
    val isIdle: Boolean get() = playback.isIdle && playback.restorable == null
}

/**
 * Drives the persistent mini player and the Now Playing screen. Speed and
 * repeat-ayah persist through [AudioPreferencesRepository]; sleep timer and
 * repeat range live in the playback client (valid in background — the
 * foreground service keeps the process alive).
 */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val ayahPlayer: ExoPlayerAyahPlaylistPlayer,
    private val preferencesRepository: AudioPreferencesRepository,
    quranRepository: QuranRepository,
) : ViewModel() {

    init {
        // Apply the persisted playback speed whenever it changes.
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                ayahPlayer.setPlaybackSpeed(prefs.playbackSpeed)
            }
        }
    }

    private val prefsFlow = preferencesRepository.preferences

    val uiState: StateFlow<MiniPlayerUiState> = combine(
        ayahPlayer.state,
        quranRepository.observeSurahs(),
        prefsFlow,
    ) { playback, surahs, prefs ->
        MiniPlayerUiState(
            playback = playback,
            surahNameAr = surahs.firstOrNull { it.id == playback.surah }?.nameArabic
                ?: surahs.firstOrNull { it.id == playback.restorable?.surah }?.nameArabic
                ?: "",
            reciterNameAr = QuranAudioCatalog.byEdition(playback.editionId).nameAr,
            blockedByAdhan = playback.blockedByAdhan,
            playbackSpeed = prefs.playbackSpeed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MiniPlayerUiState(),
    )

    // ---- actions (thin delegation, same as AudioViewModel slice 2) ----

    fun playSurah(surah: Int) {
        val edition = currentEdition()
        ayahPlayer.play(edition, surah)
    }

    fun resumeLast() = ayahPlayer.resumeLast()

    fun pause() = ayahPlayer.pause()

    fun resume() = ayahPlayer.resume()

    fun stop() = ayahPlayer.stop()

    fun seekNextAyah() = ayahPlayer.seekNextAyah()

    fun seekPreviousAyah() = ayahPlayer.seekPreviousAyah()

    fun seekToAyah(index: Int) = ayahPlayer.seekToAyah(index)

    fun toggleRepeatAyah() = ayahPlayer.toggleRepeatAyah()

    fun setSleepTimer(mode: QuranSleepMode, minutes: Int = 0) =
        ayahPlayer.setSleepTimer(mode, minutes)

    fun setRepeatRange(range: QuranRepeatRange?) =
        ayahPlayer.setRepeatRange(range)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.update { it.copy(playbackSpeed = speed) }
        }
    }

    fun positionMs(): Long = ayahPlayer.positionMs()

    fun durationMs(): Long = ayahPlayer.durationMs()

    private fun currentEdition() = QuranAudioCatalog.byEdition(
        uiState.value.playback.editionId,
    )
}
