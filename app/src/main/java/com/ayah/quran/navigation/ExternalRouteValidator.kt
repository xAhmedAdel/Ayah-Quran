package com.ayah.quran.navigation

import com.ayah.quran.domain.model.companion.CompanionReminderType

object ExternalRouteValidator {

    val ALLOWED_ROUTES: Set<String> =
        CompanionReminderType.entries.map { it.deepLinkRoute }.toSet()

    fun resolveExternalRoute(hasExtra: Boolean, rawRoute: String?): String? {
        if (!hasExtra) return null
        val candidate = rawRoute?.trim()
        if (candidate.isNullOrEmpty() || candidate !in ALLOWED_ROUTES) {
            return TopLevelDestination.HOME.route
        }
        return candidate
    }

    fun isAllowed(route: String?): Boolean = route != null && route in ALLOWED_ROUTES
}
