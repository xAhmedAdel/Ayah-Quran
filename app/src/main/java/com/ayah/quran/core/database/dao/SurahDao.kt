package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayah.quran.core.database.entity.SurahEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurahDao {

    /** All 114 surahs in canonical order. */
    @Query("SELECT * FROM surahs ORDER BY id ASC")
    fun observeAll(): Flow<List<SurahEntity>>

    /** Name search across Arabic, transliterated and English names. */
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

    /** Used by the Phase 2 import; REPLACE makes re-imports idempotent. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(surahs: List<SurahEntity>)

    @Query("SELECT COUNT(*) FROM surahs")
    suspend fun count(): Int
}
