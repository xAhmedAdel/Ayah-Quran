package com.ayah.quran.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.ReadingPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ObserveReadingPositionUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperHistoryUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperScheduleStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The saved reading position enriched for display. */
data class ContinueReadingState(
    val surahId: Int,
    /** Localized presentation name (Arabic in the Arabic UI). */
    val surahDisplayName: String,
    val ayahNumber: Int,
    val surahAyahCount: Int,
    /** Fraction (0..1) of the surah read — REAL data: ayah ÷ surah ayah count. */
    val progressFraction: Float,
)

/** The most recent successfully applied wallpaper, with a rendered thumbnail. */
data class WallpaperCardState(
    val thumbnail: Bitmap?,
    /** Localized presentation name (Arabic in the Arabic UI). */
    val surahDisplayName: String,
    val surahId: Int,
    val ayahNumber: Int,
    /** Template id; the UI resolves the localized label. */
    val templateId: String,
    val appliedAtMillis: Long,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueReading: ContinueReadingState? = null,
    val wallpaperCard: WallpaperCardState? = null,
    val scheduleStatus: WallpaperScheduleStatus =
        WallpaperScheduleStatus(isScheduled = false, intervalMinutes = null),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeReadingPosition: ObserveReadingPositionUseCase,
    observeWallpaperHistory: ObserveWallpaperHistoryUseCase,
    observeScheduleStatus: ObserveWallpaperScheduleStatusUseCase,
    private val getSurah: GetSurahUseCase,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    private val readingFlow = observeReadingPosition()
        .distinctUntilChanged()
        .flatMapLatest { position ->
            flow { emit(position?.let { buildContinueReading(it) }) }
        }

    private val wallpaperFlow = observeWallpaperHistory()
        .distinctUntilChanged()
        .flatMapLatest { history ->
            flow { emit(buildWallpaperCard(history)) }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        readingFlow,
        wallpaperFlow,
        observeScheduleStatus(),
    ) { reading, wallpaperCard, schedule ->
        HomeUiState(
            isLoading = false,
            continueReading = reading,
            wallpaperCard = wallpaperCard,
            scheduleStatus = schedule,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )

    private suspend fun buildContinueReading(position: ReadingPosition): ContinueReadingState? {
        val surah = getSurah(position.surahId) ?: return null
        return ContinueReadingState(
            surahId = surah.id,
            surahDisplayName = surah.localizedDisplayName(),
            ayahNumber = position.ayahNumber,
            surahAyahCount = surah.ayahCount,
            progressFraction = if (surah.ayahCount > 0) {
                position.ayahNumber / surah.ayahCount.toFloat()
            } else {
                0f
            },
        )
    }

    private suspend fun buildWallpaperCard(history: List<com.ayah.quran.domain.model.WallpaperHistoryEntry>): WallpaperCardState? {
        val latest = history.firstOrNull { it.status == WallpaperApplyStatus.SUCCESS } ?: return null
        val surah = getSurah(latest.surahId)
        val template = WallpaperTemplate.byId(latest.templateId)
        // Small thumbnail rendered by the production renderer.
        val ayah = wallpaperRepository.loadAyah(latest.surahId, latest.ayahNumber)
        val thumbnail = ayah?.let {
            wallpaperRepository.renderPreview(
                ayahText = it.textUthmani,
                referenceLabel = "${surah?.localizedDisplayName() ?: it.surahId} · ${it.surahId}:${it.ayahNumber}",
                template = template,
                widthPx = THUMBNAIL_WIDTH_PX,
                heightPx = THUMBNAIL_HEIGHT_PX,
                fontScale = 1.0f,
                verticalPosition = com.ayah.quran.domain.model.TextVerticalPosition.CENTER,
            )
        }
        return WallpaperCardState(
            thumbnail = thumbnail,
            surahDisplayName = surah?.localizedDisplayName() ?: "Surah ${latest.surahId}",
            surahId = latest.surahId,
            ayahNumber = latest.ayahNumber,
            templateId = template.id,
            appliedAtMillis = latest.appliedAtMillis,
        )
    }

    private companion object {
        const val THUMBNAIL_WIDTH_PX = 360
        const val THUMBNAIL_HEIGHT_PX = 760
    }
}
