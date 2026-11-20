package com.ayah.quran.ui.companion.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.core.time.CompanionDateProvider
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrDailyProgress
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.DhikrSectionType
import com.ayah.quran.domain.repository.companion.AdhkarRepository
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

data class AdhkarCategorySummary(
    val category: DhikrCategory,
    val totalItems: Int,
    val completedItems: Int,
)

data class AdhkarHubUiState(
    val loading: Boolean = true,
    val query: String = "",
    val savedOnly: Boolean = false,
    val recommendedSession: AdhkarCategorySummary? = null,
    val dailySessions: List<AdhkarCategorySummary> = emptyList(),
    val generalCategories: List<AdhkarCategorySummary> = emptyList(),
    val sunnahSurahs: List<DhikrItem> = emptyList(),
    val searchResults: List<DhikrItem> = emptyList(),
    val favoriteItems: List<DhikrItem> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AdhkarHubViewModel @Inject constructor(
    private val adhkarRepository: AdhkarRepository,
    private val dateProvider: CompanionDateProvider,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val savedOnly = MutableStateFlow(false)
    private val dayKey = MutableStateFlow(dateProvider.todayKey())

    private val searchResultsFlow = query.flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else flow { emit(adhkarRepository.searchItems(q)) }
    }

    private val categoryItemCountsFlow = adhkarRepository.observeCategories().flatMapLatest { categories ->
        if (categories.isEmpty()) {
            flowOf(emptyMap())
        } else {
            val flows = categories.map { cat ->
                adhkarRepository.observeItemsByCategory(cat.id)
            }
            combine(flows) { arrays ->
                categories.indices.associate { idx -> categories[idx].id to arrays[idx].size }
            }
        }
    }

    private val progressFlow = dayKey.flatMapLatest { key ->
        adhkarRepository.observeDailyProgress(key)
    }

    val uiState: StateFlow<AdhkarHubUiState> = combine(
        combine(
            adhkarRepository.observeCategories(),
            categoryItemCountsFlow,
            progressFlow,
            adhkarRepository.observeItemsByCategory(DAILY_SURAHS_CATEGORY_ID),
        ) { categories, itemCounts, progress, sunnahSurahs ->
            HubDataSlice(categories, itemCounts, progress, sunnahSurahs)
        },
        combine(
            query,
            savedOnly,
            searchResultsFlow,
            adhkarRepository.observeFavoriteItems(),
            adhkarRepository.observeFavoriteIds(),
        ) { q, saved, search, favItems, favIds ->
            HubFilterSlice(q, saved, search, favItems, favIds)
        },
    ) { data, filter ->
        val completedByCat = data.progress
            .filter { it.completed }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.size }

        val summaries = data.categories.map { cat ->
            val total = data.itemCounts[cat.id] ?: 0
            val done = (completedByCat[cat.id] ?: 0).coerceAtMost(total)
            AdhkarCategorySummary(category = cat, totalItems = total, completedItems = done)
        }

        val dailySessions = summaries.filter { it.category.sectionType == DhikrSectionType.ADHKAR_SESSION }
        val generalCategories = summaries.filter { it.category.sectionType == DhikrSectionType.ADHKAR_GENERAL }
        val recommendedId = recommendedCategoryId(dateProvider.currentHour())
        val recommended = dailySessions.firstOrNull { it.category.id == recommendedId }
            ?: dailySessions.firstOrNull()

        AdhkarHubUiState(
            loading = data.categories.isEmpty(),
            query = filter.query,
            savedOnly = filter.savedOnly,
            recommendedSession = recommended,
            dailySessions = dailySessions,
            generalCategories = generalCategories,
            sunnahSurahs = data.sunnahSurahs,
            searchResults = filter.searchResults,
            favoriteItems = filter.favoriteItems,
            favoriteIds = filter.favoriteIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdhkarHubUiState(),
    )

    fun refreshDayKey() {
        val current = dateProvider.todayKey()
        if (dayKey.value != current) {
            dayKey.value = current
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleSavedFilter() {
        savedOnly.value = !savedOnly.value
    }

    fun toggleFavorite(itemId: String) {
        viewModelScope.launch {
            adhkarRepository.toggleFavorite(itemId)
        }
    }

    companion object {
        const val DAILY_SURAHS_CATEGORY_ID = "daily_surahs"

        internal fun recommendedCategoryId(hourOfDay: Int): String = when (hourOfDay) {
            in 4..11 -> "morning"
            in 15..20 -> "evening"
            in 21..23, in 0..3 -> "sleep"
            else -> "after_prayer"
        }
    }
}

private data class HubDataSlice(
    val categories: List<DhikrCategory>,
    val itemCounts: Map<String, Int>,
    val progress: List<DhikrDailyProgress>,
    val sunnahSurahs: List<DhikrItem>,
)

private data class HubFilterSlice(
    val query: String,
    val savedOnly: Boolean,
    val searchResults: List<DhikrItem>,
    val favoriteItems: List<DhikrItem>,
    val favoriteIds: Set<String>,
)
