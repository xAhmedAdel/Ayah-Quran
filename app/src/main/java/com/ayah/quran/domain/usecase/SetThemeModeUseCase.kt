package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.repository.UserPreferencesRepository
import javax.inject.Inject

/** Persists the user's appearance preference. */
class SetThemeModeUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke(mode: ThemeMode) {
        repository.setThemeMode(mode)
    }
}
