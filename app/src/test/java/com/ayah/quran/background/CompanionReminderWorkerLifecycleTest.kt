package com.ayah.quran.background

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ayah.quran.data.companion.CompanionReminderTypeLocks
import com.ayah.quran.data.companion.ReminderWorkEnqueuer
import com.ayah.quran.data.companion.ReminderWorkSnapshot
import com.ayah.quran.data.companion.WorkManagerCompanionReminderScheduler
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CompanionReminderWorkerLifecycleTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun runningWorkerSchedulesTomorrowWithAppendOrReplaceWithoutSelfCancellation() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        var now = ZonedDateTime.of(2026, 11, 21, 6, 59, 58, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } returns true
        }

        val cfg = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(cfg)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val worker = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(WorkInfo.State.RUNNING, enqueuer.stateOf(parent.id))
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, enqueuer.lastPolicyFor(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName))

        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)

        val active = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        assertEquals(1, active.size)
        assertEquals(WorkInfo.State.ENQUEUED, active.single().state)
        assertTrue(active.single().id != parent.id)
        assertEquals(24 * 3_600_000L + 2_000L, active.single().initialDelayMillis)
        assertEquals(now.toLocalDate().toEpochDay(), repo.preferences.first().morningAdhkar.lastHandledEpochDay)
        verify(exactly = 1) { notifier.postReminder(CompanionReminderType.MORNING_ADHKAR) }
    }

    @Test
    fun sharedLockBlocksSettingsWriteUntilInFlightSchedulerReadAndEnqueueComplete() = runBlocking {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val enteredEnqueue = CompletableDeferred<Unit>()
        val releaseEnqueue = CompletableDeferred<Unit>()
        try {
            val typeLocks = CompanionReminderTypeLocks()
            val repo = LockedFakeRemindersRepository(typeLocks)
            val enqueuer = FakeWorkManagerEnqueuer(
                onBeforeEnqueue = { policy, _ ->
                    if (policy == ExistingWorkPolicy.REPLACE && !enteredEnqueue.isCompleted) {
                        enteredEnqueue.complete(Unit)
                        releaseEnqueue.await()
                    }
                },
            )
            val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
            val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

            val n1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
            val firstSchedule = launch(dispatcher) { scheduler.schedule(n1) }
            enteredEnqueue.await()

            val writeAttempted = AtomicBoolean(false)
            val writeCommitted = AtomicBoolean(false)
            val settingsEdit = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                writeAttempted.set(true)
                val n2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 8, 30)
                writeCommitted.set(true)
                scheduler.schedule(n2)
                n2
            }

            assertTrue(writeAttempted.get())
            assertFalse(writeCommitted.get())
            assertEquals(n1.scheduleVersion, repo.preferences.first().morningAdhkar.scheduleVersion)
            assertTrue(firstSchedule.isActive)

            releaseEnqueue.complete(Unit)
            firstSchedule.join()
            val n2 = settingsEdit.await()

            assertTrue(writeCommitted.get())
            val active = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
            assertEquals(1, active.size)
            assertTrue(WorkManagerCompanionReminderScheduler.specTag(n2) in active.single().tags)
            assertEquals(n2.scheduleVersion, active.single().inputData.getLong(CompanionReminderWorker.KEY_SCHEDULE_VERSION, -1L))
        } finally {
            releaseEnqueue.complete(Unit)
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun reverseOrderRapidSettingsSchedulingKeepsLatestVersionWithoutDowngrade() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val n1 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 7, 15)
        val n2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 8, 45)

        scheduler.schedule(n2)
        val afterN2 = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        val enqueueCountAfterN2 = enqueuer.enqueueCount.get()

        scheduler.schedule(n1)
        val afterN1 = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()

        assertEquals(afterN2.id, afterN1.id)
        assertEquals(enqueueCountAfterN2, enqueuer.enqueueCount.get())
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(n2) in afterN1.tags)
    }

    @Test
    fun staleStartupAndBroadcastSnapshotsNeverOverwriteNewerPersistedSettings() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val stalePrefs = repo.preferences.first()
        val n2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 9, 15)
        scheduler.schedule(n2)

        scheduler.reconcileAll(stalePrefs, replaceExisting = false)
        val afterStartup = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(n2) in afterStartup.tags)

        scheduler.reconcileAll(stalePrefs, replaceExisting = true)
        val afterBroadcast = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(n2) in afterBroadcast.tags)
        assertEquals(n2.scheduleVersion, afterBroadcast.inputData.getLong(CompanionReminderWorker.KEY_SCHEDULE_VERSION, -1L))
    }

    @Test
    fun disableAndReEnableRacesAlwaysConvergeToAuthoritativeDataStoreState() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        val n1Enabled = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, false)
        scheduler.schedule(n1Enabled)
        assertTrue(enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).isEmpty())

        val n3Enabled = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(n3Enabled)
        val scheduledId = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single().id

        scheduler.cancel(CompanionReminderType.MORNING_ADHKAR)
        val afterStaleCancel = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        assertEquals(1, afterStaleCancel.size)
        assertEquals(scheduledId, afterStaleCancel.single().id)
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(n3Enabled) in afterStaleCancel.single().tags)
    }

    @Test
    fun startupReconciliationPreservesValidRunningWorkerAndReplacesStaleEnqueuedWork() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val running = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)
        assertEquals(WorkInfo.State.RUNNING, enqueuer.stateOf(running.id))

        enqueuer.completeRunning(running.id, WorkInfo.State.SUCCEEDED)
        scheduler.schedule(v1)
        val staleEnqueuedId = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single().id

        val v2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 8, 20)
        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)

        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(staleEnqueuedId))
        val repaired = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(repaired.id != staleEnqueuedId)
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(v2) in repaired.tags)
    }

    @Test
    fun staleWorkerSkipsNotificationAndSelfHealsMissingNewerSchedule() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier>(relaxed = true)

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val runningV1 = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val v2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 9, 0)

        val worker = buildWorker(
            id = runningV1.id,
            inputData = runningV1.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { notifier.postReminder(any()) }

        val active = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(WorkInfo.State.ENQUEUED, active.state)
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(v2) in active.tags)
    }

    @Test
    fun notificationCriticalSectionHoldsSharedLockAcrossPrePostValidationAndNotify() = runBlocking {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val insidePost = CountDownLatch(1)
        val allowPostFinish = CountDownLatch(1)
        try {
            val typeLocks = CompanionReminderTypeLocks()
            val repo = LockedFakeRemindersRepository(typeLocks)
            val enqueuer = FakeWorkManagerEnqueuer()
            val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
            val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

            val writeAttempted = AtomicBoolean(false)
            val writeCommittedDuringPost = AtomicBoolean(false)

            val notifier = mockk<CompanionReminderNotifier> {
                every { canPostNotifications() } returns true
                every { postReminder(CompanionReminderType.MORNING_ADHKAR) } answers {
                    insidePost.countDown()
                    assertTrue(allowPostFinish.await(5, TimeUnit.SECONDS))
                    true
                }
            }

            val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
            scheduler.schedule(v1)
            val runningV1 = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

            val worker = buildWorker(
                id = runningV1.id,
                inputData = runningV1.inputData,
                repo = repo,
                scheduler = scheduler,
                notifier = notifier,
                typeLocks = typeLocks,
                nowProvider = { now },
            )

            val workerJob = async(dispatcher) { worker.doWork() }
            assertTrue(insidePost.await(5, TimeUnit.SECONDS))

            val concurrentEdit = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                writeAttempted.set(true)
                val v2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 10, 0)
                writeCommittedDuringPost.set(true)
                scheduler.schedule(v2)
                v2
            }

            assertTrue(writeAttempted.get())
            assertFalse(writeCommittedDuringPost.get())
            assertEquals(v1.scheduleVersion, repo.preferences.first().morningAdhkar.scheduleVersion)
            assertTrue(workerJob.isActive)

            allowPostFinish.countDown()
            val workerResult = workerJob.await()
            val v2 = concurrentEdit.await()

            assertEquals(ListenableWorker.Result.success(), workerResult)
            assertTrue(writeCommittedDuringPost.get())
            assertNull(repo.preferences.first().morningAdhkar.lastHandledEpochDay)
            val active = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
            assertTrue(WorkManagerCompanionReminderScheduler.specTag(v2) in active.tags)
        } finally {
            allowPostFinish.countDown()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun conditionalMarkHandledDoesNotPolluteNewerVersion() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val v2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 8, 30)

        repo.markHandled(CompanionReminderType.MORNING_ADHKAR, epochDay = 20_778L, expectedVersion = v1.scheduleVersion)
        assertNull(repo.preferences.first().morningAdhkar.lastHandledEpochDay)

        repo.markHandled(CompanionReminderType.MORNING_ADHKAR, epochDay = 20_778L, expectedVersion = v2.scheduleVersion)
        assertEquals(20_778L, repo.preferences.first().morningAdhkar.lastHandledEpochDay)
    }

    @Test
    fun permissionDeniedSkipsPostDoesNotRetryMarksHandledAndSchedulesTomorrow() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 59, 55, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns false
        }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val worker = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { notifier.postReminder(any()) }
        assertEquals(now.toLocalDate().toEpochDay(), repo.preferences.first().morningAdhkar.lastHandledEpochDay)

        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)
        val child = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(24 * 3_600_000L + 5_000L, child.initialDelayMillis)

        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = true)
        val afterReconcile = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(24 * 3_600_000L + 5_000L, afterReconcile.initialDelayMillis)
    }

    @Test
    fun postFailureRetriesUpToCapThenPreservesFutureScheduleOnExhaustion() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } returns false
        }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val attempt0 = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = 0,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.retry(), attempt0.doWork())
        assertEquals(1, enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).size)
        assertNull(repo.preferences.first().morningAdhkar.lastHandledEpochDay)

        val attemptExhausted = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = CompanionReminderWorker.MAX_RETRY_ATTEMPTS,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), attemptExhausted.doWork())
        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)

        val tomorrow = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(WorkInfo.State.ENQUEUED, tomorrow.state)
        assertEquals(now.toLocalDate().toEpochDay(), repo.preferences.first().morningAdhkar.lastHandledEpochDay)
    }

    @Test
    fun stoppedWorkerThrowsCancellationWithoutPostingOrScheduling() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier>(relaxed = true)

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val worker = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            stopped = true,
            nowProvider = { now },
        )

        var caught: CancellationException? = null
        try {
            worker.doWork()
        } catch (ce: CancellationException) {
            caught = ce
        }

        assertTrue(caught != null)
        verify(exactly = 0) { notifier.postReminder(any()) }
        assertNull(repo.preferences.first().morningAdhkar.lastHandledEpochDay)
        assertEquals(1, enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).size)
    }

    @Test
    fun processDeathAfterChildAppendDoesNotDuplicateChildOrNotificationOnRestart() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } returns true
        }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val firstRun = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), firstRun.doWork())

        val childBeforeCrash = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
            .single { it.state == WorkInfo.State.BLOCKED }

        enqueuer.simulateProcessRestartWithUnfinishedParent(parent.id)
        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)
        assertEquals(WorkInfo.State.BLOCKED, enqueuer.stateOf(childBeforeCrash.id))

        val restartedParent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        assertEquals(parent.id, restartedParent.id)

        val secondRun = buildWorker(
            id = restartedParent.id,
            inputData = restartedParent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), secondRun.doWork())
        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)

        verify(exactly = 1) { notifier.postReminder(CompanionReminderType.MORNING_ADHKAR) }
        val activeAfter = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        assertEquals(1, activeAfter.size)
        assertEquals(childBeforeCrash.id, activeAfter.single().id)
        assertEquals(WorkInfo.State.ENQUEUED, activeAfter.single().state)
    }

    @Test
    fun transientEnqueueFailureAfterPostRetriesIdempotentlyWithoutDuplicateNotification() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val failFirstAppend = AtomicBoolean(true)
        val enqueuer = FakeWorkManagerEnqueuer(
            onBeforeEnqueue = { policy, _ ->
                if (policy == ExistingWorkPolicy.APPEND_OR_REPLACE && failFirstAppend.compareAndSet(true, false)) {
                    throw IOException("transient sqlite failure")
                }
            },
        )
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } returns true
        }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val attempt0 = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = 0,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.retry(), attempt0.doWork())
        verify(exactly = 1) { notifier.postReminder(CompanionReminderType.MORNING_ADHKAR) }

        val attempt1 = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = 1,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), attempt1.doWork())
        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)

        verify(exactly = 1) { notifier.postReminder(CompanionReminderType.MORNING_ADHKAR) }
        assertEquals(1, enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).size)
    }

    @Test
    fun disabledReminderAndLegacyUnversionedWorkAreHandledCleanly() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } returns true
        }

        val disabledWorker = buildWorker(
            id = UUID.randomUUID(),
            inputData = workDataOf(CompanionReminderWorker.KEY_REMINDER_TYPE to CompanionReminderType.MORNING_ADHKAR.key),
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), disabledWorker.doWork())
        verify(exactly = 0) { notifier.postReminder(any()) }
        assertTrue(enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).isEmpty())

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val legacyId = enqueuer.insertLegacyRunningWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        val legacyWorker = buildWorker(
            id = legacyId,
            inputData = workDataOf(CompanionReminderWorker.KEY_REMINDER_TYPE to CompanionReminderType.MORNING_ADHKAR.key),
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), legacyWorker.doWork())
        enqueuer.completeRunning(legacyId, WorkInfo.State.SUCCEEDED)

        verify(exactly = 1) { notifier.postReminder(CompanionReminderType.MORNING_ADHKAR) }
        val upgraded = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(v1) in upgraded.tags)
        assertEquals(v1.scheduleVersion, upgraded.inputData.getLong(CompanionReminderWorker.KEY_SCHEDULE_VERSION, -1L))
    }

    @Test
    fun postReminderRuntimeExceptionRetriesThenRecoversFutureScheduleOnExhaustion() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val notifier = mockk<CompanionReminderNotifier> {
            every { canPostNotifications() } returns true
            every { postReminder(CompanionReminderType.MORNING_ADHKAR) } throws RuntimeException("notification manager failure")
        }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val parent = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val initialAttempt = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = 0,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.retry(), initialAttempt.doWork())
        assertNull(repo.preferences.first().morningAdhkar.lastHandledEpochDay)
        assertEquals(1, enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).size)

        val exhaustedAttempt = buildWorker(
            id = parent.id,
            inputData = parent.inputData,
            repo = repo,
            scheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
            runAttemptCount = CompanionReminderWorker.MAX_RETRY_ATTEMPTS,
            nowProvider = { now },
        )
        assertEquals(ListenableWorker.Result.success(), exhaustedAttempt.doWork())
        assertEquals(now.toLocalDate().toEpochDay(), repo.preferences.first().morningAdhkar.lastHandledEpochDay)

        enqueuer.completeRunning(parent.id, WorkInfo.State.SUCCEEDED)
        val tomorrow = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(WorkInfo.State.ENQUEUED, tomorrow.state)
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(v1) in tomorrow.tags)
        assertEquals(24 * 3_600_000L, tomorrow.initialDelayMillis)
    }

    @Test
    fun startupReconciliationRepairsAnomalousDuplicateBlockedOrRunningChains() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = LockedFakeRemindersRepository(typeLocks)
        val enqueuer = FakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, utc)
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val uniqueName = CompanionReminderType.MORNING_ADHKAR.uniqueWorkName
        val validTags = setOf(
            WorkManagerCompanionReminderScheduler.WORK_TAG,
            uniqueName,
            WorkManagerCompanionReminderScheduler.specTag(v1),
        )

        val runningId = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.RUNNING, validTags)
        val blocked1 = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.BLOCKED, validTags)
        val blocked2 = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.BLOCKED, validTags)
        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)

        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(runningId))
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(blocked1))
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(blocked2))
        val repairedFromDupBlocked = enqueuer.activeEntries(uniqueName)
        assertEquals(1, repairedFromDupBlocked.size)
        assertEquals(WorkInfo.State.ENQUEUED, repairedFromDupBlocked.single().state)

        enqueuer.cancelUniqueWork(uniqueName)
        val run1 = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.RUNNING, validTags)
        val run2 = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.RUNNING, validTags)
        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)

        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(run1))
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(run2))
        val repairedFromDupRunning = enqueuer.activeEntries(uniqueName)
        assertEquals(1, repairedFromDupRunning.size)
        assertEquals(WorkInfo.State.ENQUEUED, repairedFromDupRunning.single().state)

        enqueuer.cancelUniqueWork(uniqueName)
        val orphanedBlocked = enqueuer.insertCustomWork(uniqueName, WorkInfo.State.BLOCKED, validTags)
        scheduler.reconcileAll(repo.preferences.first(), replaceExisting = false)

        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(orphanedBlocked))
        val repairedFromOrphan = enqueuer.activeEntries(uniqueName)
        assertEquals(1, repairedFromOrphan.size)
        assertEquals(WorkInfo.State.ENQUEUED, repairedFromOrphan.single().state)
    }

    private fun buildWorker(
        id: UUID,
        inputData: Data,
        repo: CompanionRemindersRepository,
        scheduler: WorkManagerCompanionReminderScheduler,
        notifier: CompanionReminderNotifier,
        typeLocks: CompanionReminderTypeLocks,
        runAttemptCount: Int = 0,
        stopped: Boolean = false,
        nowProvider: () -> ZonedDateTime,
    ): CompanionReminderWorker {
        val context = mockk<Context>(relaxed = true)
        val params = mockk<WorkerParameters>(relaxed = true) {
            every { getId() } returns id
            every { getInputData() } returns inputData
            every { getRunAttemptCount() } returns runAttemptCount
        }
        return CompanionReminderWorker(
            appContext = context,
            workerParams = params,
            remindersRepository = repo,
            reminderScheduler = scheduler,
            notifier = notifier,
            typeLocks = typeLocks,
        ).apply {
            this.nowProvider = nowProvider
            if (stopped) {
                stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
            }
        }
    }
}

