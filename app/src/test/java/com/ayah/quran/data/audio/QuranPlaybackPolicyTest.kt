package com.ayah.quran.data.audio

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranPlaybackPolicyTest {

    private val range = QuranRepeatRange(fromAyah = 3, toAyah = 5)

    @Test
    fun sleepAfterAyahStopsOnFirstTransition() {
        val action = QuranPlaybackPolicy.onTransition(
            newIndex = 3, repeatRange = null, sleepMode = QuranSleepMode.AFTER_AYAH,
        )
        assertTrue(action is QuranPlaybackPolicy.TransitionAction.Stop)
    }

    @Test
    fun sleepAfterAyahWinsOverRangeWrap() {
        // Range 3..5, wrap from index 5 back to 2 — but the sleep fires first.
        val action = QuranPlaybackPolicy.onTransition(
            newIndex = 5, repeatRange = range, sleepMode = QuranSleepMode.AFTER_AYAH,
        )
        assertTrue(action is QuranPlaybackPolicy.TransitionAction.Stop)
    }

    @Test
    fun sleepAfterSurahContinuesTransitionsAndStopsOnlyAtEnded() {
        assertEquals(
            QuranPlaybackPolicy.TransitionAction.Continue,
            QuranPlaybackPolicy.onTransition(3, null, QuranSleepMode.AFTER_SURA),
        )
        assertEquals(
            QuranPlaybackPolicy.TransitionAction.Continue,
            QuranPlaybackPolicy.onTransition(6, range, QuranSleepMode.AFTER_SURA),
        )
    }

    @Test
    fun noTimerNoRangeContinues() {
        assertEquals(
            QuranPlaybackPolicy.TransitionAction.Continue,
            QuranPlaybackPolicy.onTransition(4, null, QuranSleepMode.NONE),
        )
    }

    @Test
    fun rangeWrapsPastItsEnd() {
        val action = QuranPlaybackPolicy.onTransition(
            newIndex = 5, repeatRange = range, sleepMode = QuranSleepMode.NONE,
        )
        assertTrue(action is QuranPlaybackPolicy.TransitionAction.WrapToRangeStart)
        assertEquals(2, (action as QuranPlaybackPolicy.TransitionAction.WrapToRangeStart).ayahIndex)
    }

    @Test
    fun rangeWrapsWhenLeavingFromBelow() {
        // Seek/transition landed BEFORE the range start (e.g. previous-ayah
        // from index 2 landed on 1) — wrap forward to the range start.
        val action = QuranPlaybackPolicy.onTransition(
            newIndex = 0, repeatRange = range, sleepMode = QuranSleepMode.NONE,
        )
        assertTrue(action is QuranPlaybackPolicy.TransitionAction.WrapToRangeStart)
        assertEquals(2, (action as QuranPlaybackPolicy.TransitionAction.WrapToRangeStart).ayahIndex)
    }

    @Test
    fun insideRangeContinues() {
        listOf(2, 3, 4).forEach { index ->
            assertEquals(
                QuranPlaybackPolicy.TransitionAction.Continue,
                QuranPlaybackPolicy.onTransition(index, range, QuranSleepMode.NONE),
            )
        }
    }

    @Test
    fun repeatAyahMapsToPlayerRepeatOne() {
        assertEquals(
            Player.REPEAT_MODE_ONE,
            QuranPlaybackPolicy.playerRepeatMode(repeatAyah = true),
        )
        assertEquals(
            Player.REPEAT_MODE_OFF,
            QuranPlaybackPolicy.playerRepeatMode(repeatAyah = false),
        )
    }

    @Test
    fun repeatRangeBoundsAreValidated() {
        var thrown = false
        try {
            QuranRepeatRange(fromAyah = 5, toAyah = 3)
        } catch (expected: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("invalid range must be rejected", thrown)
    }
}
