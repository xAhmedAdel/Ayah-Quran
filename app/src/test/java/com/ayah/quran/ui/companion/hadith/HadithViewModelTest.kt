package com.ayah.quran.ui.companion.hadith

import androidx.lifecycle.SavedStateHandle
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.CompanionImportState
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import com.ayah.quran.domain.repository.companion.HadithRepository
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
class HadithViewModelTest {

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
    fun libraryLoadsAllHadithsWithoutDuplicatesAndFiltersByVerifiedCollection() = runTest {
        val repo = FakeHadithRepository(sampleCollections(), sampleHadiths())
        val vm = HadithLibraryViewModel(
            hadithRepository = repo,
            importStateFlow = MutableStateFlow(CompanionImportState.Completed),
            onRetryImport = {},
            searchDebounceMs = 0L,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(2, vm.uiState.value.collections.size)
        assertEquals(3, vm.uiState.value.items.size)
        assertEquals(3, vm.uiState.value.items.map { it.id }.toSet().size)

        vm.selectCollection("nawawi40")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("nawawi_1", vm.uiState.value.items.first().id)

        vm.selectCollection("hadeethenc_quran")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.items.size)
        assertTrue(vm.uiState.value.items.all { it.collectionId == "hadeethenc_quran" })
        job.cancel()
    }

    @Test
    fun arabicAndEnglishSearchReturnsMatchesAndHandlesNoResults() = runTest {
        val repo = FakeHadithRepository(sampleCollections(), sampleHadiths())
        val vm = HadithLibraryViewModel(
            hadithRepository = repo,
            importStateFlow = MutableStateFlow(CompanionImportState.Completed),
            onRetryImport = {},
            searchDebounceMs = 0L,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onQueryChange("النيات")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("nawawi_1", vm.uiState.value.items.first().id)

        vm.onQueryChange("Quran")
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.items.size)
        assertEquals("henc_1", vm.uiState.value.items.first().id)

        vm.onQueryChange("nonexistent_phrase_xyz")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.items.isEmpty())
        assertFalse(vm.uiState.value.isSearching)
        job.cancel()
    }

    @Test
    fun favoritesTogglePersistsAcrossViewModelRecreationAndSyncsInSearchResults() = runTest {
        val repo = FakeHadithRepository(sampleCollections(), sampleHadiths())
        val vm1 = HadithLibraryViewModel(
            hadithRepository = repo,
            importStateFlow = MutableStateFlow(CompanionImportState.Completed),
            onRetryImport = {},
            searchDebounceMs = 0L,
        )
        val job1 = backgroundScope.launch { vm1.uiState.collect {} }
        advanceUntilIdle()

        vm1.onQueryChange("النيات")
        advanceUntilIdle()
        vm1.toggleFavorite("nawawi_1")
        advanceUntilIdle()
        assertTrue(vm1.uiState.value.items.first().isFavorite)
        job1.cancel()

        val vm2 = HadithLibraryViewModel(
            hadithRepository = repo,
            importStateFlow = MutableStateFlow(CompanionImportState.Completed),
            onRetryImport = {},
            searchDebounceMs = 0L,
        )
        val job2 = backgroundScope.launch { vm2.uiState.collect {} }
        advanceUntilIdle()

        vm2.toggleFavoritesOnly()
        advanceUntilIdle()
        assertEquals(1, vm2.uiState.value.items.size)
        assertEquals("nawawi_1", vm2.uiState.value.items.first().id)

        vm2.toggleFavorite("nawawi_1")
        advanceUntilIdle()
        assertTrue(vm2.uiState.value.items.isEmpty())
        job2.cancel()
    }

    @Test
    fun detailPreservesExactTextGradeAttributionRecordsReadAndClampsFontScale() = runTest {
        val repo = FakeHadithRepository(sampleCollections(), sampleHadiths())
        val vm = HadithDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("hadithId" to "henc_1")),
            hadithRepository = repo,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.notFound)
        assertEquals("خَيْرُكُمْ مَنْ تَعَلَّمَ القُرْآنَ وَعَلَّمَهُ", state.hadith?.textAr)
        assertEquals("The best of you are those who learn the Quran and teach it.", state.hadith?.textEn)
        assertEquals("صحيح", state.hadith?.gradeAr)
        assertEquals("HadeethEnc.com", state.hadith?.graderAttribution)
        assertEquals("hadeethenc-api-v1-2026-11-20", state.hadith?.sourceVersion)
        assertEquals("HadeethEnc.com — Encyclopedia of Translated Prophetic Hadiths", state.collection?.sourceAttribution)
        assertTrue("henc_1" in repo.recordedReads)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hadith!!.isFavorite)

        vm.adjustFontScale(1.0f)
        advanceUntilIdle()
        assertEquals(HadithDetailViewModel.MAX_FONT_SCALE, vm.uiState.value.fontScale, 0.001f)
        vm.adjustFontScale(-2.0f)
        advanceUntilIdle()
        assertEquals(HadithDetailViewModel.MIN_FONT_SCALE, vm.uiState.value.fontScale, 0.001f)
        job.cancel()
    }

    @Test
    fun nawawiDetailKeepsMissingEnglishTranslationNullWithoutFabrication() = runTest {
        val repo = FakeHadithRepository(sampleCollections(), sampleHadiths())
        val vm = HadithDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("hadithId" to "nawawi_1")),
            hadithRepository = repo,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertNull(vm.uiState.value.hadith?.textEn)
        assertEquals("مُبيَّن في متن الإمام النووي", vm.uiState.value.hadith?.gradeAr)
        job.cancel()
    }

    @Test
    fun gradeBadgeKindAndErrorRetryWorkAccurately() = runTest {
        assertEquals(AyahStatusKind.SUCCESS, resolveGradeBadgeKind("صحيح", "Authentic"))
        assertEquals(AyahStatusKind.GOLD, resolveGradeBadgeKind("حسن", "Hasan"))
        assertEquals(AyahStatusKind.WARNING, resolveGradeBadgeKind("ضعيف", "Weak"))
        assertEquals(AyahStatusKind.NEUTRAL, resolveGradeBadgeKind("مُبيَّن في متن الإمام النووي", "Stated within Imam An-Nawawi's text"))

        var retried = false
        val repo = FakeHadithRepository(emptyList(), emptyList())
        val vm = HadithLibraryViewModel(
            hadithRepository = repo,
            importStateFlow = MutableStateFlow(CompanionImportState.Failed("disk full")),
            onRetryImport = { retried = true },
            searchDebounceMs = 0L,
        )
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals("disk full", vm.uiState.value.errorMessage)
        vm.retryImport()
        assertTrue(retried)
        job.cancel()
    }
}

