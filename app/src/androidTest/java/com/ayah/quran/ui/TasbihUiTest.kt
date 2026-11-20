package com.ayah.quran.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasbihUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTasbih() {
        composeRule.onNodeWithTag("nav_prayer").performClick()
        waitForTag("prayer_open_all_adhkar")
        composeRule.onNodeWithTag("prayer_open_all_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_open_tasbih")
        composeRule.onNodeWithTag("adhkar_open_tasbih").performClick()
        waitForTag("tasbih_tap_surface")
    }

    @Test
    fun tasbihCountingPresetSelectionUndoAndResetConfirmationWork() {
        openTasbih()

        val hasNonEmptyDescription = SemanticsMatcher("has non-empty contentDescription") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.any { it.isNotBlank() } == true
        }
        composeRule.onNodeWithTag("tasbih_tap_surface").assert(hasNonEmptyDescription)

        composeRule.onNodeWithTag("tasbih_presets_button").performClick()
        waitForTag("tasbih_preset_alhamdulillah")
        composeRule.onNodeWithTag("tasbih_preset_alhamdulillah").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tasbih_tap_surface").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tasbih_count_value", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("1") } == true
        }

        composeRule.onNodeWithTag("tasbih_undo_button").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tasbih_count_value", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("0") } == true
        }

        composeRule.onNodeWithTag("tasbih_tap_surface").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tasbih_count_value", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("1") } == true
        }

        composeRule.onNodeWithTag("tasbih_reset_button").performClick()
        composeRule.onNodeWithText(s(R.string.tasbih_reset_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(s(R.string.tasbih_reset_confirm_action)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tasbih_count_value", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("0") } == true
        }
    }

    @Test
    fun tasbihGoalCompletionContinuesBeyondGoalAndCustomDhikrCrudWorks() {
        openTasbih()

        composeRule.onNodeWithTag("tasbih_presets_button").performClick()
        waitForTag("tasbih_add_custom")
        composeRule.onNodeWithTag("tasbih_add_custom").performClick()
        waitForTag("tasbih_custom_ar_input")

        val customPhrase = "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ"
        composeRule.onNodeWithTag("tasbih_custom_ar_input").performTextInput(customPhrase)
        composeRule.onNodeWithTag("tasbih_custom_en_input").performTextInput("Ya Hayyu Ya Qayyum")
        composeRule.onNodeWithTag("tasbih_custom_goal_input").performTextClearance()
        composeRule.onNodeWithTag("tasbih_custom_goal_input").performTextInput("2")
        composeRule.onNodeWithTag("tasbih_custom_save").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("tasbih_active_dhikr_text", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("أَسْتَغِيثُ") } == true
        }

        composeRule.onNodeWithTag("tasbih_tap_surface").performClick()
        Thread.sleep(100)
        composeRule.onNodeWithTag("tasbih_tap_surface").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("tasbih_goal_reached_badge", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tasbih_count_value", useUnmergedTree = true)
            .assertTextContains("2", substring = true)

        Thread.sleep(100)
        composeRule.onNodeWithTag("tasbih_tap_surface").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("tasbih_count_value", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
                ?.any { it.text.contains("3") } == true
        }

        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("adhkar_open_tasbih")
        composeRule.onNodeWithTag("adhkar_open_tasbih").performClick()
        waitForTag("tasbih_tap_surface")
        composeRule.onNodeWithTag("tasbih_count_value", useUnmergedTree = true)
            .assertTextContains("3", substring = true)
    }
}
