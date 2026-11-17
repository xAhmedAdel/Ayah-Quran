package com.ayah.quran.data.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferences
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed audio preferences: selected reciter, repeat mode, and
 * playback speed. Transient playback state is deliberately NOT persisted.
 * Shares the app's single preferences file; keys are distinct.
 */
@Singleton
class AudioPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AudioPreferencesRepository {

    override val preferences: Flow<AudioPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            AudioPreferences(
                selectedReciterId = prefs[KEY_SELECTED_RECITER]?.takeIf { it.isNotBlank() },
                repeatMode = enumOrDefault(prefs[KEY_REPEAT_MODE], RepeatMode.OFF),
                playbackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
            )
        }

    override suspend fun update(transform: (AudioPreferences) -> AudioPreferences) {
        dataStore.edit { prefs ->
            val current = AudioPreferences(
                selectedReciterId = prefs[KEY_SELECTED_RECITER]?.takeIf { it.isNotBlank() },
                repeatMode = enumOrDefault(prefs[KEY_REPEAT_MODE], RepeatMode.OFF),
                playbackSpeed = prefs[KEY_PLAYBACK_SPEED] ?: 1.0f,
            )
            val updated = transform(current)
            prefs[KEY_SELECTED_RECITER] = updated.selectedReciterId.orEmpty()
            prefs[KEY_REPEAT_MODE] = updated.repeatMode.name
            prefs[KEY_PLAYBACK_SPEED] = updated.playbackSpeed.coerceIn(0.5f, 2.0f)
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private companion object {
        val KEY_SELECTED_RECITER = stringPreferencesKey("audio_selected_reciter")
        val KEY_REPEAT_MODE = stringPreferencesKey("audio_repeat_mode")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("audio_playback_speed")
    }
}
