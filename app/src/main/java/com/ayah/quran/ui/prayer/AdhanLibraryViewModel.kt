package com.ayah.quran.ui.prayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.prayer.AdhanDownloadRepository
import com.ayah.quran.data.prayer.AdhanPreviewController
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the audio library. */
data class AdhanLibraryRow(
    val recording: AdhanRecording,
    val isSelectedForPrayer: Boolean,
    val downloadState: com.ayah.quran.data.prayer.AdhanDownloadState,
    val isDownloaded: Boolean,
)

data class AdhanLibraryUiState(
    val prayer: Prayer? = null,
    val rows: List<AdhanLibraryRow> = emptyList(),
    val searchQuery: String = "",
    val categoryFilter: AdhanCategory? = null,
    val downloadedOnly: Boolean = false,
    val selectedVoiceId: String? = null,
    val adhanConfig: PrayerAdhanConfig = PrayerAdhanConfig(),
) {
    /** True when the prayer's stored voice points at an undownloaded remote file. */
    val selectedVoiceNeedsDownload: Boolean
        get() = rows.any { it.isSelectedForPrayer && !it.isDownloaded && !it.recording.isBundled }
}

@HiltViewModel
class AdhanLibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val preferencesRepository: PrayerPreferencesRepository,
    private val catalog: BundledAdhanCatalog,
    private val downloadRepository: AdhanDownloadRepository,
    private val previewController: AdhanPreviewController,
) : ViewModel() {

    val prayer: Prayer = runCatching {
        Prayer.valueOf(savedStateHandle.get<String>(KEY_PRAYER) ?: Prayer.FAJR.name)
    }.getOrDefault(Prayer.FAJR)

    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<AdhanCategory?>(null)
    private val _downloadedOnly = MutableStateFlow(false)

    val downloadStates: StateFlow<Map<String, com.ayah.quran.data.prayer.AdhanDownloadState>> =
        downloadRepository.states

    val uiState: StateFlow<AdhanLibraryUiState> = combine(
        preferencesRepository.preferences,
        preferencesRepository.downloadedAdhanIds,
        kotlinx.coroutines.flow.flowOf(emptyMap<String, com.ayah.quran.data.prayer.AdhanDownloadState>()),
        _searchQuery,
        combine(_categoryFilter, _downloadedOnly) { c, d -> c to d },
    ) { prefs, downloadedIds, downloadStates, query, (category, downloadedOnly) ->
        buildState(prefs, downloadedIds, downloadStates, query, category, downloadedOnly)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdhanLibraryUiState(prayer = prayer),
    )

    private fun buildState(
        prefs: PrayerPreferences,
        downloadedIds: Set<String>,
        downloadStates: Map<String, com.ayah.quran.data.prayer.AdhanDownloadState>,
        query: String,
        category: AdhanCategory?,
        downloadedOnly: Boolean,
    ): AdhanLibraryUiState {
        val selectedVoice = prefs.voiceSelection.voiceOf(prayer)
        val all = catalog.recordings.map { recording ->
            val isDownloaded = recording.isBundled || recording.id in downloadedIds
            AdhanLibraryRow(
                recording = recording,
                isSelectedForPrayer = selectedVoice == recording.id,
                downloadState = when {
                    recording.isBundled -> com.ayah.quran.data.prayer.AdhanDownloadState.Downloaded
                    else -> downloadStates[recording.id]
                        ?: if (isDownloaded) {
                            com.ayah.quran.data.prayer.AdhanDownloadState.Downloaded
                        } else {
                            com.ayah.quran.data.prayer.AdhanDownloadState.NotDownloaded
                        }
                },
                isDownloaded = isDownloaded,
            )
        }
        val filtered = all.filter { row ->
            val matchesQuery = query.isBlank() || listOf(
                row.recording.displayNameAr,
                row.recording.displayNameEn,
                row.recording.descriptionAr,
                row.recording.descriptionEn,
                row.recording.mosqueCityAr,
                row.recording.mosqueCityEn,
                row.recording.muadhinNameAr,
                row.recording.muadhinNameEn,
            ).any { it?.contains(query, ignoreCase = true) == true }
            val matchesCategory = category == null || row.recording.category == category
            val matchesDownloaded = !downloadedOnly || row.isDownloaded
            matchesQuery && matchesCategory && matchesDownloaded
        }
        return AdhanLibraryUiState(
            prayer = prayer,
            rows = filtered,
            searchQuery = query,
            categoryFilter = category,
            downloadedOnly = downloadedOnly,
            selectedVoiceId = selectedVoice,
            adhanConfig = prefs.adhanConfig,
        )
    }

    // ---- search/filter ----

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: AdhanCategory?) {
        _categoryFilter.value = category
    }

    fun setDownloadedOnly(enabled: Boolean) {
        _downloadedOnly.value = enabled
    }

    // ---- selection ----

    fun select(recording: AdhanRecording) {
        // A remote recording must be DOWNLOADED before it can be assigned for
        // offline scheduled playback (Phase 15 §4).
        val downloaded = downloadRepository.isDownloaded(recording.id) || recording.isBundled
        if (!downloaded && !recording.isBundled) return
        viewModelScope.launch {
            preferencesRepository.setVoice(prayer, recording.id)
        }
    }

    // ---- downloads ----

    fun download(recording: AdhanRecording) = downloadRepository.download(recording)

    fun cancelDownload(recording: AdhanRecording) = downloadRepository.cancelDownload(recording.id)

    fun deleteDownload(recording: AdhanRecording) = downloadRepository.deleteDownload(recording.id)

    // ---- preview ----

    fun startPreview(recording: AdhanRecording) {
        val file = if (recording.isBundled) null else downloadRepository.downloadedFile(recording.id)
        previewController.start(recording, file)
    }

    fun pausePreview() = previewController.pause()

    fun resumePreview() = previewController.resume()

    fun stopPreview() = previewController.stop()

    override fun onCleared() {
        previewController.stop()
        super.onCleared()
    }

    private companion object {
        const val KEY_PRAYER = "prayer"
    }
}
