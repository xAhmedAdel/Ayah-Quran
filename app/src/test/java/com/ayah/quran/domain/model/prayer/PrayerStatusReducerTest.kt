package com.ayah.quran.domain.model.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure rules for the Phase 14 dashboard: the "ready" banner appears ONLY
 * when every essential status is actually verified, and the sequential
 * onboarding always surfaces the FIRST unsatisfied step.
 */
class PrayerStatusReducerTest {

    @Test
    fun `ready requires all three essential statuses`() {
        assertFalse(PrayerStatusReducer.readyForPrayerNotifications(false, true, true))
        assertFalse(PrayerStatusReducer.readyForPrayerNotifications(true, false, true))
        assertFalse(PrayerStatusReducer.readyForPrayerNotifications(true, true, false))
        assertTrue(PrayerStatusReducer.readyForPrayerNotifications(true, true, true))
    }

    @Test
    fun `next step follows the sequential order`() {
        assertEquals(1, PrayerStatusReducer.nextRequiredStep(false, true, true))
        assertEquals(2, PrayerStatusReducer.nextRequiredStep(true, false, true))
        assertEquals(3, PrayerStatusReducer.nextRequiredStep(true, true, false))
    }

    @Test
    fun `all satisfied means onboarding complete`() {
        assertEquals(4, PrayerStatusReducer.nextRequiredStep(true, true, true))
    }

    @Test
    fun `board ready flag mirrors the reducer`() {
        val board = PrayerStatusBoard(
            notificationsGranted = true,
            adhanChannelEnabled = true,
            exactAlarmsGranted = true,
            vendorGuidanceAcknowledged = false, // optional vendor guidance
            onboardingCompleted = false,
            adhanConfig = com.ayah.quran.domain.model.prayer.PrayerAdhanConfig(),
            vendor = com.ayah.quran.domain.model.prayer.VendorKind.HONOR,
        )
        // Vendor guidance is RECOMMENDED-ONLY: it must not block "ready".
        assertTrue(board.readyForPrayerNotifications)
        assertFalse(board.vendorGuidanceVerified)
    }
}
