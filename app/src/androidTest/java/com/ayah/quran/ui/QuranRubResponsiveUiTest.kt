package com.ayah.quran.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ayah.quran.core.designsystem.theme.AyahTheme
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.ui.quran.QuranHubContent
import com.ayah.quran.ui.quran.QuranHubTab
import com.ayah.quran.ui.quran.QuranListUiState
import com.ayah.quran.ui.quran.RubSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranRubResponsiveUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleState(): QuranListUiState = QuranListUiState(
        isLoading = false,
        surahs = listOf(
            Surah(1, "الفاتحة", "Al-Faatiha", "The Opening", RevelationType.MECCAN, 7),
        ),
        surahStartPages = mapOf(1 to 1),
        rubList = listOf(
            RubSummary(
                rubNumber = 1,
                hizbNumber = 1,
                quarterInHizb = 1,
                juzNumber = 1,
                startSurahId = 1,
                startAyahNumber = 1,
                startSurahNameAr = "الفاتحة",
                startSurahNameTransliterated = "Al-Faatiha",
                startAyahText = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                startPage = 1,
            ),
            RubSummary(
                rubNumber = 106,
                hizbNumber = 27,
                quarterInHizb = 2,
                juzNumber = 14,
                startSurahId = 15,
                startAyahNumber = 49,
                startSurahNameAr = "الحجر",
                startSurahNameTransliterated = "Al-Hijr",
                startAyahText = "نَبِّئْ عِبَادِىٓ أَنِّىٓ أَنَا ٱلْغَفُورُ ٱلرَّحِيمُ",
                startPage = 264,
            ),
        ),
        totalBookmarkCount = 5,
    )

    @Test
    fun compact360dpLightThemeRtlLaysOutFourTabsAndNavigatesOnRubClick() {
        var openedAyah: Pair<Int, Int>? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AyahTheme(themeMode = ThemeMode.LIGHT) {
                    Box(modifier = Modifier.width(360.dp).height(720.dp)) {
                        QuranHubContent(
                            state = sampleState(),
                            onQueryChange = {},
                            onRetryImport = {},
                            onOpenSurah = {},
                            onOpenAyah = { s, a -> openedAyah = s to a },
                            onOpenPage = {},
                            onRemoveBookmark = { _, _ -> },
                            initialTab = QuranHubTab.RUB,
                        )
                    }
                }
            }
        }

        val switcherBounds = composeRule.onNodeWithTag("quran_tab_switcher").getUnclippedBoundsInRoot()
        val surahs = composeRule.onNodeWithTag("quran_tab_surahs").getUnclippedBoundsInRoot()
        val juz = composeRule.onNodeWithTag("quran_tab_juz").getUnclippedBoundsInRoot()
        val rub = composeRule.onNodeWithTag("quran_tab_rub").getUnclippedBoundsInRoot()
        val bookmarks = composeRule.onNodeWithTag("quran_tab_bookmarks").getUnclippedBoundsInRoot()

        assertTrue(surahs.right <= switcherBounds.right + 1.dp)
        assertTrue(bookmarks.left >= switcherBounds.left - 1.dp)
        assertTrue(surahs.left >= juz.right - 1.dp)
        assertTrue(juz.left >= rub.right - 1.dp)
        assertTrue(rub.left >= bookmarks.right - 1.dp)

        composeRule.onNodeWithTag("rub_row_106").assertHasClickAction().performClick()
        assertEquals(15 to 49, openedAyah)
    }

    @Test
    fun compact360dpDarkThemeIncreasedFontScalePreservesAccessibilityAndRowNavigation() {
        var openedAyah: Pair<Int, Int>? = null
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(baseDensity.density, fontScale = 1.4f),
            ) {
                AyahTheme(themeMode = ThemeMode.DARK) {
                    Box(modifier = Modifier.width(360.dp).height(760.dp)) {
                        QuranHubContent(
                            state = sampleState(),
                            onQueryChange = {},
                            onRetryImport = {},
                            onOpenSurah = {},
                            onOpenAyah = { s, a -> openedAyah = s to a },
                            onOpenPage = {},
                            onRemoveBookmark = { _, _ -> },
                            initialTab = QuranHubTab.RUB,
                        )
                    }
                }
            }
        }

        val bookmarksNode = composeRule.onNodeWithTag("quran_tab_bookmarks").fetchSemanticsNode()
        val bookmarksCd = bookmarksNode.config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue("Accessible bookmark tab description must retain count 5", bookmarksCd.contains("5"))

        val rubRowNode = composeRule.onNodeWithTag("rub_row_106").fetchSemanticsNode()
        val rubRowCd = rubRowNode.config[SemanticsProperties.ContentDescription].joinToString(" ")
        assertTrue("Rub row description must include Rub 106", rubRowCd.contains("106"))
        assertTrue("Rub row description must include Page 264", rubRowCd.contains("264"))

        composeRule.onNodeWithTag("rub_row_1").performClick()
        assertEquals(1 to 1, openedAyah)
    }
}
