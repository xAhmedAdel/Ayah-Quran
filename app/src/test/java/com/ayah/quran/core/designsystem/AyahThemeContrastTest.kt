package com.ayah.quran.core.designsystem

import androidx.compose.ui.graphics.Color
import com.ayah.quran.core.designsystem.theme.AyahDarkColors
import com.ayah.quran.core.designsystem.theme.AyahDarkExtendedColors
import com.ayah.quran.core.designsystem.theme.AyahLightColors
import com.ayah.quran.core.designsystem.theme.AyahLightExtendedColors
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class AyahThemeContrastTest {

    @Test
    fun lightSchemeMeetsWcagAaContrast() {
        assertContrastAtLeast("light onBackground/background", AyahLightColors.onBackground, AyahLightColors.background, 7.0)
        assertContrastAtLeast("light onSurface/surface", AyahLightColors.onSurface, AyahLightColors.surface, 7.0)
        assertContrastAtLeast("light onSurfaceVariant/surface", AyahLightColors.onSurfaceVariant, AyahLightColors.surface, 4.5)
        assertContrastAtLeast("light onPrimary/primary", AyahLightColors.onPrimary, AyahLightColors.primary, 7.0)
        assertContrastAtLeast("light primary/surface", AyahLightColors.primary, AyahLightColors.surface, 7.0)
        assertContrastAtLeast("light secondary/surface", AyahLightColors.secondary, AyahLightColors.surface, 4.5)
        assertContrastAtLeast("light onPrimaryContainer/primaryContainer", AyahLightColors.onPrimaryContainer, AyahLightColors.primaryContainer, 7.0)
        assertContrastAtLeast("light onSecondaryContainer/secondaryContainer", AyahLightColors.onSecondaryContainer, AyahLightColors.secondaryContainer, 7.0)
        assertContrastAtLeast("light error/surface", AyahLightColors.error, AyahLightColors.surface, 4.5)
        assertContrastAtLeast("light heroOnSurface/heroSurface", AyahLightExtendedColors.heroOnSurface, AyahLightExtendedColors.heroSurface, 7.0)
        assertContrastAtLeast("light heroAccent/heroSurface", AyahLightExtendedColors.heroAccent, AyahLightExtendedColors.heroSurface, 4.5)
        assertContrastAtLeast("light statusSuccess", AyahLightExtendedColors.statusSuccess, AyahLightExtendedColors.statusSuccessContainer, 4.5)
        assertContrastAtLeast("light statusWarning", AyahLightExtendedColors.statusWarning, AyahLightExtendedColors.statusWarningContainer, 4.5)
    }

    @Test
    fun darkSchemeMeetsWcagAaContrast() {
        assertContrastAtLeast("dark onBackground/background", AyahDarkColors.onBackground, AyahDarkColors.background, 7.0)
        assertContrastAtLeast("dark onSurface/surface", AyahDarkColors.onSurface, AyahDarkColors.surface, 7.0)
        assertContrastAtLeast("dark onSurfaceVariant/surface", AyahDarkColors.onSurfaceVariant, AyahDarkColors.surface, 4.5)
        assertContrastAtLeast("dark onPrimary/primary", AyahDarkColors.onPrimary, AyahDarkColors.primary, 7.0)
        assertContrastAtLeast("dark primary/surface", AyahDarkColors.primary, AyahDarkColors.surface, 7.0)
        assertContrastAtLeast("dark secondary/surface", AyahDarkColors.secondary, AyahDarkColors.surface, 7.0)
        assertContrastAtLeast("dark onPrimaryContainer/primaryContainer", AyahDarkColors.onPrimaryContainer, AyahDarkColors.primaryContainer, 7.0)
        assertContrastAtLeast("dark onSecondaryContainer/secondaryContainer", AyahDarkColors.onSecondaryContainer, AyahDarkColors.secondaryContainer, 7.0)
        assertContrastAtLeast("dark error/surface", AyahDarkColors.error, AyahDarkColors.surface, 4.5)
        assertContrastAtLeast("dark heroOnSurface/heroSurface", AyahDarkExtendedColors.heroOnSurface, AyahDarkExtendedColors.heroSurface, 7.0)
        assertContrastAtLeast("dark heroAccent/heroSurface", AyahDarkExtendedColors.heroAccent, AyahDarkExtendedColors.heroSurface, 4.5)
        assertContrastAtLeast("dark statusSuccess", AyahDarkExtendedColors.statusSuccess, AyahDarkExtendedColors.statusSuccessContainer, 4.5)
        assertContrastAtLeast("dark statusWarning", AyahDarkExtendedColors.statusWarning, AyahDarkExtendedColors.statusWarningContainer, 4.5)
    }

    private fun assertContrastAtLeast(label: String, fg: Color, bg: Color, minRatio: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label contrast was $ratio, expected >= $minRatio", ratio >= minRatio)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(c: Color): Double {
        fun channel(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
