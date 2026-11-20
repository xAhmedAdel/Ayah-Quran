package com.ayah.quran.ui.companion.duas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.DhikrSectionType
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.domain.repository.companion.AdhkarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DuaTab {
    QURANIC,
    PROPHETIC,
    SITUATIONAL,
}

data class DuasHubUiState(
    val loading: Boolean = true,
    val activeTab: DuaTab = DuaTab.QURANIC,
    val query: String = "",
    val savedOnly: Boolean = false,
    val selectedQuranicCategory: String? = null,
    val selectedSituationalCategory: String? = null,
    val quranicDuas: List<ResolvedQuranicDua> = emptyList(),
    val propheticCategories: List<DhikrCategory> = emptyList(),
    val situationalCategories: List<DhikrCategory> = emptyList(),
    val propheticItems: List<DhikrItem> = emptyList(),
    val situationalItems: List<DhikrItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DuasHubViewModel @Inject constructor(
    private val adhkarRepository: AdhkarRepository,
) : ViewModel() {

    private val activeTabFlow = MutableStateFlow(DuaTab.QURANIC)
    private val queryFlow = MutableStateFlow("")
    private val savedOnlyFlow = MutableStateFlow(false)
    private val selectedQuranicCategoryFlow = MutableStateFlow<String?>(null)
    private val selectedSituationalCategoryFlow = MutableStateFlow<String?>(null)

    private val duaCategoriesAndItemsFlow = adhkarRepository.observeCategories().flatMapLatest { categories ->
        val propheticCats = categories.filter { it.sectionType == DhikrSectionType.DUA_PROPHETIC }
        val situationalCats = categories.filter { it.sectionType == DhikrSectionType.DUA_SITUATION }
        val allCats = propheticCats + situationalCats
        if (allCats.isEmpty()) {
            flowOf(DuaCatalogSlice(propheticCats, situationalCats, emptyList(), emptyList()))
        } else {
            val flows = allCats.map { adhkarRepository.observeItemsByCategory(it.id) }
            combine(flows) { arrays ->
                val propItems = propheticCats.indices.flatMap { arrays[it] }
                val sitOffset = propheticCats.size
                val sitItems = situationalCats.indices.flatMap { arrays[sitOffset + it] }
                DuaCatalogSlice(propheticCats, situationalCats, propItems, sitItems)
            }
        }
    }

    val uiState: StateFlow<DuasHubUiState> = combine(
        combine(
            adhkarRepository.observeQuranicDuas(),
            duaCategoriesAndItemsFlow,
            adhkarRepository.observeFavoriteIds(),
        ) { quranic, catalog, favIds ->
            Triple(quranic, catalog, favIds)
        },
        combine(
            activeTabFlow,
            queryFlow,
            savedOnlyFlow,
            selectedQuranicCategoryFlow,
            selectedSituationalCategoryFlow,
        ) { tab, q, saved, qCat, sCat ->
            DuaFilterSlice(tab, q, saved, qCat, sCat)
        },
    ) { (quranic, catalog, favIds), filter ->
        val normalizedQuery = ArabicTextNormalizer.normalize(filter.query)

        val filteredQuranic = quranic.filter { entry ->
            val catOk = filter.selectedQuranicCategory == null ||
                entry.pointer.categoryId == filter.selectedQuranicCategory
            val queryOk = normalizedQuery.isEmpty() || matchesQuery(
                normalizedQuery,
                entry.pointer.titleAr,
                entry.pointer.titleEn,
                entry.textUthmani,
                entry.surahNameArabic,
                entry.surahNameEnglish,
            )
            catOk && queryOk
        }

        val filteredProphetic = catalog.propheticItems.filter { item ->
            val favOk = !filter.savedOnly || item.id in favIds
            val queryOk = normalizedQuery.isEmpty() || matchesQuery(
                normalizedQuery,
                item.titleAr,
                item.titleEn,
                item.textAr,
                item.textEn,
                item.sourceAr,
            )
            favOk && queryOk
        }

        val filteredSituational = catalog.situationalItems.filter { item ->
            val catOk = filter.selectedSituationalCategory == null ||
                item.categoryId == filter.selectedSituationalCategory
            val favOk = !filter.savedOnly || item.id in favIds
            val queryOk = normalizedQuery.isEmpty() || matchesQuery(
                normalizedQuery,
                item.titleAr,
                item.titleEn,
                item.textAr,
                item.textEn,
                item.sourceAr,
            )
            catOk && favOk && queryOk
        }

        DuasHubUiState(
            loading = quranic.isEmpty() && catalog.propheticItems.isEmpty() && catalog.situationalItems.isEmpty(),
            activeTab = filter.tab,
            query = filter.query,
            savedOnly = filter.savedOnly,
            selectedQuranicCategory = filter.selectedQuranicCategory,
            selectedSituationalCategory = filter.selectedSituationalCategory,
            quranicDuas = filteredQuranic,
            propheticCategories = catalog.propheticCategories,
            situationalCategories = catalog.situationalCategories,
            propheticItems = filteredProphetic,
            situationalItems = filteredSituational,
            favoriteIds = favIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DuasHubUiState(),
    )

    fun selectTab(tab: DuaTab) {
        activeTabFlow.value = tab
    }

    fun onQueryChange(value: String) {
        queryFlow.value = value
    }

    fun toggleSavedFilter() {
        savedOnlyFlow.value = !savedOnlyFlow.value
    }

    fun selectQuranicCategory(categoryId: String?) {
        selectedQuranicCategoryFlow.value = categoryId
    }

    fun selectSituationalCategory(categoryId: String?) {
        selectedSituationalCategoryFlow.value = categoryId
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            adhkarRepository.toggleFavorite(itemId)
        }
    }

    private fun matchesQuery(normalizedQuery: String, vararg fields: String?): Boolean =
        fields.any { field ->
            !field.isNullOrBlank() && ArabicTextNormalizer.normalize(field).contains(normalizedQuery)
        }
}

private data class DuaCatalogSlice(
    val propheticCategories: List<DhikrCategory>,
    val situationalCategories: List<DhikrCategory>,
    val propheticItems: List<DhikrItem>,
    val situationalItems: List<DhikrItem>,
)

private data class DuaFilterSlice(
    val tab: DuaTab,
    val query: String,
    val savedOnly: Boolean,
    val selectedQuranicCategory: String?,
    val selectedSituationalCategory: String?,
)
