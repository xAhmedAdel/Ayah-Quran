package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/** Resolves the authentic mushaf page containing a surah/ayah reference. */
class PageForAyahUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    /**
     * The page for [surahId]:[ayahNumber]; falls back to the surah's first
     * page when the ayah number is unknown (e.g. 0 from saved progress).
     */
    suspend operator fun invoke(surahId: Int, ayahNumber: Int): Int? {
        repository.pageForAyah(surahId, ayahNumber)?.let { return it }
        return repository.pageForSurahStart(surahId)
    }
}
