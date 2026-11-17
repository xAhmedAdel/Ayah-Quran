package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.ReadingProgressRepository
import javax.inject.Inject

/** Persists the user's last reading position. */
class SaveReadingPositionUseCase @Inject constructor(
    private val repository: ReadingProgressRepository,
) {
    suspend operator fun invoke(surahId: Int, ayahNumber: Int) {
        repository.saveReadingPosition(surahId, ayahNumber)
    }
}
