package com.ayah.quran.data.companion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.ayah.quran.domain.repository.companion.TasbihPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreTasbihPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TasbihPreferencesRepository {

    private val safeData: Flow<Preferences> = dataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    override val selectedCounterId: Flow<Long?> = safeData.map { prefs ->
        prefs[KEY_SELECTED_COUNTER_ID]
    }

    override val hapticsEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_HAPTICS_ENABLED] ?: true
    }

    override suspend fun setSelectedCounterId(counterId: Long?) {
        dataStore.edit { prefs ->
            if (counterId == null) {
                prefs.remove(KEY_SELECTED_COUNTER_ID)
            } else {
                prefs[KEY_SELECTED_COUNTER_ID] = counterId
            }
        }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    companion object {
        val KEY_SELECTED_COUNTER_ID = longPreferencesKey("tasbih_selected_counter_id")
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("tasbih_haptics_enabled")
    }
}
