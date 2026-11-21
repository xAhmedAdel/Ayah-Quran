package com.ayah.quran.data.companion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.await
import androidx.work.workDataOf
import com.ayah.quran.background.CompanionReminderWorker
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderTimeCalculator
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class ReminderWorkSnapshot(
    val id: UUID,
    val state: WorkInfo.State,
    val tags: Set<String>,
)

internal interface ReminderWorkEnqueuer {
    suspend fun getWorkInfosForUniqueWork(uniqueWorkName: String): List<ReminderWorkSnapshot>
    suspend fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    )
    suspend fun cancelUniqueWork(uniqueWorkName: String)
}

private class WorkManagerReminderWorkEnqueuer(
    private val context: Context,
) : ReminderWorkEnqueuer {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override suspend fun getWorkInfosForUniqueWork(uniqueWorkName: String): List<ReminderWorkSnapshot> =
        workManager.getWorkInfosForUniqueWork(uniqueWorkName)
            .awaitResult()
            .map { ReminderWorkSnapshot(it.id, it.state, it.tags) }

    override suspend fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ) {
        workManager.enqueueUniqueWork(uniqueWorkName, policy, request).await()
    }

    override suspend fun cancelUniqueWork(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName).await()
    }
}

private suspend fun <T> ListenableFuture<T>.awaitResult(): T {
    if (isDone) {
        return try {
            get()
        } catch (e: ExecutionException) {
            throw (e.cause ?: e)
        }
    }
    return suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: ExecutionException) {
                    cont.resumeWithException(e.cause ?: e)
                } catch (ce: CancellationException) {
                    cont.cancel(ce)
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            Executor { command -> command.run() },
        )
        cont.invokeOnCancellation {
            cancel(false)
        }
    }
}

@Singleton
class WorkManagerCompanionReminderScheduler internal constructor(
    private val remindersRepository: CompanionRemindersRepository,
    private val typeLocks: CompanionReminderTypeLocks,
    private val enqueuer: ReminderWorkEnqueuer,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : CompanionReminderScheduler {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        remindersRepository: CompanionRemindersRepository,
        typeLocks: CompanionReminderTypeLocks,
    ) : this(
        remindersRepository = remindersRepository,
        typeLocks = typeLocks,
        enqueuer = WorkManagerReminderWorkEnqueuer(context),
    )

    internal constructor(
        context: Context,
        remindersRepository: CompanionRemindersRepository,
        typeLocks: CompanionReminderTypeLocks,
        nowProvider: () -> ZonedDateTime,
    ) : this(
        remindersRepository = remindersRepository,
        typeLocks = typeLocks,
        enqueuer = WorkManagerReminderWorkEnqueuer(context),
        nowProvider = nowProvider,
    )

    override suspend fun schedule(config: CompanionReminderConfig) {
        reconcile(config.type, replaceExisting = false)
    }

    override suspend fun cancel(type: CompanionReminderType) {
        reconcile(type, replaceExisting = false)
    }

    override suspend fun reconcileAll(
        preferences: CompanionRemindersPreferences,
        replaceExisting: Boolean,
    ) {
        CompanionReminderType.entries.forEach { type ->
            reconcile(type, replaceExisting = replaceExisting)
        }
    }

    override suspend fun reconcile(
        type: CompanionReminderType,
        replaceExisting: Boolean,
    ) {
        typeLocks.withTypeLock(type) {
            val latest = remindersRepository.preferences.first().configOf(type)
            if (!latest.enabled) {
                enqueuer.cancelUniqueWork(type.uniqueWorkName)
                return@withTypeLock
            }

            if (!replaceExisting) {
                val existing = enqueuer.getWorkInfosForUniqueWork(type.uniqueWorkName)
                if (isHealthyCurrentChain(existing, specTag(latest))) {
                    return@withTypeLock
                }
            }

            val earliestDate = latest.lastHandledEpochDay?.let { LocalDate.ofEpochDay(it).plusDays(1) }
            val request = buildWorkRequest(latest, earliestDate)
            enqueuer.enqueueUniqueWork(
                type.uniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override suspend fun scheduleNextFromWorker(
        type: CompanionReminderType,
        expectedVersion: Long,
        currentWorkId: UUID,
    ): Boolean = typeLocks.withTypeLock(type) {
        currentCoroutineContext().ensureActive()
        val latest = remindersRepository.preferences.first().configOf(type)
        if (!latest.enabled || latest.scheduleVersion != expectedVersion) {
            return@withTypeLock false
        }

        val existing = enqueuer.getWorkInfosForUniqueWork(type.uniqueWorkName)
        val expectedTag = specTag(latest)
        val active = existing.filter { !it.state.isFinished }

        if (active.any { it.id != currentWorkId && expectedTag !in it.tags }) {
            return@withTypeLock false
        }
        if (active.any {
                it.id != currentWorkId &&
                    expectedTag in it.tags &&
                    (it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.ENQUEUED)
            }
        ) {
            return@withTypeLock true
        }
        if (active.none { it.id == currentWorkId && it.state == WorkInfo.State.RUNNING }) {
            return@withTypeLock false
        }

        val today = nowProvider().toLocalDate()
        val handledNextDay = latest.lastHandledEpochDay?.let { LocalDate.ofEpochDay(it).plusDays(1) }
        val minDate = if (handledNextDay != null && handledNextDay.isAfter(today.plusDays(1))) {
            handledNextDay
        } else {
            today.plusDays(1)
        }
        val request = buildWorkRequest(latest, earliestDate = minDate)
        enqueuer.enqueueUniqueWork(
            type.uniqueWorkName,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        true
    }

    private fun isHealthyCurrentChain(
        existing: List<ReminderWorkSnapshot>,
        expectedTag: String,
    ): Boolean {
        val active = existing.filter { !it.state.isFinished }
        if (active.isEmpty()) return false
        if (!active.all { expectedTag in it.tags }) return false
        val runningCount = active.count { it.state == WorkInfo.State.RUNNING }
        val enqueuedCount = active.count { it.state == WorkInfo.State.ENQUEUED }
        val blockedCount = active.count { it.state == WorkInfo.State.BLOCKED }
        val headCount = runningCount + enqueuedCount
        return headCount == 1 && blockedCount <= 1 && active.size == headCount + blockedCount
    }

    private fun buildWorkRequest(
        config: CompanionReminderConfig,
        earliestDate: LocalDate?,
    ): OneTimeWorkRequest {
        val delayMillis = CompanionReminderTimeCalculator.computeInitialDelayMillis(
            now = nowProvider(),
            hour = config.hour,
            minute = config.minute,
            earliestDate = earliestDate,
        )
        return OneTimeWorkRequestBuilder<CompanionReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .setInputData(
                workDataOf(
                    CompanionReminderWorker.KEY_REMINDER_TYPE to config.type.key,
                    CompanionReminderWorker.KEY_SCHEDULE_VERSION to config.scheduleVersion,
                ),
            )
            .addTag(WORK_TAG)
            .addTag(config.type.uniqueWorkName)
            .addTag(specTag(config))
            .build()
    }

    companion object {
        const val WORK_TAG = "companion_reminders"

        fun specTag(config: CompanionReminderConfig): String =
            "companion_reminder_spec_${config.scheduleVersion}_${config.hour}_${config.minute}"
    }
}
