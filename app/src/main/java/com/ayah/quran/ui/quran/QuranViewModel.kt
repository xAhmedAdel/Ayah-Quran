package com.ayah.quran.ui.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.usecase.ImportQuranIfNeededUseCase
import com.ayah.quran.domain.usecase.ObserveQuranImportStateUseCase
import com.ayah.quran.domain.usecase.SearchSurahsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable UI state of the Surah list screen. */
data class QuranListUiState(
    val isLoading: Boolean = true,
    // Non-null while the dataset import is running.
    val importProgress: Float? = null,
    // Non-null when the import failed; the list stays empty and honest.
    val importError: String? = null,
    val surahs: List<Surah> = emptyList(),
    val query: String = "",
) {
    val isSearchResultsEmpty: Boolean get() = query.isNotBlank() && surahs.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class QuranViewModel @Inject constructor(
    observeQuranImportState: ObserveQuranImportStateUseCase,
    private val searchSurahs: SearchSurahsUseCase,
    private val importQuranIfNeeded: ImportQuranIfNeededUseCase,
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<QuranListUiState> =
        combine(
            observeQuranImportState(),
            queryFlow,
            // Re-run the pipeline when the user explicitly retries a failed import.
            retryTrigger,
        ) { importState, query, _ -> Triple(importState, query, retryTrigger.value) }
            .flatMapLatest { (importState, query, _) ->
                when (importState) {
                    is QuranImportState.Completed ->
                        searchSurahs(query).map { surahs ->
                            QuranListUiState(
                                isLoading = false,
                                importProgress = null,
                                importError = null,
                                surahs = surahs,
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
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = QuranListUiState(isLoading = true),
            )

    fun onQueryChange(query: String) {
        queryFlow.value = query
    }

    /** Retries the import after a failure (the importer re-validates from scratch). */
    fun retryImport() {
        retryTrigger.value += 1
        viewModelScope.launch { importQuranIfNeeded() }
    }
}
