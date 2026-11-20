package com.ayah.quran.ui.companion.adhkar

import androidx.lifecycle.SavedStateHandle
import com.ayah.quran.core.time.CompanionDateProvider
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrDailyProgress
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.DhikrItemType
import com.ayah.quran.domain.model.companion.DhikrSectionType
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.domain.repository.companion.AdhkarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdhkarSessionViewModelTest {

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
    fun openingSessionDoesNotMarkItemsCompletedAndCoercesZeroRepeatCountToOne() = runTest {
        val repo = FakeAdhkarRepository(
            items = listOf(
                sampleDhikr("d1", repeatCount = 0),
                sampleDhikr("d2", repeatCount = 3),
            ),
        )
        val vm = AdhkarSessionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to "morning")),
            adhkarRepository = repo,
            dateProvider = FakeDateProvider("2026-11-20", 7),
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.rows.size)
        assertEquals(1, state.rows[0].targetCount)
        assertEquals(0, state.rows[0].currentCount)
        assertFalse(state.rows[0].completed)
        assertEquals(0, state.completedCount)
        assertFalse(state.allCompleted)
        job.cancel()
    }

    @Test
    fun incrementAutoAdvancesOnTargetCompletionAndUndoStepsBackAndDecrements() = runTest {
        val repo = FakeAdhkarRepository(
            items = listOf(
                sampleDhikr("d1", repeatCount = 1),
                sampleDhikr("d2", repeatCount = 2),
            ),
        )
        val vm = AdhkarSessionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to "morning")),
            adhkarRepository = repo,
            dateProvider = FakeDateProvider("2026-11-20", 7),
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.incrementCurrent(autoAdvanceDelayMs = 100L)
        advanceTimeBy(150L)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.currentIndex)
        assertTrue(vm.uiState.value.rows[0].completed)
        assertEquals(1, vm.uiState.value.completedCount)

        vm.undo()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.currentIndex)
        assertEquals(0, vm.uiState.value.rows[0].currentCount)
        assertFalse(vm.uiState.value.rows[0].completed)
        job.cancel()
    }

    @Test
    fun reopeningSessionRestoresFirstUncompletedItemAndResetClearsProgress() = runTest {
        val repo = FakeAdhkarRepository(
            items = listOf(
                sampleDhikr("d1", repeatCount = 1),
                sampleDhikr("d2", repeatCount = 3),
            ),
        )
        repo.incrementProgress("2026-11-20", "d1")

        val vm = AdhkarSessionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to "morning")),
            adhkarRepository = repo,
            dateProvider = FakeDateProvider("2026-11-20", 7),
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.currentIndex)
        assertTrue(vm.uiState.value.rows[0].completed)

        vm.resetSession()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.currentIndex)
        assertEquals(0, vm.uiState.value.completedCount)
        assertFalse(vm.uiState.value.rows[0].completed)
        job.cancel()
    }

    @Test
    fun dayRolloverStartsFreshProgressWithoutCarryingOverPreviousDay() = runTest {
        val dateProvider = FakeDateProvider("2026-11-20", 7)
        val repo = FakeAdhkarRepository(
            items = listOf(sampleDhikr("d1", repeatCount = 1)),
        )
        repo.incrementProgress("2026-11-20", "d1")

        val vm = AdhkarSessionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to "morning")),
            adhkarRepository = repo,
            dateProvider = dateProvider,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.allCompleted)

        dateProvider.day = "2026-11-21"
        vm.refreshDayKey()
        advanceUntilIdle()

        assertEquals("2026-11-21", vm.uiState.value.dayKey)
        assertFalse(vm.uiState.value.allCompleted)
        assertEquals(0, vm.uiState.value.completedCount)
        job.cancel()
    }

    @Test
    fun rapidTapsInsideGuardWindowDoNotDoubleIncrement() = runTest {
        val repo = FakeAdhkarRepository(
            items = listOf(sampleDhikr("d1", repeatCount = 5)),
        )
        val vm = AdhkarSessionViewModel(
            savedStateHandle = SavedStateHandle(mapOf("categoryId" to "morning")),
            adhkarRepository = repo,
            dateProvider = FakeDateProvider("2026-11-20", 7),
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.incrementCurrent(autoAdvanceDelayMs = 0L)
        vm.incrementCurrent(autoAdvanceDelayMs = 0L)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.rows[0].currentCount)
        job.cancel()
    }
}

internal class FakeDateProvider(var day: String, var hour: Int) : CompanionDateProvider {
    override fun todayKey(): String = day
    override fun currentHour(): Int = hour
}

