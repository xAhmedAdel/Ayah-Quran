package com.ayah.quran.domain.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionTest {

    @Test
    fun prereleaseNumbersCompareNumerically() {
        assertTrue(AppUpdateVersion.isNewer("v1.0.0-beta.2", "1.0.0-beta.1"))
        assertTrue(AppUpdateVersion.isNewer("v1.0.0-beta.10", "1.0.0-beta.2"))
        assertFalse(AppUpdateVersion.isNewer("v1.0.0-beta.1", "1.0.0-beta.1"))
        assertFalse(AppUpdateVersion.isNewer("v1.0.0-beta.1", "1.0.0-beta.9"))
    }

    @Test
    fun coreComponentsCompareNumericallyNotLexically() {
        assertTrue(AppUpdateVersion.isNewer("v1.10.0", "1.2.0"))
        assertTrue(AppUpdateVersion.isNewer("v2.0.0", "1.9.9"))
        assertFalse(AppUpdateVersion.isNewer("v1.2.0", "1.10.0"))
    }

    @Test
    fun fullReleaseOutranksPrereleaseOfSameCore() {
        assertTrue(AppUpdateVersion.isNewer("v1.0.0", "1.0.0-beta.9"))
        assertFalse(AppUpdateVersion.isNewer("v1.0.0-beta.9", "1.0.0"))
    }

    @Test
    fun channelsRankAlphaBelowBetaBelowRc() {
        assertTrue(AppUpdateVersion.isNewer("v1.0.0-rc.1", "1.0.0-beta.9"))
        assertTrue(AppUpdateVersion.isNewer("v1.0.0-beta.1", "1.0.0-alpha.3"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(AppUpdateVersion.isNewer("v1.0.0-beta.1", "1.0.0-beta.1"))
        assertFalse(AppUpdateVersion.isNewer("v1.0.0", "1.0.0"))
    }

    @Test
    fun unparsableCandidateIsNeverNewer() {
        assertFalse(AppUpdateVersion.isNewer(null, "1.0.0"))
        assertFalse(AppUpdateVersion.isNewer("", "1.0.0"))
        assertFalse(AppUpdateVersion.isNewer("garbage", "1.0.0"))
    }

    @Test
    fun unparsableCurrentAcceptsAnyParsedCandidate() {
        // Defensive: a corrupted stored version must not block updates.
        assertTrue(AppUpdateVersion.isNewer("v1.0.0-beta.1", "garbage"))
    }

    @Test
    fun shortenedCoresPadToEqualLength() {
        assertTrue(AppUpdateVersion.isNewer("v1.0.0.1", "1.0.0"))
        assertFalse(AppUpdateVersion.isNewer("v1.0", "1.0.0"))
    }
}
