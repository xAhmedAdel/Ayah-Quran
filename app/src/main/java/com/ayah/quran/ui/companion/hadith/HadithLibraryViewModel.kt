package com.ayah.quran.ui.companion.hadith

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.companion.CompanionContentImporter
import com.ayah.quran.domain.model.companion.CompanionImportState
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import com.ayah.quran.domain.repository.companion.HadithRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HadithLibraryUiState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val collections: List<HadithCollection> = emptyList(),
    val collectionsById: Map<String, HadithCollection> = emptyMap(),
    val selectedCollectionId: String? = null,
    val favoritesOnly: Boolean = false,
    val query: String = "",
    val items: List<HadithItem> = emptyList(),
    val isSearching: Boolean = false,
)

private data class HadithFilterSlice(
    val selectedCollectionId: String?,
    val favoritesOnly: Boolean,
    val query: String,
)

private data class HadithListSlice(
    val items: List<HadithItem>,
    val isSearching: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HadithLibraryViewModel internal constructor(
    private val hadithRepository: HadithRepository,
    importStateFlow: StateFlow<CompanionImportState>,
    private val onRetryImport: () -> Unit,
    searchDebounceMs: Long,
) : ViewModel() {

    @Inject
    constructor(
        hadithRepository: HadithRepository,
        importer: CompanionContentImporter,
    ) : this(
        hadithRepository = hadithRepository,
        importStateFlow = importer.importState,
        onRetryImport = importer::startAsync,
        searchDebounceMs = DEFAULT_SEARCH_DEBOUNCE_MS,
    )

    private val queryFlow = MutableStateFlow("")
    private val selectedCollectionIdFlow = MutableStateFlow<String?>(null)
    private val favoritesOnlyFlow = MutableStateFlow(false)

    private val favoriteIdsFlow: Flow<Set<String>> = hadithRepository.observeFavorites()
        .map { list -> list.mapTo(mutableSetOf()) { it.id } }

    private val debouncedQueryFlow: Flow<String> = if (searchDebounceMs > 0L) {
        queryFlow.debounce(searchDebounceMs).distinctUntilChanged()
    } else {
        queryFlow
    }

    private val activeListFlow: Flow<HadithListSlice> = combine(
        selectedCollectionIdFlow,
        favoritesOnlyFlow,
        debouncedQueryFlow,
    ) { collectionId, favOnly, query ->
        HadithFilterSlice(collectionId, favOnly, query.trim())
    }.flatMapLatest { filter ->
        when {
            filter.query.isNotEmpty() -> {
                flow {
                    emit(HadithListSlice(emptyList(), isSearching = true))
                    val hits = hadithRepository.search(filter.query, limit = SEARCH_LIMIT)
                    val filtered = hits.filter { item ->
                        filter.selectedCollectionId == null || item.collectionId == filter.selectedCollectionId
                    }
                    emit(HadithListSlice(filtered, isSearching = false))
                }.combine(favoriteIdsFlow) { slice, favIds ->
                    val synced = slice.items.map { item ->
                        item.copy(isFavorite = item.id in favIds)
                    }
                    val finalItems = if (filter.favoritesOnly) synced.filter { it.isFavorite } else synced
                    slice.copy(items = finalItems)
                }
            }

            filter.favoritesOnly -> {
                hadithRepository.observeFavorites().map { favs ->
                    val filtered = if (filter.selectedCollectionId == null) {
                        favs
                    } else {
                        favs.filter { it.collectionId == filter.selectedCollectionId }
                    }
                    HadithListSlice(filtered, isSearching = false)
                }
            }

            filter.selectedCollectionId != null -> {
                hadithRepository.observeByCollection(filter.selectedCollectionId).map { list ->
                    HadithListSlice(list, isSearching = false)
                }
            }

            else -> {
                hadithRepository.observeAll().map { list ->
                    HadithListSlice(list, isSearching = false)
                }
            }
        }
    }

    val uiState: StateFlow<HadithLibraryUiState> = combine(
        hadithRepository.observeCollections(),
        activeListFlow,
        combine(
            selectedCollectionIdFlow,
            favoritesOnlyFlow,
            queryFlow,
            importStateFlow,
        ) { colId, favOnly, rawQuery, importState ->
            HadithControlsSlice(colId, favOnly, rawQuery, importState)
        },
    ) { collections, listSlice, controls ->
        val importFailed = controls.importState as? CompanionImportState.Failed
        val isImporting = controls.importState is CompanionImportState.Importing ||
            controls.importState is CompanionImportState.NotStarted
        val loading = collections.isEmpty() && isImporting && importFailed == null
        HadithLibraryUiState(
            loading = loading,
            errorMessage = if (collections.isEmpty()) importFailed?.reason else null,
            collections = collections,
            collectionsById = collections.associateBy { it.id },
            selectedCollectionId = controls.selectedCollectionId,
            favoritesOnly = controls.favoritesOnly,
            query = controls.rawQuery,
            items = listSlice.items,
            isSearching = listSlice.isSearching,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HadithLibraryUiState(),
    )

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    fun clearQuery() {
        queryFlow.value = ""
    }

    fun selectCollection(collectionId: String?) {
        selectedCollectionIdFlow.value = collectionId
    }

    fun toggleFavoritesOnly() {
        favoritesOnlyFlow.value = !favoritesOnlyFlow.value
    }

    fun toggleFavorite(hadithId: String) {
        viewModelScope.launch {
            hadithRepository.toggleFavorite(hadithId)
        }
    }

    fun retryImport() {
        onRetryImport()
    }

    companion object {
        const val DEFAULT_SEARCH_DEBOUNCE_MS = 150L
        const val SEARCH_LIMIT = 100
    }
}

private data class HadithControlsSlice(
    val selectedCollectionId: String?,
    val favoritesOnly: Boolean,
    val rawQuery: String,
    val importState: CompanionImportState,
)
