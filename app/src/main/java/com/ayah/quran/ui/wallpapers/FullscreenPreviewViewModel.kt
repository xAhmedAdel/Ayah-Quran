package com.ayah.quran.ui.wallpapers

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import com.ayah.quran.domain.usecase.ApplyWallpaperNowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FullscreenPreviewUiState(
    val bitmap: Bitmap? = null,
    val ayahLabel: String? = null,
    val busy: Boolean = true,
    val applying: Boolean = false,
    val applyOutcome: WallpaperApplyStatus? = null,
)

/**
 * Immersive full-screen preview: renders the requested verse with the SAVED
 * settings (template/font/position) at true screen dimensions using the
 * production renderer, and applies with one tap.
 */
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
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val ayah = wallpaperRepository.loadAyah(surahId, ayahNumber)
            if (ayah == null) {
                _uiState.value = _uiState.value.copy(busy = false)
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
            _uiState.value = FullscreenPreviewUiState(
                bitmap = bitmap,
                ayahLabel = label,
                busy = false,
            )
        }
    }

    fun applyWallpaper() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(applying = true, applyOutcome = null)
            val ayah = wallpaperRepository.loadAyah(surahId, ayahNumber)
            val outcome = if (ayah == null) {
                WallpaperApplyStatus.FAILED
            } else {
                applyWallpaperNow(ayah)
            }
            _uiState.value = _uiState.value.copy(applying = false, applyOutcome = outcome)
        }
    }
}
