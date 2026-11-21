package com.ayah.quran.ui.settings

import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderTimeCalculator
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionRemindersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun timeCalculatorHandlesSameDayNextDayAndTimeZoneChanges() {
        val utc = ZoneId.of("UTC")
        val nowMorning = ZonedDateTime.of(2026, 11, 20, 6, 0, 0, 0, utc)
        val sameDayDelay = CompanionReminderTimeCalculator.computeInitialDelayMillis(nowMorning, 7, 0)
        assertEquals(3_600_000L, sameDayDelay)

        val nowAfter = ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, utc)
        val nextDayDelay = CompanionReminderTimeCalculator.computeInitialDelayMillis(nowAfter, 7, 0)
        assertEquals(23 * 3_600_000L, nextDayDelay)

        val nyDstSpringForward = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        val dstDelay = CompanionReminderTimeCalculator.computeInitialDelayMillis(nyDstSpringForward, 7, 0)
        assertEquals(7 * 3_600_000L, dstDelay)

        val earlyFire = ZonedDateTime.of(2026, 11, 20, 6, 59, 58, 0, utc)
        val nextDayFromEarly = CompanionReminderTimeCalculator.computeInitialDelayMillis(
            now = earlyFire,
            hour = 7,
            minute = 0,
            earliestDate = LocalDate.of(2026, 11, 21),
        )
        assertEquals(24 * 3_600_000L + 2_000L, nextDayFromEarly)
    }

    @Test
    fun remindersEnableDisableUpdateTimeIndependentlyWithoutDuplicateWork() = runTest {
        val repo = FakeRemindersRepository()
        val scheduler = RecordingReminderScheduler()
        val vm = CompanionRemindersViewModel(repo, scheduler) { true }
        val job = backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.preferences.allConfigs().none { it.enabled })

        vm.setReminderEnabled(CompanionReminderType.MORNING_ADHKAR, true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.preferences.morningAdhkar.enabled)
        assertFalse(vm.uiState.value.preferences.eveningAdhkar.enabled)
        assertFalse(vm.uiState.value.preferences.dailyReflection.enabled)
        assertEquals(setOf(CompanionReminderType.MORNING_ADHKAR), scheduler.activeScheduled.keys)

        vm.setReminderTime(CompanionReminderType.MORNING_ADHKAR, 6, 45)
        advanceUntilIdle()
        assertEquals("06:45", vm.uiState.value.preferences.morningAdhkar.formattedTime())
        assertEquals(6, scheduler.activeScheduled[CompanionReminderType.MORNING_ADHKAR]?.hour)
        assertEquals(45, scheduler.activeScheduled[CompanionReminderType.MORNING_ADHKAR]?.minute)
        assertEquals(1, scheduler.activeScheduled.size)

        vm.setReminderTime(CompanionReminderType.EVENING_ADHKAR, 18, 15)
        advanceUntilIdle()
        assertEquals("18:15", vm.uiState.value.preferences.eveningAdhkar.formattedTime())
        assertFalse(CompanionReminderType.EVENING_ADHKAR in scheduler.activeScheduled)

        vm.setReminderEnabled(CompanionReminderType.MORNING_ADHKAR, false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.preferences.morningAdhkar.enabled)
        assertTrue(scheduler.activeScheduled.isEmpty())
        assertTrue(CompanionReminderType.MORNING_ADHKAR in scheduler.cancelledTypes)
        job.cancel()
    }

    @Test
    fun preferencesAndPermissionStateSurviveViewModelRecreation() = runTest {
        val repo = FakeRemindersRepository()
        val scheduler = RecordingReminderScheduler()
        var allowed = false

        val vm1 = CompanionRemindersViewModel(repo, scheduler) { allowed }
        val job1 = backgroundScope.launch { vm1.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(vm1.uiState.value.notificationsAllowed)
        vm1.markPermissionRequested()
        vm1.setReminderTime(CompanionReminderType.DAILY_REFLECTION, 10, 30)
        vm1.setReminderEnabled(CompanionReminderType.DAILY_REFLECTION, true)
        advanceUntilIdle()
        job1.cancel()

        allowed = true
        val vm2 = CompanionRemindersViewModel(repo, scheduler) { allowed }
        val job2 = backgroundScope.launch { vm2.uiState.collect {} }
        advanceUntilIdle()

        assertTrue(vm2.uiState.value.notificationsAllowed)
        assertTrue(vm2.uiState.value.preferences.permissionRequestedBefore)
        assertTrue(vm2.uiState.value.preferences.dailyReflection.enabled)
        assertEquals("10:30", vm2.uiState.value.preferences.dailyReflection.formattedTime())
        job2.cancel()
    }

    @Test
    fun reminderTypesHaveUniqueStableWorkNamesNotificationIdsAndDestinations() {
        val types = CompanionReminderType.entries
        assertEquals(3, types.size)
        assertEquals(types.size, types.map { it.uniqueWorkName }.toSet().size)
        assertEquals(types.size, types.map { it.notificationId }.toSet().size)
        assertEquals("companion/adhkar/session/morning", CompanionReminderType.MORNING_ADHKAR.deepLinkRoute)
        assertEquals("companion/adhkar/session/evening", CompanionReminderType.EVENING_ADHKAR.deepLinkRoute)
        assertEquals("home", CompanionReminderType.DAILY_REFLECTION.deepLinkRoute)
    }
}

