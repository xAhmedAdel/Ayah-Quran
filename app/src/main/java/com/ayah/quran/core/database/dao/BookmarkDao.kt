package com.ayah.quran.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.BookmarkWithAyah
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    /** Bookmarks joined with verse text and surah names for display. */
    @Query(
        "SELECT b.id AS bookmarkId, b.surahId AS surahId, b.ayahNumber AS ayahNumber, " +
            "b.createdAt AS createdAtMillis, a.textUthmani AS textUthmani, " +
            "s.nameArabic AS surahNameArabic, s.nameTransliterated AS surahNameTransliterated " +
            "FROM bookmarks b " +
            "JOIN ayahs a ON a.surahId = b.surahId AND a.ayahNumber = b.ayahNumber " +
            "JOIN surahs s ON s.id = b.surahId " +
            "ORDER BY b.createdAt DESC",
    )
    fun observeBookmarkedAyahs(): Flow<List<BookmarkWithAyah>>

    /**
     * Adds a bookmark. The (surahId, ayahNumber) unique index plus IGNORE
     * makes re-bookmarking the same verse a no-op.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE surahId = :surahId AND ayahNumber = :ayahNumber")
    suspend fun delete(surahId: Int, ayahNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE surahId = :surahId AND ayahNumber = :ayahNumber)")
    fun observeIsBookmarked(surahId: Int, ayahNumber: Int): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int
}
