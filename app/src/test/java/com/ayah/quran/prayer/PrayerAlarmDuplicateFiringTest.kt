package com.ayah.quran.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.data.prayer.PrayerPreferencesRepositoryImpl
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerLocation
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection
import com.ayah.quran.domain.prayer.PrayerAlarm
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger

class PrayerAlarmDuplicateFiringTest {

    @Test
    fun earlyDeliveryBy10msFiresOnceAndSchedulesNextPrayer() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.dispatched.single().first)
        assertEquals(1, harness.scheduled.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
        assertEquals(day.toString(), harness.scheduled.single().dayKey)
        assertEquals(dhuhrMillis, harness.scheduled.single().triggerAtMillis)
    }

    @Test
    fun extraEarlyDeliveryBy15MinutesFiresOnceAndSchedulesNextPrayer() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis - 15 * 60_000L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
        assertEquals(day.toString(), harness.scheduled.single().dayKey)
    }

    @Test
    fun exactTimeDeliveryFiresOnceAndSchedulesNextPrayer() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
    }

    @Test
    fun lateDeliveryBy5SecondsAnd70SecondsSchedulesNextPrayer() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis + 5_000L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)
        assertEquals(Prayer.DHUHR, harness.scheduled.last().prayer)

        clock.setMillis(dhuhrMillis + 70_000L)
        harness.fireAlarm(Prayer.DHUHR, day.toString(), dhuhrMillis)
        assertEquals(Prayer.ASR, harness.scheduled.last().prayer)
        assertEquals(2, harness.dispatched.size)
    }

    @Test
    fun concurrentDuplicateBroadcastsTriggerExactlyOneDispatch() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)
        val gate = CompletableDeferred<Unit>()
        val dispatchCount = AtomicInteger(0)

        coroutineScope {
            val jobs = (1..10).map {
                async(Dispatchers.Default) {
                    gate.await()
                    PrayerAlarmReceiver.handleAlarm(
                        prayer = Prayer.FAJR,
                        dayKey = day.toString(),
                        scheduledAtMillis = fajrMillis,
                        correlationId = "concurrent-$it",
                        preferencesRepository = harness.repo,
                        occurrenceGuard = harness.guard,
                        manager = harness.manager,
                        onDispatch = { _, _ -> dispatchCount.incrementAndGet() },
                        onFinish = {},
                        clock = clock,
                    )
                }
            }
            gate.complete(Unit)
            jobs.awaitAll()
        }

        assertEquals(1, dispatchCount.get())
        assertTrue(harness.scheduled.isNotEmpty())
        assertTrue(harness.scheduled.all { it.prayer == Prayer.DHUHR && it.dayKey == day.toString() })
    }

    @Test
    fun duplicateDeliveryAcrossGuardAndRepositoryRecreationWithSharedDataStoreIsSuppressed() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val sharedStore = InMemoryPreferencesDataStore()

        val repo1 = PrayerPreferencesRepositoryImpl(sharedStore)
        repo1.setLocation(testLocation(zone.id))
        val guard1 = PrayerAlarmOccurrenceGuard(repo1)
        val scheduled1 = mutableListOf<PrayerAlarm>()
        val manager1 = PrayerTimeManager(repo1, fixedCalculator(), recordingScheduler(scheduled1), clock)
        var dispatched1 = 0

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.FAJR,
            dayKey = day.toString(),
            scheduledAtMillis = fajrMillis,
            correlationId = "proc1",
            preferencesRepository = repo1,
            occurrenceGuard = guard1,
            manager = manager1,
            onDispatch = { _, _ -> dispatched1++ },
            onFinish = {},
            clock = clock,
        )

        assertEquals(1, dispatched1)
        assertEquals(Prayer.DHUHR, scheduled1.single().prayer)

        val repo2 = PrayerPreferencesRepositoryImpl(sharedStore)
        val guard2 = PrayerAlarmOccurrenceGuard(repo2)
        val scheduled2 = mutableListOf<PrayerAlarm>()
        val manager2 = PrayerTimeManager(repo2, fixedCalculator(), recordingScheduler(scheduled2), clock)
        var dispatched2 = 0

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.FAJR,
            dayKey = day.toString(),
            scheduledAtMillis = fajrMillis,
            correlationId = "proc2-dup",
            preferencesRepository = repo2,
            occurrenceGuard = guard2,
            manager = manager2,
            onDispatch = { _, _ -> dispatched2++ },
            onFinish = {},
            clock = clock,
        )

        assertEquals(0, dispatched2)
        assertEquals(1, scheduled2.size)
        assertEquals(Prayer.DHUHR, scheduled2.single().prayer)
    }

    @Test
    fun minuteBoundaryCrossingDuplicatesAreSuppressedBeforeAndAfterScheduledTime() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)
        clock.setMillis(fajrMillis)
        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        clock.setMillis(dhuhrMillis + 59_900L)
        harness.fireAlarm(Prayer.DHUHR, day.toString(), dhuhrMillis)
        clock.setMillis(dhuhrMillis + 60_100L)
        harness.fireAlarm(Prayer.DHUHR, day.toString(), dhuhrMillis)

        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR), harness.dispatched.map { it.first })
    }

    @Test
    fun modeChangeBetweenDuplicateDeliveriesDoesNotBypassGuard() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.repo.setAdhanConfig(PrayerAdhanConfig(fajr = AdhanMode.FULL_ADHAN))
        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        harness.repo.setAdhanConfig(PrayerAdhanConfig(fajr = AdhanMode.NOTIFICATION_SOUND))
        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(AdhanMode.FULL_ADHAN, harness.dispatched.single().second)
    }

    @Test
    fun manualPrayerTimeAdjustmentsApplyAndAdvanceCleanlyOnEarlyDelivery() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val adjustedFajrMillis = epochMillis(day.atTime(4, 25), zone)
        val adjustedDhuhrMillis = epochMillis(day.atTime(12, 3), zone)
        val clock = MutableClock(Instant.ofEpochMilli(adjustedFajrMillis - 10L), zone)
        val harness = createHarness(
            zone = zone,
            clock = clock,
            adjustments = PrayerAdjustments(fajr = -5, dhuhr = +3),
        )

        harness.fireAlarm(Prayer.FAJR, day.toString(), adjustedFajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
        assertEquals(adjustedDhuhrMillis, harness.scheduled.single().triggerAtMillis)
    }

    @Test
    fun scheduledTimestampDiffersWhenPrayerMovedLaterSchedulesAndFiresNewOccurrence() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val oldFajrMillis = epochMillis(day.atTime(4, 30), zone)
        val newFajrMillis = epochMillis(day.atTime(4, 45), zone)
        val clock = MutableClock(Instant.ofEpochMilli(oldFajrMillis), zone)
        val harness = createHarness(
            zone = zone,
            clock = clock,
            adjustments = PrayerAdjustments(fajr = +15),
        )

        harness.fireAlarm(Prayer.FAJR, day.toString(), oldFajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.scheduled.single().prayer)
        assertEquals(newFajrMillis, harness.scheduled.single().triggerAtMillis)

        clock.setMillis(newFajrMillis)
        harness.fireAlarm(Prayer.FAJR, day.toString(), newFajrMillis)

        assertEquals(2, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.last().prayer)
    }

    @Test
    fun scheduledTimestampDiffersWhenPrayerMovedEarlierSkipsSupersededOccurrence() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val staleScheduledMillis = epochMillis(day.atTime(4, 45), zone)
        val clock = MutableClock(Instant.ofEpochMilli(epochMillis(day.atTime(4, 34), zone)), zone)
        val harness = createHarness(
            zone = zone,
            clock = clock,
            adjustments = PrayerAdjustments(fajr = +5),
        )

        harness.fireAlarm(Prayer.FAJR, day.toString(), staleScheduledMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
    }

    @Test
    fun persistenceIOExceptionDegradesToInMemoryDeduplication() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val harness = createHarness(zone = zone, clock = clock)
        harness.repo.failPersistence = true

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)
        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)

        clock.setMillis(dhuhrMillis)
        harness.fireAlarm(Prayer.DHUHR, day.toString(), dhuhrMillis)

        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR), harness.dispatched.map { it.first })
    }

    @Test
    fun persistenceRecoveryResumesPersistedDeduplicationAcrossRecreation() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val sharedStore = InMemoryPreferencesDataStore()

        val realRepo1 = PrayerPreferencesRepositoryImpl(sharedStore)
        var failNext = true
        val flakyRepo = object : PrayerPreferencesRepository by realRepo1 {
            override suspend fun tryMarkAlarmOccurrenceFired(
                occurrenceKey: String,
                retentionCutoffDate: LocalDate?,
            ): Boolean {
                if (failNext) throw IOException("transient disk full")
                return realRepo1.tryMarkAlarmOccurrenceFired(occurrenceKey, retentionCutoffDate)
            }
        }
        val guard1 = PrayerAlarmOccurrenceGuard(flakyRepo)
        val cutoff = day.minusDays(PrayerAlarmOccurrenceGuard.RETENTION_DAYS)

        val fajrKey = AlarmOccurrenceKey.build(Prayer.FAJR, day.toString(), fajrMillis, fajrMillis)
        assertTrue(guard1.tryMarkFired(fajrKey, cutoff))
        assertFalse(guard1.tryMarkFired(fajrKey, cutoff))

        failNext = false
        val dhuhrKey = AlarmOccurrenceKey.build(Prayer.DHUHR, day.toString(), dhuhrMillis, dhuhrMillis)
        assertTrue(guard1.tryMarkFired(dhuhrKey, cutoff))

        val guard2 = PrayerAlarmOccurrenceGuard(PrayerPreferencesRepositoryImpl(sharedStore))

        assertFalse(guard2.tryMarkFired(dhuhrKey, cutoff))
    }

    @Test
    fun twoConsecutivePrayersSameDayBothFireAndChain() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day.toString(), fajrMillis)
        assertEquals(Prayer.DHUHR, harness.scheduled.last().prayer)

        clock.setMillis(dhuhrMillis)
        harness.fireAlarm(Prayer.DHUHR, day.toString(), dhuhrMillis)
        assertEquals(Prayer.ASR, harness.scheduled.last().prayer)
        assertEquals(listOf(Prayer.FAJR, Prayer.DHUHR), harness.dispatched.map { it.first })
    }

    @Test
    fun legitimateNextDaySamePrayerIsNeverSuppressed() = runTest {
        val zone = ZoneId.of("UTC")
        val day1 = LocalDate.of(2026, 11, 21)
        val day2 = LocalDate.of(2026, 11, 22)
        val fajrDay1 = epochMillis(day1.atTime(4, 30), zone)
        val fajrDay2 = epochMillis(day2.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrDay1), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, day1.toString(), fajrDay1)
        clock.setMillis(fajrDay2)
        harness.fireAlarm(Prayer.FAJR, day2.toString(), fajrDay2)

        assertEquals(2, harness.dispatched.size)
        assertEquals(day2.toString(), harness.scheduled.last().dayKey)
        assertEquals(Prayer.DHUHR, harness.scheduled.last().prayer)
    }

    @Test
    fun midnightCrossingLateIshaSchedulesNextDayFajr() = runTest {
        val zone = ZoneId.of("UTC")
        val day1 = LocalDate.of(2026, 11, 21)
        val day2 = LocalDate.of(2026, 11, 22)
        val ishaMillis = epochMillis(day1.atTime(23, 55), zone)
        val nextFajrMillis = epochMillis(day2.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(epochMillis(day2.atTime(0, 1), zone)), zone)
        val harness = createHarness(
            zone = zone,
            clock = clock,
            calculator = fixedCalculator(ishaHour = 23, ishaMinute = 55),
        )

        harness.fireAlarm(Prayer.ISHA, day1.toString(), ishaMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.scheduled.single().prayer)
        assertEquals(day2.toString(), harness.scheduled.single().dayKey)
        assertEquals(nextFajrMillis, harness.scheduled.single().triggerAtMillis)
    }

    @Test
    fun midnightCrossingEarlyMidnightFajrSchedulesSameDayDhuhr() = runTest {
        val zone = ZoneId.of("UTC")
        val day2 = LocalDate.of(2026, 11, 22)
        val midnightFajrMillis = epochMillis(day2.atTime(0, 0), zone)
        val day2DhuhrMillis = epochMillis(day2.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(midnightFajrMillis - 10L), zone)
        val harness = createHarness(
            zone = zone,
            clock = clock,
            calculator = fixedCalculator(fajrHour = 0, fajrMinute = 0),
        )

        harness.fireAlarm(Prayer.FAJR, day2.toString(), midnightFajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.single().prayer)
        assertEquals(day2.toString(), harness.scheduled.single().dayKey)
        assertEquals(day2DhuhrMillis, harness.scheduled.single().triggerAtMillis)
    }

    @Test
    fun nonUtcTimezoneDayKeyAndDstOffsetResolveLocalPrayerDate() = runTest {
        val zone = ZoneId.of("America/New_York")
        val localDay = LocalDate.of(2026, 11, 1)
        val ishaMillis = epochMillis(localDay.atTime(19, 30), zone)
        val expectedNextFajrMillis = epochMillis(localDay.plusDays(1).atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(ishaMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.ISHA, localDay.toString(), ishaMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.scheduled.single().prayer)
        assertEquals(localDay.plusDays(1).toString(), harness.scheduled.single().dayKey)
        assertEquals(expectedNextFajrMillis, harness.scheduled.single().triggerAtMillis)
        assertTrue(harness.scheduled.single().triggerAtMillis > clock.millis())
    }

    @Test
    fun retentionPolicyPrunesExpiredDaysAndEnforces40EntryCap() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repo = PrayerPreferencesRepositoryImpl(store)
        val guard = PrayerAlarmOccurrenceGuard(repo)

        val baseDay = LocalDate.of(2026, 11, 1)
        val expiredKey = AlarmOccurrenceKey.build(Prayer.FAJR, baseDay.toString(), 1_000L, 1_000L)
        assertTrue(guard.tryMarkFired(expiredKey, baseDay.minusDays(7)))

        val tenDaysLater = baseDay.plusDays(10)
        val cutoffAfterTenDays = tenDaysLater.minusDays(7)
        val recentKey = AlarmOccurrenceKey.build(Prayer.FAJR, tenDaysLater.toString(), 2_000L, 2_000L)
        assertTrue(guard.tryMarkFired(recentKey, cutoffAfterTenDays))

        assertTrue(guard.tryMarkFired(expiredKey, null))

        for (i in 1..45) {
            val d = tenDaysLater.plusDays((i / 5).toLong())
            val key = AlarmOccurrenceKey.build(Prayer.entries[i % 5], d.toString(), 10_000L + i, 10_000L + i)
            assertTrue(guard.tryMarkFired(key, null))
        }
        assertTrue(guard.inMemorySizeForTest() <= PrayerAlarmOccurrenceGuard.MAX_ENTRIES)

        val oldestKey = AlarmOccurrenceKey.build(Prayer.entries[1], tenDaysLater.toString(), 10_001L, 10_001L)
        val newestKey = AlarmOccurrenceKey.build(Prayer.entries[45 % 5], tenDaysLater.plusDays(9).toString(), 10_045L, 10_045L)
        assertFalse(guard.tryMarkFired(newestKey, null))
        assertTrue(guard.tryMarkFired(oldestKey, null))
    }

    @Test
    fun missingDayKeyAndMissingScheduledTimestampDoesNotSkipNextDayPrayer() = runTest {
        val zone = ZoneId.of("UTC")
        val day1 = LocalDate.of(2026, 11, 21)
        val day2 = LocalDate.of(2026, 11, 22)
        val nextFajrMillis = epochMillis(day2.atTime(4, 30), zone)
        val clock = MutableClock(Instant.ofEpochMilli(epochMillis(day1.atTime(20, 0), zone)), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, null, 0L)
        harness.fireAlarm(Prayer.FAJR, null, 0L)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.scheduled.last().prayer)
        assertEquals(day2.toString(), harness.scheduled.last().dayKey)
        assertEquals(nextFajrMillis, harness.scheduled.last().triggerAtMillis)
    }

    @Test
    fun malformedDayKeyWithValidScheduledTimestampStillAdvancesAndDeduplicates() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.FAJR, "not-a-valid-date", fajrMillis)
        harness.fireAlarm(Prayer.FAJR, "not-a-valid-date", fajrMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.DHUHR, harness.scheduled.last().prayer)
        assertEquals(day.toString(), harness.scheduled.last().dayKey)
        assertEquals(dhuhrMillis, harness.scheduled.last().triggerAtMillis)
    }

    @Test
    fun newlineTaintedDayKeyIsNormalizedToNullAndPreservesTimestampKey() = runTest {
        val store = InMemoryPreferencesDataStore()
        val repo = PrayerPreferencesRepositoryImpl(store)
        val guard = PrayerAlarmOccurrenceGuard(repo)

        assertEquals("2026-11-21", AlarmOccurrenceKey.normalizeDayKey(" 2026-11-21 "))
        assertNull(AlarmOccurrenceKey.normalizeDayKey("2026-11-21\nFAJR|2026-11-21|999"))
        assertNull(AlarmOccurrenceKey.normalizeDayKey("2026-11-21\r\n"))
        assertNull(AlarmOccurrenceKey.normalizeDayKey("2026-02-30"))

        val key = AlarmOccurrenceKey.build(
            prayer = Prayer.FAJR,
            dayKey = "2026-11-21\nDHUHR|2026-11-21|999",
            scheduledAtMillis = 55_000L,
            fallbackNowMillis = 55_000L,
        )
        assertEquals("FAJR|ts:55000", key)
        assertTrue(guard.tryMarkFired(key, LocalDate.of(2026, 11, 14)))
        assertFalse(guard.tryMarkFired(key, LocalDate.of(2026, 11, 14)))

        val persisted = store.data.first()[stringPreferencesKey("prayer_fired_alarm_occurrences")]
        assertEquals("FAJR|ts:55000", persisted)
    }

    @Test
    fun malformedAndLegacyPersistedKeysSurvivePruningWithoutCrashing() = runTest {
        val store = InMemoryPreferencesDataStore()
        val storageKey = stringPreferencesKey("prayer_fired_alarm_occurrences")
        store.edit { prefs ->
            prefs[storageKey] = listOf(
                "garbage",
                "FAJR|not-a-date|1000",
                "FAJR|ts:1000",
                "FAJR|fallback:100",
                "FAJR|2026-11-01|1000",
                "FAJR|2026-11-10|2000",
            ).joinToString("\n")
        }
        val repo = PrayerPreferencesRepositoryImpl(store)
        val guard = PrayerAlarmOccurrenceGuard(repo)

        val cutoff = LocalDate.of(2026, 11, 4)
        val newKey = AlarmOccurrenceKey.build(Prayer.DHUHR, "2026-11-11", 3_000L, 3_000L)
        assertTrue(guard.tryMarkFired(newKey, cutoff))

        val remaining = store.data.first()[storageKey]
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        assertFalse("FAJR|2026-11-01|1000" in remaining)
        assertTrue("garbage" in remaining)
        assertTrue("FAJR|not-a-date|1000" in remaining)
        assertTrue("FAJR|ts:1000" in remaining)
        assertTrue("FAJR|fallback:100" in remaining)
        assertTrue("FAJR|2026-11-10|2000" in remaining)
        assertTrue(newKey in remaining)
    }

    @Test
    fun dstFallBackTransitionFromEdtToEstSchedulesExactNextMorningFajr() = runTest {
        val zone = ZoneId.of("America/New_York")
        val edtDay = LocalDate.of(2026, 10, 31)
        val estDay = LocalDate.of(2026, 11, 1)
        val ishaLocal = edtDay.atTime(19, 30)
        val nextFajrLocal = estDay.atTime(4, 30)
        assertEquals(ZoneOffset.ofHours(-4), zone.rules.getOffset(ishaLocal))
        assertEquals(ZoneOffset.ofHours(-5), zone.rules.getOffset(nextFajrLocal))

        val ishaMillis = epochMillis(ishaLocal, zone)
        val expectedNextFajrMillis = epochMillis(nextFajrLocal, zone)
        assertEquals(10L * 60L * 60L * 1000L, expectedNextFajrMillis - ishaMillis)

        val clock = MutableClock(Instant.ofEpochMilli(ishaMillis - 10L), zone)
        val harness = createHarness(zone = zone, clock = clock)

        harness.fireAlarm(Prayer.ISHA, edtDay.toString(), ishaMillis)

        assertEquals(1, harness.dispatched.size)
        assertEquals(Prayer.FAJR, harness.scheduled.single().prayer)
        assertEquals(estDay.toString(), harness.scheduled.single().dayKey)
        assertEquals(expectedNextFajrMillis, harness.scheduled.single().triggerAtMillis)
    }

    @Test
    fun nonCancellationDispatchExceptionStillReconcilesNextAlarm() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val dhuhrMillis = epochMillis(day.atTime(12, 0), zone)
        val clock = MutableClock(Instant.ofEpochMilli(fajrMillis), zone)
        val harness = createHarness(zone = zone, clock = clock)
        var dispatchAttempts = 0
        var finished = 0

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.FAJR,
            dayKey = day.toString(),
            scheduledAtMillis = fajrMillis,
            correlationId = "dispatch-err-1",
            preferencesRepository = harness.repo,
            occurrenceGuard = harness.guard,
            manager = harness.manager,
            onDispatch = { _, _ ->
                dispatchAttempts++
                throw IllegalStateException("notification manager error")
            },
            onFinish = { finished++ },
            clock = clock,
        )

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.FAJR,
            dayKey = day.toString(),
            scheduledAtMillis = fajrMillis,
            correlationId = "dispatch-err-2",
            preferencesRepository = harness.repo,
            occurrenceGuard = harness.guard,
            manager = harness.manager,
            onDispatch = { _, _ -> dispatchAttempts++ },
            onFinish = { finished++ },
            clock = clock,
        )

        assertEquals(1, dispatchAttempts)
        assertEquals(2, finished)
        assertEquals(2, harness.scheduled.size)
        assertTrue(harness.scheduled.all { it.prayer == Prayer.DHUHR && it.triggerAtMillis == dhuhrMillis })
    }

    private fun createHarness(
        zone: ZoneId,
        clock: MutableClock,
        adjustments: PrayerAdjustments = PrayerAdjustments(),
        calculator: PrayerTimesCalculator = fixedCalculator(),
    ): TestHarness {
        val repo = FakePrayerPreferencesRepository(testLocation(zone.id), adjustments)
        val scheduled = mutableListOf<PrayerAlarm>()
        val manager = PrayerTimeManager(repo, calculator, recordingScheduler(scheduled), clock)
        val guard = PrayerAlarmOccurrenceGuard(repo)
        return TestHarness(repo, guard, manager, scheduled, clock)
    }

    private class TestHarness(
        val repo: FakePrayerPreferencesRepository,
        val guard: PrayerAlarmOccurrenceGuard,
        val manager: PrayerTimeManager,
        val scheduled: MutableList<PrayerAlarm>,
        val clock: MutableClock,
    ) {
        val dispatched = mutableListOf<Pair<Prayer, AdhanMode>>()

        suspend fun fireAlarm(prayer: Prayer, dayKey: String?, scheduledAtMillis: Long) {
            PrayerAlarmReceiver.handleAlarm(
                prayer = prayer,
                dayKey = dayKey,
                scheduledAtMillis = scheduledAtMillis,
                correlationId = "${prayer.name}-$dayKey",
                preferencesRepository = repo,
                occurrenceGuard = guard,
                manager = manager,
                onDispatch = { mode, _ -> dispatched += prayer to mode },
                onFinish = {},
                clock = clock,
            )
        }
    }

    private class MutableClock(
        private var currentInstant: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        fun setMillis(millis: Long) {
            currentInstant = Instant.ofEpochMilli(millis)
        }

        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)
        override fun instant(): Instant = currentInstant
    }

    private class FakePrayerPreferencesRepository(
        location: PrayerLocation,
        adjustments: PrayerAdjustments,
    ) : PrayerPreferencesRepository {
        private val prefsFlow = MutableStateFlow(
            PrayerPreferences(
                location = location,
                autoLocationEnabled = false,
                method = CalculationMethod.EGYPTIAN,
                asrMethod = AsrMethod.STANDARD,
                highLatitudeRule = HighLatitudeRulePref.ANGLE_BASED,
                adjustments = adjustments,
                adhanConfig = PrayerAdhanConfig(),
                voiceSelection = PrayerVoiceSelection(),
                hijriAdjustmentDays = 0,
                customAdhanAudioUri = null,
            ),
        )
        private val firedKeys = LinkedHashSet<String>()
        var failPersistence: Boolean = false

        override val preferences: Flow<PrayerPreferences> = prefsFlow
        override val onboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
        override val vendorGuidanceAcknowledged: Flow<Boolean> = MutableStateFlow(true)
        override val voiceSelection: Flow<PrayerVoiceSelection> = MutableStateFlow(PrayerVoiceSelection())
        override val downloadedAdhanIds: Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun setLocation(location: PrayerLocation) {
            prefsFlow.value = prefsFlow.value.copy(location = location)
        }

        override suspend fun setAutoLocationEnabled(enabled: Boolean) = Unit
        override suspend fun setMethod(method: CalculationMethod) = Unit
        override suspend fun setAsrMethod(method: AsrMethod) = Unit
        override suspend fun setHighLatitudeRule(rule: HighLatitudeRulePref) = Unit
        override suspend fun setAdjustments(adjustments: PrayerAdjustments) {
            prefsFlow.value = prefsFlow.value.copy(adjustments = adjustments)
        }

        override suspend fun setAdhanConfig(config: PrayerAdhanConfig) {
            prefsFlow.value = prefsFlow.value.copy(adhanConfig = config)
        }

        override suspend fun setCustomAdhanAudioUri(uri: String?) = Unit
        override suspend fun setVoice(prayer: Prayer, recordingId: String) = Unit
        override suspend fun setAdhanDownloaded(id: String, downloaded: Boolean) = Unit
        override suspend fun setHijriAdjustmentDays(days: Int) = Unit
        override suspend fun setOnboardingCompleted() = Unit
        override suspend fun setVendorGuidanceAcknowledged() = Unit

        override suspend fun tryMarkAlarmOccurrenceFired(
            occurrenceKey: String,
            retentionCutoffDate: LocalDate?,
        ): Boolean {
            if (failPersistence) throw IOException("simulated persistence failure")
            synchronized(firedKeys) {
                if (retentionCutoffDate != null) {
                    firedKeys.removeIf { key ->
                        val date = AlarmOccurrenceKey.extractDayKey(key)
                        date != null && date.isBefore(retentionCutoffDate)
                    }
                }
                if (occurrenceKey in firedKeys) return false
                firedKeys.add(occurrenceKey)
                while (firedKeys.size > PrayerAlarmOccurrenceGuard.MAX_ENTRIES) {
                    firedKeys.remove(firedKeys.iterator().next())
                }
                return true
            }
        }
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val lock = Mutex()
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = lock.withLock {
            val next = transform(state.value)
            state.value = next
            next
        }
    }

    private fun testLocation(timezoneId: String) = PrayerLocation(
        cityAr = "القاهرة",
        cityEn = "Cairo",
        countryAr = "مصر",
        countryEn = "Egypt",
        latitude = 30.0444,
        longitude = 31.2357,
        timezoneId = timezoneId,
    )

    private fun recordingScheduler(target: MutableList<PrayerAlarm>) = object : PrayerAlarmScheduler {
        override fun canScheduleExact(): Boolean = true
        override fun schedule(alarm: PrayerAlarm): Boolean {
            synchronized(target) { target += alarm }
            return true
        }

        override fun cancel() = Unit
    }

    private fun fixedCalculator(
        fajrHour: Int = 4,
        fajrMinute: Int = 30,
        ishaHour: Int = 19,
        ishaMinute: Int = 30,
    ) = object : PrayerTimesCalculator {
        override fun calculate(
            date: LocalDate,
            latitude: Double,
            longitude: Double,
            timezoneId: ZoneId,
            method: CalculationMethod,
            asrMethod: AsrMethod,
            highLatitudeRule: HighLatitudeRulePref,
            adjustments: PrayerAdjustments,
        ): DayPrayerTimes = DayPrayerTimes(
            date = date,
            fajr = date.atTime(fajrHour, fajrMinute).plusMinutes(adjustments.fajr.toLong()),
            sunrise = date.atTime(6, 0),
            dhuhr = date.atTime(12, 0).plusMinutes(adjustments.dhuhr.toLong()),
            asr = date.atTime(15, 30).plusMinutes(adjustments.asr.toLong()),
            maghrib = date.atTime(18, 0).plusMinutes(adjustments.maghrib.toLong()),
            isha = date.atTime(ishaHour, ishaMinute).plusMinutes(adjustments.isha.toLong()),
        )
    }

    private fun epochMillis(time: LocalDateTime, zone: ZoneId): Long =
        time.toInstant(zone.rules.getOffset(time)).toEpochMilli()
}
