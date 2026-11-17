package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/** Resolves the authentic mushaf page where a surah begins. */
class PageForSurahStartUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(surahId: Int): Int? =
        repository.pageForSurahStart(surahId)
}
