package com.ayah.quran.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 14 flags: the permissions onboarding is shown until finished OR
 * skipped (skipping sets the same flag), and the vendor background guidance
 * acknowledgement persists — both survive repository re-creation.
 */
class PrayerOnboardingFlagsTest {

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
    fun `onboarding flag defaults to not-completed`() = runTest {
        val repository = PrayerPreferencesRepositoryImpl(FakeDataStore())
        assertFalse(repository.onboardingCompleted.first())
    }

    @Test
    fun `skipping or finishing sets the onboarding flag persistently`() = runTest {
        val store = FakeDataStore()
        val first = PrayerPreferencesRepositoryImpl(store)
        first.setOnboardingCompleted()

        val second = PrayerPreferencesRepositoryImpl(store)
        assertTrue(second.onboardingCompleted.first())
    }

    @Test
    fun `vendor guidance acknowledgement persists`() = runTest {
        val store = FakeDataStore()
        val first = PrayerPreferencesRepositoryImpl(store)
        assertFalse(first.vendorGuidanceAcknowledged.first())
        first.setVendorGuidanceAcknowledged()

        val second = PrayerPreferencesRepositoryImpl(store)
        assertTrue(second.vendorGuidanceAcknowledged.first())
    }
}
