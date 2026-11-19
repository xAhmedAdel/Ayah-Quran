package com.ayah.quran.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.core.locale.AppLocale
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose UI tests against the REAL app (real Hilt graph, real
 * database). They verify navigation, home sections, the five wallpaper
 * templates, history presence, and accessibility semantics.
 *
 * The app ships Arabic-first with an in-app language switch, so every
 * assertion resolves strings through the locale-wrapped activity resources
 * (and surah names through [AppLocale.isArabic]) instead of hardcoding one
 * language.
 *
 * Tests tolerate any application state (fresh install or used) and wait for
 * the async Quran import where needed.
 */
@RunWith(AndroidJUnit4::class)
class AppUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Resolves a resource through the app's locale-wrapped activity. */
    private fun s(resId: Int): String = composeRule.activity.getString(resId)

    /** Formats a resource with args, matching how Compose renders it. */
    private fun s(resId: Int, vararg args: Any): String = composeRule.activity.getString(resId, *args)

    /** The surah name exactly as the UI displays it in the current language. */
    private fun surahName(arabic: String, transliterated: String): String =
        if (AppLocale.isArabic) arabic else transliterated

    private fun faatiha(): String = surahName("الفاتحة", "Al-Faatiha")

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun resetPersistedPlayback() {
        // A previous session's mini-player restore snapshot leaks text into
        // list queries ("الفاتحة" appears in both the mini player and the
        // surah list). Tests start from an idle playback world.
        com.ayah.quran.di.AudioPlaybackEntryPoint.from(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
        ).ayahPlaylistPlayer().clearRestore()
    }

    @Test
    fun homeShowsBrandAndCoreSections() {
        composeRule.onNodeWithText(s(R.string.app_name)).assertExists()
        composeRule.onNodeWithText(s(R.string.home_continue_reading_title)).assertExists()
        composeRule.onNodeWithText(s(R.string.home_wallpaper_card_title)).assertExists()
        // Status must be communicated with text, not color alone.
        val hasOffStatus = composeRule.onAllNodesWithText(s(R.string.home_wallpaper_automation_off))
            .fetchSemanticsNodes().isNotEmpty()
        val hasOnStatus = listOf(30, 60, 180, 360, 720, 1440).any { minutes ->
            composeRule.onAllNodesWithText(s(R.string.home_wallpaper_automation_on, minutes))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("automation status text missing", hasOffStatus || hasOnStatus)
    }

    @Test
    fun bottomNavigationReachesQuranSurahList() {
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        // The surah list populates after the async import completes.
        waitForText(faatiha())
        composeRule.onNodeWithText(s(R.string.quran_search_hint)).assertExists()
        composeRule.onNodeWithTag("quran_tab_surahs").assertExists()
        composeRule.onNodeWithTag("quran_tab_juz").assertExists()
        composeRule.onNodeWithTag("quran_tab_bookmarks").assertExists()
        composeRule.onNodeWithTag("quran_page_jump_button").assertExists()
    }

    @Test
    fun quranHubSwitchesAcrossJuzAndBookmarksAndOpensPageJump() {
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        waitForText(faatiha())

        composeRule.onNodeWithTag("quran_tab_juz").performClick()
        waitForText(s(R.string.quran_juz_title, 1))
        composeRule.onNodeWithTag("juz_row_1").assertExists()

        composeRule.onNodeWithTag("quran_tab_bookmarks").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quran_page_jump_button").performClick()
        waitForText(s(R.string.mushaf_page_jump_hint))
        composeRule.onNodeWithText(s(R.string.action_cancel)).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun wallpapersScreenShowsAllFiveTemplates() {
        composeRule.onAllNodesWithText(s(R.string.nav_wallpapers)).onFirst().performClick()
        waitForText(s(R.string.wallpapers_template_emerald_luxury))
        composeRule.onNodeWithText(s(R.string.wallpapers_template_minimal_cream)).assertExists()
        composeRule.onNodeWithText(s(R.string.wallpapers_template_midnight_gold)).assertExists()
        composeRule.onNodeWithText(s(R.string.wallpapers_template_islamic_architecture)).assertExists()
        composeRule.onNodeWithText(s(R.string.wallpapers_template_geometric_art)).assertExists()
    }

    @Test
    fun wallpapersScreenShowsHistorySection() {
        composeRule.onAllNodesWithText(s(R.string.nav_wallpapers)).onFirst().performClick()
        waitForText(s(R.string.wallpapers_history_section))
        // Honest empty state OR populated rows — never fabricated content.
        val emptyShown = composeRule.onAllNodesWithText(s(R.string.wallpapers_history_empty))
            .fetchSemanticsNodes().isNotEmpty()
        if (!emptyShown) {
            assertTrue(
                "history rows must expose Reapply actions",
                composeRule.onAllNodesWithText(s(R.string.wallpapers_history_reapply))
                    .fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun continueReadingTapOpensReaderAtSavedPosition_whenPositionExists() {
        // Open the Quran tab and Al-Faatiha to create a reading position.
        // Default reading mode is the authentic 604-page mushaf, so the list
        // opens the mushaf page reader.
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        waitForText(faatiha())
        clickWithRetry(faatiha())
        waitForText(
            composeRule.activity.getString(R.string.mushaf_page_indicator, 1, 604),
            timeoutMs = 15_000,
        )

        // The reader hides the bottom bar (immersive) — navigate back first.
        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()

        // Home: Continue Reading must now offer the saved position.
        composeRule.onAllNodesWithText(s(R.string.nav_home)).onFirst().performClick()
        waitForText(s(R.string.home_continue_reading_format, faatiha(), 1, 7))
        composeRule.onNodeWithText(s(R.string.home_continue_reading_button)).assertExists()
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
        listOf(
            R.string.nav_home,
            R.string.nav_quran,
            R.string.nav_prayer,
            R.string.nav_audio,
            R.string.nav_wallpapers,
        ).forEach { resId ->
            composeRule.onNodeWithContentDescription(s(resId), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun homeHeaderSettingsOpensSettingsScreenAndNavigatesBack() {
        composeRule.onNodeWithTag("home_settings_button").performClick()
        waitForText(s(R.string.settings_appearance))
        composeRule.onNodeWithText(s(R.string.settings_language)).assertExists()
        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_settings_button").assertExists()
    }

    @Test
    fun homeSanctuaryShowsNextPrayerAndDailyAyah() {
        composeRule.onNodeWithTag("home_continue_reading_card").assertExists()
        composeRule.onNodeWithTag("home_next_prayer_card").assertExists()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(s(R.string.home_daily_ayah_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_daily_ayah_card").assertExists()
    }

    @Test
    fun settingsHubShowsPreferencesAndOpensLicensesSheet() {
        composeRule.onNodeWithTag("home_settings_button").performClick()
        waitForText(s(R.string.settings_preferences_section))
        composeRule.onNodeWithTag("settings_reading_row").assertExists()
        composeRule.onNodeWithTag("settings_version_value").performScrollTo().assertExists()
        composeRule.onNodeWithTag("settings_licenses_row").performScrollTo().performClick()
        waitForText(s(R.string.settings_licenses_summary))
        androidx.test.espresso.Espresso.pressBack()
        composeRule.waitForIdle()
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
