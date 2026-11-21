package com.ayah.quran.prayer

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerAlarmWakeLockAndReceiverLifecycleTest {

    @Test
    fun perTokenIndependentWakeLockInstancesAcquireOnceEach() {
        val clock = AtomicLong(1_000L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val lease1 = owner.acquireHandover(5_000L)
        val lease2 = owner.acquireHandover(8_000L)

        assertEquals(2, created.size)
        assertNotSame(created[0], created[1])
        assertEquals(1, created[0].acquireCount)
        assertEquals(1, created[1].acquireCount)
        assertEquals(PrayerAlarmWakeLock.WAKE_LOCK_TAG, created[0].tag)
        assertTrue(created[0].isHeld())
        assertTrue(created[1].isHeld())

        lease1.release()
        lease2.release()
        assertEquals(0, owner.activeLeaseCountForTest())
        assertTrue(created.none { it.isHeld() })
    }

    @Test
    fun timeoutValuesAreClampedToPositiveMaximumBound() {
        val clock = AtomicLong(10_000L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val zeroLease = owner.acquireHandover(0L)
        val negativeLease = owner.acquireHandover(-500L)
        val oversizedLease = owner.acquireHandover(120_000L)

        assertEquals(1L, created[0].lastTimeoutMs)
        assertEquals(1L, created[1].lastTimeoutMs)
        assertEquals(PrayerAlarmWakeLock.HANDOVER_WAKELOCK_MAX_MILLIS, created[2].lastTimeoutMs)

        zeroLease.release()
        negativeLease.release()
        oversizedLease.release()
        assertEquals(0, owner.activeLeaseCountForTest())
        assertTrue(created.none { it.isHeld() })
    }

    @Test
    fun leaseReleaseAndReleaseTokenAreIdempotent() {
        val clock = AtomicLong(1_000L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val lease = owner.acquireHandover()
        lease.release()
        lease.release()
        owner.releaseToken(lease.tokenId)
        owner.releaseToken(PrayerAlarmWakeLock.INVALID_TOKEN_ID)

        assertEquals(1, created.single().releaseCount)
        assertFalse(created.single().isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun staleTokenExpiryCannotReleaseNewerToken() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock, autoExpireOnClock = true).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val lease1 = owner.acquireHandover(15_000L)
        clock.set(9_000L)
        val lease2 = owner.acquireHandover(15_000L)

        clock.set(15_000L)
        assertFalse(created[0].isHeld())
        assertTrue(created[1].isHeld())

        lease1.release()
        assertTrue(created[1].isHeld())
        assertEquals(1, owner.activeLeaseCountForTest())

        lease2.release()
        assertFalse(created[1].isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun overlappingTokensRemainIndependentlyHeldUntilEachReleases() {
        val clock = AtomicLong(1_000L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val lease1 = owner.acquireHandover(10_000L)
        val lease2 = owner.acquireHandover(10_000L)

        lease1.release()
        assertFalse(created[0].isHeld())
        assertTrue(created[1].isHeld())
        assertEquals(1, owner.activeLeaseCountForTest())

        lease2.release()
        assertFalse(created[1].isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun expiredTokenPruningDefensivelyReleasesItsOwnPlatformLock() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock, autoExpireOnClock = false).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        owner.acquireHandover(5_000L)
        assertTrue(created[0].isHeld())

        clock.set(6_000L)
        val lease2 = owner.acquireHandover(5_000L)

        assertFalse(created[0].isHeld())
        assertEquals(1, created[0].releaseCount)
        assertTrue(created[1].isHeld())
        assertEquals(1, owner.activeLeaseCountForTest())

        lease2.release()
        assertFalse(created[1].isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun releaseExceptionOnOneTokenDoesNotLeakOtherTokens() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                val failOnRelease = created.isEmpty()
                FakePlatformWakeLock(tag, clock, throwOnRelease = failOnRelease).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val lease1 = owner.acquireHandover(10_000L)
        val lease2 = owner.acquireHandover(10_000L)

        owner.releaseTokens(listOf(lease1.tokenId, lease2.tokenId))

        assertEquals(1, created[0].releaseCount)
        assertEquals(1, created[1].releaseCount)
        assertFalse(created[1].isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun nonFullAdhanModesAcquireNoHandoverWakeLock() = runTest {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val testClock = FixedClock(Instant.ofEpochMilli(fajrMillis), zone)

        val postedNotifications = mutableListOf<AdhanMode>()
        val startedServiceTokens = mutableListOf<Long>()

        val nonFullModes = listOf(AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT, AdhanMode.DISABLED)
        for ((index, mode) in nonFullModes.withIndex()) {
            val repo = FakePrayerPreferencesRepository(
                location = testLocation(zone.id),
                adhanConfig = PrayerAdhanConfig(fajr = mode),
            )
            val scheduled = mutableListOf<PrayerAlarm>()
            val manager = PrayerTimeManager(repo, fixedCalculator(), recordingScheduler(scheduled), testClock)
            val guard = PrayerAlarmOccurrenceGuard(repo)
            var finished = 0

            PrayerAlarmReceiver.handleAlarm(
                prayer = Prayer.FAJR,
                dayKey = day.toString(),
                scheduledAtMillis = fajrMillis + index,
                correlationId = "mode-${mode.name}",
                preferencesRepository = repo,
                occurrenceGuard = guard,
                manager = manager,
                onDispatch = { dispatchedMode, voiceId ->
                    PrayerAlarmReceiver.routePrayerAlert(
                        mode = dispatchedMode,
                        voiceId = voiceId,
                        correlationId = "mode-${mode.name}",
                        wakeLock = wakeLock,
                        onPostNotification = {
                            postedNotifications += it
                            true
                        },
                        onStartFullAdhanService = { token -> startedServiceTokens += token },
                        onFallbackAlert = {
                            postedNotifications += it
                            true
                        },
                    )
                },
                onFinish = { finished++ },
                clock = testClock,
            )
            assertEquals(1, finished)
        }

        assertEquals(listOf(AdhanMode.NOTIFICATION_SOUND, AdhanMode.SILENT), postedNotifications)
        assertEquals(0, startedServiceTokens.size)
        assertEquals(0, created.size)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val fullRepo = FakePrayerPreferencesRepository(
            location = testLocation(zone.id),
            adhanConfig = PrayerAdhanConfig(fajr = AdhanMode.FULL_ADHAN),
        )
        val fullScheduled = mutableListOf<PrayerAlarm>()
        val fullManager = PrayerTimeManager(fullRepo, fixedCalculator(), recordingScheduler(fullScheduled), testClock)
        val fullGuard = PrayerAlarmOccurrenceGuard(fullRepo)
        var fullFinished = 0

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.FAJR,
            dayKey = day.toString(),
            scheduledAtMillis = fajrMillis + 100L,
            correlationId = "mode-FULL_ADHAN",
            preferencesRepository = fullRepo,
            occurrenceGuard = fullGuard,
            manager = fullManager,
            onDispatch = { dispatchedMode, voiceId ->
                PrayerAlarmReceiver.routePrayerAlert(
                    mode = dispatchedMode,
                    voiceId = voiceId,
                    correlationId = "mode-FULL_ADHAN",
                    wakeLock = wakeLock,
                    onPostNotification = {
                        postedNotifications += it
                        true
                    },
                    onStartFullAdhanService = { token -> startedServiceTokens += token },
                    onFallbackAlert = {
                        postedNotifications += it
                        true
                    },
                )
            },
            onFinish = { fullFinished++ },
            clock = testClock,
        )

        assertEquals(1, fullFinished)
        assertEquals(1, startedServiceTokens.size)
        assertTrue(startedServiceTokens.single() > PrayerAlarmWakeLock.INVALID_TOKEN_ID)
        assertEquals(1, created.size)
        assertTrue(created.single().isHeld())

        wakeLock.releaseToken(startedServiceTokens.single())
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun fullAdhanHandoverStaysHeldThroughUriResolutionAndBufferingUntilFifoBarrier() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val pendingBarriers = mutableListOf<() -> Unit>()
        var watchdogCancelled = 0
        var active = false

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        pendingBarriers += action
                        return true
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = { watchdogCancelled++ },
        )

        var handedToken = PrayerAlarmWakeLock.INVALID_TOKEN_ID
        PrayerAlarmReceiver.dispatchFullAdhan(
            voiceId = "default",
            correlationId = "fajr-full",
            wakeLock = wakeLock,
            startService = { tokenId -> handedToken = tokenId },
            onFallbackAlert = { true },
        )
        assertTrue(handedToken > 0L)
        assertTrue(created.single().isHeld())

        val gen = controller.nextGeneration()
        tracker.register(gen, handedToken)
        assertTrue(created.single().isHeld())

        controller.startPlayback(gen, Prayer.FAJR, "android.resource://com.ayah.quran/1")
        assertTrue(active)
        assertTrue(created.single().isHeld())
        assertEquals(0, watchdogCancelled)
        assertEquals(0, pendingBarriers.size)

        controller.onIsPlayingStarted(gen)
        assertEquals(1, watchdogCancelled)
        assertEquals(1, pendingBarriers.size)
        assertTrue(created.single().isHeld())

        pendingBarriers.single().invoke()
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
        assertEquals(0, tracker.pendingCountForTest())
    }

    @Test
    fun stateReadyWithPlayWhenReadyFalseRetainsLeaseUntilFocusGainAndFifoBarrier() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val pendingBarriers = mutableListOf<() -> Unit>()
        var paused = false
        var playCalls = 0
        var watchdogCancelled = 0

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() {
                        paused = true
                    }

                    override fun play() {
                        paused = false
                        playCalls++
                    }

                    override fun stopAndRelease() = Unit
                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        pendingBarriers += action
                        return true
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = { watchdogCancelled++ },
        )

        val lease = wakeLock.acquireHandover()
        val gen = controller.nextGeneration()
        tracker.register(gen, lease.tokenId)
        controller.startPlayback(gen, Prayer.FAJR, "file:///adhan.mp3")

        controller.onFocusLossTransient()
        assertTrue(paused)
        assertTrue(created.single().isHeld())
        assertEquals(0, watchdogCancelled)
        assertEquals(0, pendingBarriers.size)

        controller.onFocusGain()
        assertFalse(paused)
        assertEquals(1, playCalls)
        assertTrue(created.single().isHeld())

        controller.onIsPlayingStarted(gen)
        assertEquals(1, watchdogCancelled)
        assertTrue(created.single().isHeld())

        pendingBarriers.single().invoke()
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun unavailablePlaybackLooperOnBarrierPostFallsBackAndReleasesToken() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        var fallbacks = 0
        var stoppedSelf = 0
        var releasedPlayers = 0

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks++ },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean = false
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val lease = wakeLock.acquireHandover()
        val gen = controller.nextGeneration()
        tracker.register(gen, lease.tokenId)
        controller.startPlayback(gen, Prayer.ASR, "file:///adhan.mp3")

        controller.onIsPlayingStarted(gen)

        assertEquals(1, fallbacks)
        assertEquals(1, stoppedSelf)
        assertEquals(1, releasedPlayers)
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun watchdogTimeoutCoversBufferingAndPausedStateReadyWithFallbackAndCleanup() = runTest {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var stoppedSelf = 0
        var releasedPlayers = 0
        var watchdogJob: kotlinx.coroutines.Job? = null

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean = true
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {
                watchdogJob?.cancel()
                watchdogJob = null
            },
        )

        val lease = wakeLock.acquireHandover()
        val gen = controller.nextGeneration()
        tracker.register(gen, lease.tokenId)
        watchdogJob = async {
            delay(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS)
            controller.onPreparationTimeout(gen, Prayer.MAGHRIB)
        }
        controller.startPlayback(gen, Prayer.MAGHRIB, "content://slow/adhan.mp3")
        controller.onFocusLossTransient()

        advanceTimeBy(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS - 1L)
        runCurrent()
        assertTrue(created.single().isHeld())
        assertEquals(0, fallbacks.size)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(Prayer.MAGHRIB), fallbacks)
        assertEquals(1, stoppedSelf)
        assertEquals(1, releasedPlayers)
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun playbackStartAndWatchdogRaceIsDeterministicInBothOrderings() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        var fallbacks = 0
        var stoppedSelf = 0
        var active = false

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks++ },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val lease1 = wakeLock.acquireHandover()
        val gen1 = controller.nextGeneration()
        tracker.register(gen1, lease1.tokenId)
        controller.startPlayback(gen1, Prayer.FAJR, "file:///1.mp3")
        controller.onIsPlayingStarted(gen1)
        controller.onPreparationTimeout(gen1, Prayer.FAJR)

        assertTrue(active)
        assertEquals(0, fallbacks)
        assertEquals(0, stoppedSelf)
        assertFalse(created[0].isHeld())

        val lease2 = wakeLock.acquireHandover()
        val gen2 = controller.nextGeneration()
        tracker.register(gen2, lease2.tokenId)
        controller.startPlayback(gen2, Prayer.DHUHR, "file:///2.mp3")
        controller.onPreparationTimeout(gen2, Prayer.DHUHR)
        controller.onIsPlayingStarted(gen2)

        assertFalse(active)
        assertEquals(1, fallbacks)
        assertEquals(1, stoppedSelf)
        assertFalse(created[1].isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun startForegroundServiceFailureReleasesHandoverTokenAndRunsFallback() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val fallbackModes = mutableListOf<AdhanMode>()

        PrayerAlarmReceiver.dispatchFullAdhan(
            voiceId = "default",
            correlationId = "fgs-fail",
            wakeLock = wakeLock,
            startService = { throw IllegalStateException("ForegroundServiceStartNotAllowed") },
            onFallbackAlert = { mode ->
                fallbackModes += mode
                true
            },
        )

        assertEquals(1, created.size)
        assertEquals(1, created.single().acquireCount)
        assertEquals(1, created.single().releaseCount)
        assertFalse(created.single().isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
        assertEquals(listOf(AdhanMode.NOTIFICATION_SOUND), fallbackModes)
    }

    @Test
    fun serviceReEntryActionStopAndOnDestroyDrainAllPendingTokensWithoutOrphans() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }

        val lease1 = wakeLock.acquireHandover()
        tracker.register(1L, lease1.tokenId)
        assertTrue(created[0].isHeld())

        val lease2 = wakeLock.acquireHandover()
        tracker.register(2L, lease2.tokenId)
        assertFalse(created[0].isHeld())
        assertTrue(created[1].isHeld())

        tracker.register(3L, PrayerAlarmWakeLock.INVALID_TOKEN_ID)
        assertFalse(created[1].isHeld())
        assertEquals(0, tracker.pendingCountForTest())
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lease3 = wakeLock.acquireHandover()
        tracker.register(4L, lease3.tokenId)
        assertTrue(created[2].isHeld())

        tracker.releaseAll()
        assertFalse(created[2].isHeld())
        assertEquals(0, tracker.pendingCountForTest())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun pendingResultFinishExecutesExactlyOnceEvenWhenFinishThrows() = runTest {
        val finishCalls = AtomicInteger(0)

        PrayerAlarmReceiver.launchAlarmWork(
            scope = this,
            correlationId = "finish-once",
            onFinish = {
                finishCalls.incrementAndGet()
                throw IllegalStateException("already finished")
            },
        ) { finishOnce ->
            finishOnce()
            finishOnce()
        }
        advanceUntilIdle()

        assertEquals(1, finishCalls.get())
    }

    @Test
    fun synchronousLaunchFailureAndPreStartCancellationStillFinishExactlyOnce() {
        val launchFailFinish = AtomicInteger(0)
        val rejectingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                throw RejectedExecutionException("pool closed")
            }
        }
        val rejectingScope = CoroutineScope(SupervisorJob() + rejectingDispatcher)

        PrayerAlarmReceiver.launchAlarmWork(
            scope = rejectingScope,
            correlationId = "reject-launch",
            onFinish = { launchFailFinish.incrementAndGet() },
        ) { _ ->
            Unit
        }
        assertEquals(1, launchFailFinish.get())

        val preCancelledFinish = AtomicInteger(0)
        val cancelledScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher())
        cancelledScope.cancel()

        PrayerAlarmReceiver.launchAlarmWork(
            scope = cancelledScope,
            correlationId = "pre-cancelled",
            onFinish = { preCancelledFinish.incrementAndGet() },
        ) { _ ->
            error("should not run")
        }
        assertEquals(1, preCancelledFinish.get())

        val queuedCancelFinish = AtomicInteger(0)
        val testDispatcher = StandardTestDispatcher()
        val queuedScope = CoroutineScope(SupervisorJob() + testDispatcher)
        PrayerAlarmReceiver.launchAlarmWork(
            scope = queuedScope,
            correlationId = "queued-cancelled",
            onFinish = { queuedCancelFinish.incrementAndGet() },
        ) { _ ->
            error("should not run after cancel before dispatch")
        }
        queuedScope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, queuedCancelFinish.get())
    }

    @Test
    fun dispatchExceptionAndCancellationStillExecuteCleanupAndFinishExactlyOnce() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = epochMillis(day.atTime(4, 30), zone)
        val clock = FixedClock(Instant.ofEpochMilli(fajrMillis), zone)

        val repo1 = FakePrayerPreferencesRepository(testLocation(zone.id))
        val scheduled1 = mutableListOf<PrayerAlarm>()
        val manager1 = PrayerTimeManager(repo1, fixedCalculator(), recordingScheduler(scheduled1), clock)
        val guard1 = PrayerAlarmOccurrenceGuard(repo1)
        val finish1 = AtomicInteger(0)

        PrayerAlarmReceiver.launchAlarmWork(
            scope = this,
            correlationId = "dispatch-throw",
            onFinish = { finish1.incrementAndGet() },
        ) { finishOnce ->
            PrayerAlarmReceiver.handleAlarm(
                prayer = Prayer.FAJR,
                dayKey = day.toString(),
                scheduledAtMillis = fajrMillis,
                correlationId = "dispatch-throw",
                preferencesRepository = repo1,
                occurrenceGuard = guard1,
                manager = manager1,
                onDispatch = { _, _ -> throw IllegalStateException("dispatch boom") },
                onFinish = finishOnce,
                clock = clock,
            )
        }
        advanceUntilIdle()
        assertEquals(1, finish1.get())
        assertEquals(1, scheduled1.size)
        assertEquals(Prayer.DHUHR, scheduled1.single().prayer)

        val repo2 = FakePrayerPreferencesRepository(testLocation(zone.id))
        val scheduled2 = mutableListOf<PrayerAlarm>()
        val manager2 = PrayerTimeManager(repo2, fixedCalculator(), recordingScheduler(scheduled2), clock)
        val guard2 = PrayerAlarmOccurrenceGuard(repo2)
        val finish2 = AtomicInteger(0)
        var caughtCancel = false

        try {
            PrayerAlarmReceiver.handleAlarm(
                prayer = Prayer.FAJR,
                dayKey = day.toString(),
                scheduledAtMillis = fajrMillis,
                correlationId = "cancel-mid",
                preferencesRepository = repo2,
                occurrenceGuard = guard2,
                manager = manager2,
                onDispatch = { _, _ -> throw CancellationException("cancelled") },
                onFinish = { finish2.incrementAndGet() },
                clock = clock,
            )
        } catch (_: CancellationException) {
            caughtCancel = true
        }
        assertTrue(caughtCancel)
        assertEquals(1, finish2.get())
        assertEquals(0, scheduled2.size)
    }

    @Test
    fun noLockRemainsHeldAcrossAllTerminalFailureAndCompletionPaths() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        var grantFocus = true
        var throwInPlayer = false
        var endedCallback: (() -> Unit)? = null
        var errorCallback: ((Throwable?) -> Unit)? = null
        val fallbacks = mutableListOf<Prayer>()

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { grantFocus },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, onEnded, onError ->
                if (throwInPlayer) throw IllegalStateException("decoder init failed")
                endedCallback = onEnded
                errorCallback = onError
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean = true
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val l1 = wakeLock.acquireHandover()
        val g1 = controller.nextGeneration()
        tracker.register(g1, l1.tokenId)
        controller.startPlayback(g1, Prayer.FAJR, null)
        assertFalse(created.last().isHeld())

        grantFocus = false
        val l2 = wakeLock.acquireHandover()
        val g2 = controller.nextGeneration()
        tracker.register(g2, l2.tokenId)
        controller.startPlayback(g2, Prayer.DHUHR, "file:///ok.mp3")
        assertFalse(created.last().isHeld())
        grantFocus = true

        throwInPlayer = true
        val l3 = wakeLock.acquireHandover()
        val g3 = controller.nextGeneration()
        tracker.register(g3, l3.tokenId)
        controller.startPlayback(g3, Prayer.ASR, "file:///ok.mp3")
        assertFalse(created.last().isHeld())
        throwInPlayer = false

        val l4 = wakeLock.acquireHandover()
        val g4 = controller.nextGeneration()
        tracker.register(g4, l4.tokenId)
        controller.startPlayback(g4, Prayer.MAGHRIB, "file:///ok.mp3")
        assertTrue(created.last().isHeld())
        errorCallback?.invoke(RuntimeException("io"))
        assertFalse(created.last().isHeld())

        val l5 = wakeLock.acquireHandover()
        val g5 = controller.nextGeneration()
        tracker.register(g5, l5.tokenId)
        controller.startPlayback(g5, Prayer.ISHA, "file:///ok.mp3")
        assertTrue(created.last().isHeld())
        endedCallback?.invoke()
        assertFalse(created.last().isHeld())

        val l6 = wakeLock.acquireHandover()
        val g6 = controller.nextGeneration()
        tracker.register(g6, l6.tokenId)
        controller.startPlayback(g6, Prayer.FAJR, "file:///ok.mp3")
        assertTrue(created.last().isHeld())
        controller.onFocusLossPermanent()
        assertFalse(created.last().isHeld())

        assertEquals(
            listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.FAJR),
            fallbacks,
        )
        assertEquals(0, wakeLock.activeLeaseCountForTest())
        assertEquals(0, tracker.pendingCountForTest())
        assertTrue(created.none { it.isHeld() })
    }

    @Test
    fun staleWatchdogAfterOnEndedAndOnErrorIsIgnoredAndDoesNotAffectNextGeneration() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var stoppedSelf = 0
        var releasedPlayers = 0
        var active = false
        var endedCallback: (() -> Unit)? = null
        var errorCallback: ((Throwable?) -> Unit)? = null

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, onEnded, onError ->
                endedCallback = onEnded
                errorCallback = onError
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        action()
                        return true
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val l1 = wakeLock.acquireHandover()
        val g1 = controller.nextGeneration()
        tracker.register(g1, l1.tokenId)
        controller.startPlayback(g1, Prayer.FAJR, "file:///short.mp3")
        endedCallback?.invoke()
        assertFalse(controller.onPreparationTimeout(g1, Prayer.FAJR))
        assertEquals(0, fallbacks.size)
        assertEquals(1, stoppedSelf)
        assertEquals(1, releasedPlayers)
        assertFalse(created[0].isHeld())

        val l2 = wakeLock.acquireHandover()
        val g2 = controller.nextGeneration()
        tracker.register(g2, l2.tokenId)
        controller.startPlayback(g2, Prayer.DHUHR, "file:///broken.mp3")
        errorCallback?.invoke(IllegalStateException("codec error"))
        assertFalse(controller.onPreparationTimeout(g2, Prayer.DHUHR))
        assertEquals(listOf(Prayer.DHUHR), fallbacks)
        assertEquals(2, stoppedSelf)
        assertEquals(2, releasedPlayers)
        assertFalse(created[1].isHeld())

        val l3 = wakeLock.acquireHandover()
        val g3 = controller.nextGeneration()
        tracker.register(g3, l3.tokenId)
        controller.startPlayback(g3, Prayer.ASR, "file:///valid.mp3")
        assertFalse(controller.onPreparationTimeout(g1, Prayer.FAJR))
        assertFalse(controller.onPreparationTimeout(g2, Prayer.DHUHR))
        assertTrue(active)
        assertTrue(created[2].isHeld())

        controller.onIsPlayingStarted(g3)
        assertTrue(active)
        assertFalse(created[2].isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun reEntrantStartFailurePreservesPriorGenerationAndReleasesOnlyIncomingToken() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var cancelledPriorJobs = 0
        var stopSelfIfIdleCalls = 0
        var releasedPlayers = 0
        var active = false

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        action()
                        return true
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val coldLease = wakeLock.acquireHandover()
        val coldResult = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.FAJR,
            incomingWakeLockToken = coldLease.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw SecurityException("fgs blocked") },
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { fallbacks += it },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = {
                if (!controller.hasActivePlayback()) stopSelfIfIdleCalls++
            },
        )
        assertEquals(null, coldResult)
        assertFalse(created[0].isHeld())
        assertEquals(listOf(Prayer.FAJR), fallbacks)
        assertEquals(1, stopSelfIfIdleCalls)
        assertEquals(0, cancelledPriorJobs)

        val priorLease = wakeLock.acquireHandover()
        val gen1 = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.DHUHR,
            incomingWakeLockToken = priorLease.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = {},
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { fallbacks += it },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = {
                if (!controller.hasActivePlayback()) stopSelfIfIdleCalls++
            },
        )
        assertTrue(gen1 != null)
        controller.startPlayback(gen1!!, Prayer.DHUHR, "file:///dhuhr.mp3")
        assertTrue(active)
        assertTrue(created[1].isHeld())

        val incomingLease = wakeLock.acquireHandover()
        val failedReEntry = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.ASR,
            incomingWakeLockToken = incomingLease.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw IllegalStateException("fgs rejected on re-entry") },
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { fallbacks += it },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = {
                if (!controller.hasActivePlayback()) stopSelfIfIdleCalls++
            },
        )

        assertEquals(null, failedReEntry)
        assertFalse(created[2].isHeld())
        assertTrue(created[1].isHeld())
        assertTrue(controller.isCurrentGeneration(gen1))
        assertTrue(active)
        assertEquals(0, releasedPlayers)
        assertEquals(1, cancelledPriorJobs)
        assertEquals(1, stopSelfIfIdleCalls)
        assertEquals(listOf(Prayer.FAJR, Prayer.ASR), fallbacks)

        controller.onIsPlayingStarted(gen1)
        assertFalse(created[1].isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun reEntrantStartSuccessSupersedesPriorGenerationWithoutReleasingIncomingToken() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val pendingBarriers = mutableListOf<() -> Unit>()
        var cancelledPriorJobs = 0
        var releasedPlayers = 0
        val fallbacks = mutableListOf<Prayer>()

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        pendingBarriers += action
                        return true
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {},
        )

        val lease1 = wakeLock.acquireHandover()
        val gen1 = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.FAJR,
            incomingWakeLockToken = lease1.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = {},
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { fallbacks += it },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = {},
        )!!
        controller.startPlayback(gen1, Prayer.FAJR, "file:///1.mp3")
        assertTrue(created[0].isHeld())

        val lease2 = wakeLock.acquireHandover()
        val gen2 = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.DHUHR,
            incomingWakeLockToken = lease2.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = {},
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { fallbacks += it },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = {},
        )!!

        assertEquals(2, cancelledPriorJobs)
        assertEquals(1, releasedPlayers)
        assertFalse(created[0].isHeld())
        assertTrue(created[1].isHeld())
        assertEquals(1, tracker.pendingCountForTest())
        assertFalse(controller.isCurrentGeneration(gen1))
        assertTrue(controller.isCurrentGeneration(gen2))

        controller.onIsPlayingStarted(gen1)
        assertFalse(controller.onPreparationTimeout(gen1, Prayer.FAJR))
        assertEquals(0, pendingBarriers.size)
        assertTrue(created[1].isHeld())

        controller.startPlayback(gen2, Prayer.DHUHR, "file:///2.mp3")
        assertTrue(created[1].isHeld())
        controller.onIsPlayingStarted(gen2)
        assertEquals(1, pendingBarriers.size)
        assertTrue(created[1].isHeld())

        pendingBarriers.single().invoke()
        assertFalse(created[1].isHeld())
        assertEquals(0, wakeLock.activeLeaseCountForTest())
        assertEquals(0, fallbacks.size)
    }

    @Test
    fun failedPlatformWakeLockAcquisitionReturnsInvalidTokenWithoutPhantomLease() {
        val clock = AtomicLong(0L)
        val created = mutableListOf<FakePlatformWakeLock>()
        var failAcquire = true
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                FakePlatformWakeLock(tag, clock, throwOnAcquire = failAcquire).also { created += it }
            },
            elapsedRealtimeMs = clock::get,
        )

        val failedLease = owner.acquireHandover(5_000L)
        assertEquals(PrayerAlarmWakeLock.INVALID_TOKEN_ID, failedLease.tokenId)
        assertEquals(0, owner.activeLeaseCountForTest())
        assertFalse(created[0].isHeld())
        failedLease.release()
        owner.releaseToken(failedLease.tokenId)

        failAcquire = false
        val validLease = owner.acquireHandover(5_000L)
        assertTrue(validLease.tokenId > PrayerAlarmWakeLock.INVALID_TOKEN_ID)
        assertEquals(1, owner.activeLeaseCountForTest())
        assertTrue(created[1].isHeld())

        validLease.release()
        assertFalse(created[1].isHeld())
        assertEquals(0, owner.activeLeaseCountForTest())
    }

    @Test
    fun concurrentAcquireAndReleaseMaintainsIndependentTokenOwnership() = runTest {
        val clock = AtomicLong(1_000L)
        val created = mutableListOf<FakePlatformWakeLock>()
        val owner = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag ->
                val instance = FakePlatformWakeLock(tag, clock)
                synchronized(created) { created += instance }
                instance
            },
            elapsedRealtimeMs = clock::get,
        )
        val workerCount = 12
        val halfCount = workerCount / 2
        val acquireGate = CompletableDeferred<Unit>()
        val releaseFirstHalfGate = CompletableDeferred<Unit>()
        val releaseSecondHalfGate = CompletableDeferred<Unit>()
        val leases = arrayOfNulls<AlarmWakeLockLease>(workerCount)

        coroutineScope {
            val acquireJobs = (0 until workerCount).map { index ->
                async(Dispatchers.Default) {
                    acquireGate.await()
                    leases[index] = owner.acquireHandover(10_000L)
                }
            }
            acquireGate.complete(Unit)
            acquireJobs.awaitAll()

            val tokenIds = leases.map { it!!.tokenId }
            assertEquals(workerCount, tokenIds.toSet().size)
            assertTrue(tokenIds.all { it > PrayerAlarmWakeLock.INVALID_TOKEN_ID })
            assertEquals(workerCount, owner.activeLeaseCountForTest())
            synchronized(created) {
                assertEquals(workerCount, created.size)
                assertTrue(created.all { it.isHeld() })
            }

            val firstHalfJobs = (0 until halfCount).map { index ->
                async(Dispatchers.Default) {
                    releaseFirstHalfGate.await()
                    leases[index]!!.release()
                }
            }
            releaseFirstHalfGate.complete(Unit)
            firstHalfJobs.awaitAll()

            assertEquals(halfCount, owner.activeLeaseCountForTest())
            synchronized(created) {
                assertEquals(halfCount, created.count { it.isHeld() })
            }

            val secondHalfJobs = (halfCount until workerCount).map { index ->
                async(Dispatchers.Default) {
                    releaseSecondHalfGate.await()
                    leases[index]!!.release()
                }
            }
            releaseSecondHalfGate.complete(Unit)
            secondHalfJobs.awaitAll()
        }

        assertEquals(0, owner.activeLeaseCountForTest())
        synchronized(created) {
            assertTrue(created.none { it.isHeld() })
            assertTrue(created.all { it.acquireCount == 1 && it.releaseCount == 1 })
        }
    }

    private class FakePlatformWakeLock(
        val tag: String,
        private val clock: AtomicLong,
        private val autoExpireOnClock: Boolean = false,
        private val throwOnRelease: Boolean = false,
        private val throwOnAcquire: Boolean = false,
    ) : PlatformWakeLock {
        @Volatile
        private var held: Boolean = false
        private var expiresAtMs: Long = Long.MAX_VALUE
        var acquireCount: Int = 0
            private set
        var releaseCount: Int = 0
            private set
        var lastTimeoutMs: Long = 0L
            private set

        override fun acquire(timeoutMs: Long) {
            acquireCount++
            lastTimeoutMs = timeoutMs
            if (throwOnAcquire) {
                throw SecurityException("simulated wake lock acquire failure")
            }
            held = true
            expiresAtMs = clock.get() + timeoutMs
        }

        override fun release() {
            releaseCount++
            held = false
            if (throwOnRelease) {
                throw RuntimeException("simulated wake lock release failure")
            }
        }

        override fun isHeld(): Boolean {
            if (held && autoExpireOnClock && clock.get() >= expiresAtMs) {
                held = false
            }
            return held
        }
    }

    private class FixedClock(
        private val fixedInstant: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = FixedClock(fixedInstant, zone)
        override fun instant(): Instant = fixedInstant
    }

    private class FakePrayerPreferencesRepository(
        location: PrayerLocation,
        adhanConfig: PrayerAdhanConfig = PrayerAdhanConfig(),
    ) : PrayerPreferencesRepository {
        private val prefsFlow = MutableStateFlow(
            PrayerPreferences(
                location = location,
                autoLocationEnabled = false,
                method = CalculationMethod.EGYPTIAN,
                asrMethod = AsrMethod.STANDARD,
                highLatitudeRule = HighLatitudeRulePref.ANGLE_BASED,
                adjustments = PrayerAdjustments(),
                adhanConfig = adhanConfig,
                voiceSelection = PrayerVoiceSelection(),
                hijriAdjustmentDays = 0,
                customAdhanAudioUri = null,
            ),
        )
        private val firedKeys = LinkedHashSet<String>()

        override val preferences: Flow<PrayerPreferences> = prefsFlow
        override val onboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
        override val vendorGuidanceAcknowledged: Flow<Boolean> = MutableStateFlow(true)
        override val voiceSelection: Flow<PrayerVoiceSelection> = MutableStateFlow(PrayerVoiceSelection())
        override val downloadedAdhanIds: Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun setLocation(location: PrayerLocation) = Unit
        override suspend fun setAutoLocationEnabled(enabled: Boolean) = Unit
        override suspend fun setMethod(method: CalculationMethod) = Unit
        override suspend fun setAsrMethod(method: AsrMethod) = Unit
        override suspend fun setHighLatitudeRule(rule: HighLatitudeRulePref) = Unit
        override suspend fun setAdjustments(adjustments: PrayerAdjustments) = Unit
        override suspend fun setAdhanConfig(config: PrayerAdhanConfig) = Unit
        override suspend fun setCustomAdhanAudioUri(uri: String?) = Unit
        override suspend fun setVoice(prayer: Prayer, recordingId: String) = Unit
        override suspend fun setAdhanDownloaded(id: String, downloaded: Boolean) = Unit
        override suspend fun setHijriAdjustmentDays(days: Int) = Unit
        override suspend fun setOnboardingCompleted() = Unit
        override suspend fun setVendorGuidanceAcknowledged() = Unit

        override suspend fun tryMarkAlarmOccurrenceFired(
            occurrenceKey: String,
            retentionCutoffDate: LocalDate?,
        ): Boolean = synchronized(firedKeys) { firedKeys.add(occurrenceKey) }
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

    private fun fixedCalculator() = object : PrayerTimesCalculator {
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
            fajr = date.atTime(4, 30),
            sunrise = date.atTime(6, 0),
            dhuhr = date.atTime(12, 0),
            asr = date.atTime(15, 30),
            maghrib = date.atTime(18, 0),
            isha = date.atTime(19, 30),
        )
    }

    private fun epochMillis(time: LocalDateTime, zone: ZoneId): Long =
        time.toInstant(zone.rules.getOffset(time)).toEpochMilli()
}
