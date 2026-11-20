package com.ayah.quran.data.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExoPlayerSleepTimerTest {

    @Test
    fun sleepTimerExpirationStopsControllerAndServiceOnMainDispatcher() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        var controllerStopped = 0
        var serviceStopped = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            stopControllerAction = { controllerStopped++ },
            stopServiceAction = { serviceStopped++ },
            autoConnect = false,
        )
        player.setStateForTest(
            AyahPlaybackState(
                editionId = "ar.alafasy",
                surah = 1,
                ayahIndex = 2,
                totalAyahs = 7,
                isPlaying = true,
            ),
        )

        player.setSleepTimerMillis(QuranSleepMode.MINUTES, minutes = 1, delayMillis = 5_000L)
        assertEquals(QuranSleepMode.MINUTES, player.state.value.sleepMode)
        assertEquals(1, player.state.value.sleepMinutes)

        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(0, controllerStopped)
        assertTrue(player.state.value.isPlaying)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, controllerStopped)
        assertEquals(1, serviceStopped)
        assertFalse(player.state.value.isPlaying)
        assertEquals(QuranSleepMode.NONE, player.state.value.sleepMode)
        assertTrue(player.state.value.isIdle)
    }

    @Test
    fun cancellingSleepTimerPreventsStopOnOriginalDeadline() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        var controllerStopped = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            stopControllerAction = { controllerStopped++ },
            stopServiceAction = {},
            autoConnect = false,
        )
        player.setStateForTest(
            AyahPlaybackState(editionId = "ar.alafasy", surah = 2, ayahIndex = 0, totalAyahs = 286, isPlaying = true),
        )

        player.setSleepTimerMillis(QuranSleepMode.MINUTES, minutes = 1, delayMillis = 5_000L)
        advanceTimeBy(2_000L)
        player.setSleepTimer(QuranSleepMode.NONE)

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(0, controllerStopped)
        assertTrue(player.state.value.isPlaying)
        assertEquals(QuranSleepMode.NONE, player.state.value.sleepMode)
    }

    @Test
    fun replacingSleepTimerCancelsPreviousTimerAndOnlyFiresOnce() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        var controllerStopped = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            stopControllerAction = { controllerStopped++ },
            stopServiceAction = {},
            autoConnect = false,
        )
        player.setStateForTest(
            AyahPlaybackState(editionId = "ar.alafasy", surah = 1, ayahIndex = 0, totalAyahs = 7, isPlaying = true),
        )

        player.setSleepTimerMillis(QuranSleepMode.MINUTES, minutes = 1, delayMillis = 3_000L)
        advanceTimeBy(2_000L)
        player.setSleepTimerMillis(QuranSleepMode.MINUTES, minutes = 2, delayMillis = 5_000L)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(0, controllerStopped)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, controllerStopped)
    }

    @Test
    fun stopSafelyHandlesNullControllerAndServiceRestrictionException() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        var stopServiceInvocations = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            stopControllerAction = null,
            stopServiceAction = {
                stopServiceInvocations++
                throw IllegalStateException("Background service stop restriction")
            },
            autoConnect = false,
        )
        player.setStateForTest(
            AyahPlaybackState(
                editionId = "ar.alafasy",
                surah = 1,
                ayahIndex = 0,
                totalAyahs = 7,
                isPlaying = true,
                repeatAyah = true,
            ),
        )

        player.stop()
        runCurrent()

        assertEquals(1, stopServiceInvocations)
        assertTrue(player.state.value.isIdle)
        assertTrue(player.state.value.repeatAyah)
    }
}
