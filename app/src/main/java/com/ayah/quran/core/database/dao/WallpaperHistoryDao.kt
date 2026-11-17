package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ayah.quran.core.database.entity.SelectionHistoryEntity
import com.ayah.quran.core.database.entity.WallpaperHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperHistoryDao {

    @Query("SELECT * FROM wallpaper_history ORDER BY appliedAt DESC")
    fun observeAll(): Flow<List<WallpaperHistoryEntity>>

    @Insert
    suspend fun insert(entry: WallpaperHistoryEntity): Long

    @Query("SELECT * FROM wallpaper_history WHERE id = :id")
    suspend fun getById(id: Long): WallpaperHistoryEntity?

    @Query("DELETE FROM wallpaper_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM wallpaper_history")
    suspend fun clearAll()

    /** Keeps the ring bounded: removes everything outside the newest [keep]. */
    @Query(
        "DELETE FROM wallpaper_history WHERE id NOT IN " +
            "(SELECT id FROM wallpaper_history ORDER BY appliedAt DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    @Query("SELECT MAX(appliedAt) FROM wallpaper_history WHERE status = 'SUCCESS'")
    suspend fun lastSuccessAt(): Long?

    @Query("SELECT COUNT(*) FROM wallpaper_history")
    suspend fun count(): Int
}

@Dao
interface SelectionHistoryDao {

    @Insert
    suspend fun insert(entry: SelectionHistoryEntity)

    /** The newest [limit] selected verses, newest first. */
    @Query("SELECT * FROM selection_history ORDER BY selectedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SelectionHistoryEntity>

    /** Keeps the ring bounded: removes everything outside the newest [keep]. */
    @Query(
        "DELETE FROM selection_history WHERE id NOT IN " +
            "(SELECT id FROM selection_history ORDER BY selectedAt DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}
