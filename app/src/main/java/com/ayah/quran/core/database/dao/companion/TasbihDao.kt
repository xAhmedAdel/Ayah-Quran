package com.ayah.quran.core.database.dao.companion

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import com.ayah.quran.core.database.entity.companion.TasbihDailyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TasbihDao {

    @Query("SELECT * FROM tasbih_counters ORDER BY sortOrder ASC, id ASC")
    fun observeCounters(): Flow<List<TasbihCounterEntity>>

    @Query("SELECT * FROM tasbih_counters WHERE id = :id")
    suspend fun getById(id: Long): TasbihCounterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeedCounters(counters: List<TasbihCounterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCounter(counter: TasbihCounterEntity): Long

    @Upsert
    suspend fun upsertCounter(counter: TasbihCounterEntity)

    @Query("DELETE FROM tasbih_counters WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomCounter(id: Long)

    @Query("SELECT COUNT(*) FROM tasbih_counters")
    suspend fun counterCount(): Int

    @Query("SELECT * FROM tasbih_daily_log WHERE dayKey = :dayKey AND counterId = :counterId")
    suspend fun getDailyLog(dayKey: String, counterId: Long): TasbihDailyLogEntity?

    @Upsert
    suspend fun upsertDailyLog(log: TasbihDailyLogEntity)

    @Query("SELECT COALESCE(SUM(countTotal), 0) FROM tasbih_daily_log WHERE dayKey = :dayKey")
    fun observeDayTotal(dayKey: String): Flow<Int>
}
