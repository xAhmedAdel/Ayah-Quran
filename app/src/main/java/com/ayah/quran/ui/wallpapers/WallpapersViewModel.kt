package com.ayah.quran.ui.wallpapers

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.quran.AyahSelector
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.model.WallpaperHistoryEntry
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettings
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import com.ayah.quran.domain.usecase.ApplyWallpaperNowUseCase
import com.ayah.quran.domain.usecase.ClearWallpaperHistoryUseCase
import com.ayah.quran.domain.usecase.DeleteWallpaperHistoryEntryUseCase
import com.ayah.quran.domain.usecase.EnableWallpaperUpdatesUseCase
import com.ayah.quran.domain.usecase.ObserveSurahListUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperHistoryUseCase
import com.ayah.quran.domain.usecase.ObserveWallpaperScheduleStatusUseCase
import com.ayah.quran.domain.usecase.PauseWallpaperUpdatesUseCase
import com.ayah.quran.domain.usecase.ReapplyWallpaperFromHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Preview canvas: common phone proportions; the same renderer is used as
// for the real wallpaper, only at reduced resolution.
private const val PREVIEW_WIDTH_PX = 720
private const val PREVIEW_HEIGHT_PX = 1520

/** One verse loaded for the preview canvas. */
data class PreviewVerse(
    val textUthmani: String,
    val referenceLabel: String,
    val surahId: Int,
    val ayahNumber: Int,
)

data class WallpapersUiState(
    val settings: WallpaperSettings = WallpaperSettings(),
    val surahs: List<Surah> = emptyList(),
    val scheduleStatus: WallpaperScheduleStatus = WallpaperScheduleStatus(isScheduled = false, intervalMinutes = null),
    val lastAppliedAtMillis: Long? = null,
    val history: List<HistoryItem> = emptyList(),
    val previewVerse: PreviewVerse? = null,
    val previewBitmap: Bitmap? = null,
    val previewBusy: Boolean = true,
    val applying: Boolean = false,
    val applyOutcome: WallpaperApplyStatus? = null,
)

/** History row with a small thumbnail rendered by the production renderer. */
data class HistoryItem(
    val entry: WallpaperHistoryEntry,
    val thumbnail: Bitmap?,
)