internal fun sampleDhikr(
    id: String,
    categoryId: String = "morning",
    repeatCount: Int = 3,
    itemType: DhikrItemType = DhikrItemType.DHIKR,
    surahId: Int? = null,
) = DhikrItem(
    id = id,
    categoryId = categoryId,
    itemType = itemType,
    sortOrder = 1,
    titleAr = "ذكر $id",
    titleEn = "Dhikr $id",
    textAr = "سُبْحَانَ اللَّهِ $id",
    textEn = "Glory be to Allah $id",
    virtueAr = null,
    virtueEn = null,
    sourceAr = "صحيح مسلم",
    sourceEn = "Sahih Muslim",
    repeatCount = repeatCount,
    surahId = surahId,
    startAyah = null,
    endAyah = null,
    datasetVersion = "v1",
)

internal class FakeAdhkarRepository(
    categories: List<DhikrCategory> = listOf(
        DhikrCategory("morning", DhikrSectionType.ADHKAR_SESSION, "أذكار الصباح", "Morning", 1, "MORNING"),
        DhikrCategory("evening", DhikrSectionType.ADHKAR_SESSION, "أذكار المساء", "Evening", 2, "EVENING"),
        DhikrCategory("after_prayer", DhikrSectionType.ADHKAR_SESSION, "بعد الصلاة", "After Prayer", 3, "AFTER_PRAYER"),
        DhikrCategory("sleep", DhikrSectionType.ADHKAR_SESSION, "أذكار النوم", "Sleep", 4, "NIGHT"),
    ),
    items: List<DhikrItem> = emptyList(),
    quranicDuas: List<ResolvedQuranicDua> = emptyList(),
) : AdhkarRepository {

    private val categoriesFlow = MutableStateFlow(categories)
    private val itemsFlow = MutableStateFlow(items)
    private val quranicFlow = MutableStateFlow(quranicDuas)
    private val progressFlow = MutableStateFlow<Map<Pair<String, String>, DhikrDailyProgress>>(emptyMap())
    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())

    override fun observeCategories(): Flow<List<DhikrCategory>> = categoriesFlow

    override fun observeItemsByCategory(categoryId: String): Flow<List<DhikrItem>> =
        itemsFlow.map { list -> list.filter { it.categoryId == categoryId } }

    override fun observeDailyProgress(dayKey: String): Flow<List<DhikrDailyProgress>> =
        progressFlow.map { map -> map.values.filter { it.dayKey == dayKey } }

    override fun observeCategoryProgress(dayKey: String, categoryId: String): Flow<List<DhikrDailyProgress>> =
        progressFlow.map { map -> map.values.filter { it.dayKey == dayKey && it.categoryId == categoryId } }

    override fun observeFavoriteIds(): Flow<Set<String>> = favoritesFlow

    override fun observeFavoriteItems(): Flow<List<DhikrItem>> =
        itemsFlow.map { list -> list.filter { it.id in favoritesFlow.value } }

    override fun observeQuranicDuas(): Flow<List<ResolvedQuranicDua>> = quranicFlow

    override suspend fun searchItems(query: String): List<DhikrItem> =
        itemsFlow.value.filter { it.textAr.contains(query) || it.titleAr.contains(query) || it.titleEn.contains(query, true) }

    override suspend fun incrementProgress(dayKey: String, itemId: String) {
        val item = itemsFlow.value.firstOrNull { it.id == itemId } ?: return
        val target = item.repeatCount.coerceAtLeast(1)
        val key = dayKey to itemId
        val cur = progressFlow.value[key]?.currentCount ?: 0
        val next = (cur + 1).coerceAtMost(target)
        progressFlow.value = progressFlow.value + (
            key to DhikrDailyProgress(dayKey, itemId, item.categoryId, next, target, next >= target, 1L)
            )
    }

    override suspend fun decrementProgress(dayKey: String, itemId: String) {
        val item = itemsFlow.value.firstOrNull { it.id == itemId } ?: return
        val target = item.repeatCount.coerceAtLeast(1)
        val key = dayKey to itemId
        val cur = progressFlow.value[key]?.currentCount ?: 0
        val next = (cur - 1).coerceAtLeast(0)
        progressFlow.value = progressFlow.value + (
            key to DhikrDailyProgress(dayKey, itemId, item.categoryId, next, target, next >= target, 2L)
            )
    }

    override suspend fun resetCategoryProgress(dayKey: String, categoryId: String) {
        progressFlow.value = progressFlow.value.filterValues { !(it.dayKey == dayKey && it.categoryId == categoryId) }
    }

    override suspend fun toggleFavorite(itemId: String) {
        favoritesFlow.value = if (itemId in favoritesFlow.value) {
            favoritesFlow.value - itemId
        } else {
            favoritesFlow.value + itemId
        }
    }
}
