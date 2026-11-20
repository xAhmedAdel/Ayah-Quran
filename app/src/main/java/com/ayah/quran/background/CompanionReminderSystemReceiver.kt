package com.ayah.quran.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CompanionReminderSystemReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun remindersRepository(): CompanionRemindersRepository
        fun reminderScheduler(): CompanionReminderScheduler
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = entryPoint.remindersRepository().preferences.first()
                entryPoint.reminderScheduler().reconcileAll(prefs)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
