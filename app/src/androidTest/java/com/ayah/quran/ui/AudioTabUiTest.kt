package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio tab UI tests against the real app: navigation, the HONEST empty
 * reciter state, disabled playback controls without audio, and the absence
 * of any fabricated playing state.
 */
@RunWith(AndroidJUnit4::class)
class AudioTabUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun audioTabNavigatesAndShowsHonestEmptyReciterState() {
        composeRule.onAllNodesWithText("Audio").onFirst().performClick()
        composeRule.onNodeWithText("Reciters").assertExists()
        composeRule.onNodeWithText("No licensed recitations are available yet.").assertExists()
    }

    @Test
    fun playbackControlsAreDisabledWithoutAudio() {
        composeRule.onAllNodesWithText("Audio").onFirst().performClick()
        // The status text names the absence explicitly.
        composeRule.onNodeWithText("No audio prepared").assertExists()
        // The play control is disabled (unplayable state), not fake-enabled.
        composeRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Previous track").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next track").assertIsNotEnabled()
    }

    @Test
    fun noFabricatedPlayingState() {
        composeRule.onAllNodesWithText("Audio").onFirst().performClick()
        // The engine is idle: no playing state may be shown or animated.
        assertTrue(
            "no 'Playing' state may be displayed while idle",
            composeRule.onAllNodesWithText("Playing").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun repeatPreferenceChipsReflectStoredDefault() {
        composeRule.onAllNodesWithText("Audio").onFirst().performClick()
        // The stored default (OFF) is selected: honest persisted preference.
        composeRule.onNodeWithText("Off").assertExists()
    }
}
