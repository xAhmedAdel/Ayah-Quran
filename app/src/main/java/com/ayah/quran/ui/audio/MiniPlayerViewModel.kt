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

data class MiniPlayerUiState(
    val playback: AyahPlaybackState = AyahPlaybackState(),
    val surahNameAr: String = "",
    val surahNameTransliterated: String = "",
    val reciterNameAr: String = "",
    val reciterNameEn: String = "",
    val blockedByAdhan: Boolean = false,
    val playbackSpeed: Float = 1.0f,
) {
    val isIdle: Boolean get() = playback.isIdle && playback.restorable == null

    val progressFraction: Float
        get() = if (playback.totalAyahs > 0) {
            ((playback.ayahIndex + 1).toFloat() / playback.totalAyahs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val ayahPlayer: ExoPlayerAyahPlaylistPlayer,
    private val preferencesRepository: AudioPreferencesRepository,
    quranRepository: QuranRepository,
) : ViewModel() {

    init {
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
        val surah = surahs.firstOrNull { it.id == playback.surah }
            ?: surahs.firstOrNull { it.id == playback.restorable?.surah }
        val edition = QuranAudioCatalog.byEdition(playback.editionId)
        MiniPlayerUiState(
            playback = playback,
            surahNameAr = surah?.nameArabic ?: "",
            surahNameTransliterated = surah?.nameTransliterated ?: "",
            reciterNameAr = edition.nameAr,
            reciterNameEn = edition.nameEn,
            blockedByAdhan = playback.blockedByAdhan,
            playbackSpeed = prefs.playbackSpeed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MiniPlayerUiState(),
    )

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
