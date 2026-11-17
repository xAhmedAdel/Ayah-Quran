package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.BookmarkRepository
import javax.inject.Inject

/** Toggles a verse bookmark; returns the resulting bookmarked state. */
class ToggleBookmarkUseCase @Inject constructor(
    private val repository: BookmarkRepository,
) {
    suspend operator fun invoke(surahId: Int, ayahNumber: Int): Boolean =
        repository.toggleBookmark(surahId, ayahNumber)
}
