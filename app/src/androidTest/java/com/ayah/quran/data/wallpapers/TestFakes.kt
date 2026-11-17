package com.ayah.quran.data.wallpapers

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ayah.quran.domain.repository.WallpaperSettings
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Minimal in-memory settings repository for instrumentation tests. */
internal class FakeSettingsRepository : WallpaperSettingsRepository {
    private val state = MutableStateFlow(WallpaperSettings())

    override val settings: Flow<WallpaperSettings> = state

    override suspend fun update(transform: (WallpaperSettings) -> WallpaperSettings) {
        state.value = transform(state.value)
    }

    override val selectedAyahIds: Flow<Set<String>> = MutableStateFlow(emptySet<String>())

    override suspend fun addSelectedAyah(surahId: Int, ayahNumber: Int) { /* no-op */ }

    override suspend fun removeSelectedAyah(surahId: Int, ayahNumber: Int) { /* no-op */ }

    override suspend fun clearSelectedAyahs() { /* no-op */ }
}

/** In-memory DataStore fake (only the sequential cursor uses it). */
internal class FakeDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
