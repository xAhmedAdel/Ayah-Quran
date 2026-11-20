package com.ayah.quran.prayer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ayah.quran.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class PrayerSystemReceiver : BroadcastReceiver() {

    @Inject lateinit var manager: PrayerTimeManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (!isSupportedAction(intent.action)) return
        val pendingResult = goAsync()
        val scope = if (::appScope.isInitialized) {
            appScope
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
        scope.launch {
            executeReconciliation(
                action = intent.action,
                reconcile = { manager.reconcileNow() },
                onFinish = { pendingResult.finish() },
            )
        }
    }

    companion object {
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        private const val RECONCILE_TIMEOUT_MILLIS = 8_000L

        val SUPPORTED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        )

        fun isSupportedAction(action: String?): Boolean =
            action != null && action in SUPPORTED_ACTIONS

        internal suspend fun executeReconciliation(
            action: String?,
            reconcile: suspend () -> Unit,
            onFinish: () -> Unit,
        ) {
            try {
                withTimeout(RECONCILE_TIMEOUT_MILLIS) {
                    reconcile()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(PrayerAlarmLog.TAG, "SYSTEM_RECEIVER_ERROR | action=$action | ${t.message}", t)
            } finally {
                onFinish()
            }
        }
    }
}
