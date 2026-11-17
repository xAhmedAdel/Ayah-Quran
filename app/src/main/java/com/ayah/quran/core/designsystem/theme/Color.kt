package com.ayah.quran.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ---- Brand palette (from the Ayah design specification) ----
val Emerald = Color(0xFF153D32)
val EmeraldDeep = Color(0xFF0E2B23)
val EmeraldBright = Color(0xFF1E5A49)
val Gold = Color(0xFFD5B56A)
val GoldSoft = Color(0xFFE8D3A2)
val GoldDeep = Color(0xFFA88C4C)
val Cream = Color(0xFFF7F2E8)
val CreamBright = Color(0xFFFCF9F2)
val Ink = Color(0xFF111714)
val InkSurface = Color(0xFF161D19)
val InkSurfaceVariant = Color(0xFF1D2621)
val TextPrimaryDark = Color(0xFFF5F2EA)
val TextSecondaryDark = Color(0xFFC0C9C3)

// Derived supporting tones (kept alongside the brand palette so all scheme
// colors are visible in one place).
val EmeraldContainerLight = Color(0xFFD2E3DB)
val GoldContainerLight = Color(0xFFF1E3C4)
val TextPrimaryLight = Color(0xFF17201B)
val TextSecondaryLight = Color(0xFF47514B)
val OutlineLight = Color(0xFF7A857E)
val OutlineDark = Color(0xFF8A938D)

/**
 * Light scheme. Dynamic (Material You) color extraction is deliberately NOT
 * used: Ayah has a fixed premium identity and must look identical on every
 * device.
 */
val AyahLightColors = lightColorScheme(
    primary = Emerald,
    onPrimary = TextPrimaryDark,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldDeep,
    secondary = GoldDeep,
    onSecondary = Cream,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = Color(0xFF4A3B15),
    tertiary = EmeraldBright,
    onTertiary = Cream,
    background = Cream,
    onBackground = TextPrimaryLight,
    surface = CreamBright,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFE4DED0),
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFCBCFC9),
    surfaceTint = Emerald,
)

/** Dark scheme, anchored on the spec's Ink background and Gold accent. */
val AyahDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1F2B25),
    primaryContainer = Color(0xFF23463A),
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = Color(0xFF33290F),
    secondaryContainer = Color(0xFF4A3D1E),
    onSecondaryContainer = GoldContainerLight,
    tertiary = EmeraldBright,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimaryDark,
    surface = InkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF3A423D),
    surfaceTint = Gold,
)
