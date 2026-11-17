package com.ayah.quran.data.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioPreferences
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Validates [AudioPreferencesRepositoryImpl]'s mapping logic (defaults,
 * blank→null reciter, speed clamping, enum parsing) over an in-memory
 * DataStore — avoiding the known Windows-only rename issue in JVM
 * DataStore file tests. The real file-backed DataStore is exercised on
 * device by the running app.
 */
class AudioPreferencesRepositoryTest {

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private fun createRepository(): AudioPreferencesRepository =
        AudioPreferencesRepositoryImpl(FakeDataStore())

    @Test
    fun `defaults are honest - no reciter, repeat off, speed 1`() = runTest {
        val repository = createRepository()
        val prefs = repository.preferences.first()
        assertNull(prefs.selectedReciterId)
        assertEquals(RepeatMode.OFF, prefs.repeatMode)
        assertEquals(1.0f, prefs.playbackSpeed)
    }

    @Test
    fun `update persists reciter, repeat and speed`() = runTest {
        val repository = createRepository()
        repository.update {
            it.copy(selectedReciterId = "rec_1", repeatMode = RepeatMode.REPEAT_ONE, playbackSpeed = 1.25f)
        }
        val prefs = repository.preferences.first()
        assertEquals("rec_1", prefs.selectedReciterId)
        assertEquals(RepeatMode.REPEAT_ONE, prefs.repeatMode)
        assertEquals(1.25f, prefs.playbackSpeed)
    }

    @Test
    fun `speed is clamped to the supported range`() = runTest {
        val repository = createRepository()
        repository.update { it.copy(playbackSpeed = 9f) }
        assertEquals(2.0f, repository.preferences.first().playbackSpeed)
        repository.update { it.copy(playbackSpeed = 0.1f) }
        assertEquals(0.5f, repository.preferences.first().playbackSpeed)
    }

    @Test
    fun `blank reciter id reads back as null`() = runTest {
        val repository = createRepository()
        repository.update { it.copy(selectedReciterId = "") }
        assertNull(repository.preferences.first().selectedReciterId)
    }
}
