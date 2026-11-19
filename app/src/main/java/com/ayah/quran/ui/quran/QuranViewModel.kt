package com.ayah.quran.ui.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.usecase.GetAyahUseCase
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ImportQuranIfNeededUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkedAyahsUseCase
import com.ayah.quran.domain.usecase.ObserveQuranImportStateUseCase
import com.ayah.quran.domain.usecase.PageForSurahStartUseCase
import com.ayah.quran.domain.usecase.SearchSurahsUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuranHubTab {
    SURAHS,
    JUZ,
    BOOKMARKS,
}

data class JuzSummary(
    val juzNumber: Int,
    val startSurahId: Int,
    val startAyahNumber: Int,
    val startSurahNameAr: String,
    val startSurahNameTransliterated: String,
    val startAyahText: String,
    val startPage: Int,
    val endPage: Int,
)

data class QuranListUiState(
    val isLoading: Boolean = true,
    val importProgress: Float? = null,
    val importError: String? = null,
    val surahs: List<Surah> = emptyList(),
    val surahStartPages: Map<Int, Int> = emptyMap(),
    val juzList: List<JuzSummary> = emptyList(),
    val bookmarks: List<BookmarkedAyah> = emptyList(),
    val totalBookmarkCount: Int = 0,
    val query: String = "",
) {
    val isSearchResultsEmpty: Boolean get() = query.isNotBlank() && surahs.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuranViewModel @Inject constructor(
    observeQuranImportState: ObserveQuranImportStateUseCase,
    observeBookmarkedAyahs: ObserveBookmarkedAyahsUseCase,
    private val searchSurahs: SearchSurahsUseCase,
    private val importQuranIfNeeded: ImportQuranIfNeededUseCase,
    private val pageForSurahStart: PageForSurahStartUseCase,
    private val getAyah: GetAyahUseCase,
    private val getSurah: GetSurahUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val retryTrigger = MutableStateFlow(0)
    private var cachedStartPages: Map<Int, Int>? = null
    private var cachedJuzList: List<JuzSummary>? = null
    private var lastRemovedBookmark: Pair<Int, Int>? = null

    private val metadataFlow = observeQuranImportState()
        .flatMapLatest { state ->
            if (state is QuranImportState.Completed) {
                flow { emit(loadMetadata()) }
            } else {
                flowOf(emptyMap<Int, Int>() to emptyList<JuzSummary>())
            }
        }

    private val coreImportAndSurahsFlow = combine(
        observeQuranImportState(),
        queryFlow,
        retryTrigger,
    ) { importState, query, _ -> Triple(importState, query, retryTrigger.value) }
        .flatMapLatest { (importState, query, _) ->
            when (importState) {
                is QuranImportState.Completed ->
                    searchSurahs(query).combine(metadataFlow) { surahs, (pages, juzs) ->
                        QuranListUiState(
                            isLoading = false,
                            importProgress = null,
                            importError = null,
                            surahs = surahs,
                            surahStartPages = pages,
                            juzList = filterJuz(juzs, query),
                            query = query,
                        )
                    }

                is QuranImportState.Failed -> flowOf(
                    QuranListUiState(
                        isLoading = false,
                        importError = importState.reason,
                        query = query,
                    ),
                )

                is QuranImportState.Importing -> flowOf(
                    QuranListUiState(isLoading = true, importProgress = importState.progress),
                )

                QuranImportState.NotStarted -> flowOf(QuranListUiState(isLoading = true))
            }
        }

    val uiState: StateFlow<QuranListUiState> = combine(
        coreImportAndSurahsFlow,
        observeBookmarkedAyahs(),
    ) { base, allBookmarks ->
        base.copy(
            bookmarks = filterBookmarks(allBookmarks, base.query),
            totalBookmarkCount = allBookmarks.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuranListUiState(isLoading = true),
    )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    fun retryImport() {
        retryTrigger.value += 1
        viewModelScope.launch { importQuranIfNeeded() }
    }

    fun removeBookmark(surahId: Int, ayahNumber: Int) {
        lastRemovedBookmark = surahId to ayahNumber
        viewModelScope.launch {
            toggleBookmark(surahId, ayahNumber)
        }
    }

    fun undoLastRemove() {
        val target = lastRemovedBookmark ?: return
        lastRemovedBookmark = null
        viewModelScope.launch {
            toggleBookmark(target.first, target.second)
        }
    }

    private suspend fun loadMetadata(): Pair<Map<Int, Int>, List<JuzSummary>> {
        val existingPages = cachedStartPages
        val existingJuz = cachedJuzList
        if (existingPages != null && existingJuz != null) {
            return existingPages to existingJuz
        }
        val pages = buildMap {
            for (surahId in 1..TOTAL_SURAHS) {
                pageForSurahStart(surahId)?.let { put(surahId, it) }
            }
        }
        val rawJuz = JUZ_STARTS.map { (juz, surahId, ayahNumber) ->
            val ayah = getAyah(surahId, ayahNumber)
            val surah = getSurah(surahId)
            JuzSeed(
                juzNumber = juz,
                startSurahId = surahId,
                startAyahNumber = ayahNumber,
                startSurahNameAr = surah?.nameArabic ?: "",
                startSurahNameTransliterated = surah?.nameTransliterated ?: "Surah $surahId",
                startAyahText = ayah?.textUthmani ?: "",
                startPage = ayah?.page ?: 1,
            )
        }
        val juzList = rawJuz.mapIndexed { index, seed ->
            val nextStartPage = rawJuz.getOrNull(index + 1)?.startPage
            val endPage = if (nextStartPage != null) {
                (nextStartPage - 1).coerceAtLeast(seed.startPage)
            } else {
                TOTAL_MUSHAF_PAGES
            }
            JuzSummary(
                juzNumber = seed.juzNumber,
                startSurahId = seed.startSurahId,
                startAyahNumber = seed.startAyahNumber,
                startSurahNameAr = seed.startSurahNameAr,
                startSurahNameTransliterated = seed.startSurahNameTransliterated,
                startAyahText = seed.startAyahText,
                startPage = seed.startPage,
                endPage = endPage,
            )
        }
        if (pages.isNotEmpty()) cachedStartPages = pages
        if (juzList.isNotEmpty()) cachedJuzList = juzList
        return pages to juzList
    }

    private fun filterJuz(list: List<JuzSummary>, query: String): List<JuzSummary> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { juz ->
            juz.juzNumber.toString().contains(q) ||
                juz.startSurahNameAr.contains(q) ||
                juz.startSurahNameTransliterated.contains(q, ignoreCase = true) ||
                juz.startAyahText.contains(q)
        }
    }

    private fun filterBookmarks(list: List<BookmarkedAyah>, query: String): List<BookmarkedAyah> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { bm ->
            bm.surahNameArabic.contains(q) ||
                bm.surahNameTransliterated.contains(q, ignoreCase = true) ||
                bm.textUthmani.contains(q) ||
                "${bm.surahId}:${bm.ayahNumber}".contains(q)
        }
    }

    private data class JuzSeed(
        val juzNumber: Int,
        val startSurahId: Int,
        val startAyahNumber: Int,
        val startSurahNameAr: String,
        val startSurahNameTransliterated: String,
        val startAyahText: String,
        val startPage: Int,
    )

    companion object {
        private const val TOTAL_SURAHS = 114
        private const val TOTAL_MUSHAF_PAGES = 604

        val JUZ_STARTS: List<Triple<Int, Int, Int>> = listOf(
            Triple(1, 1, 1),
            Triple(2, 2, 142),
            Triple(3, 2, 253),
            Triple(4, 3, 93),
            Triple(5, 4, 24),
            Triple(6, 4, 148),
            Triple(7, 5, 82),
            Triple(8, 6, 111),
            Triple(9, 7, 88),
            Triple(10, 8, 41),
            Triple(11, 9, 93),
            Triple(12, 11, 6),
            Triple(13, 12, 53),
            Triple(14, 15, 1),
            Triple(15, 17, 1),
            Triple(16, 18, 75),
            Triple(17, 21, 1),
            Triple(18, 23, 1),
            Triple(19, 25, 21),
            Triple(20, 27, 56),
            Triple(21, 29, 46),
            Triple(22, 33, 31),
            Triple(23, 36, 28),
            Triple(24, 39, 32),
            Triple(25, 41, 47),
            Triple(26, 46, 1),
            Triple(27, 51, 31),
            Triple(28, 58, 1),
            Triple(29, 67, 1),
            Triple(30, 78, 1),
        )
    }
}
