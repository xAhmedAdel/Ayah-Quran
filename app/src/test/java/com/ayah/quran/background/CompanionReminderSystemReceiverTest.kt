package com.ayah.quran.background

import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.ayah.quran.data.companion.CompanionReminderTypeLocks
import com.ayah.quran.data.companion.ReminderWorkEnqueuer
import com.ayah.quran.data.companion.ReminderWorkSnapshot
import com.ayah.quran.data.companion.WorkManagerCompanionReminderScheduler
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

class CompanionReminderSystemReceiverTest {

    @Test
    fun actionPolicyAcceptsOnlySupportedActionsAndMapsForceReplaceCorrectly() {
        assertTrue(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_TIMEZONE_CHANGED))
        assertTrue(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_DATE_CHANGED))
        assertTrue(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertFalse(CompanionReminderSystemReceiver.isSupportedAction(null))
        assertFalse(CompanionReminderSystemReceiver.isSupportedAction(""))
        assertFalse(CompanionReminderSystemReceiver.isSupportedAction("com.example.UNKNOWN"))
        assertFalse(CompanionReminderSystemReceiver.isSupportedAction(Intent.ACTION_LOCKED_BOOT_COMPLETED))

        assertFalse(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_TIME_CHANGED))
        assertTrue(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_TIMEZONE_CHANGED))
        assertFalse(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_DATE_CHANGED))
        assertFalse(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(CompanionReminderSystemReceiver.shouldForceReplace(null))
        assertFalse(CompanionReminderSystemReceiver.shouldForceReplace(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    }

    @Test
    fun successfulReconciliationCompletesAndFinishesExactlyOnce() = runBlocking {
        val events = mutableListOf<String>()
        val finishCount = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val job = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = Dispatchers.Unconfined,
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { forceReplace ->
                    events += "reconcile:$forceReplace"
                },
                onFinish = {
                    finishCount.incrementAndGet()
                    events += "finish"
                },
            )
            assertNotNull(job)
            job!!.join()
            assertEquals(1, finishCount.get())
            assertEquals(listOf("reconcile:false", "finish"), events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ordinaryExceptionIsHandledAndFinishesExactlyOnce() = runBlocking {
        val finishCount = AtomicInteger(0)
        val uncaught = AtomicReference<Throwable?>(null)
        val handler = CoroutineExceptionHandler { _, throwable -> uncaught.set(throwable) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        try {
            val job = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = Dispatchers.Unconfined,
                action = Intent.ACTION_TIME_CHANGED,
                reconcile = { throw IOException("simulated storage failure") },
                onFinish = { finishCount.incrementAndGet() },
            )
            assertNotNull(job)
            job!!.join()
            assertEquals(1, finishCount.get())
            assertNull(uncaught.get())
        } finally {
            scope.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun singleTotalTimeoutCapsReconciliationAndFinishesExactlyOnce() = runTest {
        val finishCount = AtomicInteger(0)
        val stepsStarted = AtomicInteger(0)
        val uncaught = AtomicReference<Throwable?>(null)
        val handler = CoroutineExceptionHandler { _, throwable -> uncaught.set(throwable) }
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + testDispatcher + handler)
        try {
            val job = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = testDispatcher,
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = {
                    repeat(CompanionReminderType.entries.size) {
                        stepsStarted.incrementAndGet()
                        delay(5_000L)
                    }
                },
                onFinish = { finishCount.incrementAndGet() },
            )
            assertNotNull(job)
            advanceUntilIdle()
            assertTrue(job!!.isCompleted)
            assertEquals(1, finishCount.get())
            assertNull(uncaught.get())
            assertEquals(2, stepsStarted.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun externalCancellationPropagatesAndStillFinishesExactlyOnce() = runBlocking {
        val finishCount = AtomicInteger(0)
        var propagated: CancellationException? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { throw CancellationException("external cancel") },
                onFinish = { finishCount.incrementAndGet() },
            )
        } catch (ce: CancellationException) {
            propagated = ce
        }
        assertNotNull(propagated)
        assertEquals(1, finishCount.get())
    }

    @Test
    fun fatalErrorPropagatesAndStillFinishesExactlyOnce() = runBlocking {
        val finishCount = AtomicInteger(0)
        var propagated: AssertionError? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { throw AssertionError("fatal error") },
                onFinish = { finishCount.incrementAndGet() },
            )
        } catch (err: AssertionError) {
            propagated = err
        }
        assertNotNull(propagated)
        assertEquals(1, finishCount.get())
    }

    @Test
    fun cancelledScopeCompletionFallbackFinishesExactlyOnceWithoutRunningBody() = runBlocking {
        val finishCount = AtomicInteger(0)
        val bodyRan = AtomicBoolean(false)
        val cancelledScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        cancelledScope.cancel()

        val job = CompanionReminderSystemReceiver.launchReconciliation(
            scope = cancelledScope,
            dispatcher = Dispatchers.Default,
            action = Intent.ACTION_BOOT_COMPLETED,
            reconcile = { bodyRan.set(true) },
            onFinish = { finishCount.incrementAndGet() },
        )
        job?.join()

        assertFalse(bodyRan.get())
        assertEquals(1, finishCount.get())
    }

    @Test
    fun rejectedDispatchFinishesExactlyOnceWithoutCrashing() = runBlocking {
        val finishCount = AtomicInteger(0)
        val bodyRan = AtomicBoolean(false)
        val rejectingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                throw RejectedExecutionException("executor rejected")
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val job = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = rejectingDispatcher,
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { bodyRan.set(true) },
                onFinish = { finishCount.incrementAndGet() },
            )
            job?.join()
            assertFalse(bodyRan.get())
            assertEquals(1, finishCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun bootAndPackageReplacedPreserveHealthyWorkRepairMissingOrStaleAndAreIdempotent() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = ReceiverFakeRemindersRepository(typeLocks)
        val enqueuer = ReceiverFakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, ZoneId.of("UTC"))
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        val morningV1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val eveningV1 = repo.setEnabled(CompanionReminderType.EVENING_ADHKAR, true)
        scheduler.schedule(morningV1)
        val runningMorning = enqueuer.startNextEnqueued(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)

        val finishCount = AtomicInteger(0)
        runReceiverAction(Intent.ACTION_BOOT_COMPLETED, scheduler, finishCount)
        assertEquals(1, finishCount.get())
        assertEquals(WorkInfo.State.RUNNING, enqueuer.stateOf(runningMorning.id))
        val repairedEvening = enqueuer.activeEntries(CompanionReminderType.EVENING_ADHKAR.uniqueWorkName).single()
        assertEquals(WorkInfo.State.ENQUEUED, repairedEvening.state)
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(eveningV1) in repairedEvening.tags)
        assertTrue(enqueuer.activeEntries(CompanionReminderType.DAILY_REFLECTION.uniqueWorkName).isEmpty())

        val enqueuesAfterFirstBoot = enqueuer.enqueueCount.get()
        runReceiverAction(Intent.ACTION_BOOT_COMPLETED, scheduler, finishCount)
        assertEquals(2, finishCount.get())
        assertEquals(enqueuesAfterFirstBoot, enqueuer.enqueueCount.get())
        assertEquals(WorkInfo.State.RUNNING, enqueuer.stateOf(runningMorning.id))

        enqueuer.completeRunning(runningMorning.id, WorkInfo.State.SUCCEEDED)
        val legacyMorningId = enqueuer.insertLegacyWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName)
        runReceiverAction(Intent.ACTION_MY_PACKAGE_REPLACED, scheduler, finishCount)
        assertEquals(3, finishCount.get())
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(legacyMorningId))
        val upgradedMorning = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(morningV1) in upgradedMorning.tags)

        val enqueuesAfterUpgrade = enqueuer.enqueueCount.get()
        runReceiverAction(Intent.ACTION_MY_PACKAGE_REPLACED, scheduler, finishCount)
        assertEquals(enqueuesAfterUpgrade, enqueuer.enqueueCount.get())
    }

    @Test
    fun timeAndTimezoneChangesForceReplacementAndRecomputeDelays() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = ReceiverFakeRemindersRepository(typeLocks)
        val enqueuer = ReceiverFakeWorkManagerEnqueuer()
        val clock = AtomicReference(ZonedDateTime.of(2026, 11, 21, 5, 0, 0, 0, ZoneId.of("UTC")))
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { clock.get() }

        val morning = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(morning)
        val initial = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertEquals(2 * 3_600_000L, initial.initialDelayMillis)

        clock.set(ZonedDateTime.of(2026, 11, 21, 6, 30, 0, 0, ZoneId.of("UTC")))
        val finishCount = AtomicInteger(0)
        runReceiverAction(Intent.ACTION_TIME_CHANGED, scheduler, finishCount)
        assertEquals(1, finishCount.get())
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(initial.id))
        val afterTimeChange = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(afterTimeChange.id != initial.id)
        assertEquals(30 * 60_000L, afterTimeChange.initialDelayMillis)

        clock.set(ZonedDateTime.of(2026, 11, 21, 6, 30, 0, 0, ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/New_York")))
        runReceiverAction(Intent.ACTION_TIMEZONE_CHANGED, scheduler, finishCount)
        assertEquals(2, finishCount.get())
        assertEquals(WorkInfo.State.CANCELLED, enqueuer.stateOf(afterTimeChange.id))
        val afterTzChange = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
        assertTrue(afterTzChange.id != afterTimeChange.id)
        assertEquals(5 * 3_600_000L + 30 * 60_000L, afterTzChange.initialDelayMillis)
    }

    @Test
    fun disabledRemindersRemainCancelledAcrossAllSupportedSystemActions() = runBlocking {
        val typeLocks = CompanionReminderTypeLocks()
        val repo = ReceiverFakeRemindersRepository(typeLocks)
        val enqueuer = ReceiverFakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, ZoneId.of("UTC"))
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

        val morning = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(morning)
        repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, false)

        val finishCount = AtomicInteger(0)
        CompanionReminderSystemReceiver.SUPPORTED_ACTIONS.forEach { action ->
            runReceiverAction(action, scheduler, finishCount)
            CompanionReminderType.entries.forEach { type ->
                assertTrue(
                    "Expected no active work for ${type.key} after $action",
                    enqueuer.activeEntries(type.uniqueWorkName).isEmpty(),
                )
            }
        }
        assertEquals(CompanionReminderSystemReceiver.SUPPORTED_ACTIONS.size, finishCount.get())
    }

    @Test
    fun concurrentSettingsUpdateRacingWithSystemBroadcastConvergesToAuthoritativeState() = runBlocking {
        val executor = Executors.newFixedThreadPool(2)
        val dispatcher = executor.asCoroutineDispatcher()
        val enteredBroadcastEnqueue = CompletableDeferred<Unit>()
        val releaseBroadcastEnqueue = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val typeLocks = CompanionReminderTypeLocks()
            val repo = ReceiverFakeRemindersRepository(typeLocks)
            val enqueuer = ReceiverFakeWorkManagerEnqueuer(
                onBeforeEnqueue = { uniqueWorkName, policy, _ ->
                    if (
                        uniqueWorkName == CompanionReminderType.MORNING_ADHKAR.uniqueWorkName &&
                        policy == ExistingWorkPolicy.REPLACE &&
                        !enteredBroadcastEnqueue.isCompleted
                    ) {
                        enteredBroadcastEnqueue.complete(Unit)
                        releaseBroadcastEnqueue.await()
                    }
                },
            )
            val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, ZoneId.of("UTC"))
            val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }

            val v1 = repo.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
            val finishCount = AtomicInteger(0)
            val broadcastJob = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = dispatcher,
                action = Intent.ACTION_TIME_CHANGED,
                reconcile = { forceReplace ->
                    CompanionReminderType.entries.forEach { type ->
                        scheduler.reconcile(type, replaceExisting = forceReplace)
                    }
                },
                onFinish = { finishCount.incrementAndGet() },
            )!!
            enteredBroadcastEnqueue.await()

            val editAttempted = AtomicBoolean(false)
            val editCommitted = AtomicBoolean(false)
            val settingsEdit = async(dispatcher, start = CoroutineStart.UNDISPATCHED) {
                editAttempted.set(true)
                val v2 = repo.setTime(CompanionReminderType.MORNING_ADHKAR, 9, 30)
                editCommitted.set(true)
                scheduler.schedule(v2)
                v2
            }

            assertTrue(editAttempted.get())
            assertFalse(editCommitted.get())
            assertEquals(v1.scheduleVersion, repo.currentSnapshot().morningAdhkar.scheduleVersion)

            releaseBroadcastEnqueue.complete(Unit)
            broadcastJob.join()
            val v2 = settingsEdit.await()

            assertEquals(1, finishCount.get())
            assertTrue(editCommitted.get())
            val finalEntry = enqueuer.activeEntries(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).single()
            assertTrue(WorkManagerCompanionReminderScheduler.specTag(v2) in finalEntry.tags)
            assertEquals(v2.scheduleVersion, finalEntry.inputData.getLong(CompanionReminderWorker.KEY_SCHEDULE_VERSION, -1L))
        } finally {
            releaseBroadcastEnqueue.complete(Unit)
            scope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun handleSupportedActionResolvesAllDependenciesBeforeAcquiringPendingResult() = runBlocking {
        val events = mutableListOf<String>()
        val typeLocks = CompanionReminderTypeLocks()
        val repo = ReceiverFakeRemindersRepository(typeLocks)
        val enqueuer = ReceiverFakeWorkManagerEnqueuer()
        val now = ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, ZoneId.of("UTC"))
        val scheduler = WorkManagerCompanionReminderScheduler(repo, typeLocks, enqueuer) { now }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val job = CompanionReminderSystemReceiver.handleSupportedAction(
                action = Intent.ACTION_BOOT_COMPLETED,
                resolveDependencies = {
                    events += "resolve"
                    CompanionReminderSystemReceiver.ResolvedDependencies(
                        scheduler = scheduler,
                        applicationScope = scope,
                        ioDispatcher = Dispatchers.Unconfined,
                    )
                },
                acquirePendingResult = {
                    events += "goAsync"
                    val onFinish: () -> Unit = { events += "finish" }
                    onFinish
                },
            )
            assertNotNull(job)
            job!!.join()
            assertEquals(listOf("resolve", "goAsync", "finish"), events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun handleSupportedActionNeverAcquiresPendingResultWhenDependencyResolutionFails() {
        val acquireCount = AtomicInteger(0)
        val noopFinish: () -> Unit = {}
        val job = CompanionReminderSystemReceiver.handleSupportedAction(
            action = Intent.ACTION_BOOT_COMPLETED,
            resolveDependencies = { throw IllegalStateException("provider failure") },
            acquirePendingResult = {
                acquireCount.incrementAndGet()
                noopFinish
            },
        )
        assertNull(job)
        assertEquals(0, acquireCount.get())

        var propagatedError: AssertionError? = null
        try {
            CompanionReminderSystemReceiver.handleSupportedAction(
                action = Intent.ACTION_BOOT_COMPLETED,
                resolveDependencies = { throw AssertionError("fatal provider error") },
                acquirePendingResult = {
                    acquireCount.incrementAndGet()
                    noopFinish
                },
            )
        } catch (err: AssertionError) {
            propagatedError = err
        }
        assertNotNull(propagatedError)
        assertEquals(0, acquireCount.get())
    }

    @Test
    fun onFinishIllegalStateExceptionDoesNotCrashSuccessOrHandledFailureAndRunsAtMostOnce() = runBlocking {
        val uncaught = AtomicReference<Throwable?>(null)
        val handler = CoroutineExceptionHandler { _, throwable -> uncaught.set(throwable) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + handler)
        try {
            val successFinishAttempts = AtomicInteger(0)
            val successJob = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = Dispatchers.Unconfined,
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = {},
                onFinish = {
                    successFinishAttempts.incrementAndGet()
                    throw IllegalStateException("broadcast already finished")
                },
            )
            assertNotNull(successJob)
            successJob!!.join()
            assertEquals(1, successFinishAttempts.get())
            assertNull(uncaught.get())

            val failureFinishAttempts = AtomicInteger(0)
            val failureJob = CompanionReminderSystemReceiver.launchReconciliation(
                scope = scope,
                dispatcher = Dispatchers.Unconfined,
                action = Intent.ACTION_TIME_CHANGED,
                reconcile = { throw IOException("storage failure") },
                onFinish = {
                    failureFinishAttempts.incrementAndGet()
                    throw IllegalStateException("broadcast already finished")
                },
            )
            assertNotNull(failureJob)
            failureJob!!.join()
            assertEquals(1, failureFinishAttempts.get())
            assertNull(uncaught.get())

            val cancelledScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)
            cancelledScope.cancel()
            val fallbackFinishAttempts = AtomicInteger(0)
            val fallbackJob = CompanionReminderSystemReceiver.launchReconciliation(
                scope = cancelledScope,
                dispatcher = Dispatchers.Default,
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = {},
                onFinish = {
                    fallbackFinishAttempts.incrementAndGet()
                    throw IllegalStateException("broadcast already finished")
                },
            )
            fallbackJob?.join()
            assertEquals(1, fallbackFinishAttempts.get())
            assertNull(uncaught.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun onFinishExceptionNeverMasksExternalCancellationOrFatalReconciliationError() = runBlocking {
        val cancelFinishAttempts = AtomicInteger(0)
        val expectedCancel = CancellationException("external cancel")
        var caughtCancel: CancellationException? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { throw expectedCancel },
                onFinish = {
                    cancelFinishAttempts.incrementAndGet()
                    throw IllegalStateException("finish failed")
                },
            )
        } catch (ce: CancellationException) {
            caughtCancel = ce
        }
        assertNotNull(caughtCancel)
        assertEquals("external cancel", caughtCancel!!.message)
        assertEquals(1, cancelFinishAttempts.get())
        assertTrue(caughtCancel.suppressedExceptions.any { it is IllegalStateException })

        val errorFinishAttempts = AtomicInteger(0)
        val expectedError = AssertionError("primary fatal error")
        var caughtError: AssertionError? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { throw expectedError },
                onFinish = {
                    errorFinishAttempts.incrementAndGet()
                    throw IllegalStateException("finish failed")
                },
            )
        } catch (err: AssertionError) {
            caughtError = err
        }
        assertNotNull(caughtError)
        assertEquals("primary fatal error", caughtError!!.message)
        assertEquals(1, errorFinishAttempts.get())
        assertTrue(caughtError.suppressedExceptions.any { it is IllegalStateException })
    }

    @Test
    fun fatalErrorFromOnFinishPropagatesNormallyAndAttachesAsSuppressedWhenPrimaryErrorExists() = runBlocking {
        val normalFinishAttempts = AtomicInteger(0)
        val finishFatal = AssertionError("fatal finish error")
        var caughtNormalFinishError: AssertionError? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = {},
                onFinish = {
                    normalFinishAttempts.incrementAndGet()
                    throw finishFatal
                },
            )
        } catch (err: AssertionError) {
            caughtNormalFinishError = err
        }
        assertNotNull(caughtNormalFinishError)
        assertEquals("fatal finish error", caughtNormalFinishError!!.message)
        assertEquals(1, normalFinishAttempts.get())

        val dualErrorFinishAttempts = AtomicInteger(0)
        val primaryFatal = AssertionError("primary fatal error")
        val secondaryFatal = AssertionError("secondary fatal finish error")
        var caughtPrimary: AssertionError? = null
        try {
            CompanionReminderSystemReceiver.executeReconciliation(
                action = Intent.ACTION_BOOT_COMPLETED,
                reconcile = { throw primaryFatal },
                onFinish = {
                    dualErrorFinishAttempts.incrementAndGet()
                    throw secondaryFatal
                },
            )
        } catch (err: AssertionError) {
            caughtPrimary = err
        }
        assertNotNull(caughtPrimary)
        assertEquals("primary fatal error", caughtPrimary!!.message)
        assertEquals(1, dualErrorFinishAttempts.get())
        assertTrue(caughtPrimary.suppressedExceptions.contains(secondaryFatal))
    }

    private suspend fun runReceiverAction(
        action: String,
        scheduler: WorkManagerCompanionReminderScheduler,
        finishCount: AtomicInteger,
    ) {
        CompanionReminderSystemReceiver.executeReconciliation(
            action = action,
            reconcile = { forceReplace ->
                CompanionReminderType.entries.forEach { type ->
                    scheduler.reconcile(type, replaceExisting = forceReplace)
                }
            },
            onFinish = { finishCount.incrementAndGet() },
        )
    }
}

