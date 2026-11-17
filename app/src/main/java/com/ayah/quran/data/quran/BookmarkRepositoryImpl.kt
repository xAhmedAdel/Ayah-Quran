package com.ayah.quran.data.quran

import com.ayah.quran.core.database.dao.BookmarkDao
import com.ayah.quran.core.database.entity.BookmarkEntity
import com.ayah.quran.core.database.entity.BookmarkWithAyah
import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {

    override fun observeBookmarkedAyahs(): Flow<List<BookmarkedAyah>> =
        bookmarkDao.observeBookmarkedAyahs().map { rows -> rows.map { it.toDomain() } }

    override fun observeIsBookmarked(surahId: Int, ayahNumber: Int): Flow<Boolean> =
        bookmarkDao.observeIsBookmarked(surahId, ayahNumber)

    override suspend fun toggleBookmark(surahId: Int, ayahNumber: Int): Boolean {
        val insertedId = bookmarkDao.insert(
            BookmarkEntity(
                surahId = surahId,
                ayahNumber = ayahNumber,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return if (insertedId != -1L) {
            true
        } else {
            bookmarkDao.delete(surahId, ayahNumber)
            false
        }
    }

    private fun BookmarkWithAyah.toDomain() = BookmarkedAyah(
        bookmarkId = bookmarkId,
        surahId = surahId,
        ayahNumber = ayahNumber,
        createdAtMillis = createdAtMillis,
        textUthmani = textUthmani,
        surahNameArabic = surahNameArabic,
        surahNameTransliterated = surahNameTransliterated,
    )
}