private fun sampleCollections() = listOf(
    HadithCollection(
        id = "nawawi40",
        titleAr = "الأربعون النووية",
        titleEn = "Forty Hadith of An-Nawawi",
        descriptionAr = "متن الأربعين النووية",
        descriptionEn = "Classical 40 Hadith",
        sourceAttribution = "Imam An-Nawawi via fawazahmed0/hadith-api (The Unlicense)",
        datasetVersion = "fawazahmed0-ara-nawawi-unlicense-2026-11-20",
        isBundled = true,
        isDownloaded = true,
        hadithCount = 1,
        sortOrder = 1,
    ),
    HadithCollection(
        id = "hadeethenc_quran",
        titleAr = "القرآن الكريم وعلومه",
        titleEn = "The Noble Quran & Its Sciences",
        descriptionAr = "أحاديث في فضائل القرآن",
        descriptionEn = "Hadiths on the Noble Quran",
        sourceAttribution = "HadeethEnc.com — Encyclopedia of Translated Prophetic Hadiths",
        datasetVersion = "hadeethenc-api-v1-2026-11-20",
        isBundled = true,
        isDownloaded = true,
        hadithCount = 2,
        sortOrder = 2,
    ),
)

private fun sampleHadiths() = listOf(
    HadithItem(
        id = "nawawi_1",
        collectionId = "nawawi40",
        chapterTitleAr = "الأربعون النووية",
        chapterTitleEn = "Forty Hadith of An-Nawawi",
        hadithNumber = "1",
        titleAr = "إنما الأعمال بالنيات",
        titleEn = "Hadith 1",
        textAr = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى",
        textEn = null,
        gradeAr = "مُبيَّن في متن الإمام النووي",
        gradeEn = "Stated within Imam An-Nawawi's text",
        graderAttribution = "Imam An-Nawawi (source text)",
        referenceAr = "الأربعون النووية (1)",
        referenceEn = "40 Hadith An-Nawawi #1",
        explanationAr = null,
        explanationEn = null,
        sourceVersion = "fawazahmed0-ara-nawawi-unlicense-2026-11-20",
        sortOrder = 1,
    ),
    HadithItem(
        id = "henc_1",
        collectionId = "hadeethenc_quran",
        chapterTitleAr = "فضائل القرآن",
        chapterTitleEn = "Virtues of the Quran",
        hadithNumber = "2901",
        titleAr = "خيركم من تعلم القرآن وعلمه",
        titleEn = "The best of you are those who learn the Quran",
        textAr = "خَيْرُكُمْ مَنْ تَعَلَّمَ القُرْآنَ وَعَلَّمَهُ",
        textEn = "The best of you are those who learn the Quran and teach it.",
        gradeAr = "صحيح",
        gradeEn = "Authentic",
        graderAttribution = "HadeethEnc.com",
        referenceAr = "صحيح البخاري",
        referenceEn = "Sahih al-Bukhari",
        explanationAr = "بيان فضل تعلم القرآن وتعليمه.",
        explanationEn = "Explains the virtue of learning and teaching the Quran.",
        sourceVersion = "hadeethenc-api-v1-2026-11-20",
        sortOrder = 1,
    ),
    HadithItem(
        id = "henc_2",
        collectionId = "hadeethenc_quran",
        chapterTitleAr = "فضائل القرآن",
        chapterTitleEn = "Virtues of the Quran",
        hadithNumber = "2902",
        titleAr = "الماهر بالقرآن",
        titleEn = "The one proficient in recitation",
        textAr = "المَاهِرُ بِالقُرْآنِ مَعَ السَّفَرَةِ الكِرَامِ البَرَرَةِ",
        textEn = "The one who is proficient in reciting will be with the noble scribes.",
        gradeAr = "صحيح",
        gradeEn = "Authentic",
        graderAttribution = "HadeethEnc.com",
        referenceAr = "متفق عليه",
        referenceEn = "Agreed upon",
        explanationAr = null,
        explanationEn = null,
        sourceVersion = "hadeethenc-api-v1-2026-11-20",
        sortOrder = 2,
    ),
)