private data class PreviewOutput(
    val verse: PreviewVerse?,
    val bitmap: Bitmap?,
    val busy: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class WallpapersViewModel @Inject constructor(
    private val settingsRepository: WallpaperSettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val selector: AyahSelector,
    private val applyWallpaperNow: ApplyWallpaperNowUseCase,
    private val reapplyFromHistory: ReapplyWallpaperFromHistoryUseCase,
    private val deleteWallpaperHistoryEntry: DeleteWallpaperHistoryEntryUseCase,
    private val clearWallpaperHistory: ClearWallpaperHistoryUseCase,
    private val enableWallpaperUpdates: EnableWallpaperUpdatesUseCase,
    private val pauseWallpaperUpdates: PauseWallpaperUpdatesUseCase,
    observeSurahList: ObserveSurahListUseCase,
    observeWallpaperHistory: ObserveWallpaperHistoryUseCase,
    observeScheduleStatus: ObserveWallpaperScheduleStatusUseCase,
) : ViewModel() {

    private val previewVerse = MutableStateFlow<PreviewVerse?>(null)
    private val applying = MutableStateFlow(false)
    private val applyOutcome = MutableStateFlow<WallpaperApplyStatus?>(null)
    private val surahs = MutableStateFlow<List<Surah>>(emptyList())
    private val thumbnailCache = mutableMapOf<Long, Bitmap>()

    init {
        observeSurahList()
            .onEach { surahs.value = it }
            .launchIn(viewModelScope)
        // Seed the preview with the deterministic daily verse.
        viewModelScope.launch {
            val pool = wallpaperRepository.allGlobalIds()
            if (pool.isNotEmpty()) {
                setPreviewVerseByGlobalId(
                    selector.selectDaily(epochDay = LocalDate.now().toEpochDay(), poolSize = pool.size),
                )
            }
        }
    }

    private fun previewPipeline(): kotlinx.coroutines.flow.Flow<PreviewOutput> =
        combine(settingsRepository.settings, previewVerse) { settings, verse ->
            Triple(settings.templateId, settings.fontScale, settings.verticalPosition) to verse
        }
            .debounce(PREVIEW_DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { (typography, verse) ->
                if (verse == null) {
                    flowOf(PreviewOutput(null, null, busy = true))
                } else {
                    flow {
                        val template = WallpaperTemplate.byId(typography.first)
                        val bitmap = wallpaperRepository.renderPreview(
                            ayahText = verse.textUthmani,
                            referenceLabel = verse.referenceLabel,
                            template = template,
                            widthPx = PREVIEW_WIDTH_PX,
                            heightPx = PREVIEW_HEIGHT_PX,
                            fontScale = typography.second,
                            verticalPosition = typography.third,
                        )
                        emit(PreviewOutput(verse, bitmap, busy = false))
                    }
                }
            }

    /**
     * History rows with small thumbnails rendered by the production
     * renderer. Rendering suspends (never blocks a thread) and is cached
     * per entry id; the list itself is bounded.
     */
    private val historyItems: kotlinx.coroutines.flow.Flow<List<HistoryItem>> =
        observeWallpaperHistory()
            .map { entries -> entries.take(HISTORY_LIMIT) }
            .map { entries -> historyWithThumbnails(entries) }
            .distinctUntilChanged()

    private suspend fun historyWithThumbnails(
        entries: List<WallpaperHistoryEntry>,
    ): List<HistoryItem> = entries.map { entry ->
        val cached = thumbnailCache[entry.id]
        val thumbnail = if (cached != null) {
            cached
        } else {
            val ayah = wallpaperRepository.loadAyah(entry.surahId, entry.ayahNumber)
                ?: return@map HistoryItem(entry, null)
            wallpaperRepository.renderPreview(
                ayahText = ayah.textUthmani,
                referenceLabel = "",
                template = WallpaperTemplate.byId(entry.templateId),
                widthPx = THUMBNAIL_WIDTH_PX,
                heightPx = THUMBNAIL_HEIGHT_PX,
                fontScale = 1.0f,
                verticalPosition = TextVerticalPosition.CENTER,
            ).also { thumbnailCache[entry.id] = it }
        }
        HistoryItem(entry, thumbnail)
    }

    val uiState: StateFlow<WallpapersUiState> = combine(
        settingsRepository.settings,
        previewPipeline(),
        surahs,
        historyItems,
        combine(observeScheduleStatus(), applying, applyOutcome) { schedule, busy, outcome ->
            Triple(schedule, busy, outcome)
        },
    ) { settings, preview, surahList, history, scheduleAndApply ->
        WallpapersUiState(
            settings = settings,
            surahs = surahList,
            scheduleStatus = scheduleAndApply.first,
            lastAppliedAtMillis = history.firstOrNull()?.entry
                ?.takeIf { it.status == WallpaperApplyStatus.SUCCESS }?.appliedAtMillis,
            history = history,
            previewVerse = preview.verse,
            previewBitmap = preview.bitmap,
            previewBusy = preview.busy,
            applying = scheduleAndApply.second,
            applyOutcome = scheduleAndApply.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WallpapersUiState(previewBusy = true),
    )

    // ---- preview verse selection ----

    /** Random verse from the whole verified mushaf, avoiding recent ones. */
    fun shufflePreviewVerse() {
        viewModelScope.launch {
            val pool = wallpaperRepository.allGlobalIds()
            if (pool.isEmpty()) return@launch
            setPreviewVerseByGlobalId(
                selector.selectRandom(pool, wallpaperRepository.recentSelections()),
            )
        }
    }

    fun pickPreviewVerse(surahId: Int, ayahNumber: Int) {
        viewModelScope.launch {
            val ayah = wallpaperRepository.loadAyah(surahId, ayahNumber) ?: return@launch
            val name = wallpaperRepository.surahName(surahId) ?: "Surah $surahId"
            previewVerse.value = PreviewVerse(
                textUthmani = ayah.textUthmani,
                referenceLabel = "$name · $surahId:$ayahNumber",
                surahId = surahId,
                ayahNumber = ayahNumber,
            )
        }
    }

    private suspend fun setPreviewVerseByGlobalId(globalId: Long) {
        val ayah = wallpaperRepository.loadAyahByGlobalId(globalId) ?: return
        val name = wallpaperRepository.surahName(ayah.surahId) ?: "Surah ${ayah.surahId}"
        previewVerse.value = PreviewVerse(
            textUthmani = ayah.textUthmani,
            referenceLabel = "$name · ${ayah.surahId}:${ayah.ayahNumber}",
            surahId = ayah.surahId,
            ayahNumber = ayah.ayahNumber,
        )
    }

    // ---- settings mutations ----

    fun selectTemplate(templateId: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(templateId = templateId) } }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(fontScale = scale.coerceIn(0.6f, 1.6f)) }
        }
    }

    fun setVerticalPosition(position: TextVerticalPosition) {
        viewModelScope.launch { settingsRepository.update { it.copy(verticalPosition = position) } }
    }

    fun setDestinations(destinations: WallpaperDestination) {
        viewModelScope.launch { settingsRepository.update { it.copy(destinations = destinations) } }
    }

    fun setSelectionMode(mode: SelectionMode) {
        viewModelScope.launch { settingsRepository.update { it.copy(selectionMode = mode) } }
    }

    fun setFixedVerse(surahId: Int, ayahNumber: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(fixedSurahId = surahId, fixedAyahNumber = ayahNumber) }
        }
    }

    // ---- apply ----

    fun applyPreviewVerse() {
        val verse = previewVerse.value ?: return
        viewModelScope.launch {
            applying.value = true
            applyOutcome.value = null
            val ayah = wallpaperRepository.loadAyah(verse.surahId, verse.ayahNumber)
            applyOutcome.value = if (ayah == null) {
                WallpaperApplyStatus.FAILED
            } else {
                applyWallpaperNow(ayah)
            }
            applying.value = false
        }
    }

    fun reapply(item: HistoryItem) {
        viewModelScope.launch {
            applying.value = true
            applyOutcome.value = reapplyFromHistory(item.entry)
            applying.value = false
        }
    }

    /** Deletes one history entry (metadata only; system wallpaper untouched). */
    fun deleteHistoryEntry(item: HistoryItem) {
        thumbnailCache.remove(item.entry.id)
        viewModelScope.launch { deleteWallpaperHistoryEntry(item.entry.id) }
    }

    /** Clears all history (metadata only; system wallpaper untouched). */
    fun clearHistory() {
        thumbnailCache.clear()
        viewModelScope.launch { clearWallpaperHistory() }
    }

    // ---- automation ----

    fun setAutoUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(autoUpdateEnabled = enabled) }
            syncScheduleWithSettings()
        }
    }

    fun setInterval(intervalMinutes: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(intervalMinutes = intervalMinutes) }
            syncScheduleWithSettings()
        }
    }

    private suspend fun syncScheduleWithSettings() {
        val settings = settingsRepository.settings.first()
        if (settings.autoUpdateEnabled) {
            enableWallpaperUpdates(settings.intervalMinutes)
        } else {
            pauseWallpaperUpdates()
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 150L
        const val HISTORY_LIMIT = 20
        const val THUMBNAIL_WIDTH_PX = 120
        const val THUMBNAIL_HEIGHT_PX = 253
    }
}