private class LockedFakeRemindersRepository(
    private val typeLocks: CompanionReminderTypeLocks,
) : CompanionRemindersRepository {
    private val flow = MutableStateFlow(CompanionRemindersPreferences())
    override val preferences: Flow<CompanionRemindersPreferences> = flow

    override suspend fun setEnabled(type: CompanionReminderType, enabled: Boolean): CompanionReminderConfig =
        typeLocks.withTypeLock(type) {
            val current = flow.value.configOf(type)
            val next = current.copy(
                enabled = enabled,
                scheduleVersion = current.scheduleVersion + 1L,
                lastHandledEpochDay = if (enabled && !current.enabled) null else current.lastHandledEpochDay,
            )
            flow.value = flow.value.withConfig(next)
            next
        }

    override suspend fun setTime(type: CompanionReminderType, hour: Int, minute: Int): CompanionReminderConfig =
        typeLocks.withTypeLock(type) {
            val current = flow.value.configOf(type)
            val next = current.copy(
                hour = hour,
                minute = minute,
                scheduleVersion = current.scheduleVersion + 1L,
                lastHandledEpochDay = null,
            )
            flow.value = flow.value.withConfig(next)
            next
        }

    override suspend fun markHandled(type: CompanionReminderType, epochDay: Long, expectedVersion: Long) {
        typeLocks.withTypeLock(type) {
            val current = flow.value.configOf(type)
            if (current.scheduleVersion == expectedVersion) {
                flow.value = flow.value.withConfig(current.copy(lastHandledEpochDay = epochDay))
            }
        }
    }

    override suspend fun markPermissionRequested() {
        flow.value = flow.value.copy(permissionRequestedBefore = true)
    }

    private fun CompanionRemindersPreferences.withConfig(
        config: CompanionReminderConfig,
    ): CompanionRemindersPreferences = when (config.type) {
        CompanionReminderType.MORNING_ADHKAR -> copy(morningAdhkar = config)
        CompanionReminderType.EVENING_ADHKAR -> copy(eveningAdhkar = config)
        CompanionReminderType.DAILY_REFLECTION -> copy(dailyReflection = config)
    }
}

