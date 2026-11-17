package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/** Fetches a single verse by its canonical (surah, ayah) reference. */
class GetAyahUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(surahId: Int, ayahNumber: Int): Ayah? =
        repository.getAyah(surahId, ayahNumber)
}
