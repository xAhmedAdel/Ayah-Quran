package com.ayah.quran.prayer

import com.ayah.quran.domain.model.prayer.Prayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class AdhanPlaybackWatchdogAndFocusLifecycleTest {

    @Test
    fun maxDurationWatchdogStopsPlaybackAtExactFiveMinuteBoundaryWithoutFallback() = runTest {
        val harness = createHarness(this)
        val gen = harness.startConfirmedPlayback(Prayer.FAJR)

        advanceTimeBy(AdhanPlaybackService.MAX_PLAYBACK_DURATION_MILLIS - 1L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)
        assertEquals(0, harness.releasedPlayers)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(0, harness.fallbacks.size)
        assertTrue(harness.abandonedFocus >= 1)
        assertFalse(harness.controller.isCurrentGeneration(gen))
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun transientFocusLossAfterPlaybackConfirmedStopsAtExactThirtySecondBoundaryAndIgnoresLateGain() = runTest {
        val harness = createHarness(this)
        val gen = harness.startConfirmedPlayback(Prayer.DHUHR)

        harness.controller.onFocusLossTransient()
        assertEquals(1, harness.pauseCalls)
        assertTrue(harness.active)

        advanceTimeBy(AdhanPlaybackService.TRANSIENT_FOCUS_LOSS_TIMEOUT_MILLIS - 1L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(0, harness.fallbacks.size)
        assertTrue(harness.abandonedFocus >= 1)
        assertFalse(harness.controller.isCurrentGeneration(gen))
        assertEquals(null, harness.maxDurationJob)
        assertEquals(null, harness.transientFocusJob)

        val playCallsBeforeLateGain = harness.playCalls
        harness.controller.onFocusGain()
        assertEquals(playCallsBeforeLateGain, harness.playCalls)
        assertFalse(harness.active)
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun focusRegainBeforeThirtySecondTimeoutCancelsTransientWatchdogAndResumes() = runTest {
        val harness = createHarness(this)
        harness.startConfirmedPlayback(Prayer.ASR)

        harness.controller.onFocusLossTransient()
        assertEquals(1, harness.pauseCalls)

        advanceTimeBy(15_000L)
        runCurrent()
        assertTrue(harness.active)

        harness.controller.onFocusGain()
        assertEquals(1, harness.playCalls)
        assertEquals(null, harness.transientFocusJob)

        advanceTimeBy(AdhanPlaybackService.TRANSIENT_FOCUS_LOSS_TIMEOUT_MILLIS + 10_000L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)
        assertEquals(0, harness.fallbacks.size)

        harness.controller.stopAll()
        assertFalse(harness.active)
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun repeatedTransientFocusLossWithoutGainDoesNotExtendThirtySecondDeadline() = runTest {
        val harness = createHarness(this)
        harness.startConfirmedPlayback(Prayer.MAGHRIB)

        harness.controller.onFocusLossTransient()
        advanceTimeBy(20_000L)
        runCurrent()
        assertTrue(harness.active)

        harness.controller.onFocusLossTransient()
        advanceTimeBy(9_999L)
        runCurrent()
        assertTrue(harness.active)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(0, harness.fallbacks.size)
    }

    @Test
    fun multiplePauseAndRegainCyclesDoNotResetOriginalFiveMinuteMaxDurationAnchor() = runTest {
        val harness = createHarness(this)
        val gen = harness.startConfirmedPlayback(Prayer.ISHA)

        advanceTimeBy(60_000L)
        runCurrent()
        harness.controller.onFocusLossTransient()
        advanceTimeBy(20_000L)
        runCurrent()
        harness.controller.onFocusGain()
        harness.controller.onIsPlayingStarted(gen)

        advanceTimeBy(100_000L)
        runCurrent()
        harness.controller.onFocusLossTransient()
        advanceTimeBy(20_000L)
        runCurrent()
        harness.controller.onFocusGain()
        harness.controller.onIsPlayingStarted(gen)

        assertTrue(harness.active)
        assertEquals(1, harness.maxDurationStartCount)

        val elapsedSoFar = 60_000L + 20_000L + 100_000L + 20_000L
        val remainingToFiveMinutes = AdhanPlaybackService.MAX_PLAYBACK_DURATION_MILLIS - elapsedSoFar

        advanceTimeBy(remainingToFiveMinutes - 1L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(0, harness.fallbacks.size)
    }

    @Test
    fun transientFocusLossBeforePlaybackConfirmedUsesEightSecondPrepWatchdogNotThirtySecond() = runTest {
        val harness = createHarness(this)
        val gen = harness.startUnconfirmedPlayback(Prayer.FAJR)

        harness.controller.onFocusLossTransient()
        assertEquals(0, harness.transientFocusStartCount)
        assertEquals(null, harness.transientFocusJob)
        assertEquals(0, harness.maxDurationStartCount)

        advanceTimeBy(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS - 1L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.fallbacks.size)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(harness.active)
        assertEquals(listOf(Prayer.FAJR), harness.fallbacks)
        assertEquals(1, harness.stoppedSelf)
        assertFalse(harness.controller.isCurrentGeneration(gen))
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun normalCompletionCancelsMaxDurationWatchdogAndIgnoresStaleTimeout() = runTest {
        val harness = createHarness(this)
        val gen = harness.startConfirmedPlayback(Prayer.FAJR)

        advanceTimeBy(150_000L)
        runCurrent()
        harness.endedCallback?.invoke()

        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(null, harness.maxDurationJob)
        assertEquals(0, harness.fallbacks.size)
        assertFalse(harness.controller.onMaxDurationTimeout(gen))
        assertFalse(harness.controller.onTransientFocusTimeout(gen))

        advanceTimeBy(AdhanPlaybackService.MAX_PLAYBACK_DURATION_MILLIS)
        runCurrent()
        assertEquals(1, harness.stoppedSelf)
        assertEquals(1, harness.releasedPlayers)
        assertEquals(0, harness.fallbacks.size)
    }

    @Test
    fun staleWatchdogsAfterNewerGenerationStartsNeverAffectNewerGeneration() = runTest {
        val harness = createHarness(this)
        val gen1 = harness.startConfirmedPlayback(Prayer.FAJR)
        harness.controller.onFocusLossTransient()

        val gen2 = harness.startConfirmedPlayback(Prayer.DHUHR)
        assertTrue(harness.active)
        assertFalse(harness.controller.isCurrentGeneration(gen1))
        assertTrue(harness.controller.isCurrentGeneration(gen2))

        assertFalse(harness.controller.onMaxDurationTimeout(gen1))
        assertFalse(harness.controller.onTransientFocusTimeout(gen1))
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)
        assertEquals(0, harness.fallbacks.size)

        advanceTimeBy(AdhanPlaybackService.TRANSIENT_FOCUS_LOSS_TIMEOUT_MILLIS + 5_000L)
        runCurrent()
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)

        harness.controller.stopAll()
        assertFalse(harness.active)
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun transientFocusGainAndTimeoutRaceIsDeterministicInBothOrderings() = runTest {
        val harness = createHarness(this)

        val gen1 = harness.startConfirmedPlayback(Prayer.FAJR)
        harness.controller.onFocusLossTransient()
        harness.controller.onFocusGain()
        assertFalse(harness.controller.onTransientFocusTimeout(gen1))
        assertTrue(harness.active)
        assertEquals(0, harness.stoppedSelf)
        assertEquals(0, harness.fallbacks.size)

        val gen2 = harness.startConfirmedPlayback(Prayer.DHUHR)
        harness.controller.onFocusLossTransient()
        assertTrue(harness.controller.onTransientFocusTimeout(gen2))
        val playCallsAfterTimeout = harness.playCalls
        harness.controller.onFocusGain()
        assertEquals(playCallsAfterTimeout, harness.playCalls)
        assertFalse(harness.active)
        assertEquals(1, harness.stoppedSelf)
        assertEquals(0, harness.fallbacks.size)
    }

    @Test
    fun terminalErrorStopAndPermanentFocusLossCancelBothWatchdogsWithNoAccidentalFallback() = runTest {
        val harness = createHarness(this)

        harness.startConfirmedPlayback(Prayer.FAJR)
        harness.controller.onFocusLossTransient()
        harness.errorCallback?.invoke(IllegalStateException("audio track died"))
        assertFalse(harness.active)
        assertEquals(null, harness.maxDurationJob)
        assertEquals(null, harness.transientFocusJob)
        assertEquals(listOf(Prayer.FAJR), harness.fallbacks)

        harness.startConfirmedPlayback(Prayer.DHUHR)
        harness.controller.onFocusLossTransient()
        harness.controller.onFocusLossPermanent()
        assertFalse(harness.active)
        assertEquals(null, harness.maxDurationJob)
        assertEquals(null, harness.transientFocusJob)
        assertEquals(listOf(Prayer.FAJR), harness.fallbacks)

        harness.startConfirmedPlayback(Prayer.ASR)
        harness.controller.onFocusLossTransient()
        harness.controller.stopAll()
        assertFalse(harness.active)
        assertEquals(null, harness.maxDurationJob)
        assertEquals(null, harness.transientFocusJob)
        assertEquals(listOf(Prayer.FAJR), harness.fallbacks)

        advanceTimeBy(AdhanPlaybackService.MAX_PLAYBACK_DURATION_MILLIS * 2)
        runCurrent()
        assertEquals(3, harness.stoppedSelf)
        assertEquals(0, harness.wakeLock.activeLeaseCountForTest())
    }

    private fun createHarness(scope: CoroutineScope): WatchdogTestHarness = WatchdogTestHarness(scope)

    private class WatchdogTestHarness(private val scope: CoroutineScope) {
        private val clock = AtomicLong(0L)
        val wakeLock = PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { tag -> FakePlatformWakeLock(tag) },
            elapsedRealtimeMs = clock::get,
        )
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var active = false
        var stoppedSelf = 0
        var releasedPlayers = 0
        var abandonedFocus = 0
        var pauseCalls = 0
        var playCalls = 0
        var maxDurationStartCount = 0
        var transientFocusStartCount = 0
        var prepareJob: Job? = null
        var maxDurationJob: Job? = null
        var transientFocusJob: Job? = null
        var endedCallback: (() -> Unit)? = null
        var errorCallback: ((Throwable?) -> Unit)? = null

        val controller: AdhanPlaybackLifecycleController = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = { abandonedFocus++ },
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, onEnded, onError ->
                endedCallback = onEnded
                errorCallback = onError
                object : AdhanPlaybackHandle {
                    override fun pause() {
                        pauseCalls++
                    }

                    override fun play() {
                        playCalls++
                    }

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
            onCancelPreparationWatchdog = {
                prepareJob?.cancel()
                prepareJob = null
            },
            onStartMaxDurationWatchdog = { token ->
                maxDurationStartCount++
                maxDurationJob?.cancel()
                maxDurationJob = scope.launch {
                    delay(AdhanPlaybackService.MAX_PLAYBACK_DURATION_MILLIS)
                    controller.onMaxDurationTimeout(token)
                }
            },
            onCancelMaxDurationWatchdog = {
                maxDurationJob?.cancel()
                maxDurationJob = null
            },
            onStartTransientFocusWatchdog = { token ->
                transientFocusStartCount++
                if (transientFocusJob?.isActive != true) {
                    transientFocusJob = scope.launch {
                        delay(AdhanPlaybackService.TRANSIENT_FOCUS_LOSS_TIMEOUT_MILLIS)
                        controller.onTransientFocusTimeout(token)
                    }
                }
            },
            onCancelTransientFocusWatchdog = {
                transientFocusJob?.cancel()
                transientFocusJob = null
            },
        )

        fun startUnconfirmedPlayback(prayer: Prayer): Long {
            val lease = wakeLock.acquireHandover()
            val token = AdhanPlaybackService.beginReEntrantStart(
                prayer = prayer,
                incomingWakeLockToken = lease.tokenId,
                lifecycleController = controller,
                handoverTokens = tracker,
                onEnterForeground = {},
                onReleaseIncomingToken = { wakeLock.releaseToken(it) },
                onFallbackNotify = { fallbacks += it },
                onCancelPriorJobs = {
                    prepareJob?.cancel()
                    prepareJob = null
                    maxDurationJob?.cancel()
                    maxDurationJob = null
                    transientFocusJob?.cancel()
                    transientFocusJob = null
                },
                onStopSelfIfIdle = {},
            )!!
            prepareJob = scope.launch {
                delay(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS)
                controller.onPreparationTimeout(token, prayer)
            }
            controller.startPlayback(token, prayer, "file:///adhan.mp3")
            return token
        }

        fun startConfirmedPlayback(prayer: Prayer): Long {
            val token = startUnconfirmedPlayback(prayer)
            controller.onIsPlayingStarted(token)
            return token
        }
    }

    private class FakePlatformWakeLock(val tag: String) : PlatformWakeLock {
        private var held = false
        override fun acquire(timeoutMs: Long) {
            held = true
        }

        override fun release() {
            held = false
        }

        override fun isHeld(): Boolean = held
    }
}
