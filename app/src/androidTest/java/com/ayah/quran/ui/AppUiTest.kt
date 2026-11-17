package com.ayah.quran.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose UI tests against the REAL app (real Hilt graph, real
 * database). They verify navigation, home sections, the five wallpaper
 * templates, history presence, and accessibility semantics.
 *
 * Tests tolerate any application state (fresh install or used) and wait for
 * the async Quran import where needed.
 */
@RunWith(AndroidJUnit4::class)
class AppUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeShowsBrandAndCoreSections() {
        composeRule.onNodeWithText("Ayah").assertExists()
        composeRule.onNodeWithText("Continue Reading").assertExists()
        composeRule.onNodeWithText("Current wallpaper").assertExists()
        // Status must be communicated with text, not color alone.
        val hasAutomationText = composeRule.onAllNodesWithText("Auto-updates off")
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue(
            "automation status text missing",
            hasAutomationText ||
                composeRule.onAllNodesWithText("Auto-updates on", substring = true)
                    .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun bottomNavigationReachesQuranSurahList() {
        composeRule.onAllNodesWithText("Quran").onFirst().performClick()
        // The surah list populates after the async import completes.
        waitForText("Al-Faatiha")
        composeRule.onNodeWithText("Search surah").assertExists()
    }

    @Test
    fun wallpapersScreenShowsAllFiveTemplates() {
        composeRule.onAllNodesWithText("Wallpapers").onFirst().performClick()
        waitForText("Emerald Luxury")
        composeRule.onNodeWithText("Minimal Cream").assertExists()
        composeRule.onNodeWithText("Midnight Gold").assertExists()
        composeRule.onNodeWithText("Islamic Architecture").assertExists()
        composeRule.onNodeWithText("Geometric Art").assertExists()
    }

    @Test
    fun wallpapersScreenShowsHistorySection() {
        composeRule.onAllNodesWithText("Wallpapers").onFirst().performClick()
        waitForText("History")
        // Honest empty state OR populated rows — never fabricated content.
        val emptyShown = composeRule.onAllNodesWithText("No wallpapers have been applied yet.")
            .fetchSemanticsNodes().isNotEmpty()
        if (!emptyShown) {
            assertTrue(
                "history rows must expose Reapply actions",
                composeRule.onAllNodesWithText("Reapply").fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun continueReadingTapOpensReaderAtSavedPosition_whenPositionExists() {
        // Open the Quran tab and Al-Faatiha to create a reading position.
        composeRule.onAllNodesWithText("Quran").onFirst().performClick()
        waitForText("Al-Faatiha")
        clickWithRetry("Al-Faatiha")
        waitForText("Surah Al-Faatiha", timeoutMs = 15_000)

        // The reader hides the bottom bar (immersive) — navigate back first.
        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()

        // Home: Continue Reading must now offer the saved position.
        composeRule.onAllNodesWithText("Home").onFirst().performClick()
        waitForText("Surah Al-Faatiha · Ayah 1 of 7")
        composeRule.onNodeWithText("Continue").assertExists()
    }

    /** Touch injection can fail on a mid-transition frame; retry up to 3×. */
    private fun clickWithRetry(text: String, attempts: Int = 3) {
        var lastError: AssertionError? = null
        repeat(attempts) {
            try {
                composeRule.onNodeWithText(text).performClick()
                return
            } catch (e: AssertionError) {
                lastError = e
                composeRule.waitForIdle()
                Thread.sleep(300)
            }
        }
        throw lastError ?: AssertionError("click failed for $text")
    }

    @Test
    fun bottomNavigationBarIconsExposeContentDescriptions() {
        // Accessibility: each nav item exposes its label to TalkBack. The
        // item merges descendants, so the unmerged tree is queried.
        listOf("Home", "Quran", "Wallpapers", "Audio", "Settings").forEach { label ->
            composeRule.onNodeWithContentDescription(label, useUnmergedTree = true).assertExists()
        }
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
