package com.ayah.quran.ui.companion.adhkar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.time.CompanionDateProvider
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.repository.companion.AdhkarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class SessionDhikrRow(
    val item: DhikrItem,
    val currentCount: Int,
    val targetCount: Int,
    val completed: Boolean,
    val isFavorite: Boolean,
)

data class AdhkarSessionUiState(
    val loading: Boolean = true,
    val category: DhikrCategory? = null,
    val dayKey: String = "",
    val rows: List<SessionDhikrRow> = emptyList(),
    val currentIndex: Int = 0,
    val completedCount: Int = 0,
    val allCompleted: Boolean = false,
    val canUndo: Boolean = false,
    val listViewMode: Boolean = false,
    val fontScale: Float = 1.0f,
)

private data class LastSessionAction(
    val itemId: String,
    val pageIndex: Int,
    val autoAdvanced: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdhkarSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val adhkarRepository: AdhkarRepository,
    private val dateProvider: CompanionDateProvider,
) : ViewModel() {

    val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    private val requestedItemId: String? = savedStateHandle.get<String>("itemId")?.takeIf { it.isNotBlank() }

    private val dayKeyFlow = MutableStateFlow(dateProvider.todayKey())
    private val currentIndexFlow = MutableStateFlow(0)
    private val listViewModeFlow = MutableStateFlow(false)
    private val fontScaleFlow = MutableStateFlow(1.0f)
    private val lastActionFlow = MutableStateFlow<LastSessionAction?>(null)

    private val tapMutex = Mutex()
    private var initialPageResolved = false
    private var autoAdvanceJob: Job? = null
    private var lastTapElapsedMs: Long = 0L

    private val progressFlow = dayKeyFlow.flatMapLatest { key ->
        adhkarRepository.observeCategoryProgress(key, categoryId)
    }

    val uiState: StateFlow<AdhkarSessionUiState> = combine(
        combine(
            adhkarRepository.observeCategories(),
            adhkarRepository.observeItemsByCategory(categoryId),
            progressFlow,
            adhkarRepository.observeFavoriteIds(),
        ) { categories, items, progressList, favIds ->
            val category = categories.firstOrNull { it.id == categoryId }
            val progressMap = progressList.associateBy { it.itemId }
            val rows = items.map { item ->
                val target = item.repeatCount.coerceAtLeast(1)
                val prog = progressMap[item.id]
                val count = (prog?.currentCount ?: 0).coerceIn(0, target)
                SessionDhikrRow(
                    item = item,
                    currentCount = count,
                    targetCount = target,
                    completed = count >= target,
                    isFavorite = item.id in favIds,
                )
            }
            if (!initialPageResolved && rows.isNotEmpty()) {
                initialPageResolved = true
                val targetIdx = resolveInitialIndex(rows, requestedItemId)
                currentIndexFlow.value = targetIdx
            }
            category to rows
        },
        combine(
            dayKeyFlow,
            currentIndexFlow,
            listViewModeFlow,
            fontScaleFlow,
            lastActionFlow,
        ) { dayKey, index, listMode, scale, lastAction ->
            SessionControlsSlice(dayKey, index, listMode, scale, lastAction)
        },
    ) { (category, rows), controls ->
        val safeIndex = if (rows.isEmpty()) 0 else controls.currentIndex.coerceIn(0, rows.lastIndex)
        val completedCount = rows.count { it.completed }
        val activeRow = rows.getOrNull(safeIndex)
        val canUndo = (activeRow != null && activeRow.currentCount > 0) || controls.lastAction != null
        AdhkarSessionUiState(
            loading = rows.isEmpty() && category == null,
            category = category,
            dayKey = controls.dayKey,
            rows = rows,
            currentIndex = safeIndex,
            completedCount = completedCount,
            allCompleted = rows.isNotEmpty() && completedCount == rows.size,
            canUndo = canUndo,
            listViewMode = controls.listViewMode,
            fontScale = controls.fontScale,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdhkarSessionUiState(dayKey = dateProvider.todayKey()),
    )

    fun refreshDayKey() {
        val current = dateProvider.todayKey()
        if (dayKeyFlow.value != current) {
            autoAdvanceJob?.cancel()
            lastActionFlow.value = null
            initialPageResolved = false
            dayKeyFlow.value = current
        }
    }

    fun selectPage(index: Int) {
        val rows = uiState.value.rows
        if (rows.isEmpty()) return
        val clamped = index.coerceIn(0, rows.lastIndex)
        if (clamped != currentIndexFlow.value) {
            autoAdvanceJob?.cancel()
            currentIndexFlow.value = clamped
        }
    }

    fun stepPrevious() {
        selectPage(currentIndexFlow.value - 1)
    }

    fun stepNext() {
        selectPage(currentIndexFlow.value + 1)
    }

    fun toggleListViewMode() {
        listViewModeFlow.value = !listViewModeFlow.value
    }

    fun adjustFontScale(delta: Float) {
        fontScaleFlow.value = (fontScaleFlow.value + delta).coerceIn(0.85f, 1.4f)
    }

    fun incrementCurrent(autoAdvanceDelayMs: Long = AUTO_ADVANCE_DELAY_MS) {
        viewModelScope.launch {
            tapMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastTapElapsedMs < RAPID_TAP_GUARD_MS) return@withLock
                val state = uiState.value
                val index = state.currentIndex
                val row = state.rows.getOrNull(index) ?: return@withLock
                if (row.completed) return@withLock
                lastTapElapsedMs = now

                val activeDay = dateProvider.todayKey()
                if (dayKeyFlow.value != activeDay) {
                    dayKeyFlow.value = activeDay
                }
                adhkarRepository.incrementProgress(activeDay, row.item.id)

                val willComplete = row.currentCount + 1 >= row.targetCount
                val hasNext = index < state.rows.lastIndex
                lastActionFlow.value = LastSessionAction(
                    itemId = row.item.id,
                    pageIndex = index,
                    autoAdvanced = willComplete && hasNext,
                )

                if (willComplete && hasNext) {
                    autoAdvanceJob?.cancel()
                    autoAdvanceJob = viewModelScope.launch {
                        if (autoAdvanceDelayMs > 0L) {
                            delay(autoAdvanceDelayMs)
                        }
                        if (currentIndexFlow.value == index) {
                            currentIndexFlow.value = index + 1
                        }
                    }
                }
            }
        }
    }

    fun undo() {
        viewModelScope.launch {
            tapMutex.withLock {
                autoAdvanceJob?.cancel()
                lastTapElapsedMs = 0L
                val state = uiState.value
                val activeDay = dateProvider.todayKey()
                val activeRow = state.rows.getOrNull(state.currentIndex)
                val last = lastActionFlow.value

                when {
                    activeRow != null && activeRow.currentCount > 0 -> {
                        adhkarRepository.decrementProgress(activeDay, activeRow.item.id)
                        if (last?.itemId == activeRow.item.id && activeRow.currentCount <= 1) {
                            lastActionFlow.value = null
                        }
                    }
                    last != null -> {
                        currentIndexFlow.value = last.pageIndex.coerceIn(0, (state.rows.size - 1).coerceAtLeast(0))
                        adhkarRepository.decrementProgress(activeDay, last.itemId)
                        lastActionFlow.value = null
                    }
                }
            }
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            tapMutex.withLock {
                autoAdvanceJob?.cancel()
                lastTapElapsedMs = 0L
                lastActionFlow.value = null
                val activeDay = dateProvider.todayKey()
                adhkarRepository.resetCategoryProgress(activeDay, categoryId)
                currentIndexFlow.value = 0
            }
        }
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            adhkarRepository.toggleFavorite(itemId)
        }
    }

    companion object {
        const val AUTO_ADVANCE_DELAY_MS = 320L
        private const val RAPID_TAP_GUARD_MS = 70L

        internal fun resolveInitialIndex(
            rows: List<SessionDhikrRow>,
            requestedItemId: String?,
        ): Int {
            if (rows.isEmpty()) return 0
            if (requestedItemId != null) {
                val match = rows.indexOfFirst { it.item.id == requestedItemId }
                if (match >= 0) return match
            }
            val firstUnfinished = rows.indexOfFirst { !it.completed }
            return if (firstUnfinished >= 0) firstUnfinished else 0
        }
    }
}

private data class SessionControlsSlice(
    val dayKey: String,
    val currentIndex: Int,
    val listViewMode: Boolean,
    val fontScale: Float,
    val lastAction: LastSessionAction?,
)
