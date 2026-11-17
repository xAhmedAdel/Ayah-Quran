package com.ayah.quran.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
