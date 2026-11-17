package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayah.quran.core.database.entity.AyahEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AyahDao {

    @Query("SELECT * FROM ayahs WHERE surahId = :surahId ORDER BY ayahNumber ASC")
    fun observeSurahAyahs(surahId: Int): Flow<List<AyahEntity>>

    @Query("SELECT * FROM ayahs WHERE surahId = :surahId AND ayahNumber = :ayahNumber")
    suspend fun getAyah(surahId: Int, ayahNumber: Int): AyahEntity?

    @Query("SELECT * FROM ayahs WHERE id = :id")
    suspend fun getAyahById(id: Int): AyahEntity?

    /** Random verse lookup for the wallpaper selector (Phase 3). */
    @Query("SELECT * FROM ayahs WHERE surahId = :surahId ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAyahFromSurah(surahId: Int): AyahEntity?

    /** Global random ayah (whole mushaf pool). */
    @Query("SELECT * FROM ayahs ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAyah(): AyahEntity?

    /** Global id range of one surah, for building selection pools. */
    @Query("SELECT MIN(id) AS minId, MAX(id) AS maxId FROM ayahs WHERE surahId = :surahId")
    suspend fun surahGlobalIdRange(surahId: Int): GlobalIdRange?

    /** Direct global-id lookup (selection pools address verses by global id). */
    @Query("SELECT * FROM ayahs WHERE id = :globalId")
    suspend fun getAyahById(globalId: Long): AyahEntity?

    /** Fetches a contiguous pool of global ids (whole mushaf or one surah). */
    @Query("SELECT id FROM ayahs WHERE id BETWEEN :fromId AND :toId ORDER BY id ASC")
    suspend fun globalIdsInRange(fromId: Long, toId: Long): List<Long>

    /** Used by the Phase 2 import; REPLACE makes re-imports idempotent. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ayahs: List<AyahEntity>)

    @Query("SELECT COUNT(*) FROM ayahs")
    suspend fun count(): Int

    @Query("SELECT MIN(id) AS minId, MAX(id) AS maxId, COUNT(*) AS count FROM ayahs")
    suspend fun idRangeAndCount(): IdRangeAndCount

    data class IdRangeAndCount(
        val minId: Int?,
        val maxId: Int?,
        val count: Int,
    )

    data class GlobalIdRange(
        val minId: Long,
        val maxId: Long,
    )
}
