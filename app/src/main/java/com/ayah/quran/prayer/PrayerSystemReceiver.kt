package com.ayah.quran.prayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms the prayer alarm after events that can invalidate it: device
 * reboot, time/timezone/date changes, and app updates. All paths funnel
 * into [PrayerTimeManager.reconcile], which recomputes from persisted
 * preferences — no alarm state survives in memory alone.
 */
@AndroidEntryPoint
class PrayerSystemReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: PrayerTimeManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> manager.reconcile()
        }
    }
}
