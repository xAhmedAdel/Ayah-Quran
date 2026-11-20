package com.ayah.quran.ui.companion.tasbih

import com.ayah.quran.domain.model.companion.TasbihCounter
import com.ayah.quran.domain.repository.companion.TasbihPreferencesRepository
import com.ayah.quran.domain.repository.companion.TasbihRepository
import com.ayah.quran.ui.companion.adhkar.FakeDateProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasbihViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun incrementReachesGoalWithoutSilentResetAndAllowsContinuingBeyondGoal() = runTest {
        var nowMs = 1_000L
        val repo = FakeTasbihRepository(
            listOf(sampleCounter(id = 1L, target = 2)),
        )
        val prefs = FakeTasbihPreferencesRepository()
        val vm = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val events = mutableListOf<TasbihFeedbackEvent>()
        val stateJob = backgroundScope.launch { vm.uiState.collect {} }
        val eventJob = backgroundScope.launch { vm.feedbackEvents.collect { events.add(it) } }
        advanceUntilIdle()

        vm.increment()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeCounter?.currentCount)
        assertFalse(vm.uiState.value.goalReached)
        assertEquals(1, vm.uiState.value.remainingToGoal)

        nowMs += 200L
        vm.increment()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(1, vm.uiState.value.activeCounter?.completedRounds)
        assertTrue(vm.uiState.value.goalReached)
        assertEquals(0, vm.uiState.value.remainingToGoal)
        assertEquals(1f, vm.uiState.value.progressFraction, 0.001f)

        nowMs += 200L
        vm.increment()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(1, vm.uiState.value.activeCounter?.completedRounds)
        assertTrue(vm.uiState.value.goalReached)
        assertEquals(3, vm.uiState.value.todayTotal)
        assertEquals(
            listOf(TasbihFeedbackEvent.TAP, TasbihFeedbackEvent.GOAL_REACHED, TasbihFeedbackEvent.TAP),
            events,
        )

        stateJob.cancel()
        eventJob.cancel()
    }

    @Test
    fun undoReversesIncrementsAfterGoalCompletionAndNeverGoesNegative() = runTest {
        var nowMs = 1_000L
        val repo = FakeTasbihRepository(
            listOf(sampleCounter(id = 1L, target = 2)),
        )
        val prefs = FakeTasbihPreferencesRepository()
        val vm = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.undo()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.activeCounter?.currentCount)

        vm.increment()
        advanceUntilIdle()
        nowMs += 200L
        vm.increment()
        advanceUntilIdle()
        nowMs += 200L
        vm.increment()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(1, vm.uiState.value.activeCounter?.completedRounds)

        vm.undo()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(1, vm.uiState.value.activeCounter?.completedRounds)
        assertTrue(vm.uiState.value.goalReached)

        vm.undo()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(0, vm.uiState.value.activeCounter?.completedRounds)
        assertFalse(vm.uiState.value.goalReached)

        vm.undo()
        advanceUntilIdle()
        vm.undo()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(0, vm.uiState.value.todayTotal)
        job.cancel()
    }

    @Test
    fun rapidTapsInsideGuardWindowDoNotDoubleIncrement() = runTest {
        var nowMs = 1_000L
        val repo = FakeTasbihRepository(listOf(sampleCounter(id = 1L, target = 33)))
        val prefs = FakeTasbihPreferencesRepository()
        val vm = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.increment()
        nowMs += 20L
        vm.increment()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeCounter?.currentCount)

        nowMs += 100L
        vm.increment()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.activeCounter?.currentCount)
        job.cancel()
    }

    @Test
    fun noGoalModeAndCustomGoalPreserveCurrentCount() = runTest {
        var nowMs = 1_000L
        val repo = FakeTasbihRepository(listOf(sampleCounter(id = 1L, target = 0)))
        val prefs = FakeTasbihPreferencesRepository()
        val vm = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.increment()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.activeCounter?.currentCount)
        assertFalse(vm.uiState.value.goalReached)
        assertEquals(0f, vm.uiState.value.progressFraction, 0.001f)

        assertNull(vm.setActiveGoal(50))
        advanceUntilIdle()
        assertEquals(50, vm.uiState.value.activeCounter?.targetCount)
        assertEquals(1, vm.uiState.value.activeCounter?.currentCount)
        assertEquals(49, vm.uiState.value.remainingToGoal)

        assertEquals(TasbihValidationError.INVALID_GOAL, vm.setActiveGoal(-1))
        assertEquals(TasbihValidationError.INVALID_GOAL, vm.setActiveGoal(20_000))
        job.cancel()
    }

    @Test
    fun resetAndCountingAreIsolatedPerCounterAndRestoreAcrossViewModelRecreation() = runTest {
        var nowMs = 1_000L
        val repo = FakeTasbihRepository(
            listOf(
                sampleCounter(id = 1L, key = "subhanallah", textAr = "سُبْحَانَ اللَّهِ", target = 33),
                sampleCounter(id = 2L, key = "alhamdulillah", textAr = "الْحَمْدُ لِلَّهِ", target = 33),
            ),
        )
        val prefs = FakeTasbihPreferencesRepository()
        val vm1 = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val job1 = backgroundScope.launch { vm1.uiState.collect {} }
        advanceUntilIdle()

        vm1.increment()
        advanceUntilIdle()
        vm1.selectCounter(2L)
        advanceUntilIdle()
        nowMs += 200L
        vm1.increment()
        advanceUntilIdle()
        nowMs += 200L
        vm1.increment()
        advanceUntilIdle()
        vm1.setHapticsEnabled(false)
        advanceUntilIdle()
        job1.cancel()

        val vm2 = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { nowMs }
        val job2 = backgroundScope.launch { vm2.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(2L, vm2.uiState.value.activeCounter?.id)
        assertEquals(2, vm2.uiState.value.activeCounter?.currentCount)
        assertFalse(vm2.uiState.value.hapticsEnabled)

        vm2.resetActive()
        advanceUntilIdle()
        assertEquals(0, vm2.uiState.value.activeCounter?.currentCount)
        assertEquals(1, vm2.uiState.value.counters.first { it.id == 1L }.currentCount)
        job2.cancel()
    }

    @Test
    fun customDhikrCrudValidatesInvalidAndDuplicateInputAndPreservesArabicText() = runTest {
        val repo = FakeTasbihRepository(
            listOf(sampleCounter(id = 1L, key = "subhanallah", textAr = "سُبْحَانَ اللَّهِ", target = 33)),
        )
        val prefs = FakeTasbihPreferencesRepository()
        val vm = TasbihViewModel(repo, prefs, FakeDateProvider("2026-11-20", 12)) { 1_000L }
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(TasbihValidationError.EMPTY_TEXT, vm.createCustomDhikr("   ", "", 33))
        assertEquals(TasbihValidationError.TEXT_TOO_LONG, vm.createCustomDhikr("أ".repeat(161), "", 33))
        assertEquals(TasbihValidationError.INVALID_GOAL, vm.createCustomDhikr("لا حول ولا قوة إلا بالله", "", 10_001))
        assertEquals(TasbihValidationError.DUPLICATE_DHIKR, vm.createCustomDhikr("سبحان الله", "", 33))

        val exactArabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"
        assertNull(vm.createCustomDhikr(exactArabic, "Hawqalah", 100))
        advanceUntilIdle()

        val created = vm.uiState.value.activeCounter!!
        assertTrue(created.isCustom)
        assertEquals(exactArabic, created.dhikrTextAr)
        assertEquals("Hawqalah", created.titleEn)
        assertEquals(100, created.targetCount)

        assertNull(vm.updateCustomDhikr(created.id, exactArabic, "Updated Label", 50))
        advanceUntilIdle()
        val updated = vm.uiState.value.counters.first { it.id == created.id }
        assertEquals("Updated Label", updated.titleEn)
        assertEquals(50, updated.targetCount)

        vm.deleteCustomDhikr(created.id)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.counters.size)
        assertEquals(1L, vm.uiState.value.activeCounter?.id)
        job.cancel()
    }
}

