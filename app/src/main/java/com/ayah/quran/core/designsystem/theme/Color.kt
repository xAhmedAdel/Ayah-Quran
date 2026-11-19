package com.ayah.quran.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val SanctuaryEmerald = Color(0xFF0F3D32)
val SanctuaryEmeraldDeep = Color(0xFF0A2921)
val SanctuaryEmeraldMuted = Color(0xFF1C5949)
val AntiqueGold = Color(0xFFC4A052)
val AntiqueGoldDeep = Color(0xFF8F6E2A)
val AntiqueGoldSoft = Color(0xFFE6D29E)
val ParchmentCanvas = Color(0xFFF6F2E9)
val ParchmentSurface = Color(0xFFFCF9F2)
val ObsidianCanvas = Color(0xFF0C1210)
val ObsidianSurface = Color(0xFF131B17)

val Emerald = SanctuaryEmerald
val EmeraldDeep = SanctuaryEmeraldDeep
val EmeraldBright = SanctuaryEmeraldMuted
val Gold = Color(0xFFD6B66B)
val GoldSoft = AntiqueGoldSoft
val GoldDeep = AntiqueGoldDeep
val Cream = ParchmentCanvas
val CreamBright = ParchmentSurface
val Ink = ObsidianCanvas
val InkSurface = ObsidianSurface
val InkSurfaceVariant = Color(0xFF1C2621)
val TextPrimaryDark = Color(0xFFF2EEE5)
val TextSecondaryDark = Color(0xFFA7B2AC)
val EmeraldContainerLight = Color(0xFFDCEAE4)
val GoldContainerLight = Color(0xFFF2E5C6)
val TextPrimaryLight = Color(0xFF151C18)
val TextSecondaryLight = Color(0xFF525C56)
val OutlineLight = Color(0xFF838D87)
val OutlineDark = Color(0xFF6F7A74)

val AyahLightColors = lightColorScheme(
    primary = SanctuaryEmerald,
    onPrimary = Color(0xFFFAF7F0),
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = SanctuaryEmeraldDeep,
    secondary = AntiqueGoldDeep,
    onSecondary = Color(0xFFFFFDF8),
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = Color(0xFF43320E),
    tertiary = SanctuaryEmeraldMuted,
    onTertiary = Color(0xFFFAF7F0),
    background = ParchmentCanvas,
    onBackground = TextPrimaryLight,
    surface = ParchmentSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEAE4D7),
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFDDD7CA),
    error = Color(0xFFB33A3A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE8E8),
    onErrorContainer = Color(0xFF6E1B1B),
    surfaceTint = SanctuaryEmerald,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF7EF),
    surfaceContainer = Color(0xFFF3EFE4),
    surfaceContainerHigh = Color(0xFFECE7DA),
    surfaceContainerHighest = Color(0xFFE4DEC0),
)

val AyahDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF16201B),
    primaryContainer = Color(0xFF1B3B31),
    onPrimaryContainer = AntiqueGoldSoft,
    secondary = Color(0xFFE2C887),
    onSecondary = Color(0xFF261E0A),
    secondaryContainer = Color(0xFF3A2F16),
    onSecondaryContainer = Color(0xFFF4E5BF),
    tertiary = Color(0xFF6DBAA2),
    onTertiary = Color(0xFF0B221B),
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF24302A),
    error = Color(0xFFF08A8A),
    onError = Color(0xFF3B0B0B),
    errorContainer = Color(0xFF441B1B),
    onErrorContainer = Color(0xFFFADADA),
    surfaceTint = Gold,
    surfaceContainerLowest = Color(0xFF090E0C),
    surfaceContainerLow = Color(0xFF111815),
    surfaceContainer = Color(0xFF161F1B),
    surfaceContainerHigh = Color(0xFF1D2722),
    surfaceContainerHighest = Color(0xFF25312B),
)

@Immutable
data class AyahExtendedColors(
    val heroSurface: Color,
    val heroSurfaceDeep: Color,
    val heroOnSurface: Color,
    val heroAccent: Color,
    val heroMuted: Color,
    val mushafCanvas: Color,
    val mushafAyahHighlight: Color,
    val mushafMarker: Color,
    val statusSuccess: Color,
    val statusSuccessContainer: Color,
    val statusWarning: Color,
    val statusWarningContainer: Color,
    val scrim: Color,
)

val AyahLightExtendedColors = AyahExtendedColors(
    heroSurface = SanctuaryEmerald,
    heroSurfaceDeep = SanctuaryEmeraldDeep,
    heroOnSurface = Color(0xFFF9F5EB),
    heroAccent = Color(0xFFDFC178),
    heroMuted = Color(0xFFB7CEC6),
    mushafCanvas = Color(0xFFFBF7EE),
    mushafAyahHighlight = Color(0x38C4A052),
    mushafMarker = Color(0xFF9A762D),
    statusSuccess = Color(0xFF1C6B4E),
    statusSuccessContainer = Color(0xFFE1F2EB),
    statusWarning = Color(0xFF8C560B),
    statusWarningContainer = Color(0xFFFBF0DC),
    scrim = Color(0x8C0C1210),
)

val AyahDarkExtendedColors = AyahExtendedColors(
    heroSurface = Color(0xFF122D25),
    heroSurfaceDeep = Color(0xFF0B1D17),
    heroOnSurface = Color(0xFFF5F1E8),
    heroAccent = Color(0xFFDFC178),
    heroMuted = Color(0xFFA5BEB5),
    mushafCanvas = Color(0xFF101613),
    mushafAyahHighlight = Color(0x33D6B66B),
    mushafMarker = Color(0xFFD6B66B),
    statusSuccess = Color(0xFF68C79F),
    statusSuccessContainer = Color(0xFF143327),
    statusWarning = Color(0xFFEBB45B),
    statusWarningContainer = Color(0xFF362812),
    scrim = Color(0xA6000000),
)

val LocalAyahColors = staticCompositionLocalOf { AyahLightExtendedColors }
