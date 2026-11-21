package com.ayah.quran.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.core.locale.AppLocale
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranRubUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun surahName(arabic: String, transliterated: String): String =
        if (AppLocale.isArabic) arabic else transliterated

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun resetPersistedPlayback() {
        com.ayah.quran.di.AudioPlaybackEntryPoint.from(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
        ).ayahPlaylistPlayer().clearRestore()
    }

    @Test
    fun fourTabsAppearInCanonicalOrderAndExistingTabsRemainFunctional() {
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        waitForText(surahName("الفاتحة", "Al-Faatiha"))

        val surahsBounds = composeRule.onNodeWithTag("quran_tab_surahs").getUnclippedBoundsInRoot()
        val juzBounds = composeRule.onNodeWithTag("quran_tab_juz").getUnclippedBoundsInRoot()
        val rubBounds = composeRule.onNodeWithTag("quran_tab_rub").getUnclippedBoundsInRoot()
        val bookmarksBounds = composeRule.onNodeWithTag("quran_tab_bookmarks").getUnclippedBoundsInRoot()

        if (AppLocale.isArabic) {
            assertTrue("Expected RTL order Surahs > Juz", surahsBounds.left > juzBounds.left)
            assertTrue("Expected RTL order Juz > Rub", juzBounds.left > rubBounds.left)
            assertTrue("Expected RTL order Rub > Bookmarks", rubBounds.left > bookmarksBounds.left)
        } else {
            assertTrue("Expected LTR order Surahs < Juz", surahsBounds.left < juzBounds.left)
            assertTrue("Expected LTR order Juz < Rub", juzBounds.left < rubBounds.left)
            assertTrue("Expected LTR order Rub < Bookmarks", rubBounds.left < bookmarksBounds.left)
        }

        composeRule.onNodeWithTag("quran_tab_juz").performClick()
        waitForText(s(R.string.quran_juz_title, 1))
        composeRule.onNodeWithTag("juz_row_1").assertExists()

        composeRule.onNodeWithTag("quran_tab_rub").performClick()
        waitForText(s(R.string.quran_rub_title, 1))
        composeRule.onNodeWithTag("quran_rub_list").assertExists()
        composeRule.onNodeWithTag("rub_row_1").assertExists()

        composeRule.onNodeWithTag("quran_tab_bookmarks").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quran_tab_surahs").performClick()
        waitForText(surahName("الفاتحة", "Al-Faatiha"))
    }

    @Test
    fun selectingRub106NavigatesReaderToMadaniPage264() {
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        waitForText(surahName("الفاتحة", "Al-Faatiha"))

        composeRule.onNodeWithTag("quran_tab_rub").performClick()
        waitForText(s(R.string.quran_rub_title, 1))

        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("15:49")
        waitForText(s(R.string.quran_rub_title, 106))

        composeRule.onNodeWithTag("rub_row_106").assertExists().performClick()
        waitForText(s(R.string.mushaf_page_indicator, 264, 604), timeoutMs = 15_000)
        composeRule.onAllNodesWithText(surahName("الحجر", "Al-Hijr")).onFirst().assertExists()
    }
}
