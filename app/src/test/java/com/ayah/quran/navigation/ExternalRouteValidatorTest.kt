package com.ayah.quran.navigation

import com.ayah.quran.domain.model.companion.CompanionReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalRouteValidatorTest {

    @Test
    fun acceptsOnlyExplicitlyAllowedCompanionReminderRoutes() {
        CompanionReminderType.entries.forEach { type ->
            assertTrue(ExternalRouteValidator.isAllowed(type.deepLinkRoute))
            assertEquals(
                type.deepLinkRoute,
                ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = type.deepLinkRoute),
            )
        }
    }

    @Test
    fun returnsNullWhenExternalExtraIsAbsent() {
        assertNull(ExternalRouteValidator.resolveExternalRoute(hasExtra = false, rawRoute = null))
        assertNull(ExternalRouteValidator.resolveExternalRoute(hasExtra = false, rawRoute = "settings"))
    }

    @Test
    fun fallsBackToHomeForEmptyOrBlankRoute() {
        assertEquals(
            TopLevelDestination.HOME.route,
            ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = ""),
        )
        assertEquals(
            TopLevelDestination.HOME.route,
            ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = "   "),
        )
        assertEquals(
            TopLevelDestination.HOME.route,
            ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = null),
        )
    }

    @Test
    fun rejectsArbitraryInternalRoutesAndFallsBackToHome() {
        val disallowed = listOf(
            Routes.SETTINGS,
            Routes.PRAYER_SETTINGS,
            Routes.BOOKMARKS,
            Routes.NOW_PLAYING,
            Routes.TASBIH,
            Routes.HADITH_LIBRARY,
            Routes.mushaf(surahId = 2, ayahNumber = 255),
            Routes.reader(surahId = 1),
        )
        disallowed.forEach { route ->
            assertFalse("Unexpectedly allowed: $route", ExternalRouteValidator.isAllowed(route))
            assertEquals(
                TopLevelDestination.HOME.route,
                ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = route),
            )
        }
    }

    @Test
    fun rejectsMalformedRoutesAndExtraQueryParameters() {
        val malformed = listOf(
            "companion/adhkar/session/morning?itemId=injected",
            "companion/adhkar/session/evening&extra=1",
            "../settings",
            "companion/adhkar/session/sleep",
            "javascript:alert(1)",
            "home/extra",
        )
        malformed.forEach { route ->
            assertFalse("Unexpectedly allowed malformed route: $route", ExternalRouteValidator.isAllowed(route))
            assertEquals(
                TopLevelDestination.HOME.route,
                ExternalRouteValidator.resolveExternalRoute(hasExtra = true, rawRoute = route),
            )
        }
    }
}
