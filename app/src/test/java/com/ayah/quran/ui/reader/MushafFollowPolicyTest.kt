package com.ayah.quran.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the slice-3 automatic page-follow policy across the scenarios of the
 * spec: same page, page boundaries, multi-page jumps, surah/juz transitions,
 * seek, prev-ayah, repeat modes, pause/resume, restore, manual navigation
 * and loop prevention.
 */
class MushafFollowPolicyTest {

    private fun inputs(
        displayed: Int = 562,
        recitedPage: Int = 562,
        surah: Int = 67,
        ayah: Int = 3,
        lastFollowed: Pair<Int, Int>? = null,
        userSelection: Boolean = false,
    ) = MushafFollowPolicy.Inputs(
        displayedPage = displayed,
        recitedPage = recitedPage,
        recitedAyah = MushafFollowPolicy.RecitedAyah(surah, ayah),
        lastFollowedAyah = lastFollowed,
        userSelectionActive = userSelection,
    )

    // ---- same page ----

    @Test
    fun ayahProgressionWithinSamePageDoesNotNavigate() {
        assertFalse(MushafFollowPolicy.shouldNavigate(inputs()))
    }

    @Test
    fun repeatCurrentAyahSamePageDoesNotNavigate() {
        // Repeat-one: the ayah key never changes → never re-navigates.
        val followed = 67 to 3
        assertFalse(
            MushafFollowPolicy.shouldNavigate(
                inputs(lastFollowed = followed),
            ),
        )
    }

    // ---- page boundaries ----

    @Test
    fun ayahProgressionToNextPageNavigates() {
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(recitedPage = 563),
            ),
        )
    }

    @Test
    fun ayahProgressionAcrossMultiplePagesNavigates() {
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 567),
            ),
        )
    }

    @Test
    fun seekingToAnotherPageNavigates() {
        // Seek changes the recited ayah key → follow (even while paused).
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 570, ayah = 25),
            ),
        )
    }

    @Test
    fun previousAyahAcrossPageBoundaryNavigatesBack() {
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 563, recitedPage = 562, ayah = 10),
            ),
        )
    }

    @Test
    fun repeatRangeAcrossPageBoundaryNavigatesOnAdvance() {
        val followed = 67 to 5
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 563, ayah = 12,
                    lastFollowed = followed),
            ),
        )
    }

    // ---- surah / juz transitions ----

    @Test
    fun surahTransitionAcrossPageBoundaryNavigates() {
        // 114-surah end → next surah start lands on a different page.
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 601, recitedPage = 602,
                    surah = 113, ayah = 1, lastFollowed = 112 to 5),
            ),
        )
    }

    @Test
    fun juzTransitionSameRuleAsAnyPageMove() {
        // Juz boundaries are just page boundaries in the 604 mapping — the
        // policy treats them identically (no fabricated boundaries).
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 141, recitedPage = 142,
                    surah = 6, ayah = 111, lastFollowed = 6 to 110),
            ),
        )
    }

    // ---- manual navigation & loop prevention ----

    @Test
    fun manualBrowsingWhileSameAyahRepeatsIsNotYankedBack() {
        // User flipped to page 570 while ayah 3 repeats on page 562 — the
        // ayah key is UNCHANGED since our last follow → do NOT force back.
        assertFalse(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 570, recitedPage = 562,
                    lastFollowed = 67 to 3),
            ),
        )
    }

    @Test
    fun followingResumesWhenPlaybackAdvancesAfterManualBrowsing() {
        // Now playback advances to ayah 4, whose page differs from the
        // manually-browsed page → follow resumes exactly once.
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 570, recitedPage = 562,
                    ayah = 4, lastFollowed = 67 to 3),
            ),
        )
    }

    @Test
    fun userLandingOnPlayingPageIsStable() {
        // Manual navigation LANDED on the recited ayah's page → same page,
        // nothing to do (and no follow-bookkeeping loop).
        assertFalse(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 562,
                    lastFollowed = 67 to 3),
            ),
        )
    }

    @Test
    fun noRepeatedNavigationForSameAyah() {
        // The follow loop fires on every state emission — after navigating
        // for ayah N, the same ayah N must not navigate again.
        val followed = 67 to 4
        assertFalse(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 562,
                    ayah = 4, lastFollowed = followed),
            ),
        )
    }

    // ---- user selection overrides ----

    @Test
    fun userSelectionPausesFollowing() {
        assertTrue(
            MushafFollowPolicy.Inputs(
                displayedPage = 570,
                recitedPage = 562,
                recitedAyah = MushafFollowPolicy.RecitedAyah(67, 4),
                lastFollowedAyah = 67 to 3,
                userSelectionActive = true,
            ).let { !MushafFollowPolicy.shouldNavigate(it) },
        )
    }

    // ---- degraded inputs ----

    @Test
    fun idlePlaybackNeverNavigates() {
        assertFalse(
            MushafFollowPolicy.Inputs(
                displayedPage = 562,
                recitedPage = null,
                recitedAyah = null,
                lastFollowedAyah = null,
                userSelectionActive = false,
            ).let { MushafFollowPolicy.shouldNavigate(it) },
        )
    }

    @Test
    fun unsettledPagerNeverNavigates() {
        assertFalse(
            MushafFollowPolicy.Inputs(
                displayedPage = null,
                recitedPage = 563,
                recitedAyah = MushafFollowPolicy.RecitedAyah(67, 3),
                lastFollowedAyah = null,
                userSelectionActive = false,
            ).let { MushafFollowPolicy.shouldNavigate(it) },
        )
    }

    @Test
    fun invalidDisplayedPageNeverNavigates() {
        assertFalse(
            MushafFollowPolicy.Inputs(
                displayedPage = 0,
                recitedPage = 563,
                recitedAyah = MushafFollowPolicy.RecitedAyah(67, 3),
                lastFollowedAyah = null,
                userSelectionActive = false,
            ).let { MushafFollowPolicy.shouldNavigate(it) },
        )
    }

    @Test
    fun restoreSnapshotNavigatesOnFirstEvaluation() {
        // After process recreation the user resumes; the first evaluation
        // has no lastFollowed → navigates to the resumed ayah's page.
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 564,
                    ayah = 18, lastFollowed = null),
            ),
        )
    }

    // ---- paused-but-active session follows seeks (intent) ----

    @Test
    fun pausedSessionStillFollowsAyahChanges() {
        // Pause does not end the session: a next/previous/seek change
        // while paused is explicit intent → navigate.
        assertTrue(
            MushafFollowPolicy.shouldNavigate(
                inputs(displayed = 562, recitedPage = 563,
                    ayah = 12, lastFollowed = 67 to 11),
            ),
        )
    }
}
