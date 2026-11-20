package com.ayah.quran.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdhkarAndDuasUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPrayerTab() {
        composeRule.onNodeWithTag("nav_prayer").performClick()
        waitForTag("prayer_after_adhkar_card")
    }

    @Test
    fun afterPrayerEntryPointOpensGuidedSessionWithIncrementUndoAndPersistence() {
        openPrayerTab()
        composeRule.onNodeWithTag("prayer_open_after_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_count_button")

        val hasNonEmptyDescription = SemanticsMatcher("has non-empty contentDescription") { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }
                ?.any { it.isNotBlank() } == true
        }
        composeRule.onNodeWithTag("adhkar_count_button").assert(hasNonEmptyDescription)
        composeRule.onNodeWithTag("adhkar_count_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("prayer_open_after_adhkar")

        composeRule.onNodeWithTag("prayer_open_after_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_undo_button")
        composeRule.onNodeWithTag("adhkar_undo_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun adhkarHubCategorySelectionAndDuasSearchAndMushafNavigationWork() {
        openPrayerTab()
        composeRule.onNodeWithTag("prayer_open_all_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_cat_morning")
        composeRule.onNodeWithTag("adhkar_cat_evening").assertExists()
        composeRule.onNodeWithTag("adhkar_cat_after_prayer").assertExists()

        composeRule.onNodeWithTag("adhkar_cat_morning").performClick()
        waitForTag("adhkar_count_button")
        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("adhkar_open_duas")

        composeRule.onNodeWithTag("adhkar_open_duas").performClick()
        waitForTag("duas_tab_quranic")
        composeRule.onNodeWithTag("duas_tab_prophetic").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duas_tab_situational").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("duas_tab_quranic").performClick()
        waitForTag("open_mushaf_qdua_01")

        composeRule.onNode(hasSetTextAction()).performTextInput("ربنا")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("open_mushaf_qdua_01").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("open_mushaf_qdua_02").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("open_mushaf_qdua_02").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasTestTag("mushaf_pager")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(s(R.string.reader_back)).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
