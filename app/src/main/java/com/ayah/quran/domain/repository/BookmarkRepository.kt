package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.BookmarkedAyah
import kotlinx.coroutines.flow.Flow

/** Bookmark persistence for verses. */
interface BookmarkRepository {

    /** All bookmarks (newest first), joined with verse text and surah names. */
    fun observeBookmarkedAyahs(): Flow<List<BookmarkedAyah>>

    /** Emits whether (surahId, ayahNumber) is currently bookmarked. */
    fun observeIsBookmarked(surahId: Int, ayahNumber: Int): Flow<Boolean>

    /**
     * Adds the bookmark if absent, removes it if present.
     * @return the resulting bookmarked state.
     */
    suspend fun toggleBookmark(surahId: Int, ayahNumber: Int): Boolean
}
