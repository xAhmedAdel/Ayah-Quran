package com.ayah.quran.core.designsystem

object AyahNumbering {

    private val ARABIC_INDIC_DIGITS = listOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    private const val LRI = '\u2066'
    private const val PDI = '\u2069'

    fun toArabicIndic(value: Int): String {
        if (value == 0) return "٠"
        val negative = value < 0
        var n = if (negative) -value.toLong() else value.toLong()
        val digits = buildString {
            while (n > 0L) {
                append(ARABIC_INDIC_DIGITS[(n % 10L).toInt()])
                n /= 10L
            }
        }.reversed()
        return if (negative) "-$digits" else digits
    }

    fun formatForLocale(value: Int, arabicDigits: Boolean): String =
        if (arabicDigits) toArabicIndic(value) else value.toString()

    fun isolateLtr(text: String): String = "$LRI$text$PDI"
}
