package com.ayah.quran.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.usecase.GetAyahUseCase
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkStateUseCase
import com.ayah.quran.domain.usecase.ObserveSurahAyahsUseCase
import com.ayah.quran.domain.usecase.SaveReadingPositionUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One rendered ayah row, after mushaf display conventions are applied. */
data class ReaderAyah(
    val ayahNumber: Int,
    // Dataset text with the inline bismillah removed where the mushaf
    // convention displays it as a separate header. The stored text is never
    // modified — this is display-only.
    val displayText: String,
)

data class ReaderUiState(
    val surah: Surah? = null,
    val ayahs: List<ReaderAyah> = emptyList(),
    // The bismillah shown above the first ayah, or null when the surah has
    // none (Al-Fatiha has it as ayah 1; At-Tawbah has none).
    val bismillahHeader: String? = null,
    // Distinct juz numbers covered by the surah, in ascending order.
    val juzs: List<Int> = emptyList(),
    val firstPage: Int = 0,
    val lastPage: Int = 0,
) {
    val isLoading: Boolean get() = ayahs.isEmpty()
    val isNotFound: Boolean get() = ayahs.isNotEmpty() && surah == null
}

/**
 * Reader for one surah. Route args: `surahId` (required), `ayah`
 * (optional target ayah to scroll to).
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSurah: GetSurahUseCase,
    observeSurahAyahs: ObserveSurahAyahsUseCase,
    private val getAyah: GetAyahUseCase,
    private val observeBookmarkState: ObserveBookmarkStateUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val saveReadingPosition: SaveReadingPositionUseCase,
) : ViewModel() {

    val surahId: Int = checkNotNull(savedStateHandle[KEY_SURAH_ID])
    val targetAyah: Int = savedStateHandle[KEY_AYAH] ?: -1

    /** Emits whether a verse is bookmarked; collected per row by the UI. */
    fun observeIsBookmarked(ayahNumber: Int): Flow<Boolean> =
        observeBookmarkState(surahId, ayahNumber)

    fun toggleBookmark(ayahNumber: Int) {
        viewModelScope.launch { toggleBookmarkUseCase(surahId, ayahNumber) }
    }

    /** Called by the UI (debounced) while the user scrolls through the surah. */
    fun onVisibleAyahChanged(ayahNumber: Int) {
        viewModelScope.launch { saveReadingPosition(surahId, ayahNumber) }
    }

    val uiState: StateFlow<ReaderUiState> = observeSurahAyahs(surahId)
        .map { ayahs ->
            if (ayahs.isEmpty()) {
                ReaderUiState()
            } else {
                buildLoadedState(ayahs)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderUiState(),
        )

    private suspend fun buildLoadedState(ayahs: List<Ayah>): ReaderUiState {
        val (bismillahHeader, readerAyahs) = applyDisplayConventions(surahId, ayahs)
        return ReaderUiState(
            surah = getSurah(surahId),
            ayahs = readerAyahs,
            bismillahHeader = bismillahHeader,
            juzs = ayahs.map { it.juz }.distinct(),
            firstPage = ayahs.minOf { it.page },
            lastPage = ayahs.maxOf { it.page },
        )
    }

    /**
     * Mushaf display convention (documented by Tanzil's own rendering sample):
     * the Uthmani text file prefixes the bismillah to the first ayah of every
     * surah except Al-Fatiha (where it IS ayah 1) and At-Tawbah (where it is
     * absent). The reader shows it as a separate header line instead, using
     * the dataset text itself as the source — the standard form comes from
     * Al-Fatiha 1:1, and the shadda variant used in surahs 95 and 97 is
     * removed by the Tanzil four-word-prefix rule.
     */
    private suspend fun applyDisplayConventions(
        surahId: Int,
        ayahs: List<Ayah>,
    ): Pair<String?, List<ReaderAyah>> {
        val plain = ayahs.map { ReaderAyah(it.ayahNumber, it.textUthmani) }
        if (surahId == BISMILLAH_AS_FIRST_AYAH_SURAH || surahId == NO_BISMILLAH_SURAH) {
            return null to plain
        }

        val first = ayahs.first().textUthmani
        val standard = getAyah(BISMILLAH_AS_FIRST_AYAH_SURAH, 1)?.textUthmani
        if (standard != null && first.startsWith("$standard ")) {
            return standard to listOf(
                ReaderAyah(ayahs.first().ayahNumber, first.removePrefix("$standard ")),
            ) + plain.drop(1)
        }

        // Variant bismillah (surahs 95/97): the first four words are the
        // bismillah, per the dataset's documented convention.
        val words = first.split(' ')
        if (words.size > 4) {
            return words.take(4).joinToString(" ") to listOf(
                ReaderAyah(ayahs.first().ayahNumber, words.drop(4).joinToString(" ")),
            ) + plain.drop(1)
        }
        return null to plain
    }

    init {
        // Opening a surah counts as reading; the position is updated again as
        // the user scrolls (debounced by the screen).
        viewModelScope.launch {
            saveReadingPosition(surahId, targetAyah.coerceAtLeast(1))
        }
    }

    companion object {
        private const val KEY_SURAH_ID = "surahId"
        private const val KEY_AYAH = "ayah"

        /** Al-Fatiha: the bismillah is its first ayah, not a header. */
        const val BISMILLAH_AS_FIRST_AYAH_SURAH = 1

        /** At-Tawbah: the only surah without a bismillah. */
        const val NO_BISMILLAH_SURAH = 9
    }
}
