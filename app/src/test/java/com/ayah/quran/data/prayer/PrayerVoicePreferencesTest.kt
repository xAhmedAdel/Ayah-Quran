package com.ayah.quran.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Per-prayer voice selection persistence (Phase 13): the default recording
 * applies to ALL five prayers on first launch; each prayer can select a
 * different voice; changing one prayer never touches the others.
 *
 * Uses the in-memory FakeDataStore pattern (Windows JVM DataStore file
 * tests are flaky; the file-backed store runs on device).
 */
class PrayerVoicePreferencesTest {

    private class FakeDataStore : DataStore<Preferences> {
        val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    @Test
    fun `first launch assigns the default recording to all five prayers`() = runTest {
        val store = FakeDataStore()
        val repository = PrayerPreferencesRepositoryImpl(store)

        val selection = repository.voiceSelection.first()
        Prayer.entries.forEach { prayer ->
            assertEquals(
                AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
                selection.voiceOf(prayer),
            )
        }
    }

    @Test
    fun `each prayer can select a different recording`() = runTest {
        val store = FakeDataStore()
        val repository = PrayerPreferencesRepositoryImpl(store)

        repository.setVoice(Prayer.FAJR, "adhan_pd_sabah_fakhri")
        repository.setVoice(Prayer.DHUHR, "adhan_by_sa_azan")
        repository.setVoice(Prayer.MAGHRIB, "adhan_by_sa_call_to_worship")

        val selection = repository.voiceSelection.first()
        assertEquals("adhan_pd_sabah_fakhri", selection.voiceOf(Prayer.FAJR))
        assertEquals("adhan_by_sa_azan", selection.voiceOf(Prayer.DHUHR))
        assertEquals(AdhanVoiceDefaults.DEFAULT_RECORDING_ID, selection.voiceOf(Prayer.ASR))
        assertEquals("adhan_by_sa_call_to_worship", selection.voiceOf(Prayer.MAGHRIB))
        assertEquals(AdhanVoiceDefaults.DEFAULT_RECORDING_ID, selection.voiceOf(Prayer.ISHA))
    }

    @Test
    fun `changing fajr does not change dhuhr asr maghrib or isha`() = runTest {
        val store = FakeDataStore()
        val repository = PrayerPreferencesRepositoryImpl(store)
        repository.setVoice(Prayer.DHUHR, "adhan_by_sa_azan")
        repository.setVoice(Prayer.ISHA, "adhan_pd_sabah_fakhri")

        repository.setVoice(Prayer.FAJR, "adhan_by_sa_call_to_worship")

        val selection = repository.voiceSelection.first()
        assertEquals("adhan_by_sa_call_to_worship", selection.voiceOf(Prayer.FAJR))
        assertEquals("adhan_by_sa_azan", selection.voiceOf(Prayer.DHUHR))
        assertEquals("adhan_pd_sabah_fakhri", selection.voiceOf(Prayer.ISHA))
    }

    @Test
    fun `selection persists across repository instances (app restart)`() = runTest {
        val store = FakeDataStore()
        val first = PrayerPreferencesRepositoryImpl(store)
        first.setVoice(Prayer.MAGHRIB, "adhan_pd_sabah_fakhri")

        // A "new process" reads the same underlying store.
        val second = PrayerPreferencesRepositoryImpl(store)
        assertEquals(
            "adhan_pd_sabah_fakhri",
            second.voiceSelection.first().voiceOf(Prayer.MAGHRIB),
        )
    }

    @Test
    fun `prayer preferences expose the voice selection`() = runTest {
        val store = FakeDataStore()
        val repository = PrayerPreferencesRepositoryImpl(store)
        repository.setVoice(Prayer.FAJR, "adhan_by_sa_azan")

        val prefs = repository.preferences.first()
        val expected = PrayerVoiceSelection(fajr = "adhan_by_sa_azan")
        assertEquals(expected, prefs.voiceSelection)
    }
}
