package com.ayah.quran.ui.companion.hadith

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import com.ayah.quran.domain.repository.companion.HadithRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HadithDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val hadith: HadithItem? = null,
    val collection: HadithCollection? = null,
    val fontScale: Float = 1.0f,
)

@HiltViewModel
class HadithDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val hadithRepository: HadithRepository,
) : ViewModel() {

    val hadithId: String = checkNotNull(savedStateHandle["hadithId"])

    private val fontScaleFlow = MutableStateFlow(1.0f)

    init {
        viewModelScope.launch {
            hadithRepository.recordRead(hadithId)
        }
    }

    val uiState: StateFlow<HadithDetailUiState> = combine(
        hadithRepository.observeById(hadithId),
        hadithRepository.observeCollections(),
        fontScaleFlow,
    ) { hadith, collections, fontScale ->
        if (hadith == null) {
            HadithDetailUiState(
                loading = false,
                notFound = true,
                hadith = null,
                collection = null,
                fontScale = fontScale,
            )
        } else {
            HadithDetailUiState(
                loading = false,
                notFound = false,
                hadith = hadith,
                collection = collections.firstOrNull { it.id == hadith.collectionId },
                fontScale = fontScale,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HadithDetailUiState(),
    )

    fun toggleFavorite() {
        viewModelScope.launch {
            hadithRepository.toggleFavorite(hadithId)
        }
    }

    fun adjustFontScale(delta: Float) {
        fontScaleFlow.value = (fontScaleFlow.value + delta).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.4f
    }
}
