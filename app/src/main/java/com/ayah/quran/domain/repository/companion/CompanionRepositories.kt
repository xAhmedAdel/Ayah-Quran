package com.ayah.quran.domain.repository.companion

import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrDailyProgress
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.domain.model.companion.TasbihCounter
import kotlinx.coroutines.flow.Flow

interface AdhkarRepository {
    fun observeCategories(): Flow<List<DhikrCategory>>
    fun observeItemsByCategory(categoryId: String): Flow<List<DhikrItem>>
    fun observeDailyProgress(dayKey: String): Flow<List<DhikrDailyProgress>>
    fun observeCategoryProgress(dayKey: String, categoryId: String): Flow<List<DhikrDailyProgress>>
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeFavoriteItems(): Flow<List<DhikrItem>>
    fun observeQuranicDuas(): Flow<List<ResolvedQuranicDua>>
    suspend fun searchItems(query: String): List<DhikrItem>
    suspend fun incrementProgress(dayKey: String, itemId: String)
    suspend fun decrementProgress(dayKey: String, itemId: String)
    suspend fun resetCategoryProgress(dayKey: String, categoryId: String)
    suspend fun toggleFavorite(itemId: String)
}

interface TasbihRepository {
    fun observeCounters(): Flow<List<TasbihCounter>>
    fun observeDayTotal(dayKey: String): Flow<Int>
    suspend fun increment(counterId: Long, dayKey: String): Boolean
    suspend fun undo(counterId: Long, dayKey: String)
    suspend fun reset(counterId: Long)
    suspend fun createCustomCounter(titleAr: String, titleEn: String, dhikrTextAr: String, targetCount: Int): Long
    suspend fun updateCounter(counter: TasbihCounter)
    suspend fun deleteCustomCounter(counterId: Long)
}

interface HadithRepository {
    fun observeCollections(): Flow<List<HadithCollection>>
    fun observeByCollection(collectionId: String): Flow<List<HadithItem>>
    fun observeById(hadithId: String): Flow<HadithItem?>
    fun observeFavorites(): Flow<List<HadithItem>>
    fun observeRecentlyRead(limit: Int = 20): Flow<List<HadithItem>>
    suspend fun search(query: String, limit: Int = 50): List<HadithItem>
    suspend fun toggleFavorite(hadithId: String)
    suspend fun recordRead(hadithId: String)
    suspend fun dailyHadithId(epochDay: Long): String?
}
