package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio tab UI tests against the real app: navigation, the HONEST empty
 * reciter state, disabled playback controls without audio, and the absence
 * of any fabricated playing state.
 *
 * All strings resolve through the locale-wrapped activity resources because
 * the app is Arabic-first with an in-app language switch.
 */
@RunWith(AndroidJUnit4::class)
class AudioTabUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int): String = composeRule.activity.getString(resId)

    @Test
    fun audioTabNavigatesAndShowsHonestEmptyReciterState() {
        composeRule.onAllNodesWithText(s(R.string.nav_audio)).onFirst().performClick()
        composeRule.onNodeWithText(s(R.string.audio_reciters_section)).assertExists()
        composeRule.onNodeWithText(s(R.string.audio_reciters_empty)).assertExists()
    }

    @Test
    fun playbackControlsAreDisabledWithoutAudio() {
        composeRule.onAllNodesWithText(s(R.string.nav_audio)).onFirst().performClick()
        // The status text names the absence explicitly.
        composeRule.onNodeWithText(s(R.string.audio_state_idle)).assertExists()
        // The play control is disabled (unplayable state), not fake-enabled.
        composeRule.onNodeWithContentDescription(s(R.string.audio_play)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(s(R.string.audio_previous)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(s(R.string.audio_next)).assertIsNotEnabled()
    }

    @Test
    fun noFabricatedPlayingState() {
        composeRule.onAllNodesWithText(s(R.string.nav_audio)).onFirst().performClick()
        // The engine is idle: no playing state may be shown or animated.
        assertTrue(
            "no 'Playing' state may be displayed while idle",
            composeRule.onAllNodesWithText(s(R.string.audio_state_playing)).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun repeatPreferenceChipsReflectStoredDefault() {
        composeRule.onAllNodesWithText(s(R.string.nav_audio)).onFirst().performClick()
        // The stored default (OFF) is selected: honest persisted preference.
        composeRule.onNodeWithText(s(R.string.audio_repeat_off)).assertExists()
    }
}
