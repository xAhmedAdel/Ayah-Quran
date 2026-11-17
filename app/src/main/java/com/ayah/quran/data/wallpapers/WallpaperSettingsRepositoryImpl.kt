package com.ayah.quran.data.wallpapers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperDestination
import com.ayah.quran.domain.repository.WallpaperSettings
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [WallpaperSettingsRepository]. Shares the app's single
 * preferences file with user preferences; keys are distinct.
 */
@Singleton
class WallpaperSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WallpaperSettingsRepository {

    override val settings: Flow<WallpaperSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            WallpaperSettings(
                autoUpdateEnabled = prefs[KEY_AUTO_ENABLED] ?: false,
                intervalMinutes = prefs[KEY_INTERVAL_MINUTES] ?: 60,
                destinations = enumOrDefault(prefs[KEY_DESTINATIONS], WallpaperDestination.BOTH),
                templateId = prefs[KEY_TEMPLATE_ID] ?: WallpaperTemplate.EMERALD_LUXURY_ID,
                fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
                verticalPosition = enumOrDefault(prefs[KEY_VERTICAL_POSITION], TextVerticalPosition.CENTER),
                selectionMode = enumOrDefault(prefs[KEY_SELECTION_MODE], SelectionMode.RANDOM),
                fixedSurahId = prefs[KEY_FIXED_SURAH_ID] ?: 1,
                fixedAyahNumber = prefs[KEY_FIXED_AYAH_NUMBER] ?: 1,
            )
        }

    override suspend fun update(transform: (WallpaperSettings) -> WallpaperSettings) {
        dataStore.edit { prefs ->
            val current = WallpaperSettings(
                autoUpdateEnabled = prefs[KEY_AUTO_ENABLED] ?: false,
                intervalMinutes = prefs[KEY_INTERVAL_MINUTES] ?: 60,
                destinations = enumOrDefault(prefs[KEY_DESTINATIONS], WallpaperDestination.BOTH),
                templateId = prefs[KEY_TEMPLATE_ID] ?: WallpaperTemplate.EMERALD_LUXURY_ID,
                fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
                verticalPosition = enumOrDefault(prefs[KEY_VERTICAL_POSITION], TextVerticalPosition.CENTER),
                selectionMode = enumOrDefault(prefs[KEY_SELECTION_MODE], SelectionMode.RANDOM),
                fixedSurahId = prefs[KEY_FIXED_SURAH_ID] ?: 1,
                fixedAyahNumber = prefs[KEY_FIXED_AYAH_NUMBER] ?: 1,
            )
            val updated = transform(current)
            prefs[KEY_AUTO_ENABLED] = updated.autoUpdateEnabled
            prefs[KEY_INTERVAL_MINUTES] = updated.intervalMinutes
            prefs[KEY_DESTINATIONS] = updated.destinations.name
            prefs[KEY_TEMPLATE_ID] = updated.templateId
            prefs[KEY_FONT_SCALE] = updated.fontScale
            prefs[KEY_VERTICAL_POSITION] = updated.verticalPosition.name
            prefs[KEY_SELECTION_MODE] = updated.selectionMode.name
            prefs[KEY_FIXED_SURAH_ID] = updated.fixedSurahId
            prefs[KEY_FIXED_AYAH_NUMBER] = updated.fixedAyahNumber
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        val KEY_AUTO_ENABLED = booleanPreferencesKey("wallpaper_auto_enabled")
        val KEY_INTERVAL_MINUTES = intPreferencesKey("wallpaper_interval_minutes")
        val KEY_DESTINATIONS = stringPreferencesKey("wallpaper_destinations")
        val KEY_TEMPLATE_ID = stringPreferencesKey("wallpaper_template_id")
        val KEY_FONT_SCALE = floatPreferencesKey("wallpaper_font_scale")
        val KEY_VERTICAL_POSITION = stringPreferencesKey("wallpaper_vertical_position")
        val KEY_SELECTION_MODE = stringPreferencesKey("wallpaper_selection_mode")
        val KEY_FIXED_SURAH_ID = intPreferencesKey("wallpaper_fixed_surah_id")
        val KEY_FIXED_AYAH_NUMBER = intPreferencesKey("wallpaper_fixed_ayah_number")
    }
}
