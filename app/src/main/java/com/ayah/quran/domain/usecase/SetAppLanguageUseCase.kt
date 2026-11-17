package com.ayah.quran.domain.usecase

import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.repository.UserPreferencesRepository
import javax.inject.Inject

/** Persists the user's app language; the caller recreates the activity to apply it. */
class SetAppLanguageUseCase @Inject constructor(
    private val repository: UserPreferencesRepository,
) {
    suspend operator fun invoke(tag: String) {
        require(tag == AppLocale.ARABIC || tag == AppLocale.ENGLISH) {
            "Unsupported language tag: $tag"
        }
        repository.setLanguageTag(tag)
    }
}
