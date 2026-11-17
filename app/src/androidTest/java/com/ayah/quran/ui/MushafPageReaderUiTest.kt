package com.ayah.quran.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.core.locale.AppLocale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for the authentic 604-page mushaf reader: navigation via
 * buttons and swipe, the direct page-jump dialog (including invalid input
 * rejection), and switching to the adjustable-text reader.
 *
 * All strings resolve through the locale-wrapped activity resources; the
 * mushaf page mapping itself is validated separately in JVM integrity tests.
 */
@RunWith(AndroidJUnit4::class)
class MushafPageReaderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun s(resId: Int, vararg args: Any): String =
        composeRule.activity.getString(resId, *args)

    private fun surahName(arabic: String, transliterated: String): String =
        if (AppLocale.isArabic) arabic else transliterated

    private fun faatiha(): String = surahName("الفاتحة", "Al-Faatiha")

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun indicator(page: Int): String = s(R.string.mushaf_page_indicator, page, 604)

    private fun openMushafAtFaatiha() {
        composeRule.onAllNodesWithText(s(R.string.nav_quran)).onFirst().performClick()
        waitForText(faatiha())
        composeRule.onNodeWithText(faatiha()).performClick()
        waitForText(indicator(1))
    }

    @Test
    fun mushafOpensOnPageOneWithPageIndicator() {
        openMushafAtFaatiha()
        composeRule.onNodeWithText(indicator(1)).assertExists()
    }

    @Test
    fun nextAndPrevButtonsNavigateBetweenPages() {
        openMushafAtFaatiha()
        composeRule.onNodeWithContentDescription(s(R.string.mushaf_next_page)).performClick()
        waitForText(indicator(2))
        // Page 2 begins Surah Al-Baqarah — the title updates.
        // "البقرة" appears twice (top-bar title + surah-start header).
        composeRule.onAllNodesWithText(surahName("البقرة", "Al-Baqara")).onFirst().assertExists()

        composeRule.onNodeWithContentDescription(s(R.string.mushaf_prev_page)).performClick()
        waitForText(indicator(1))
        composeRule.onAllNodesWithText(faatiha()).onFirst().assertExists()
    }

    @Test
    fun swipingMovesBetweenPages() {
        openMushafAtFaatiha()
        // In RTL (Arabic default) the next page arrives from the left edge;
        // in LTR it arrives from the right.
        composeRule.onRoot().performTouchInput {
            if (AppLocale.isArabic) swipeRight() else swipeLeft()
        }
        waitForText(indicator(2))
    }

    @Test
    fun pinchZoomKeepsAuthenticPageBoundaries() {
        openMushafAtFaatiha()
        // Pinch-zoom scales the page without ever changing its boundaries:
        // the pager must stay on page 1 while zoomed.
        composeRule.onRoot().performTouchInput {
            pinch(
                start0 = center + Offset(-100f, -100f),
                end0 = center + Offset(-250f, -250f),
                start1 = center + Offset(100f, 100f),
                end1 = center + Offset(250f, 250f),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(indicator(1)).assertExists()
    }

    @Test
    fun pageJumpDialogRejectsInvalidAndAcceptsValidPageNumbers() {
        openMushafAtFaatiha()
        composeRule.onNodeWithText(indicator(1)).performClick()
        val field = composeRule.onNodeWithText(s(R.string.mushaf_page_jump_hint))
        field.performTextInput("9999")
        composeRule.onNodeWithText(
            s(R.string.mushaf_invalid_page, 1, 604),
        ).assertExists()
        // Invalid input keeps the confirm button disabled — no navigation.
        composeRule.onNodeWithText(s(R.string.mushaf_go)).assertIsNotEnabled()

        field.performTextReplacement("300")
        composeRule.onNodeWithText(s(R.string.mushaf_go)).performClick()
        waitForText(indicator(300))
        // Page 300 lies mid-surah (Al-Kahf) — no surah starts on it.
        composeRule.onNodeWithText(surahName("الكهف", "Al-Kahf")).assertExists()
    }
}
