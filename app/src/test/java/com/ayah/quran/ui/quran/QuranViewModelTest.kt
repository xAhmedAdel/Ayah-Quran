package com.ayah.quran.ui.quran

import app.cash.turbine.test
import com.ayah.quran.data.quran.RubElHizbParser
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.RubElHizb
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.BookmarkRepository
import com.ayah.quran.domain.repository.QuranRepository
import com.ayah.quran.domain.repository.RubElHizbCatalog
import com.ayah.quran.domain.usecase.GetAyahUseCase
import com.ayah.quran.domain.usecase.GetRubElHizbListUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QuranViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val quranRepo = mockk<QuranRepository>(relaxed = true)
    private val bookmarkRepo = mockk<BookmarkRepository>(relaxed = true)
    private val rubCatalog = mockk<RubElHizbCatalog>()

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
    fun `uiState loads surahs, 30 juz summaries, 240 rub summaries, start pages, and bookmarks when import completes`() =
        runTest(testDispatcher.scheduler) {
            val faatiha = Surah(
                id = 1,
                nameArabic = "الفاتحة",
                nameTransliterated = "Al-Faatiha",
                nameEnglish = "The Opening",
                revelationType = RevelationType.MECCAN,
                ayahCount = 7,
            )
            val hijr = Surah(
                id = 15,
                nameArabic = "الحجر",
                nameTransliterated = "Al-Hijr",
                nameEnglish = "The Rock",
                revelationType = RevelationType.MECCAN,
                ayahCount = 99,
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
            val allRubs = loadBundledRubs()
            every { quranRepo.importState } returns MutableStateFlow(QuranImportState.Completed)
            every { quranRepo.searchSurahs(any()) } returns MutableStateFlow(listOf(faatiha))
            coEvery { quranRepo.pageForSurahStart(any()) } answers { firstArg<Int>() }
            coEvery { quranRepo.getSurah(any()) } answers {
                when (firstArg<Int>()) {
                    15 -> hijr
                    else -> faatiha
                }
            }
            coEvery { quranRepo.getAyah(any(), any()) } answers {
                val s = firstArg<Int>()
                val a = secondArg<Int>()
                val page = when {
                    s == 1 && a == 1 -> 1
                    s == 15 && a == 49 -> 264
                    else -> s * 2 - 1
                }
                Ayah(
                    id = s * 1000 + a,
                    surahId = s,
                    ayahNumber = a,
                    textUthmani = if (s == 15 && a == 49) "نَبِّئْ عِبَادِىٓ أَنِّىٓ أَنَا ٱلْغَفُورُ ٱلرَّحِيمُ" else "آية",
                    juz = 1,
                    page = page,
                )
            }
            coEvery { rubCatalog.getAll() } returns allRubs
            every { bookmarkRepo.observeBookmarkedAyahs() } returns MutableStateFlow(listOf(bookmark))

            val vm = createViewModel()

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem()
                assertFalse(loaded.isLoading)
                assertEquals(1, loaded.surahs.size)
                assertEquals(30, loaded.juzList.size)
                assertEquals(240, loaded.rubList.size)
                assertEquals(1, loaded.surahStartPages[1])
                assertEquals(604, loaded.juzList.last().endPage)
                assertEquals(1, loaded.totalBookmarkCount)
                assertEquals(listOf(bookmark), loaded.bookmarks)

                val firstRub = loaded.rubList.first()
                assertEquals(1, firstRub.rubNumber)
                assertEquals(1, firstRub.startSurahId)
                assertEquals(1, firstRub.startAyahNumber)
                assertEquals(1, firstRub.startPage)

                val rub106 = loaded.rubList[105]
                assertEquals(106, rub106.rubNumber)
                assertEquals(27, rub106.hizbNumber)
                assertEquals(2, rub106.quarterInHizb)
                assertEquals(14, rub106.juzNumber)
                assertEquals(15, rub106.startSurahId)
                assertEquals(49, rub106.startAyahNumber)
                assertEquals("الحجر", rub106.startSurahNameAr)
                assertEquals("Al-Hijr", rub106.startSurahNameTransliterated)
                assertEquals(264, rub106.startPage)
                assertTrue(rub106.startAyahText.contains("نَبِّئْ"))

                val rub240 = loaded.rubList.last()
                assertEquals(240, rub240.rubNumber)
                assertEquals(60, rub240.hizbNumber)
                assertEquals(4, rub240.quarterInHizb)
                assertEquals(30, rub240.juzNumber)
                assertEquals(100, rub240.startSurahId)
                assertEquals(9, rub240.startAyahNumber)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `search filters rub list by number, surah name, reference, and hizb while keeping surah and mid-surah targets`() =
        runTest(testDispatcher.scheduler) {
            val surahMap = mapOf(
                1 to Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", RevelationType.MECCAN, 7),
                2 to Surah(2, "البقرة", "Al-Baqara", "The Cow", RevelationType.MEDINAN, 286),
                15 to Surah(15, "الحجر", "Al-Hijr", "The Rock", RevelationType.MECCAN, 99),
            )
            val sampleRubs = listOf(
                RubElHizb(1, 1, 1, 1, 1, 1),
                RubElHizb(2, 1, 2, 1, 2, 26),
                RubElHizb(105, 27, 1, 14, 15, 1),
                RubElHizb(106, 27, 2, 14, 15, 49),
            )
            every { quranRepo.importState } returns MutableStateFlow(QuranImportState.Completed)
            every { quranRepo.searchSurahs(any()) } returns MutableStateFlow(surahMap.values.toList())
            coEvery { quranRepo.pageForSurahStart(any()) } returns 1
            coEvery { quranRepo.getSurah(any()) } answers { surahMap[firstArg()] }
            coEvery { quranRepo.getAyah(any(), any()) } answers {
                val s = firstArg<Int>()
                val a = secondArg<Int>()
                val page = when (s to a) {
                    1 to 1 -> 1
                    2 to 26 -> 5
                    15 to 1 -> 262
                    15 to 49 -> 264
                    else -> 1
                }
                Ayah(s * 100 + a, s, a, "آية $s:$a", 1, page)
            }
            coEvery { rubCatalog.getAll() } returns sampleRubs
            every { bookmarkRepo.observeBookmarkedAyahs() } returns MutableStateFlow(emptyList())

            val vm = createViewModel()

            vm.uiState.test {
                awaitItem()
                val initial = awaitItem()
                assertEquals(4, initial.rubList.size)

                vm.onQueryChange("15:49")
                val byRef = awaitItem()
                assertEquals(listOf(106), byRef.rubList.map { it.rubNumber })
                assertEquals(15, byRef.rubList.single().startSurahId)
                assertEquals(49, byRef.rubList.single().startAyahNumber)
                assertEquals(264, byRef.rubList.single().startPage)

                vm.onQueryChange("الحزب 27")
                val byHizbAr = awaitItem()
                assertEquals(listOf(105, 106), byHizbAr.rubList.map { it.rubNumber })
                assertEquals(15 to 1, byHizbAr.rubList[0].startSurahId to byHizbAr.rubList[0].startAyahNumber)
                assertEquals(15 to 49, byHizbAr.rubList[1].startSurahId to byHizbAr.rubList[1].startAyahNumber)

                vm.onQueryChange("Al-Baqara")
                val bySurahEn = awaitItem()
                assertEquals(listOf(2), bySurahEn.rubList.map { it.rubNumber })
                assertEquals(5, bySurahEn.rubList.single().startPage)

                vm.onQueryChange("")
                val restored = awaitItem()
                assertEquals(4, restored.rubList.size)

                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun createViewModel(): QuranViewModel = QuranViewModel(
        observeQuranImportState = ObserveQuranImportStateUseCase(quranRepo),
        observeBookmarkedAyahs = ObserveBookmarkedAyahsUseCase(bookmarkRepo),
        searchSurahs = SearchSurahsUseCase(quranRepo),
        importQuranIfNeeded = ImportQuranIfNeededUseCase(quranRepo),
        pageForSurahStart = PageForSurahStartUseCase(quranRepo),
        getAyah = GetAyahUseCase(quranRepo),
        getSurah = GetSurahUseCase(quranRepo),
        getRubElHizbList = GetRubElHizbListUseCase(rubCatalog),
        toggleBookmark = ToggleBookmarkUseCase(bookmarkRepo),
    )

    private fun loadBundledRubs(): List<RubElHizb> {
        val file = File("src/main/assets/quran/rub-el-hizb.txt")
            .takeIf { it.exists() } ?: File("app/src/main/assets/quran/rub-el-hizb.txt")
        return RubElHizbParser().parse(file.readLines())
    }
}
