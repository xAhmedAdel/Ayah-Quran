package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/** Triggers the dataset import when the database is not yet populated. */
class ImportQuranIfNeededUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke() = repository.importIfNeeded()
}
