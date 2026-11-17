package com.ayah.quran.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.repository.UserPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [UserPreferencesRepository].
 *
 * A read failure is treated as "no preference stored" (falling back to
 * defaults) only for the expected [IOException] case; anything else is a real
 * bug and is rethrown.
 */
@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val syncPrefsContext: Context,
) : UserPreferencesRepository {

    override val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> ThemeMode.fromStorage(preferences[KEY_THEME_MODE]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    override val languageTag: Flow<String?> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> preferences[KEY_LANGUAGE_TAG] }

    override suspend fun setLanguageTag(tag: String) {
        // Persist to both DataStore (for Settings UI) and SharedPreferences
        // (for the synchronous read in AppLocale.wrap at attach time).
        dataStore.edit { preferences -> preferences[KEY_LANGUAGE_TAG] = tag }
        syncPrefsContext.getSharedPreferences(AppLocale.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(AppLocale.KEY_LANGUAGE, tag).apply()
    }

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE_TAG = stringPreferencesKey("app_language_tag")
    }
}
