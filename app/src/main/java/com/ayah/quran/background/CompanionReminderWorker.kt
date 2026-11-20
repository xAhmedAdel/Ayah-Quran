package com.ayah.quran.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class CompanionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val remindersRepository: CompanionRemindersRepository,
    private val reminderScheduler: CompanionReminderScheduler,
    private val notifier: CompanionReminderNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val type = CompanionReminderType.fromKey(inputData.getString(KEY_REMINDER_TYPE))
            ?: return Result.failure()
        val config = remindersRepository.preferences.first().configOf(type)
        if (!config.enabled) {
            return Result.success()
        }
        notifier.postReminder(type)
        reminderScheduler.schedule(config)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_TYPE = "reminder_type"
    }
}
