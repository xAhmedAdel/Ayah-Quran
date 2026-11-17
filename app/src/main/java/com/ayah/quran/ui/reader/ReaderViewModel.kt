package com.ayah.quran.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.usecase.GetAyahUseCase
import com.ayah.quran.domain.usecase.GetSurahUseCase
import com.ayah.quran.domain.usecase.ObserveBookmarkStateUseCase
import com.ayah.quran.domain.usecase.ObserveSurahAyahsUseCase
import com.ayah.quran.domain.usecase.SaveReadingPositionUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One ayah for the mushaf text builder. */
data class ReaderAyah(
    val ayahNumber: Int,
    val displayText: String,
)

data class ReaderUiState(
    val surah: Surah? = null,
    val ayahs: List<ReaderAyah> = emptyList(),
    val bismillahHeader: String? = null,
    val juzs: List<Int> = emptyList(),
    val firstPage: Int = 0,
    val lastPage: Int = 0,
    val fontScale: Float = 1.0f,
    val selectedAyah: Int? = null,
) {
    val isLoading: Boolean get() = ayahs.isEmpty()
    val isNotFound: Boolean get() = ayahs.isNotEmpty() && surah == null
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSurah: GetSurahUseCase,
    observeSurahAyahs: ObserveSurahAyahsUseCase,
    private val getAyah: GetAyahUseCase,
    private val observeBookmarkState: ObserveBookmarkStateUseCase,
    private val toggleBookmarkUseCase: ToggleBookmarkUseCase,
    private val saveReadingPosition: SaveReadingPositionUseCase,
    private val readerPrefs: ReaderPreferencesRepository,
) : ViewModel() {

    val surahId: Int = checkNotNull(savedStateHandle[KEY_SURAH_ID])
    val targetAyah: Int = savedStateHandle[KEY_AYAH] ?: -1

    private val selectedAyah = MutableStateFlow<Int?>(null)

    fun observeIsBookmarked(ayahNumber: Int): Flow<Boolean> =
        observeBookmarkState(surahId, ayahNumber)

    fun toggleBookmark(ayahNumber: Int) {
        viewModelScope.launch { toggleBookmarkUseCase(surahId, ayahNumber) }
    }

    fun onAyahTap(ayahNumber: Int) {
        selectedAyah.value = if (selectedAyah.value == ayahNumber) null else ayahNumber
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            readerPrefs.update { it.copy(fontScale = scale.coerceIn(0.5f, 2.0f)) }
        }
    }

    fun onVisibleAyahChanged(ayahNumber: Int) {
        viewModelScope.launch { saveReadingPosition(surahId, ayahNumber) }
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        observeSurahAyahs(surahId),
        readerPrefs.settings,
        selectedAyah,
    ) { ayahs, prefs, sel ->
        if (ayahs.isEmpty()) {
            ReaderUiState(fontScale = prefs.fontScale)
        } else {
            buildLoadedState(ayahs, prefs.fontScale, sel)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReaderUiState(),
    )

    private suspend fun buildLoadedState(
        ayahs: List<Ayah>,
        fontScale: Float,
        selected: Int?,
    ): ReaderUiState {
        val (bismillahHeader, readerAyahs) = applyDisplayConventions(surahId, ayahs)
        return ReaderUiState(
            surah = getSurah(surahId),
            ayahs = readerAyahs,
            bismillahHeader = bismillahHeader,
            juzs = ayahs.map { it.juz }.distinct(),
            firstPage = ayahs.minOf { it.page },
            lastPage = ayahs.maxOf { it.page },
            fontScale = fontScale,
            selectedAyah = selected,
        )
    }

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

        val words = first.split(' ')
        if (words.size > 4) {
            return words.take(4).joinToString(" ") to listOf(
                ReaderAyah(ayahs.first().ayahNumber, words.drop(4).joinToString(" ")),
            ) + plain.drop(1)
        }
        return null to plain
    }

    init {
        viewModelScope.launch {
            saveReadingPosition(surahId, targetAyah.coerceAtLeast(1))
        }
    }

    companion object {
        private const val KEY_SURAH_ID = "surahId"
        private const val KEY_AYAH = "ayah"
        const val BISMILLAH_AS_FIRST_AYAH_SURAH = 1
        const val NO_BISMILLAH_SURAH = 9
    }
}
