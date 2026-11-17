package com.ayah.quran.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.ayah.quran.domain.model.ThemeMode

/**
 * Ayah theme root.
 *
 * [ThemeMode.SYSTEM] follows the device setting; LIGHT/DARK force explicitly.
 * Dynamic color is intentionally not used (fixed brand identity).
 */
@Composable
fun AyahTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDark) AyahDarkColors else AyahLightColors,
        typography = AyahTypography,
        content = content,
    )
}
