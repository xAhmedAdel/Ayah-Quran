package com.ayah.quran.core.database.dao.companion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ayah.quran.core.database.entity.companion.DhikrCategoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrDailyProgressEntity
import com.ayah.quran.core.database.entity.companion.DhikrFavoriteEntity
import com.ayah.quran.core.database.entity.companion.DhikrItemEntity
import com.ayah.quran.core.database.entity.companion.QuranicDuaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdhkarDao {

    @Query("SELECT * FROM dhikr_categories ORDER BY sortOrder ASC")
    fun observeCategories(): Flow<List<DhikrCategoryEntity>>

    @Query("SELECT * FROM dhikr_items WHERE categoryId = :categoryId ORDER BY sortOrder ASC")
    fun observeItemsByCategory(categoryId: String): Flow<List<DhikrItemEntity>>

    @Query("SELECT * FROM dhikr_items WHERE id = :id")
    suspend fun getItemById(id: String): DhikrItemEntity?

    @Query(
        "SELECT * FROM dhikr_items " +
            "WHERE searchTextNormalized LIKE '%' || :normalizedQuery || '%' " +
            "ORDER BY categoryId ASC, sortOrder ASC LIMIT :limit",
    )
    suspend fun searchItems(normalizedQuery: String, limit: Int = 50): List<DhikrItemEntity>

    @Query("SELECT * FROM quranic_duas ORDER BY sortOrder ASC")
    fun observeQuranicDuas(): Flow<List<QuranicDuaEntity>>

    @Query(
        "SELECT * FROM quranic_duas " +
            "WHERE searchTextNormalized LIKE '%' || :normalizedQuery || '%' " +
            "ORDER BY sortOrder ASC",
    )
    suspend fun searchQuranicDuas(normalizedQuery: String): List<QuranicDuaEntity>

    @Query("SELECT * FROM dhikr_daily_progress WHERE dayKey = :dayKey")
    fun observeDailyProgress(dayKey: String): Flow<List<DhikrDailyProgressEntity>>

    @Query("SELECT * FROM dhikr_daily_progress WHERE dayKey = :dayKey AND categoryId = :categoryId")
    fun observeCategoryProgress(dayKey: String, categoryId: String): Flow<List<DhikrDailyProgressEntity>>

    @Query("SELECT * FROM dhikr_daily_progress WHERE dayKey = :dayKey AND itemId = :itemId")
    suspend fun getProgress(dayKey: String, itemId: String): DhikrDailyProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: DhikrDailyProgressEntity)

    @Query("DELETE FROM dhikr_daily_progress WHERE dayKey = :dayKey AND categoryId = :categoryId")
    suspend fun resetCategoryProgress(dayKey: String, categoryId: String)

    @Query("DELETE FROM dhikr_daily_progress WHERE dayKey < :minKeepDayKey")
    suspend fun pruneOldProgress(minKeepDayKey: String)

    @Query("SELECT itemId FROM dhikr_favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query(
        "SELECT i.* FROM dhikr_items i " +
            "INNER JOIN dhikr_favorites f ON i.id = f.itemId " +
            "ORDER BY f.createdAtMillis DESC",
    )
    fun observeFavoriteItems(): Flow<List<DhikrItemEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM dhikr_favorites WHERE itemId = :itemId)")
    suspend fun isFavorite(itemId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: DhikrFavoriteEntity)

    @Query("DELETE FROM dhikr_favorites WHERE itemId = :itemId")
    suspend fun deleteFavorite(itemId: String)

    @Upsert
    suspend fun upsertCategories(categories: List<DhikrCategoryEntity>)

    @Upsert
    suspend fun upsertItems(items: List<DhikrItemEntity>)

    @Upsert
    suspend fun upsertQuranicDuas(duas: List<QuranicDuaEntity>)

    @Query("SELECT COUNT(*) FROM dhikr_categories")
    suspend fun categoryCount(): Int

    @Query("SELECT COUNT(*) FROM dhikr_items")
    suspend fun itemCount(): Int

    @Query("SELECT COUNT(*) FROM quranic_duas")
    suspend fun quranicDuaCount(): Int
}
