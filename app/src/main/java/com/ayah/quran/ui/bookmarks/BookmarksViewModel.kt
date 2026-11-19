package com.ayah.quran.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.usecase.ObserveBookmarkedAyahsUseCase
import com.ayah.quran.domain.usecase.ToggleBookmarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val isLoading: Boolean = true,
    val bookmarks: List<BookmarkedAyah> = emptyList(),
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    observeBookmarkedAyahs: ObserveBookmarkedAyahsUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
) : ViewModel() {

    private var lastRemoved: Pair<Int, Int>? = null

    val uiState: StateFlow<BookmarksUiState> = observeBookmarkedAyahs()
        .map { BookmarksUiState(isLoading = false, bookmarks = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BookmarksUiState(),
        )

    fun removeBookmark(surahId: Int, ayahNumber: Int) {
        lastRemoved = surahId to ayahNumber
        viewModelScope.launch {
            toggleBookmark(surahId, ayahNumber)
        }
    }

    fun undoLastRemove() {
        val target = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch {
            toggleBookmark(target.first, target.second)
        }
    }
}
