package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/** Fetches a single surah by its canonical number. */
class GetSurahUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    suspend operator fun invoke(surahId: Int): Surah? = repository.getSurah(surahId)
}
