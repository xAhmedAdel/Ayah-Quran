package com.ayah.quran.core.designsystem

/**
 * Presentation helpers for Quranic numbering. These format NUMBERS only —
 * Quranic text itself is never transformed.
 */
object AyahNumbering {

    private val ARABIC_INDIC_DIGITS = listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /** Renders an integer with Arabic-Indic digits (٢٥٥), used for ayah badges. */
    fun toArabicIndic(value: Int): String = buildString {
        var n = value
        if (n == 0) return "٠"
        while (n > 0) {
            append(ARABIC_INDIC_DIGITS[n % 10])
            n /= 10
        }
    }.reversed()
}
