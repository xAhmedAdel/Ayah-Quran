package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Applies an explicit (previewed) verse with the current saved settings. */
class ApplyWallpaperNowUseCase @Inject constructor(
    private val repository: WallpaperRepository,
    private val settingsRepository: WallpaperSettingsRepository,
) {
    suspend operator fun invoke(ayah: Ayah): WallpaperApplyStatus {
        val settings = settingsRepository.settings.first()
        val template = com.ayah.quran.core.wallpaper.WallpaperTemplate.byId(settings.templateId)
        return repository.applyWallpaper(
            ayah = ayah,
            template = template,
            destinations = settings.destinations,
            fontScale = settings.fontScale,
            verticalPosition = settings.verticalPosition,
        )
    }
}
