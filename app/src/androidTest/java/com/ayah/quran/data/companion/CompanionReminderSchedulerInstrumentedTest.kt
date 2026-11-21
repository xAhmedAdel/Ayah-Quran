package com.ayah.quran.data.companion

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.ayah.quran.background.CompanionReminderNotifier
import com.ayah.quran.background.CompanionReminderWorker
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CompanionReminderSchedulerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workerExecutor: ExecutorService
    private lateinit var taskExecutor: ExecutorService
    private lateinit var typeLocks: CompanionReminderTypeLocks
    private lateinit var repository: InstrumentedRemindersRepository
    private lateinit var scheduler: WorkManagerCompanionReminderScheduler
    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver
    private val clock = AtomicReference(ZonedDateTime.of(2026, 11, 21, 6, 0, 0, 0, ZoneId.of("UTC")))

    @Before
    fun setUp() {
        workerExecutor = Executors.newFixedThreadPool(2)
        taskExecutor = Executors.newSingleThreadExecutor()
        typeLocks = CompanionReminderTypeLocks()
        repository = InstrumentedRemindersRepository(typeLocks)
        scheduler = WorkManagerCompanionReminderScheduler(context, repository, typeLocks) { clock.get() }
        val notifier = CompanionReminderNotifier(context)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? {
                if (workerClassName != CompanionReminderWorker::class.java.name) return null
                return CompanionReminderWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    remindersRepository = repository,
                    reminderScheduler = scheduler,
                    notifier = notifier,
                    typeLocks = typeLocks,
                ).apply {
                    nowProvider = { clock.get() }
                }
            }
        }

        val config = Configuration.Builder()
            .setExecutor(workerExecutor)
            .setTaskExecutor(taskExecutor)
            .setWorkerFactory(workerFactory)
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            config,
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
    }

    @After
    fun tearDown() = runBlocking {
        repository.onReadGate.set(null)
        CompanionReminderType.entries.forEach { type ->
            repository.setEnabled(type, false)
            scheduler.cancel(type)
        }
        val restoreConfig = (context.applicationContext as? Configuration.Provider)?.workManagerConfiguration
            ?: Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            restoreConfig,
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        shutdownExecutor(workerExecutor)
        shutdownExecutor(taskExecutor)
    }

    private fun shutdownExecutor(executor: ExecutorService) {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    @Test
    fun workManagerSchedulesReplacesWithoutDuplicatesAndCancelsIndependently() = runBlocking {
        val morning = repository.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        val evening = repository.setEnabled(CompanionReminderType.EVENING_ADHKAR, true)

        scheduler.schedule(morning)
        scheduler.schedule(evening)

        val morningBefore = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, morningBefore.count { it.state == WorkInfo.State.ENQUEUED })
        val firstId = morningBefore.first { it.state == WorkInfo.State.ENQUEUED }.id

        val updatedMorning = repository.setTime(CompanionReminderType.MORNING_ADHKAR, 6, 45)
        scheduler.schedule(updatedMorning)
        val morningAfterUpdate = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, morningAfterUpdate.count { it.state == WorkInfo.State.ENQUEUED })
        val secondId = morningAfterUpdate.first { it.state == WorkInfo.State.ENQUEUED }.id
        assertTrue(firstId != secondId)

        repository.setEnabled(CompanionReminderType.MORNING_ADHKAR, false)
        scheduler.cancel(CompanionReminderType.MORNING_ADHKAR)
        val morningCancelled = workManager.getWorkInfosForUniqueWork(CompanionReminderType.MORNING_ADHKAR.uniqueWorkName).get()
        assertEquals(0, morningCancelled.count { it.state == WorkInfo.State.ENQUEUED })

        val eveningStillActive = workManager.getWorkInfosForUniqueWork(CompanionReminderType.EVENING_ADHKAR.uniqueWorkName).get()
        assertEquals(1, eveningStillActive.count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun runningWorkerAppendsTomorrowChildWithoutSelfCancellationAndChildExecutes() = runBlocking {
        val cfg = repository.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(cfg)

        val uniqueName = CompanionReminderType.MORNING_ADHKAR.uniqueWorkName
        val parentId = workManager.getWorkInfosForUniqueWork(uniqueName).get()
            .single { it.state == WorkInfo.State.ENQUEUED }.id

        val parentEntered = CompletableDeferred<Unit>()
        val allowParentFinish = CompletableDeferred<Unit>()
        val gateCalls = AtomicInteger(0)
        repository.onReadGate.set {
            if (gateCalls.incrementAndGet() == 1) {
                parentEntered.complete(Unit)
                allowParentFinish.await()
            }
        }

        clock.set(ZonedDateTime.of(2026, 11, 21, 7, 0, 0, 0, ZoneId.of("UTC")))
        testDriver.setInitialDelayMet(parentId)
        withTimeout(5_000L) { parentEntered.await() }

        val runningState = withTimeout(5_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { list ->
                list.any { it.id == parentId && it.state == WorkInfo.State.RUNNING }
            }
        }
        assertTrue(runningState.any { it.id == parentId && it.state == WorkInfo.State.RUNNING })

        scheduler.reconcileAll(repository.currentSnapshot(), replaceExisting = false)
        assertEquals(WorkInfo.State.RUNNING, workManager.getWorkInfoById(parentId).get()!!.state)

        val firstAppend = scheduler.scheduleNextFromWorker(CompanionReminderType.MORNING_ADHKAR, cfg.scheduleVersion, parentId)
        val duplicateAppend = scheduler.scheduleNextFromWorker(CompanionReminderType.MORNING_ADHKAR, cfg.scheduleVersion, parentId)
        assertTrue(firstAppend)
        assertTrue(duplicateAppend)
        val blockedChildren = workManager.getWorkInfosForUniqueWork(uniqueName).get()
            .filter { it.id != parentId && it.state == WorkInfo.State.BLOCKED }
        assertEquals(1, blockedChildren.size)
        val childId = blockedChildren.single().id

        repository.onReadGate.set(null)
        allowParentFinish.complete(Unit)

        val afterParent = withTimeout(5_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { list ->
                list.any { it.id == parentId && it.state == WorkInfo.State.SUCCEEDED } &&
                    list.any { it.id == childId && it.state == WorkInfo.State.ENQUEUED }
            }
        }
        assertTrue(afterParent.any { it.id == parentId && it.state == WorkInfo.State.SUCCEEDED })
        assertEquals(1, afterParent.count { !it.state.isFinished })

        clock.set(ZonedDateTime.of(2026, 11, 22, 7, 0, 0, 0, ZoneId.of("UTC")))
        testDriver.setInitialDelayMet(childId)

        val afterChild = withTimeout(5_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { list ->
                list.any { it.id == childId && it.state == WorkInfo.State.SUCCEEDED } &&
                    list.any { it.id != childId && it.id != parentId && it.state == WorkInfo.State.ENQUEUED }
            }
        }
        assertTrue(afterChild.any { it.id == childId && it.state == WorkInfo.State.SUCCEEDED })
    }

    @Test
    fun externalReplaceSupersedesRunningWorkerAndVersionAwareStartupReconcilesStaleWork() = runBlocking {
        val v1 = repository.setEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        scheduler.schedule(v1)
        val uniqueName = CompanionReminderType.MORNING_ADHKAR.uniqueWorkName
        val v1Id = workManager.getWorkInfosForUniqueWork(uniqueName).get()
            .single { it.state == WorkInfo.State.ENQUEUED }.id

        val parentEntered = CompletableDeferred<Unit>()
        val allowParentFinish = CompletableDeferred<Unit>()
        val gateCalls = AtomicInteger(0)
        repository.onReadGate.set {
            if (gateCalls.incrementAndGet() == 1) {
                parentEntered.complete(Unit)
                allowParentFinish.await()
            }
        }

        testDriver.setInitialDelayMet(v1Id)
        withTimeout(5_000L) { parentEntered.await() }

        repository.onReadGate.set(null)
        val v2 = repository.setTime(CompanionReminderType.MORNING_ADHKAR, 8, 30)
        scheduler.schedule(v2)
        allowParentFinish.complete(Unit)

        val afterReplace = withTimeout(5_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { list ->
                list.any { it.id == v1Id && it.state == WorkInfo.State.CANCELLED } &&
                    list.count { it.state == WorkInfo.State.ENQUEUED } == 1
            }
        }
        val v2Entry = afterReplace.single { it.state == WorkInfo.State.ENQUEUED }
        assertTrue(WorkManagerCompanionReminderScheduler.specTag(v2) in v2Entry.tags)

        val v3 = repository.setTime(CompanionReminderType.MORNING_ADHKAR, 9, 15)
        scheduler.reconcileAll(repository.currentSnapshot(), replaceExisting = false)

        val afterStartupHeal = withTimeout(5_000L) {
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName).first { list ->
                list.any { it.id == v2Entry.id && it.state == WorkInfo.State.CANCELLED } &&
                    list.any { it.state == WorkInfo.State.ENQUEUED && WorkManagerCompanionReminderScheduler.specTag(v3) in it.tags }
            }
        }
        assertEquals(1, afterStartupHeal.count { !it.state.isFinished })
    }

    @Test
    fun launchIntentsCarryExactCompanionDeepLinkRoutes() {
        CompanionReminderType.entries.forEach { type ->
            val intent = CompanionReminderNotifier.buildLaunchIntent(context, type)
            assertEquals(
                type.deepLinkRoute,
                intent.getStringExtra(CompanionReminderNotifier.EXTRA_COMPANION_ROUTE),
            )
        }
    }
}

private class InstrumentedRemindersRepository(
    private val typeLocks: CompanionReminderTypeLocks,
) : CompanionRemindersRepository {
    private val flow = MutableStateFlow(CompanionRemindersPreferences())
    val onReadGate = AtomicReference<(suspend () -> Unit)?>(null)

    override val preferences: Flow<CompanionRemindersPreferences> = flow.onStart {
        onReadGate.get()?.invoke()
    }

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
