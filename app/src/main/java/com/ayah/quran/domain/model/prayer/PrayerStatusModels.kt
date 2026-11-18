package com.ayah.quran.domain.model.prayer

/**
 * Live permission/status board for prayer notifications & adhan (Phase 14).
 * Every field is VERIFIED at read time — the UI never shows a green state
 * without an actual check.
 */
data class PrayerStatusBoard(
    val notificationsGranted: Boolean,
    val adhanChannelEnabled: Boolean,
    val exactAlarmsGranted: Boolean,
    val vendorGuidanceAcknowledged: Boolean,
    val onboardingCompleted: Boolean,
    val adhanConfig: PrayerAdhanConfig,
    val vendor: VendorKind,
) {
    /** True only when everything ESSENTIAL for reliable prayer alerts holds. */
    val readyForPrayerNotifications: Boolean
        get() = notificationsGranted && adhanChannelEnabled && exactAlarmsGranted

    /** Vendor settings cannot be auto-verified — surfaced as recommended. */
    val vendorGuidanceVerified: Boolean
        get() = false
}

enum class VendorKind(val displayName: String) {
    HONOR("HONOR / MagicOS"),
    HUAWEI("HUAWEI / EMUI"),
    SAMSUNG("Samsung"),
    GENERIC("Android"),
}

/** Pure classification of a manufacturer string (build-time probe input). */
object VendorDetection {

    fun classify(manufacturer: String?, brand: String? = null): VendorKind {
        val all = listOf(manufacturer, brand)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        return when {
            "honor" in all -> VendorKind.HONOR
            "huawei" in all -> VendorKind.HUAWEI
            "samsung" in all -> VendorKind.SAMSUNG
            else -> VendorKind.GENERIC
        }
    }
}

/**
 * Pure derived statuses (no Android imports) so the rules are JVM-testable:
 * the Android probe supplies the raw booleans, this decides what they MEAN.
 */
object PrayerStatusReducer {

    fun readyForPrayerNotifications(
        notificationsGranted: Boolean,
        adhanChannelEnabled: Boolean,
        exactAlarmsGranted: Boolean,
    ): Boolean = notificationsGranted && adhanChannelEnabled && exactAlarmsGranted

    /** The first not-yet-satisfied step of the sequential onboarding flow. */
    fun nextRequiredStep(
        notificationsGranted: Boolean,
        adhanChannelEnabled: Boolean,
        exactAlarmsGranted: Boolean,
    ): Int = when {
        !notificationsGranted -> 1
        !adhanChannelEnabled -> 2
        !exactAlarmsGranted -> 3
        else -> 4 // everything essential verified; guidance is recommended-only
    }
}
