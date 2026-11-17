package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Searches surahs by Arabic, transliterated or English name. */
class SearchSurahsUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    operator fun invoke(query: String): Flow<List<Surah>> = repository.searchSurahs(query)
}
