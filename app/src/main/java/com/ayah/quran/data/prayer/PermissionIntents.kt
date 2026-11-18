package com.ayah.quran.data.prayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * System-settings intents used by the permission dashboard/onboarding.
 *
 * Policy notes (Phase 14 §4–5):
 * - Exact alarms go through ACTION_REQUEST_SCHEDULE_EXACT_ALARM — the
 *   DOCUMENTED system intent; no programmatic granting, ever.
 * - Vendor app-launch screens (HONOR/Huawei) are community-documented
 *   component names; each is attempted with try/catch and the flow falls
 *   back to the always-available application-details page. No fabricated
 *   or hidden APIs.
 */
object PermissionIntents {

    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:" + context.packageName))

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun channelSettings(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.packageName))

    /**
     * Best-effort vendor "app launch" screens, most specific first. The
     * caller launches them one by one, falling back to [appDetails] when a
     * component does not exist on the device.
     */
    fun vendorAppLaunchCandidates(context: Context): List<Intent> = listOf(
        // HONOR / Huawei MagicOS-EMUI App launch manager (community-documented).
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppActivityActivity",
        ),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
        ),
        appDetails(context),
    )
}
