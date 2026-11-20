package com.ayah.quran.core.database.dao.companion

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithEntity
import com.ayah.quran.core.database.entity.companion.HadithUserStateEntity
import kotlinx.coroutines.flow.Flow

data class HadithWithUserState(
    @Embedded val hadith: HadithEntity,
    val isFavorite: Boolean?,
    val lastReadAtMillis: Long?,
)

@Dao
interface HadithDao {

    @Query("SELECT * FROM hadith_collections ORDER BY sortOrder ASC")
    fun observeCollections(): Flow<List<HadithCollectionEntity>>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "LEFT JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE h.collectionId = :collectionId " +
            "ORDER BY h.sortOrder ASC",
    )
    fun observeByCollection(collectionId: String): Flow<List<HadithWithUserState>>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "LEFT JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE h.id = :id",
    )
    fun observeById(id: String): Flow<HadithWithUserState?>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "INNER JOIN hadith_fts f ON h.rowid = f.docid " +
            "LEFT JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE hadith_fts MATCH :ftsQuery " +
            "ORDER BY h.collectionId ASC, h.sortOrder ASC LIMIT :limit",
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 50): List<HadithWithUserState>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "LEFT JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE h.searchTextNormalized LIKE '%' || :normalizedQuery || '%' " +
            "ORDER BY h.collectionId ASC, h.sortOrder ASC LIMIT :limit",
    )
    suspend fun searchNormalized(normalizedQuery: String, limit: Int = 50): List<HadithWithUserState>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "INNER JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE u.isFavorite = 1 " +
            "ORDER BY u.favoritedAtMillis DESC",
    )
    fun observeFavorites(): Flow<List<HadithWithUserState>>

    @Query(
        "SELECT h.*, u.isFavorite AS isFavorite, u.lastReadAtMillis AS lastReadAtMillis " +
            "FROM hadith_items h " +
            "INNER JOIN hadith_user_state u ON h.id = u.hadithId " +
            "WHERE u.lastReadAtMillis IS NOT NULL " +
            "ORDER BY u.lastReadAtMillis DESC LIMIT :limit",
    )
    fun observeRecentlyRead(limit: Int = 20): Flow<List<HadithWithUserState>>

    @Query("SELECT id FROM hadith_items ORDER BY id ASC")
    suspend fun allHadithIds(): List<String>

    @Query("SELECT * FROM hadith_user_state WHERE hadithId = :hadithId")
    suspend fun getUserState(hadithId: String): HadithUserStateEntity?

    @Upsert
    suspend fun upsertUserState(state: HadithUserStateEntity)

    @Upsert
    suspend fun upsertCollections(collections: List<HadithCollectionEntity>)

    @Upsert
    suspend fun upsertHadiths(hadiths: List<HadithEntity>)

    @Query("SELECT COUNT(*) FROM hadith_collections")
    suspend fun collectionCount(): Int

    @Query("SELECT COUNT(*) FROM hadith_items")
    suspend fun hadithCount(): Int
}
