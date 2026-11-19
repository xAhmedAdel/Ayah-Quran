package com.ayah.quran.ui.bookmarks

import app.cash.turbine.test
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.repository.BookmarkRepository
import com.ayah.quran.domain.usecase.ObserveBookmarkedAyahsUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<BookmarkRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits bookmarked ayahs from repository`() = runTest(testDispatcher.scheduler) {
        val sample = BookmarkedAyah(
            bookmarkId = 7L,
            surahId = 36,
            ayahNumber = 1,
            createdAtMillis = 1_000L,
            textUthmani = "يس",
            surahNameArabic = "يس",
            surahNameTransliterated = "Yaseen",
        )
        every { repository.observeBookmarkedAyahs() } returns MutableStateFlow(listOf(sample))

        val vm = createViewModel()
        vm.uiState.test {
            awaitItem()
            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(listOf(sample), loaded.bookmarks)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeBookmark and undoLastRemove toggle the exact verse twice`() = runTest(
        testDispatcher.scheduler,
    ) {
        every { repository.observeBookmarkedAyahs() } returns MutableStateFlow(emptyList())
        coEvery { repository.toggleBookmark(2, 255) } returns false andThen true

        val vm = createViewModel()
        vm.removeBookmark(2, 255)
        advanceUntilIdle()
        vm.undoLastRemove()
        advanceUntilIdle()
        vm.undoLastRemove()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.toggleBookmark(2, 255) }
    }

    private fun createViewModel(): BookmarksViewModel = BookmarksViewModel(
        observeBookmarkedAyahs = ObserveBookmarkedAyahsUseCase(repository),
        toggleBookmark = ToggleBookmarkUseCase(repository),
    )
}