private class FakeRemindersRepository : CompanionRemindersRepository {
    private val flow = MutableStateFlow(CompanionRemindersPreferences())
    override val preferences: Flow<CompanionRemindersPreferences> = flow

    override suspend fun setEnabled(type: CompanionReminderType, enabled: Boolean): CompanionReminderConfig {
        val current = flow.value.configOf(type)
        val next = current.copy(
            enabled = enabled,
            scheduleVersion = current.scheduleVersion + 1L,
            lastHandledEpochDay = if (enabled && !current.enabled) null else current.lastHandledEpochDay,
        )
        flow.value = flow.value.withConfig(next)
        return next
    }

    override suspend fun setTime(type: CompanionReminderType, hour: Int, minute: Int): CompanionReminderConfig {
        val current = flow.value.configOf(type)
        val next = current.copy(
            hour = hour,
            minute = minute,
            scheduleVersion = current.scheduleVersion + 1L,
            lastHandledEpochDay = null,
        )
        flow.value = flow.value.withConfig(next)
        return next
    }

    override suspend fun markHandled(type: CompanionReminderType, epochDay: Long, expectedVersion: Long) {
        val current = flow.value.configOf(type)
        if (current.scheduleVersion == expectedVersion) {
            flow.value = flow.value.withConfig(current.copy(lastHandledEpochDay = epochDay))
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

private class RecordingReminderScheduler : CompanionReminderScheduler {
    val activeScheduled = linkedMapOf<CompanionReminderType, CompanionReminderConfig>()
    val cancelledTypes = mutableListOf<CompanionReminderType>()

    override suspend fun schedule(config: CompanionReminderConfig) {
        activeScheduled[config.type] = config
    }

    override suspend fun cancel(type: CompanionReminderType) {
        activeScheduled.remove(type)
        cancelledTypes.add(type)
    }

    override suspend fun reconcileAll(
        preferences: CompanionRemindersPreferences,
        replaceExisting: Boolean,
    ) {
        preferences.allConfigs().forEach { cfg ->
            if (cfg.enabled) schedule(cfg) else cancel(cfg.type)
        }
    }

    override suspend fun reconcile(
        type: CompanionReminderType,
        replaceExisting: Boolean,
    ) = Unit

    override suspend fun scheduleNextFromWorker(
        type: CompanionReminderType,
        expectedVersion: Long,
        currentWorkId: UUID,
    ): Boolean = true
}