private data class FakeWorkEntry(
    val id: UUID,
    val uniqueWorkName: String,
    var state: WorkInfo.State,
    val tags: Set<String>,
    val inputData: Data,
    val initialDelayMillis: Long,
    var prerequisiteId: UUID? = null,
)

private class FakeWorkManagerEnqueuer(
    private val onBeforeEnqueue: (suspend (ExistingWorkPolicy, OneTimeWorkRequest) -> Unit)? = null,
) : ReminderWorkEnqueuer {

    private val entries = mutableListOf<FakeWorkEntry>()
    private val lastPolicies = mutableMapOf<String, ExistingWorkPolicy>()
    val enqueueCount = AtomicInteger(0)

    override suspend fun getWorkInfosForUniqueWork(uniqueWorkName: String): List<ReminderWorkSnapshot> =
        synchronized(entries) {
            entries.filter { it.uniqueWorkName == uniqueWorkName }
                .map { ReminderWorkSnapshot(it.id, it.state, it.tags) }
        }

    override suspend fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ) {
        onBeforeEnqueue?.invoke(policy, request)
        synchronized(entries) {
            enqueueCount.incrementAndGet()
            lastPolicies[uniqueWorkName] = policy
            val active = entries.filter { it.uniqueWorkName == uniqueWorkName && !it.state.isFinished }
            val spec = request.workSpec
            val newEntry = FakeWorkEntry(
                id = request.id,
                uniqueWorkName = uniqueWorkName,
                state = WorkInfo.State.ENQUEUED,
                tags = request.tags,
                inputData = spec.input,
                initialDelayMillis = spec.initialDelay,
            )
            when (policy) {
                ExistingWorkPolicy.REPLACE -> {
                    active.forEach { it.state = WorkInfo.State.CANCELLED }
                    entries.add(newEntry)
                }
                ExistingWorkPolicy.KEEP -> {
                    if (active.isEmpty()) {
                        entries.add(newEntry)
                    }
                }
                ExistingWorkPolicy.APPEND,
                ExistingWorkPolicy.APPEND_OR_REPLACE -> {
                    if (active.isEmpty()) {
                        entries.add(newEntry)
                    } else {
                        newEntry.state = WorkInfo.State.BLOCKED
                        newEntry.prerequisiteId = active.last().id
                        entries.add(newEntry)
                    }
                }
            }
        }
    }

    override suspend fun cancelUniqueWork(uniqueWorkName: String) {
        synchronized(entries) {
            entries.filter { it.uniqueWorkName == uniqueWorkName && !it.state.isFinished }
                .forEach { it.state = WorkInfo.State.CANCELLED }
        }
    }

    fun startNextEnqueued(uniqueWorkName: String): FakeWorkEntry = synchronized(entries) {
        val next = entries.first { it.uniqueWorkName == uniqueWorkName && it.state == WorkInfo.State.ENQUEUED }
        next.state = WorkInfo.State.RUNNING
        next
    }

    fun completeRunning(id: UUID, terminalState: WorkInfo.State) {
        synchronized(entries) {
            val entry = entries.first { it.id == id }
            entry.state = terminalState
            entries.filter { it.prerequisiteId == id && it.state == WorkInfo.State.BLOCKED }
                .forEach { child ->
                    child.state = if (terminalState == WorkInfo.State.SUCCEEDED) {
                        WorkInfo.State.ENQUEUED
                    } else {
                        terminalState
                    }
                }
        }
    }

    fun simulateProcessRestartWithUnfinishedParent(parentId: UUID) {
        synchronized(entries) {
            val entry = entries.first { it.id == parentId }
            entry.state = WorkInfo.State.ENQUEUED
        }
    }

    fun insertLegacyRunningWork(uniqueWorkName: String): UUID =
        insertCustomWork(
            uniqueWorkName = uniqueWorkName,
            state = WorkInfo.State.RUNNING,
            tags = setOf(WorkManagerCompanionReminderScheduler.WORK_TAG, uniqueWorkName),
        )

    fun insertCustomWork(
        uniqueWorkName: String,
        state: WorkInfo.State,
        tags: Set<String>,
    ): UUID = synchronized(entries) {
        val id = UUID.randomUUID()
        entries.add(
            FakeWorkEntry(
                id = id,
                uniqueWorkName = uniqueWorkName,
                state = state,
                tags = tags,
                inputData = workDataOf(CompanionReminderWorker.KEY_REMINDER_TYPE to CompanionReminderType.MORNING_ADHKAR.key),
                initialDelayMillis = 0L,
            ),
        )
        id
    }

    fun stateOf(id: UUID): WorkInfo.State = synchronized(entries) {
        entries.first { it.id == id }.state
    }

    fun activeEntries(uniqueWorkName: String): List<FakeWorkEntry> = synchronized(entries) {
        entries.filter { it.uniqueWorkName == uniqueWorkName && !it.state.isFinished }
    }

    fun lastPolicyFor(uniqueWorkName: String): ExistingWorkPolicy? = synchronized(entries) {
        lastPolicies[uniqueWorkName]
    }
}
