package com.ayah.quran.ui.quran

import app.cash.turbine.test
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.BookmarkRepository
import com.ayah.quran.domain.repository.QuranRepository
import com.ayah.quran.domain.usecase.GetAyahUseCase
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ImportQuranIfNeededUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkedAyahsUseCase
import com.ayah.quran.domain.usecase.ObserveQuranImportStateUseCase
import com.ayah.quran.domain.usecase.PageForSurahStartUseCase
import com.ayah.quran.domain.usecase.SearchSurahsUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuranViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val quranRepo = mockk<QuranRepository>(relaxed = true)
    private val bookmarkRepo = mockk<BookmarkRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canonical JUZ_STARTS defines all 30 juz boundaries in ascending order`() {
        assertEquals(30, QuranViewModel.JUZ_STARTS.size)
        assertEquals((1..30).toList(), QuranViewModel.JUZ_STARTS.map { it.first })
        assertEquals(Triple(1, 1, 1), QuranViewModel.JUZ_STARTS.first())
        assertEquals(Triple(30, 78, 1), QuranViewModel.JUZ_STARTS.last())
    }

    @Test
    fun `uiState loads surahs, 30 juz summaries, start pages, and bookmarks when import completes`() =
        runTest(testDispatcher.scheduler) {
            val faatiha = Surah(
                id = 1,
                nameArabic = "الفاتحة",
                nameTransliterated = "Al-Faatiha",
                nameEnglish = "The Opening",
                revelationType = RevelationType.MECCAN,
                ayahCount = 7,
            )
            val bookmark = BookmarkedAyah(
                bookmarkId = 1L,
                surahId = 1,
                ayahNumber = 1,
                createdAtMillis = 100L,
                textUthmani = "بِسْمِ ٱللَّهِ",
                surahNameArabic = "الفاتحة",
                surahNameTransliterated = "Al-Faatiha",
            )
            every { quranRepo.importState } returns MutableStateFlow(QuranImportState.Completed)
            every { quranRepo.searchSurahs(any()) } returns MutableStateFlow(listOf(faatiha))
            coEvery { quranRepo.pageForSurahStart(any()) } answers { firstArg<Int>() }
            coEvery { quranRepo.getSurah(any()) } returns faatiha
            coEvery { quranRepo.getAyah(any(), any()) } answers {
                val s = firstArg<Int>()
                val a = secondArg<Int>()
                Ayah(
                    id = s,
                    surahId = s,
                    ayahNumber = a,
                    textUthmani = "آية",
                    juz = 1,
                    page = s * 2 - 1,
                )
            }
            every { bookmarkRepo.observeBookmarkedAyahs() } returns MutableStateFlow(listOf(bookmark))

            val vm = QuranViewModel(
                observeQuranImportState = ObserveQuranImportStateUseCase(quranRepo),
                observeBookmarkedAyahs = ObserveBookmarkedAyahsUseCase(bookmarkRepo),
                searchSurahs = SearchSurahsUseCase(quranRepo),
                importQuranIfNeeded = ImportQuranIfNeededUseCase(quranRepo),
                pageForSurahStart = PageForSurahStartUseCase(quranRepo),
                getAyah = GetAyahUseCase(quranRepo),
                getSurah = GetSurahUseCase(quranRepo),
                toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo),
            )

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals(1, loaded.surahs.size)
                assertEquals(30, loaded.juzList.size)
                assertEquals(1, loaded.surahStartPages[1])
                assertEquals(604, loaded.juzList.last().endPage)
                assertEquals(1, loaded.totalBookmarkCount)
                assertEquals(listOf(bookmark), loaded.bookmarks)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
