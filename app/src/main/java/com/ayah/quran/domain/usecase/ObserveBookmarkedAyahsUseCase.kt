package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.BookmarkedAyah
import com.ayah.quran.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes all bookmarks with their verse text, newest first. */
class ObserveBookmarkedAyahsUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(): Flow<List<BookmarkedAyah>> = repository.observeBookmarkedAyahs()
}
