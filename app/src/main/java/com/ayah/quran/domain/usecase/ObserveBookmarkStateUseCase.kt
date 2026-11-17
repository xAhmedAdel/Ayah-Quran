package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes whether a specific verse is bookmarked. */
class ObserveBookmarkStateUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    operator fun invoke(surahId: Int, ayahNumber: Int): Flow<Boolean> =
        repository.observeIsBookmarked(surahId, ayahNumber)
}
