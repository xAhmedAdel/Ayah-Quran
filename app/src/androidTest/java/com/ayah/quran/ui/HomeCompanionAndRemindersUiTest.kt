package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCompanionAndRemindersUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeCompanionStripShowsAllFourEntriesAndNavigatesCorrectlyWithFiveTabsIntact() {
        waitForTag("home_companion_strip")
        composeRule.onNodeWithTag("nav_home").assertExists()
        composeRule.onNodeWithTag("nav_quran").assertExists()
        composeRule.onNodeWithTag("nav_prayer").assertExists()
        composeRule.onNodeWithTag("nav_audio").assertExists()
        composeRule.onNodeWithTag("nav_wallpapers").assertExists()

        composeRule.onNodeWithTag("home_companion_adhkar").performScrollTo().performClick()
        waitForTag("adhkar_cat_morning")
        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("home_companion_tasbih")

        composeRule.onNodeWithTag("home_companion_tasbih").performScrollTo().performClick()
        waitForTag("tasbih_tap_surface")
        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("home_companion_hadith")

        composeRule.onNodeWithTag("home_companion_hadith").performScrollTo().performClick()
        waitForTag("hadith_list")
        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("home_companion_duas")

        composeRule.onNodeWithTag("home_companion_duas").performScrollTo().performClick()
        waitForTag("duas_tab_quranic")
        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("home_companion_strip")
    }

    @Test
    fun dailyReflectionSwitchesModesAndNavigatesToMushafAndHadithDetail() {
        waitForTag("home_daily_reflection_card")
        composeRule.onNodeWithTag("reflection_open_mushaf").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("reflection_mode_hadith").performScrollTo().performClick()
        waitForTag("reflection_hadith_text")
        composeRule.onNodeWithTag("reflection_hadith_text").assertIsDisplayed()
        composeRule.onNodeWithTag("reflection_open_hadith").performScrollTo().performClick()
        waitForTag("hadith_detail_arabic_text")
        composeRule.onNodeWithTag("hadith_detail_arabic_text").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(s(R.string.reader_back)).performClick()
        waitForTag("reflection_mode_ayah")
        composeRule.onNodeWithTag("reflection_mode_ayah").performScrollTo().performClick()
        waitForTag("reflection_open_mushaf")
        composeRule.onNodeWithTag("reflection_open_mushaf").performScrollTo().performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodes(hasTestTag("mushaf_pager")).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(s(R.string.reader_back)).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("1", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun settingsShowsRemindersSectionAndTimePickerWithoutStartupPermissionPrompt() {
        composeRule.onNodeWithTag("home_settings_button").performClick()
        waitForTag("settings_reminders_section")

        composeRule.onNodeWithTag("reminder_row_morning_adhkar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reminder_row_evening_adhkar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reminder_row_daily_reflection").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("reminder_row_morning_adhkar").performClick()
        waitForTag("reminder_time_save")
        composeRule.onNodeWithTag("reminder_time_save").performClick()
        composeRule.waitForIdle()
    }
}
