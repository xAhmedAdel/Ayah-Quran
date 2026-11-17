package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the user's app language preference; null means "never chosen" (Arabic). */
class ObserveAppLanguageUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<String?> = repository.languageTag
}
