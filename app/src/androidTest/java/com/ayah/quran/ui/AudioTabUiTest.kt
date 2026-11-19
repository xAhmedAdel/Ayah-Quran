package com.ayah.quran.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.data.audio.QuranAudioCatalog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio tab UI tests against the real app (Phase 17): navigation, the
 * verified reciter catalog (community-distribution editions with in-app
 * attribution), the surah list with honest download controls, and the
 * absence of any fabricated playing state while idle.
 *
 * All strings resolve through the locale-wrapped activity resources because
 * the app is Arabic-first with an in-app language switch.
 */
@RunWith(AndroidJUnit4::class)
class AudioTabUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int): String = composeRule.activity.getString(resId)

    private fun openAudioTab() {
        composeRule.onAllNodesWithText(s(R.string.nav_audio)).onFirst().performClick()
        composeRule.onNodeWithText(s(R.string.audio_reciters_section)).assertExists()
    }

    @Test
    fun audioTabNavigatesAndShowsReciterCatalog() {
        openAudioTab()
        // The default edition chip is rendered from the verified catalog.
        composeRule.onNodeWithText(
            QuranAudioCatalog.defaultReciter.nameAr,
        ).assertExists()
        // The attribution note is honest about the distribution source.
        composeRule.onNodeWithText(s(R.string.audio_attribution_note)).assertExists()
    }

    @Test
    fun surahRowsOfferOfflineDownloadControls() {
        openAudioTab()
        // A fresh install may still be importing the Quran dataset — wait.
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("الفاتحة")
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Al-Fatiha (imported Quran dataset) is listed with a download affordance
        // (one per surah row — 114 in total).
        composeRule.onNodeWithText("الفاتحة").assertExists()
        composeRule.onAllNodesWithContentDescription(
            s(R.string.audio_download),
        ).onFirst().assertExists()
    }

    @Test
    fun noFabricatedPlayingState() {
        openAudioTab()
        // Engine idle: no now-playing bar, no playing label, no repeat chip.
        assertTrue(
            "now-playing bar must be hidden while idle",
            composeRule.onAllNodesWithText(s(R.string.audio_now_playing))
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "repeat-ayah chip must be hidden while idle",
            composeRule.onAllNodesWithText(s(R.string.audio_repeat_ayah))
                .fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "no 'Playing' state may be displayed while idle",
            composeRule.onAllNodesWithText(s(R.string.audio_state_playing))
                .fetchSemanticsNodes().isEmpty(),
        )
    }
}
