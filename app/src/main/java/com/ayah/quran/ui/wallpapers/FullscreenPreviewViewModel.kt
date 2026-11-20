package com.ayah.quran.ui.wallpapers

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import com.ayah.quran.domain.usecase.ApplyWallpaperNowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FullscreenPreviewUiState(
    val bitmap: Bitmap? = null,
    val ayahLabel: String? = null,
    val destinations: WallpaperDestination = WallpaperDestination.BOTH,
    val busy: Boolean = true,
    val applying: Boolean = false,
    val applyOutcome: WallpaperApplyStatus? = null,
)

@HiltViewModel
class FullscreenPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wallpaperRepository: WallpaperRepository,
    private val settingsRepository: WallpaperSettingsRepository,
    private val applyWallpaperNow: ApplyWallpaperNowUseCase,
) : ViewModel() {

    val surahId: Int = checkNotNull(savedStateHandle["surahId"])
    val ayahNumber: Int = checkNotNull(savedStateHandle["ayahNumber"])

    private val _uiState = MutableStateFlow(FullscreenPreviewUiState())
    val uiState: StateFlow<FullscreenPreviewUiState> = _uiState.asStateFlow()

    init {
        settingsRepository.settings
            .onEach { settings ->
                _uiState.update { it.copy(destinations = settings.destinations) }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val ayah = wallpaperRepository.loadAyah(surahId, ayahNumber)
            if (ayah == null) {
                _uiState.update { it.copy(busy = false, destinations = settings.destinations) }
                return@launch
            }
            val surahName = wallpaperRepository.surahName(surahId) ?: "Surah $surahId"
            val label = "$surahName · $surahId:$ayahNumber"
            val template = WallpaperTemplate.byId(settings.templateId)
            val metrics = wallpaperRepository.screenDimensions()
            val bitmap = wallpaperRepository.renderPreview(
                ayahText = ayah.textUthmani,
                referenceLabel = label,
                template = template,
                widthPx = metrics.first.coerceAtMost(1600),
                heightPx = metrics.second.coerceAtMost(2800),
                fontScale = settings.fontScale,
                verticalPosition = settings.verticalPosition,
            )
            _uiState.update {
                it.copy(
                    bitmap = bitmap,
                    ayahLabel = label,
                    destinations = settings.destinations,
                    busy = false,
                )
            }
        }
    }

    fun setDestinations(destinations: WallpaperDestination) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(destinations = destinations) }
        }
    }

    fun applyWallpaper() {
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true, applyOutcome = null) }
            val ayah = wallpaperRepository.loadAyah(surahId, ayahNumber)
            val outcome = if (ayah == null) {
                WallpaperApplyStatus.FAILED
            } else {
                applyWallpaperNow(ayah)
            }
            _uiState.update { it.copy(applying = false, applyOutcome = outcome) }
        }
    }
}
