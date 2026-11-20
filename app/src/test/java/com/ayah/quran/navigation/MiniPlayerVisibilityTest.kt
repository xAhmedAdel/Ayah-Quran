package com.ayah.quran.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityTest {

    @Test
    fun visibleOnAllFiveTopLevelTabsAndUtilityScreens() {
        TopLevelDestination.entries.forEach { destination ->
            assertTrue("expected mini player on ${destination.route}", shouldShowMiniPlayer(destination.route))
        }
        assertTrue(shouldShowMiniPlayer(Routes.SETTINGS))
        assertTrue(shouldShowMiniPlayer(Routes.BOOKMARKS))
        assertTrue(shouldShowMiniPlayer(Routes.PRAYER_SETTINGS))
        assertTrue(shouldShowMiniPlayer(Routes.PRAYER_VOICE))
        assertTrue(shouldShowMiniPlayer(Routes.PRAYER_ONBOARDING))
        assertTrue(shouldShowMiniPlayer(Routes.PRAYER_ADHAN_LIBRARY))
        assertTrue(shouldShowMiniPlayer(Routes.ADHKAR_HUB))
        assertTrue(shouldShowMiniPlayer(Routes.DUAS_HUB))
    }

    @Test
    fun hiddenOnImmersiveAndNowPlayingRoutes() {
        assertFalse(shouldShowMiniPlayer(null))
        assertFalse(shouldShowMiniPlayer(Routes.NOW_PLAYING))
        assertFalse(shouldShowMiniPlayer(Routes.FULLSCREEN_PREVIEW))
        assertFalse(shouldShowMiniPlayer(Routes.MUSHAF))
        assertFalse(shouldShowMiniPlayer(Routes.READER))
        assertFalse(shouldShowMiniPlayer(Routes.ADHKAR_SESSION))
    }
}
