package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes all surahs in canonical mushaf order. */
class ObserveSurahListUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    operator fun invoke(): Flow<List<Surah>> = repository.observeSurahs()
}
