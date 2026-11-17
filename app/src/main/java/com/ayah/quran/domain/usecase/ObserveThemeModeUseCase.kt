package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the user's appearance preference. */
class ObserveThemeModeUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    operator fun invoke(): Flow<ThemeMode> = repository.themeMode
}
