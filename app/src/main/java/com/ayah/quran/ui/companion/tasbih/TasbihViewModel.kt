package com.ayah.quran.ui.companion.tasbih

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.core.time.CompanionDateProvider
import com.ayah.quran.domain.model.companion.TasbihCounter
import com.ayah.quran.domain.repository.companion.TasbihPreferencesRepository
import com.ayah.quran.domain.repository.companion.TasbihRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

enum class TasbihFeedbackEvent {
    TAP,
    GOAL_REACHED,
}

enum class TasbihValidationError {
    EMPTY_TEXT,
    TEXT_TOO_LONG,
    DUPLICATE_DHIKR,
    INVALID_GOAL,
}

data class TasbihUiState(
    val loading: Boolean = true,
    val counters: List<TasbihCounter> = emptyList(),
    val activeCounter: TasbihCounter? = null,
    val todayTotal: Int = 0,
    val hapticsEnabled: Boolean = true,
    val goalReached: Boolean = false,
    val remainingToGoal: Int = 0,
    val progressFraction: Float = 0f,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasbihViewModel internal constructor(
    private val tasbihRepository: TasbihRepository,
    private val tasbihPreferences: TasbihPreferencesRepository,
    private val dateProvider: CompanionDateProvider,
    private val nowMsProvider: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        tasbihRepository: TasbihRepository,
        tasbihPreferences: TasbihPreferencesRepository,
        dateProvider: CompanionDateProvider,
    ) : this(
        tasbihRepository = tasbihRepository,
        tasbihPreferences = tasbihPreferences,
        dateProvider = dateProvider,
        nowMsProvider = System::currentTimeMillis,
    )

    private val dayKeyFlow = MutableStateFlow(dateProvider.todayKey())
    private val tapMutex = Mutex()
    private var lastTapMs: Long = 0L

    private val _feedbackEvents = MutableSharedFlow<TasbihFeedbackEvent>(extraBufferCapacity = 4)
    val feedbackEvents: SharedFlow<TasbihFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val todayTotalFlow = dayKeyFlow.flatMapLatest { dayKey ->
        tasbihRepository.observeDayTotal(dayKey)
    }

    val uiState: StateFlow<TasbihUiState> = combine(
        tasbihRepository.observeCounters(),
        tasbihPreferences.selectedCounterId,
        todayTotalFlow,
        tasbihPreferences.hapticsEnabled,
    ) { counters, selectedId, todayTotal, hapticsEnabled ->
        val active = counters.firstOrNull { it.id == selectedId } ?: counters.firstOrNull()
        val target = active?.targetCount ?: 0
        val current = active?.currentCount ?: 0
        val goalReached = target > 0 && current >= target
        val remaining = if (target > 0) (target - current).coerceAtLeast(0) else 0
        val fraction = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
        TasbihUiState(
            loading = counters.isEmpty(),
            counters = counters,
            activeCounter = active,
            todayTotal = todayTotal,
            hapticsEnabled = hapticsEnabled,
            goalReached = goalReached,
            remainingToGoal = remaining,
            progressFraction = fraction,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasbihUiState(),
    )

    fun refreshDayKey() {
        val current = dateProvider.todayKey()
        if (dayKeyFlow.value != current) {
            dayKeyFlow.value = current
        }
    }

    fun increment() {
        viewModelScope.launch {
            tapMutex.withLock {
                val now = nowMsProvider()
                if (now - lastTapMs < RAPID_TAP_GUARD_MS) return@withLock
                val state = uiState.value
                val active = state.activeCounter ?: return@withLock
                lastTapMs = now

                val activeDay = dateProvider.todayKey()
                if (dayKeyFlow.value != activeDay) {
                    dayKeyFlow.value = activeDay
                }
                val justReached = tasbihRepository.incrementSession(active.id, activeDay)
                if (state.hapticsEnabled) {
                    _feedbackEvents.tryEmit(
                        if (justReached) TasbihFeedbackEvent.GOAL_REACHED else TasbihFeedbackEvent.TAP,
                    )
                }
            }
        }
    }

    fun undo() {
        viewModelScope.launch {
            tapMutex.withLock {
                lastTapMs = 0L
                val active = uiState.value.activeCounter ?: return@withLock
                if (active.currentCount <= 0) return@withLock
                tasbihRepository.undo(active.id, dateProvider.todayKey())
            }
        }
    }

    fun resetActive() {
        viewModelScope.launch {
            tapMutex.withLock {
                lastTapMs = 0L
                val active = uiState.value.activeCounter ?: return@withLock
                tasbihRepository.reset(active.id)
            }
        }
    }

    fun selectCounter(counterId: Long) {
        viewModelScope.launch {
            tapMutex.withLock {
                lastTapMs = 0L
                tasbihPreferences.setSelectedCounterId(counterId)
            }
        }
    }

    fun selectNextCounter() {
        val counters = uiState.value.counters
        if (counters.size <= 1) return
        val currentId = uiState.value.activeCounter?.id
        val idx = counters.indexOfFirst { it.id == currentId }
        val next = counters[(idx + 1) % counters.size]
        selectCounter(next.id)
    }

    fun setActiveGoal(targetCount: Int): TasbihValidationError? {
        if (targetCount !in 0..MAX_GOAL) return TasbihValidationError.INVALID_GOAL
        val active = uiState.value.activeCounter ?: return null
        viewModelScope.launch {
            tasbihRepository.updateCounter(active.copy(targetCount = targetCount))
        }
        return null
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            tasbihPreferences.setHapticsEnabled(enabled)
        }
    }

    fun createCustomDhikr(
        arabicText: String,
        englishLabel: String,
        targetCount: Int,
    ): TasbihValidationError? {
        val error = validateCustomInput(
            arabicText = arabicText,
            englishLabel = englishLabel,
            targetCount = targetCount,
            existingCounters = uiState.value.counters,
            editingId = null,
        )
        if (error != null) return error

        val cleanAr = arabicText.trim()
        val cleanEn = englishLabel.trim()
        viewModelScope.launch {
            val newId = tasbihRepository.createCustomCounter(
                titleAr = cleanAr,
                titleEn = cleanEn,
                dhikrTextAr = cleanAr,
                targetCount = targetCount,
            )
            tasbihPreferences.setSelectedCounterId(newId)
        }
        return null
    }

    fun updateCustomDhikr(
        counterId: Long,
        arabicText: String,
        englishLabel: String,
        targetCount: Int,
    ): TasbihValidationError? {
        val existing = uiState.value.counters.firstOrNull { it.id == counterId } ?: return null
        val error = validateCustomInput(
            arabicText = arabicText,
            englishLabel = englishLabel,
            targetCount = targetCount,
            existingCounters = uiState.value.counters,
            editingId = counterId,
        )
        if (error != null) return error

        val cleanAr = arabicText.trim()
        val cleanEn = englishLabel.trim()
        viewModelScope.launch {
            tasbihRepository.updateCounter(
                existing.copy(
                    titleAr = cleanAr,
                    titleEn = cleanEn,
                    dhikrTextAr = cleanAr,
                    targetCount = targetCount,
                ),
            )
        }
        return null
    }

    fun deleteCustomDhikr(counterId: Long) {
        val counters = uiState.value.counters
        val activeId = uiState.value.activeCounter?.id
        viewModelScope.launch {
            tasbihRepository.deleteCustomCounter(counterId)
            if (activeId == counterId) {
                val fallback = counters.firstOrNull { it.id != counterId }?.id
                tasbihPreferences.setSelectedCounterId(fallback)
            }
        }
    }

    companion object {
        const val RAPID_TAP_GUARD_MS = 65L
        const val MAX_DHIKR_LENGTH = 160
        const val MAX_LABEL_LENGTH = 120
        const val MAX_GOAL = 10_000
        val COMMON_GOALS = listOf(0, 33, 100, 1000)

        internal fun validateCustomInput(
            arabicText: String,
            englishLabel: String,
            targetCount: Int,
            existingCounters: List<TasbihCounter>,
            editingId: Long?,
        ): TasbihValidationError? {
            val cleanAr = arabicText.trim()
            val cleanEn = englishLabel.trim()
            if (cleanAr.isEmpty()) return TasbihValidationError.EMPTY_TEXT
            if (cleanAr.length > MAX_DHIKR_LENGTH || cleanEn.length > MAX_LABEL_LENGTH) {
                return TasbihValidationError.TEXT_TOO_LONG
            }
            if (targetCount !in 0..MAX_GOAL) return TasbihValidationError.INVALID_GOAL

            val normalizedCandidate = ArabicTextNormalizer.normalize(cleanAr)
            val isDuplicate = existingCounters.any { counter ->
                if (counter.id == editingId) return@any false
                val normalizedExisting = ArabicTextNormalizer.normalize(counter.dhikrTextAr)
                if (normalizedCandidate.isNotEmpty() && normalizedExisting.isNotEmpty()) {
                    normalizedCandidate == normalizedExisting
                } else {
                    cleanAr.equals(counter.dhikrTextAr.trim(), ignoreCase = true)
                }
            }
            if (isDuplicate) return TasbihValidationError.DUPLICATE_DHIKR
            return null
        }
    }
}
