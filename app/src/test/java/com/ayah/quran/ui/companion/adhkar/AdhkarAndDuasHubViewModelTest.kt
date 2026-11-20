package com.ayah.quran.ui.companion.adhkar

import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrItemType
import com.ayah.quran.domain.model.companion.DhikrSectionType
import com.ayah.quran.domain.model.companion.QuranicDuaPointer
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.ui.companion.duas.DuaTab
import com.ayah.quran.ui.companion.duas.DuasHubViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdhkarAndDuasHubViewModelTest {

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
    fun recommendsSessionBasedOnTimeOfDay() {
        assertEquals("morning", AdhkarHubViewModel.recommendedCategoryId(7))
        assertEquals("after_prayer", AdhkarHubViewModel.recommendedCategoryId(13))
        assertEquals("evening", AdhkarHubViewModel.recommendedCategoryId(17))
        assertEquals("sleep", AdhkarHubViewModel.recommendedCategoryId(22))
        assertEquals("sleep", AdhkarHubViewModel.recommendedCategoryId(1))
    }

    @Test
    fun adhkarHubAggregatesCategoryProgressAndExposesSunnahSurahs() = runTest {
        val repo = FakeAdhkarRepository(
            categories = listOf(
                DhikrCategory("morning", DhikrSectionType.ADHKAR_SESSION, "الصباح", "Morning", 1, "MORNING"),
                DhikrCategory("daily_surahs", DhikrSectionType.SUNNAH_SURAH, "سور السنة", "Daily Surahs", 2, "FRIDAY"),
            ),
            items = listOf(
                sampleDhikr("m1", "morning", repeatCount = 1),
                sampleDhikr("m2", "morning", repeatCount = 3),
                sampleDhikr("s18", "daily_surahs", repeatCount = 1, itemType = DhikrItemType.SUNNAH_SURAH_LINK, surahId = 18),
            ),
        )
        repo.incrementProgress("2026-11-20", "m1")

        val vm = AdhkarHubViewModel(repo, FakeDateProvider("2026-11-20", 8))
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("morning", state.recommendedSession?.category?.id)
        assertEquals(1, state.recommendedSession?.completedItems)
        assertEquals(2, state.recommendedSession?.totalItems)
        assertEquals(listOf(18), state.sunnahSurahs.mapNotNull { it.surahId })
        job.cancel()
    }

    @Test
    fun duasHubSeparatesQuranicPropheticSituationalAndSupportsSearchAndFavorites() = runTest {
        val repo = FakeAdhkarRepository(
            categories = listOf(
                DhikrCategory("forgiveness", DhikrSectionType.DUA_PROPHETIC, "الاستغفار", "Forgiveness", 1, null),
                DhikrCategory("travel", DhikrSectionType.DUA_SITUATION, "السفر", "Travel", 2, null),
            ),
            items = listOf(
                sampleDhikr("p1", "forgiveness", repeatCount = 1, itemType = DhikrItemType.PROPHETIC_DUA),
                sampleDhikr("t1", "travel", repeatCount = 1, itemType = DhikrItemType.PROPHETIC_DUA),
            ),
            quranicDuas = listOf(
                ResolvedQuranicDua(
                    pointer = QuranicDuaPointer("q1", "dua_gratitude", 2, 201, 201, "دعاء خير الدنيا", "Good in Both Worlds", 1),
                    textUthmani = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً",
                    surahNameArabic = "البقرة",
                    surahNameEnglish = "Al-Baqarah",
                ),
            ),
        )

        val vm = DuasHubViewModel(repo)
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(DuaTab.QURANIC, vm.uiState.value.activeTab)
        assertEquals(1, vm.uiState.value.quranicDuas.size)
        assertEquals(1, vm.uiState.value.propheticItems.size)
        assertEquals(1, vm.uiState.value.situationalItems.size)

        vm.onQueryChange("ربنا اتنا")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.quranicDuas.size)

        vm.onQueryChange("")
        vm.selectTab(DuaTab.PROPHETIC)
        vm.toggleFavorite("p1")
        vm.toggleSavedFilter()
        advanceUntilIdle()
        assertEquals(listOf("p1"), vm.uiState.value.propheticItems.map { it.id })
        assertTrue("p1" in vm.uiState.value.favoriteIds)
        job.cancel()
    }
}
