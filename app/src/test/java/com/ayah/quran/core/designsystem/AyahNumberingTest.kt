package com.ayah.quran.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class AyahNumberingTest {

    @Test
    fun toArabicIndicConvertsZeroPositiveAndMultiDigitNumbers() {
        assertEquals("٠", AyahNumbering.toArabicIndic(0))
        assertEquals("٧", AyahNumbering.toArabicIndic(7))
        assertEquals("٢٥٥", AyahNumbering.toArabicIndic(255))
        assertEquals("٦٠٤", AyahNumbering.toArabicIndic(604))
    }

    @Test
    fun formatForLocaleRespectsDigitPreference() {
        assertEquals("١١٤", AyahNumbering.formatForLocale(114, arabicDigits = true))
        assertEquals("114", AyahNumbering.formatForLocale(114, arabicDigits = false))
    }

    @Test
    fun isolateLtrWrapsCompoundTokensWithUnicodeIsolates() {
        val isolated = AyahNumbering.isolateLtr("1.0.0-beta.6")
        assertEquals("\u20661.0.0-beta.6\u2069", isolated)
    }
}