private class ReceiverFakeRemindersRepository(
    private val typeLocks: CompanionReminderTypeLocks,
) : CompanionRemindersRepository {
    private val flow = MutableStateFlow(CompanionRemindersPreferences())
    override val preferences: Flow<CompanionRemindersPreferences> = flow

    fun currentSnapshot(): CompanionRemindersPreferences = flow.value

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

private data class ReceiverFakeWorkEntry(
    val id: UUID,
    val uniqueWorkName: String,
    var state: WorkInfo.State,
    val tags: Set<String>,
    val inputData: Data,
    val initialDelayMillis: Long,
)

private class ReceiverFakeWorkManagerEnqueuer(
    private val onBeforeEnqueue: (suspend (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit)? = null,
) : ReminderWorkEnqueuer {

    private val entries = mutableListOf<ReceiverFakeWorkEntry>()
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
        onBeforeEnqueue?.invoke(uniqueWorkName, policy, request)
        synchronized(entries) {
            enqueueCount.incrementAndGet()
            val active = entries.filter { it.uniqueWorkName == uniqueWorkName && !it.state.isFinished }
            val spec = request.workSpec
            val newEntry = ReceiverFakeWorkEntry(
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
                    if (active.isEmpty()) entries.add(newEntry)
                }
                ExistingWorkPolicy.APPEND,
                ExistingWorkPolicy.APPEND_OR_REPLACE -> {
                    if (active.isNotEmpty()) {
                        newEntry.state = WorkInfo.State.BLOCKED
                    }
                    entries.add(newEntry)
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

    fun startNextEnqueued(uniqueWorkName: String): ReceiverFakeWorkEntry = synchronized(entries) {
        val next = entries.first { it.uniqueWorkName == uniqueWorkName && it.state == WorkInfo.State.ENQUEUED }
        next.state = WorkInfo.State.RUNNING
        next
    }

    fun completeRunning(id: UUID, terminalState: WorkInfo.State) {
        synchronized(entries) {
            entries.first { it.id == id }.state = terminalState
        }
    }

    fun insertLegacyWork(uniqueWorkName: String): UUID = synchronized(entries) {
        val id = UUID.randomUUID()
        entries.add(
            ReceiverFakeWorkEntry(
                id = id,
                uniqueWorkName = uniqueWorkName,
                state = WorkInfo.State.ENQUEUED,
                tags = setOf(WorkManagerCompanionReminderScheduler.WORK_TAG, uniqueWorkName),
                inputData = workDataOf(CompanionReminderWorker.KEY_REMINDER_TYPE to CompanionReminderType.MORNING_ADHKAR.key),
                initialDelayMillis = 3_600_000L,
            ),
        )
        id
    }

    fun stateOf(id: UUID): WorkInfo.State = synchronized(entries) {
        entries.first { it.id == id }.state
    }

    fun activeEntries(uniqueWorkName: String): List<ReceiverFakeWorkEntry> = synchronized(entries) {
        entries.filter { it.uniqueWorkName == uniqueWorkName && !it.state.isFinished }
    }
}
