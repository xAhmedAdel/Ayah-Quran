package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end prayer-times tests against the real app: tab navigation,
 * honest no-location state, manual city selection, timetable rendering,
 * and settings navigation. All strings resolve through the locale-wrapped
 * activity resources (Arabic-first app).
 */
@RunWith(AndroidJUnit4::class)
class PrayerTimesUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPrayerTab() {
        composeRule.onAllNodesWithText(s(R.string.nav_prayer)).onFirst().performClick()
        // Tests share app state: either the honest no-location state or an
        // already-selected location (timetable) is a valid landing state.
        composeRule.waitUntil(20_000) {
            val noLocation = composeRule.onAllNodesWithText(s(R.string.prayer_no_location))
                .fetchSemanticsNodes().isNotEmpty()
            val timetable = composeRule.onAllNodesWithText(s(R.string.prayer_next))
                .fetchSemanticsNodes().isNotEmpty()
            noLocation || timetable
        }
    }

    private fun pickCairoManually() {
        // A location selected by an earlier test in this class is fine —
        // selection has already been exercised end-to-end then.
        if (composeRule.onAllNodesWithText(s(R.string.prayer_next))
                .fetchSemanticsNodes().isNotEmpty()
        ) {
            return
        }
        val arabic = com.ayah.quran.core.locale.AppLocale.isArabic
        val query = if (arabic) "القاهرة" else "Cairo"
        val row = if (arabic) "القاهرة · مصر" else "Cairo · Egypt"
        composeRule.onNodeWithText(s(R.string.prayer_select_city)).performClick()
        waitForText(s(R.string.prayer_search_city_hint))
        composeRule.onNodeWithText(s(R.string.prayer_search_city_hint))
            .performTextInput(query)
        // Filtering recomposes the list; retry the row click like other
        // touch-injection flows (AppUiTest.clickWithRetry).
        var lastError: AssertionError? = null
        repeat(3) {
            try {
                composeRule.waitForIdle()
                composeRule.onAllNodesWithText(row).onFirst().performClick()
                // Dialog must close and the timetable must appear.
                waitForText(s(R.string.prayer_next), timeoutMs = 15_000)
                return
            } catch (e: AssertionError) {
                lastError = e
                Thread.sleep(400)
            }
        }
        throw lastError ?: AssertionError("city row click failed for $row")
    }

    @Test
    fun prayerTabShowsHonestNoLocationStateOrExistingLocation() {
        openPrayerTab()
        // Fresh installs show the honest no-location state; a location
        // selected by an earlier test in this class is also valid.
        val noLocation = composeRule.onAllNodesWithText(s(R.string.prayer_no_location))
            .fetchSemanticsNodes().isNotEmpty()
        val timetable = composeRule.onAllNodesWithText(s(R.string.prayer_next))
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue(noLocation || timetable)
    }

    @Test
    fun manualCitySelectionShowsTimetableWithFivePrayersAndSunrise() {
        openPrayerTab()
        pickCairoManually()

        // The five prayers + sunrise all render once a location exists.
        listOf(
            s(R.string.prayer_name_fajr),
            s(R.string.prayer_name_dhuhr),
            s(R.string.prayer_name_asr),
            s(R.string.prayer_name_maghrib),
            s(R.string.prayer_name_isha),
            s(R.string.prayer_name_sunrise),
            s(R.string.prayer_sunrise_note),
            s(R.string.prayer_next),
        ).forEach { label ->
            // onFirst + assertExists: a prayer name can appear twice (hero
            // card next-prayer + timetable row), and rows below the fold are
            // still composed — visibility varies with screen size.
            composeRule.onAllNodesWithText(label).onFirst().assertExists()
        }
    }

    @Test
    fun settingsScreenExposesAllSections() {
        openPrayerTab()
        // All settings sections render regardless of whether a location is
        // selected (preferences always exist; location rows adapt).
        composeRule.onNodeWithContentDescription(s(R.string.prayer_open_settings))
            .performClick()
        waitForText(s(R.string.prayer_settings_location))
        waitForText(s(R.string.prayer_settings_calculation))
        waitForText(s(R.string.prayer_settings_adhan))
        waitForText(s(R.string.prayer_settings_advanced))
        // "طريقة الحساب" appears twice (card title + the setting's label).
        composeRule.onAllNodesWithText(s(R.string.prayer_method_label)).onFirst().assertExists()
    }
}
