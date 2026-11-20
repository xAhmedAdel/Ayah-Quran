package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HadithUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openHadithLibrary() {
        composeRule.onNodeWithTag("nav_prayer").performClick()
        waitForTag("prayer_open_all_adhkar")
        composeRule.onNodeWithTag("prayer_open_all_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_cat_morning")
        composeRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasTestTag("adhkar_hadith_card"))
        composeRule.onNodeWithTag("adhkar_hadith_card").performClick()
        waitForTag("hadith_list")
    }

    @Test
    fun hadithLibraryBrowseFilterArabicEnglishSearchAndNoResultsWork() {
        openHadithLibrary()

        composeRule.onNodeWithTag("hadith_filter_nawawi40").performClick()
        waitForTag("hadith_card_nawawi_01")

        composeRule.onNodeWithTag("hadith_filter_all").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("النيات")
        waitForTag("hadith_card_nawawi_01")

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Quran")
        waitForTag("hadith_list")

        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("zzzznotahadith999")
        waitForTag("hadith_empty_search")
        composeRule.onNodeWithTag("hadith_empty_search").assertIsDisplayed()
    }

    @Test
    fun hadithDetailShowsVerbatimTextAttributionAndFavoritesToggleWorks() {
        openHadithLibrary()

        composeRule.onNodeWithTag("hadith_filter_hadeethenc_quran").performClick()
        waitForTag("hadith_card_henc_5907")
        composeRule.onNodeWithTag("hadith_card_henc_5907").performClick()
        waitForTag("hadith_detail_arabic_text")
        composeRule.onNodeWithTag("hadith_detail_arabic_text").assertIsDisplayed()
        composeRule.onNodeWithTag("hadith_detail_english_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("hadith_detail_grade_badge").assertExists()
        composeRule.onNodeWithTag("hadith_detail_attribution_card").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("HadeethEnc.com", substring = true).onFirst().assertExists()

        val saveAction = s(R.string.hadith_save_action)
        if (composeRule.onAllNodesWithContentDescription(saveAction).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("hadith_detail_favorite").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("hadith_favorites_toggle")
        composeRule.onNodeWithTag("hadith_filter_all").performClick()
        composeRule.onNodeWithTag("hadith_favorites_toggle").performClick()
        waitForTag("hadith_card_henc_5907")
        composeRule.onNodeWithTag("hadith_card_henc_5907").assertIsDisplayed()

        composeRule.onNodeWithTag("hadith_fav_henc_5907").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("hadith_card_henc_5907").fetchSemanticsNodes().isEmpty()
        }
    }
}