private class FakeHadithRepository(
    collections: List<HadithCollection>,
    items: List<HadithItem>,
) : HadithRepository {

    private val collectionsFlow = MutableStateFlow(collections)
    private val itemsFlow = MutableStateFlow(items)
    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
    val recordedReads = mutableSetOf<String>()

    private fun attachFavs(list: List<HadithItem>, favs: Set<String>): List<HadithItem> =
        list.map { it.copy(isFavorite = it.id in favs) }

    override fun observeCollections(): Flow<List<HadithCollection>> = collectionsFlow

    override fun observeAll(): Flow<List<HadithItem>> =
        favoritesFlow.map { favs -> attachFavs(itemsFlow.value, favs) }

    override fun observeByCollection(collectionId: String): Flow<List<HadithItem>> =
        favoritesFlow.map { favs ->
            attachFavs(itemsFlow.value.filter { it.collectionId == collectionId }, favs)
        }

    override fun observeById(hadithId: String): Flow<HadithItem?> =
        favoritesFlow.map { favs ->
            itemsFlow.value.firstOrNull { it.id == hadithId }?.copy(isFavorite = hadithId in favs)
        }

    override fun observeFavorites(): Flow<List<HadithItem>> =
        favoritesFlow.map { favs ->
            itemsFlow.value.filter { it.id in favs }.map { it.copy(isFavorite = true) }
        }

    override fun observeRecentlyRead(limit: Int): Flow<List<HadithItem>> =
        favoritesFlow.map { favs ->
            itemsFlow.value.filter { it.id in recordedReads }.take(limit).map { it.copy(isFavorite = it.id in favs) }
        }

    override suspend fun search(query: String, limit: Int): List<HadithItem> {
        val norm = ArabicTextNormalizer.normalize(query)
        if (norm.isEmpty()) return emptyList()
        val favs = favoritesFlow.value
        return itemsFlow.value.filter { item ->
            val hay = ArabicTextNormalizer.normalize(
                listOfNotNull(item.titleAr, item.titleEn, item.textAr, item.textEn).joinToString(" "),
            )
            hay.contains(norm)
        }.take(limit).map { it.copy(isFavorite = it.id in favs) }
    }

    override suspend fun toggleFavorite(hadithId: String) {
        favoritesFlow.value = if (hadithId in favoritesFlow.value) {
            favoritesFlow.value - hadithId
        } else {
            favoritesFlow.value + hadithId
        }
    }

    override suspend fun recordRead(hadithId: String) {
        recordedReads.add(hadithId)
    }

    override suspend fun dailyHadithId(epochDay: Long): String? =
        itemsFlow.value.firstOrNull()?.id
}
