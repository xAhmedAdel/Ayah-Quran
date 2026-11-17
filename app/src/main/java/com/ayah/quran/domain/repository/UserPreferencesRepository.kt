package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to user preferences, backed by DataStore.
 *
 * UI and use cases depend on this interface only — never on DataStore types —
 * so the storage mechanism can change without touching callers.
 */
interface UserPreferencesRepository {

    /** Emits the current theme and every subsequent change. */
    val themeMode: Flow<ThemeMode>

    /** Persists [mode]; the [themeMode] flow emits the new value afterwards. */
    suspend fun setThemeMode(mode: ThemeMode)
}
