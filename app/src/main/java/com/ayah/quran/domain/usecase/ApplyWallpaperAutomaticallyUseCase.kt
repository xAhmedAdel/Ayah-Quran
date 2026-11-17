package com.ayah.quran.domain.usecase

import com.ayah.quran.core.quran.AyahSelector
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettings
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ApplyWallpaperAutomaticallyUseCase @Inject constructor(
    private val settingsRepository: WallpaperSettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val selector: AyahSelector,
) {
    suspend operator fun invoke(): WallpaperApplyStatus {
        val settings = settingsRepository.settings.first()
        val template = WallpaperTemplate.byId(settings.templateId)

        val ayah = when (settings.selectionMode) {
            SelectionMode.RANDOM_FROM_SELECTION -> {
                val selected = wallpaperRepository.selectedAyahIds.first()
                if (selected.isEmpty()) return missingSelection()
                val picked = selected.random()
                val parts = picked.split(":")
                wallpaperRepository.loadAyah(
                    parts[0].toIntOrNull() ?: return missingSelection(),
                    parts[1].toIntOrNull() ?: return missingSelection(),
                ) ?: return missingVerse(picked)
            }

            SelectionMode.SEQUENTIAL_FROM_SELECTION -> {
                val selected = wallpaperRepository.selectedAyahIds.first().sorted()
                if (selected.isEmpty()) return missingSelection()
                val cursor = wallpaperRepository.sequentialCursor()
                val picked = selected[(cursor % selected.size).toInt()]
                wallpaperRepository.saveSequentialCursor(cursor + 1)
                val parts = picked.split(":")
                wallpaperRepository.loadAyah(
                    parts[0].toIntOrNull() ?: return missingSelection(),
                    parts[1].toIntOrNull() ?: return missingSelection(),
                ) ?: return missingVerse(picked)
            }

            SelectionMode.DAILY -> {
                val globalId = selector.selectDaily(
                    epochDay = LocalDate.now().toEpochDay(),
                    poolSize = TOTAL_AYAHS,
                )
                wallpaperRepository.loadAyahByGlobalId(globalId)
                    ?: return missingVerseReference(globalId)
            }

            SelectionMode.RANDOM -> {
                val picked = selector.selectRandom(
                    pool = wallpaperRepository.allGlobalIds(),
                    recent = wallpaperRepository.recentSelections(),
                )
                wallpaperRepository.loadAyahByGlobalId(picked)
                    ?: return missingVerseReference(picked)
            }
        }

        return applyAndRecord(ayah, template, settings)
    }

    private suspend fun applyAndRecord(
        ayah: Ayah,
        template: WallpaperTemplate,
        settings: WallpaperSettings,
    ): WallpaperApplyStatus {
        val status = wallpaperRepository.applyWallpaper(
            ayah = ayah,
            template = template,
            destinations = settings.destinations,
            fontScale = settings.fontScale,
            verticalPosition = settings.verticalPosition,
        )
        if (status == WallpaperApplyStatus.SUCCESS) {
            wallpaperRepository.recordSelection(ayah.surahId, ayah.ayahNumber, settings.selectionMode)
        }
        return status
    }

    private fun missingSelection(): WallpaperApplyStatus {
        android.util.Log.w("ApplyWallpaperAuto", "no ayahs selected for this mode")
        return WallpaperApplyStatus.FAILED
    }

    private fun missingVerse(ref: String): WallpaperApplyStatus {
        android.util.Log.w("ApplyWallpaperAuto", "verse $ref missing from dataset")
        return WallpaperApplyStatus.FAILED
    }

    private fun missingVerseReference(globalId: Long): WallpaperApplyStatus {
        android.util.Log.w("ApplyWallpaperAuto", "global verse $globalId missing from dataset")
        return WallpaperApplyStatus.FAILED
    }

    private companion object {
        const val TOTAL_AYAHS = 6236
    }
}
