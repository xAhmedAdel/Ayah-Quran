package com.ayah.quran.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ayah.quran.R

val QuranTextFontFamily: FontFamily = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

val AyahUiFontFamily: FontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

val AyahWordmarkStyle = TextStyle(
    fontFamily = QuranTextFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 46.sp,
)

val AyahSurahDisplayStyle = TextStyle(
    fontFamily = QuranTextFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 44.sp,
)

val AyahTabularCountdownStyle = TextStyle(
    fontFamily = AyahUiFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    fontFeatureSettings = "tnum",
)

fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

val AyahTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 50.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AyahUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    ),
)
