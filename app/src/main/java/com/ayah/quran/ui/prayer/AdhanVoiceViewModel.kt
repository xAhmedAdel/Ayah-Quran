package com.ayah.quran.ui.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.prayer.AdhanPreviewController
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing state for the per-prayer adhan voice screen. Preview playback is
 * delegated to [AdhanPreviewController]; selecting a voice only persists —
 * it never touches preview playback or the scheduling pipeline.
 */
@HiltViewModel
class AdhanVoiceViewModel @Inject constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
    private val previewController: AdhanPreviewController,
    val catalog: BundledAdhanCatalog,
) : ViewModel() {

    val preferences: StateFlow<PrayerPreferences?> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    val previewState: StateFlow<AdhanPreviewState> = previewController.state

    fun selectVoice(prayer: Prayer, recordingId: String) {
        viewModelScope.launch {
            preferencesRepository.setVoice(prayer, recordingId)
        }
    }

    fun selectVoiceForAll(recordingId: String) {
        viewModelScope.launch {
            Prayer.entries.forEach { prayer ->
                preferencesRepository.setVoice(prayer, recordingId)
            }
        }
    }

    fun startPreview(recording: AdhanRecording) = previewController.start(recording)

    fun pausePreview() = previewController.pause()

    fun resumePreview() = previewController.resume()

    /** Leaving the screen always stops preview playback. */
    fun stopPreview() = previewController.stop()

    override fun onCleared() {
        previewController.stop()
        super.onCleared()
    }
}
