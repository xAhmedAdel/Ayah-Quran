package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ayah.quran.core.database.entity.SurahEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurahDao {

    @Query("SELECT * FROM surahs ORDER BY id ASC")
    fun observeAll(): Flow<List<SurahEntity>>

    @Query(
        "SELECT * FROM surahs WHERE " +
            "nameTransliterated LIKE '%' || :query || '%' " +
            "OR nameEnglish LIKE '%' || :query || '%' " +
            "OR nameArabic LIKE '%' || :query || '%' " +
            "ORDER BY id ASC",
    )
    fun searchByName(query: String): Flow<List<SurahEntity>>

    @Query("SELECT * FROM surahs WHERE id = :surahId")
    suspend fun getSurah(surahId: Int): SurahEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM surahs)")
    fun observeHasData(): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM surahs")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun insertAll(surahs: List<SurahEntity>)

    @Query("SELECT COUNT(*) FROM surahs")
    suspend fun count(): Int
}
