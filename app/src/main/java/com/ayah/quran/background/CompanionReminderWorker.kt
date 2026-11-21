package com.ayah.quran.background

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ayah.quran.data.companion.CompanionReminderTypeLocks
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime

internal enum class ReminderPostOutcome {
    DISABLED,
    STALE_VERSION,
    ALREADY_HANDLED,
    PERMISSION_DENIED,
    POSTED,
    POST_FAILED,
}

@HiltWorker
class CompanionReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val remindersRepository: CompanionRemindersRepository,
    private val reminderScheduler: CompanionReminderScheduler,
    private val notifier: CompanionReminderNotifier,
    private val typeLocks: CompanionReminderTypeLocks,
) : CoroutineWorker(appContext, workerParams) {

    internal var nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) }

    override suspend fun doWork(): Result {
        val type = CompanionReminderType.fromKey(inputData.getString(KEY_REMINDER_TYPE))
            ?: return Result.failure()
        val scheduledVersion = inputData.getLong(KEY_SCHEDULE_VERSION, VERSION_UNSPECIFIED)
        var resolvedVersion: Long? = null
        var resolvedEpochDay: Long? = null

        return try {
            currentCoroutineContext().ensureActive()
            if (isStopped) {
                throw CancellationException("Worker stopped")
            }

            val initial = remindersRepository.preferences.first().configOf(type)
            if (!initial.enabled) {
                return Result.success()
            }

            val effectiveVersion = if (scheduledVersion == VERSION_UNSPECIFIED) {
                initial.scheduleVersion
            } else {
                scheduledVersion
            }
            resolvedVersion = effectiveVersion

            if (initial.scheduleVersion != effectiveVersion) {
                reminderScheduler.reconcile(type, replaceExisting = false)
                return Result.success()
            }

            val todayEpochDay = nowProvider().toLocalDate().toEpochDay()
            resolvedEpochDay = todayEpochDay

            if (initial.lastHandledEpochDay == todayEpochDay) {
                reminderScheduler.scheduleNextFromWorker(type, effectiveVersion, id)
                return Result.success()
            }

            val outcome = typeLocks.withTypeLock(type) {
                currentCoroutineContext().ensureActive()
                if (isStopped) {
                    throw CancellationException("Worker stopped")
                }
                val prePost = remindersRepository.preferences.first().configOf(type)
                when {
                    !prePost.enabled -> ReminderPostOutcome.DISABLED
                    prePost.scheduleVersion != effectiveVersion -> ReminderPostOutcome.STALE_VERSION
                    prePost.lastHandledEpochDay == todayEpochDay -> ReminderPostOutcome.ALREADY_HANDLED
                    !notifier.canPostNotifications() -> ReminderPostOutcome.PERMISSION_DENIED
                    notifier.postReminder(type) -> ReminderPostOutcome.POSTED
                    else -> ReminderPostOutcome.POST_FAILED
                }
            }

            when (outcome) {
                ReminderPostOutcome.DISABLED -> Result.success()
                ReminderPostOutcome.STALE_VERSION -> {
                    reminderScheduler.reconcile(type, replaceExisting = false)
                    Result.success()
                }
                ReminderPostOutcome.ALREADY_HANDLED -> {
                    reminderScheduler.scheduleNextFromWorker(type, effectiveVersion, id)
                    Result.success()
                }
                ReminderPostOutcome.PERMISSION_DENIED,
                ReminderPostOutcome.POSTED -> {
                    remindersRepository.markHandled(type, todayEpochDay, effectiveVersion)
                    reminderScheduler.scheduleNextFromWorker(type, effectiveVersion, id)
                    Result.success()
                }
                ReminderPostOutcome.POST_FAILED -> {
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Log.w(TAG, "doWork: exhausted retries posting ${type.key}")
                        remindersRepository.markHandled(type, todayEpochDay, effectiveVersion)
                        reminderScheduler.scheduleNextFromWorker(type, effectiveVersion, id)
                        Result.success()
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Error) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "doWork: failed for ${type.key} attempt=$runAttemptCount", e)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                val version = resolvedVersion
                val epochDay = resolvedEpochDay
                if (version != null && epochDay != null) {
                    val scheduled = try {
                        remindersRepository.markHandled(type, epochDay, version)
                        reminderScheduler.scheduleNextFromWorker(type, version, id)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (err: Error) {
                        throw err
                    } catch (fallbackError: Exception) {
                        Log.e(TAG, "doWork: fallback continuation failed for ${type.key}", fallbackError)
                        false
                    }
                    if (scheduled) Result.success() else Result.failure()
                } else {
                    Result.failure()
                }
            }
        }
    }

    companion object {
        private const val TAG = "CompanionReminderWorker"
        const val KEY_REMINDER_TYPE = "reminder_type"
        const val KEY_SCHEDULE_VERSION = "schedule_version"
        const val VERSION_UNSPECIFIED = -1L
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
