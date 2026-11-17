package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE id = ${ReadingProgressEntity.SINGLE_ROW_ID}")
    fun observe(): Flow<ReadingProgressEntity?>

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}
