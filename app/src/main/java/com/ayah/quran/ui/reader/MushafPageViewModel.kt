package com.ayah.quran.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.MushafPage
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.usecase.GetMushafPageUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkStateUseCase
import com.ayah.quran.domain.usecase.PageForAyahUseCase
import com.ayah.quran.domain.usecase.SaveReadingPositionUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backing state for the authentic 604-page mushaf reader.
 *
 * Pages are loaded on demand (current page plus a small neighborhood) from
 * the verified page mapping and cached in memory; the full mushaf is never
 * held at once.
 */
@HiltViewModel
class MushafPageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMushafPage: GetMushafPageUseCase,
    private val pageForAyah: PageForAyahUseCase,
    private val readerPrefs: ReaderPreferencesRepository,
    private val saveReadingPosition: SaveReadingPositionUseCase,
    private val observeBookmarkState: ObserveBookmarkStateUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val ayahPlayer: ExoPlayerAyahPlaylistPlayer,
) : ViewModel() {

    private val argSurahId: Int = savedStateHandle[KEY_SURAH_ID] ?: 0
    private val argAyah: Int = savedStateHandle[KEY_AYAH] ?: 0
    private val argPage: Int = savedStateHandle[KEY_PAGE] ?: 0

    /** (surahId, ayahNumber) to highlight on open, if navigation carried one. */
    private val _highlight = MutableStateFlow<Pair<Int, Int>?>(
        if (argSurahId > 0 && argAyah > 0) argSurahId to argAyah else null,
    )
    val highlight: StateFlow<Pair<Int, Int>?> = combine(
        _highlight,
        ayahPlayer.state,
    ) { userSelected, playback ->
        // Live recitation highlight wins when the user has not tapped a
        // specific ayah — the reader follows the recited ayah on this page.
        userSelected ?: playback.let {
            if (it.isIdle) null else it.surah to (it.ayahIndex + 1)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        _highlight.value,
    )

    private val _pages = MutableStateFlow<Map<Int, MushafPage>>(emptyMap())
    val pages: StateFlow<Map<Int, MushafPage>> = _pages.asStateFlow()

    /** Settled page whose first ayah was last persisted (dedupe). */
    private var lastSavedPage: Int = -1

    /** The page to open first: explicit page arg, else resolved from the ayah. */
    val startPage: StateFlow<Int> = flow {
        emit(
            argPage.takeIf { it in GetMushafPageUseCase.FIRST_PAGE..GetMushafPageUseCase.LAST_PAGE }
                ?: argSurahId.takeIf { it > 0 }?.let { surahId ->
                    pageForAyah(surahId, argAyah.coerceAtLeast(1))
                }
                ?: GetMushafPageUseCase.FIRST_PAGE,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        // 0 = still resolving from surah/ayah args; the screen waits for a
        // real page before jumping (never consumes a placeholder jump).
        initialValue = argPage,
    )

    val fontScale: StateFlow<Float> = readerPrefs.settings
        .map { it.fontScale }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 1.0f,
        )

    val readingMode: StateFlow<ReadingMode> = readerPrefs.settings
        .map { it.readingMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReadingMode.MUSHAF,
        )

    fun page(pageNumber: Int): MushafPage? = _pages.value[pageNumber]

    // ---- slice 3: automatic page navigation during recitation ----

    /** (surahId, ayahInSurah) currently recited; null when idle. */
    val recitedAyah: StateFlow<Pair<Int, Int>?> = ayahPlayer.state
        .map { st -> if (st.isIdle) null else st.surah to (st.ayahIndex + 1) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * The user's live ayah selection (tap) — their reading intent overrides
     * following WHILE IT LASTS. It expires when playback advances to a
     * different ayah (stale selection), so following resumes exactly when
     * the recitation moves on. The navigation-arg highlight (e.g. "continue
     * reading") is display-only and never blocks recitation following.
     */
    private val _userSelection = MutableStateFlow<Pair<Int, Int>?>(null)
    val userSelectionActive: StateFlow<Boolean> = _userSelection
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** Last ayah this policy navigated for (anti-loop / manual-nav respect). */
    private var lastFollowedAyah: Pair<Int, Int>? = null

    /**
     * Evaluates the follow policy against the DISPLAYED page; returns the
     * target page to navigate to, or null to stay put. One navigation per
     * recited ayah — manual browsing is never yanked back (see
     * [MushafFollowPolicy]).
     */
    suspend fun evaluateRecitationFollow(displayedPage: Int?): Int? {
        val recited = recitedAyah.value ?: return null
        // A user selection EXPIRES when playback moves to another ayah:
        // their intent applied to the ayah they tapped, and following
        // resumes exactly when the recitation advances (spec §4).
        if (_userSelection.value != null && _userSelection.value != recited) {
            _userSelection.value = null
        }
        val targetPage = pageForAyah(recited.first, recited.second) ?: return null
        val decision = MushafFollowPolicy.shouldNavigate(
            MushafFollowPolicy.Inputs(
                displayedPage = displayedPage,
                recitedPage = targetPage,
                recitedAyah = MushafFollowPolicy.RecitedAyah(
                    recited.first, recited.second,
                ),
                lastFollowedAyah = lastFollowedAyah,
                userSelectionActive = userSelectionActive.value,
            ),
        )
        if (decision) {
            lastFollowedAyah = recited
            return targetPage
        }
        // Settled on the recited page (by hand or by us): adopt the key so
        // browsing away later is not immediately undone for the SAME ayah.
        if (targetPage == displayedPage) lastFollowedAyah = recited
        return null
    }

    /** Loads [pageNumber] and its near neighbors; cheap and idempotent. */
    fun ensureLoaded(pageNumber: Int) {
        viewModelScope.launch {
            val range = (pageNumber - NEIGHBOR_PAGES)..(pageNumber + NEIGHBOR_PAGES)
            range.filter { it in GetMushafPageUseCase.FIRST_PAGE..GetMushafPageUseCase.LAST_PAGE }
                .filter { it !in _pages.value }
                .forEach { candidate ->
                    getMushafPage(candidate)?.let { loaded ->
                        _pages.update { it + (loaded.pageNumber to loaded) }
                    }
                }
        }
    }

    /** Persists the reading position as the first ayah of the settled page. */
    fun onPageSettled(pageNumber: Int) {
        val first = _pages.value[pageNumber]?.firstAyah ?: return
        if (pageNumber == lastSavedPage) return
        lastSavedPage = pageNumber
        viewModelScope.launch {
            saveReadingPosition(first.surahId, first.ayahNumber)
        }
    }

    /** Start recitation from the highlighted ayah (Phase 17 slice 2). */
    fun playFromAyah(surahId: Int, ayahNumber: Int) {
        val edition = QuranAudioCatalog.byEdition(
            ayahPlayer.state.value.editionId,
        )
        ayahPlayer.play(edition, surahId, ayahNumber)
    }

    fun onAyahTap(surahId: Int, ayahNumber: Int) {
        val tapped = surahId to ayahNumber
        _highlight.value = if (_highlight.value == tapped) null else tapped
        _userSelection.value = if (_userSelection.value == tapped) null else tapped
    }

    fun observeIsBookmarked(): Flow<Boolean> {
        val target = highlight.value
        return if (target != null) {
            observeBookmarkState(target.first, target.second)
        } else {
            kotlinx.coroutines.flow.flowOf(false)
        }
    }

    fun toggleBookmark() {
        val target = highlight.value ?: return
        viewModelScope.launch { toggleBookmarkUseCase(target.first, target.second) }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            readerPrefs.update { it.copy(readingMode = mode) }
        }
    }

    private companion object {
        const val KEY_SURAH_ID = "surahId"
        const val KEY_AYAH = "ayah"
        const val KEY_PAGE = "page"
        const val NEIGHBOR_PAGES = 2
    }
}
