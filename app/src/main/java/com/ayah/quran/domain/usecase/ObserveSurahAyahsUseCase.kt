package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the ayahs of one surah in canonical order. */
class ObserveSurahAyahsUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    operator fun invoke(surahId: Int): Flow<List<Ayah>> = repository.observeAyahs(surahId)
}
