package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the Quran dataset import lifecycle. */
class ObserveQuranImportStateUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    operator fun invoke(): Flow<QuranImportState> = repository.importState
}
