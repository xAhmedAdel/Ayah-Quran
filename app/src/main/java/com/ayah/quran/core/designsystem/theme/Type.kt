package com.ayah.quran.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ayah.quran.R

/**
 * Arabic display family: Amiri (SIL OFL 1.1).
 *
 * Amiri is used for Arabic/ Quranic display text and the wordmark. It is NOT
 * forced onto Latin UI text, whose default system family keeps UI dense
 * views crisp. The dedicated Quranic rendering font for the wallpaper
 * engine (Phase 3) will be a Uthmani-script font chosen during the data
 * phase; Amiri here covers UI display needs.
 */
val QuranTextFontFamily: FontFamily = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

/** Wordmark / hero style: Amiri, used where the brand appears. */
val AyahWordmarkStyle = TextStyle(
    fontFamily = QuranTextFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 48.sp,
)

private val Default = FontFamily.Default

/**
 * Material 3 type scale with the system family and slightly tightened
 * spacing for a calmer, premium rhythm.
 */
val AyahTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 50.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)