private fun sampleCounter(
    id: Long,
    key: String? = "preset_$id",
    textAr: String = "سُبْحَانَ اللَّهِ",
    titleEn: String = "SubhanAllah",
    target: Int = 33,
    isCustom: Boolean = false,
) = TasbihCounter(
    id = id,
    stableKey = key,
    titleAr = textAr,
    titleEn = titleEn,
    dhikrTextAr = textAr,
    targetCount = target,
    currentCount = 0,
    completedRounds = 0,
    lifetimeCount = 0L,
    isCustom = isCustom,
    sortOrder = id.toInt(),
    updatedAtMillis = 1L,
)

private class FakeTasbihPreferencesRepository : TasbihPreferencesRepository {
    override val selectedCounterId = MutableStateFlow<Long?>(null)
    override val hapticsEnabled = MutableStateFlow(true)
    override suspend fun setSelectedCounterId(counterId: Long?) {
        selectedCounterId.value = counterId
    }
    override suspend fun setHapticsEnabled(enabled: Boolean) {
        hapticsEnabled.value = enabled
    }
}

private class FakeTasbihRepository(
    initial: List<TasbihCounter>,
) : TasbihRepository {
    private val countersFlow = MutableStateFlow(initial)
    private val dailyTotals = MutableStateFlow<Map<Pair<String, Long>, Int>>(emptyMap())
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override fun observeCounters(): Flow<List<TasbihCounter>> = countersFlow

    override fun observeDayTotal(dayKey: String): Flow<Int> =
        dailyTotals.map { map -> map.filterKeys { it.first == dayKey }.values.sum() }

    override suspend fun increment(counterId: Long, dayKey: String): Boolean {
        val list = countersFlow.value
        val cur = list.firstOrNull { it.id == counterId } ?: return false
        val next = cur.currentCount + 1
        val reached = cur.targetCount > 0 && next >= cur.targetCount
        countersFlow.value = list.map {
            if (it.id == counterId) {
                it.copy(
                    currentCount = if (reached) 0 else next,
                    completedRounds = if (reached) it.completedRounds + 1 else it.completedRounds,
                    lifetimeCount = it.lifetimeCount + 1,
                )
            } else {
                it
            }
        }
        val key = dayKey to counterId
        dailyTotals.value = dailyTotals.value + (key to ((dailyTotals.value[key] ?: 0) + 1))
        return reached
    }

    override suspend fun incrementSession(counterId: Long, dayKey: String): Boolean {
        val list = countersFlow.value
        val cur = list.firstOrNull { it.id == counterId } ?: return false
        val next = cur.currentCount + 1
        val justReached = cur.targetCount > 0 && next == cur.targetCount
        countersFlow.value = list.map {
            if (it.id == counterId) {
                it.copy(
                    currentCount = next,
                    completedRounds = if (justReached) it.completedRounds + 1 else it.completedRounds,
                    lifetimeCount = it.lifetimeCount + 1,
                )
            } else {
                it
            }
        }
        val key = dayKey to counterId
        dailyTotals.value = dailyTotals.value + (key to ((dailyTotals.value[key] ?: 0) + 1))
        return justReached
    }

    override suspend fun undo(counterId: Long, dayKey: String) {
        val list = countersFlow.value
        val cur = list.firstOrNull { it.id == counterId } ?: return
        if (cur.currentCount <= 0) return
        val wasExactTarget = cur.targetCount > 0 && cur.currentCount == cur.targetCount
        countersFlow.value = list.map {
            if (it.id == counterId) {
                it.copy(
                    currentCount = it.currentCount - 1,
                    completedRounds = if (wasExactTarget && it.completedRounds > 0) it.completedRounds - 1 else it.completedRounds,
                    lifetimeCount = (it.lifetimeCount - 1).coerceAtLeast(0L),
                )
            } else {
                it
            }
        }
        val key = dayKey to counterId
        val existing = dailyTotals.value[key] ?: 0
        if (existing > 0) {
            dailyTotals.value = dailyTotals.value + (key to (existing - 1))
        }
    }

    override suspend fun reset(counterId: Long) {
        countersFlow.value = countersFlow.value.map {
            if (it.id == counterId) it.copy(currentCount = 0) else it
        }
    }

    override suspend fun createCustomCounter(
        titleAr: String,
        titleEn: String,
        dhikrTextAr: String,
        targetCount: Int,
    ): Long {
        val id = nextId++
        val created = TasbihCounter(
            id = id,
            stableKey = null,
            titleAr = titleAr,
            titleEn = titleEn,
            dhikrTextAr = dhikrTextAr,
            targetCount = targetCount,
            currentCount = 0,
            completedRounds = 0,
            lifetimeCount = 0L,
            isCustom = true,
            sortOrder = countersFlow.value.size + 1,
            updatedAtMillis = 2L,
        )
        countersFlow.value = countersFlow.value + created
        return id
    }

    override suspend fun updateCounter(counter: TasbihCounter) {
        countersFlow.value = countersFlow.value.map {
            if (it.id == counter.id) counter else it
        }
    }

    override suspend fun deleteCustomCounter(counterId: Long) {
        countersFlow.value = countersFlow.value.filterNot { it.id == counterId && it.isCustom }
    }
}
