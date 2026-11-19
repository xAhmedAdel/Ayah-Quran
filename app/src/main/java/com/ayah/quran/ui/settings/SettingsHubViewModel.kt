package com.ayah.quran.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.audio.QuranAudioCatalog
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.model.WallpaperScheduleStatus
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import com.ayah.quran.domain.usecase.ObserveWallpaperScheduleStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsHubSummary(
    val readingMode: ReadingMode = ReadingMode.MUSHAF,
    val fontScalePercent: Int = 100,
    val reciterNameAr: String = QuranAudioCatalog.defaultReciter.nameAr,
    val reciterNameEn: String = QuranAudioCatalog.defaultReciter.nameEn,
    val playbackSpeed: Float = 1.0f,
    val prayerCityAr: String? = null,
    val prayerCityEn: String? = null,
    val wallpaperSchedule: WallpaperScheduleStatus =
        WallpaperScheduleStatus(isScheduled = false, intervalMinutes = null),
)

@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val readerPreferencesRepository: ReaderPreferencesRepository,
    audioPreferencesRepository: AudioPreferencesRepository,
    prayerPreferencesRepository: PrayerPreferencesRepository,
    observeWallpaperScheduleStatus: ObserveWallpaperScheduleStatusUseCase,
) : ViewModel() {

    val summary: StateFlow<SettingsHubSummary> = combine(
        readerPreferencesRepository.settings,
        audioPreferencesRepository.preferences,
        prayerPreferencesRepository.preferences,
        observeWallpaperScheduleStatus(),
    ) { reader, audio, prayer, wallpaper ->
        val edition = QuranAudioCatalog.byEdition(audio.selectedReciterId)
        SettingsHubSummary(
            readingMode = reader.readingMode,
            fontScalePercent = (reader.fontScale * 100f).toInt(),
            reciterNameAr = edition.nameAr,
            reciterNameEn = edition.nameEn,
            playbackSpeed = audio.playbackSpeed,
            prayerCityAr = prayer.location?.cityAr?.takeIf { it.isNotBlank() },
            prayerCityEn = prayer.location?.cityEn?.takeIf { it.isNotBlank() },
            wallpaperSchedule = wallpaper,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsHubSummary(),
    )

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            readerPreferencesRepository.update { it.copy(readingMode = mode) }
        }
    }
}
