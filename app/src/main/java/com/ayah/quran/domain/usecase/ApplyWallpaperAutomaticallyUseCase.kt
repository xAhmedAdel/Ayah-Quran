package com.ayah.quran.domain.usecase

import com.ayah.quran.core.quran.AyahSelector
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One automatic wallpaper cycle: select a verse per the configured mode,
 * load its VERIFIED text from the dataset, render, apply, and record.
 *
 * Selection pool for RANDOM/SEQUENTIAL/DAILY is the whole mushaf; SURAH
 * restricts to the chosen surah's contiguous global-id range; AYAH applies
 * the exact chosen verse. Missing verses (corrupt state) fail honestly
 * instead of substituting other content.
 */
class ApplyWallpaperAutomaticallyUseCase @Inject constructor(
    private val settingsRepository: WallpaperSettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val selector: AyahSelector,
) {
    suspend operator fun invoke(): WallpaperApplyStatus {
        val settings = settingsRepository.settings.first()
        val template = WallpaperTemplate.byId(settings.templateId)

        val globalId = when (settings.selectionMode) {
            SelectionMode.AYAH -> {
                val ayah = wallpaperRepository.loadAyah(settings.fixedSurahId, settings.fixedAyahNumber)
                    ?: return missingVerse(settings.fixedSurahId, settings.fixedAyahNumber)
                return applyAndRecord(ayah, template, settings)
            }

            SelectionMode.SURAH -> {
                val range = wallpaperRepository.surahGlobalIdRange(settings.fixedSurahId)
                    ?: return missingVerse(settings.fixedSurahId, 1)
                val pool = (range.first..range.second).toList()
                selector.selectRandom(pool, wallpaperRepository.recentSelections())
            }

            SelectionMode.SEQUENTIAL -> {
                val (selected, updatedCursor) = selector.selectSequential(
                    cursor = wallpaperRepository.sequentialCursor(),
                    poolSize = TOTAL_AYAHS,
                )
                wallpaperRepository.saveSequentialCursor(updatedCursor)
                selected
            }

            SelectionMode.DAILY -> selector.selectDaily(
                epochDay = LocalDate.now().toEpochDay(),
                poolSize = TOTAL_AYAHS,
            )

            SelectionMode.RANDOM -> selector.selectRandom(
                pool = wallpaperRepository.allGlobalIds(),
                recent = wallpaperRepository.recentSelections(),
            )
        }

        val ayah = wallpaperRepository.loadAyahByGlobalId(globalId)
            ?: return missingVerseReference(globalId)
        return applyAndRecord(ayah, template, settings)
    }

    private suspend fun applyAndRecord(
        ayah: com.ayah.quran.domain.model.Ayah,
        template: WallpaperTemplate,
        settings: com.ayah.quran.domain.repository.WallpaperSettings,
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

    private fun missingVerse(surahId: Int, ayahNumber: Int): WallpaperApplyStatus {
        android.util.Log.w("ApplyWallpaperAuto", "verse $surahId:$ayahNumber missing from dataset")
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
