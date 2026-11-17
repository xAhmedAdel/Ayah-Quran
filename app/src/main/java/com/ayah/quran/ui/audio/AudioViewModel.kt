package com.ayah.quran.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.Reciter
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.AudioRepository
import com.ayah.quran.domain.repository.ReciterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) {
    /** Controls are enabled only when a real, playable source is prepared. */
    val hasPlayableSource: Boolean
        get() = playbackState is PlaybackState.Ready ||
            playbackState is PlaybackState.Playing ||
            playbackState is PlaybackState.Paused ||
            playbackState is PlaybackState.Buffering ||
            playbackState is PlaybackState.Ended
}

data class AudioUiState(
    val reciters: List<Reciter> = emptyList(),
    val isLoadingReciters: Boolean = true,
    val playback: PlaybackPanelState = PlaybackPanelState(),
)

/**
 * Audio tab foundation. With an empty licensed catalog the reciter list is
 * honestly empty and playback controls stay disabled with an explanation —
 * no fabricated reciters, sources, or playing states.
 */
@HiltViewModel
class AudioViewModel @Inject constructor(
    reciterRepository: ReciterRepository,
    private val audioRepository: AudioRepository,
    private val preferencesRepository: AudioPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<AudioUiState> = combine(
        reciterRepository.observeReciters(),
        audioRepository.playbackState,
        audioRepository.positionMs,
        audioRepository.durationMs,
        preferencesRepository.preferences,
    ) { reciters, playbackState, positionMs, durationMs, prefs ->
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AudioUiState(isLoadingReciters = true),
    )

    fun playPause() {
        viewModelScope.launch {
            when (audioRepository.playbackState.value) {
                is com.ayah.quran.domain.model.audio.PlaybackState.Playing -> audioRepository.pause()
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
