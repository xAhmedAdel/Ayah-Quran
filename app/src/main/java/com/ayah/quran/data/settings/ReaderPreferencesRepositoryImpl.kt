package com.ayah.quran.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.repository.ReaderSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore-backed reader preferences. Shares the app's single file. */
@Singleton
class ReaderPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {

    override val settings: Flow<ReaderSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs ->
            ReaderSettings(
                fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
                readingMode = ReadingMode.fromStorage(prefs[KEY_READING_MODE]),
            )
        }

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { prefs ->
            val current = ReaderSettings(
                fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
                readingMode = ReadingMode.fromStorage(prefs[KEY_READING_MODE]),
            )
            val updated = transform(current)
            prefs[KEY_FONT_SCALE] = updated.fontScale.coerceIn(0.5f, 2.0f)
            prefs[KEY_READING_MODE] = updated.readingMode.name
        }
    }

    private companion object {
        val KEY_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val KEY_READING_MODE = stringPreferencesKey("reader_reading_mode")
    }
}
