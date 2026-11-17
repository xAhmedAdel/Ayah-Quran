package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.ReadingPosition
import com.ayah.quran.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the user's last reading position (null until something was read). */
class ObserveReadingPositionUseCase @Inject constructor(
    private val repository: ReadingProgressRepository,
) {
    operator fun invoke(): Flow<ReadingPosition?> = repository.observeReadingPosition()
}
