package com.ayah.quran.data.companion

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ayah.quran.background.CompanionReminderWorker
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderTimeCalculator
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerCompanionReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : CompanionReminderScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun schedule(config: CompanionReminderConfig) {
        if (!config.enabled) {
            cancel(config.type)
            return
        }
        val delayMillis = CompanionReminderTimeCalculator.computeInitialDelayMillis(
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            hour = config.hour,
            minute = config.minute,
        )
        val request = OneTimeWorkRequestBuilder<CompanionReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CompanionReminderWorker.KEY_REMINDER_TYPE to config.type.key))
            .addTag(WORK_TAG)
            .addTag(config.type.uniqueWorkName)
            .build()
        workManager.enqueueUniqueWork(
            config.type.uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(type: CompanionReminderType) {
        workManager.cancelUniqueWork(type.uniqueWorkName)
    }

    override fun reconcileAll(preferences: CompanionRemindersPreferences) {
        preferences.allConfigs().forEach { config ->
            if (config.enabled) {
                schedule(config)
            } else {
                cancel(config.type)
            }
        }
    }

    companion object {
        const val WORK_TAG = "companion_reminders"
    }
}
