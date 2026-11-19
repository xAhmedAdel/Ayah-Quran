package com.ayah.quran.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ayah.quran.domain.model.ThemeMode

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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !useDark
            controller.isAppearanceLightNavigationBars = !useDark
        }
    }

    val extendedColors = if (useDark) AyahDarkExtendedColors else AyahLightExtendedColors

    CompositionLocalProvider(LocalAyahColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (useDark) AyahDarkColors else AyahLightColors,
            typography = AyahTypography,
            shapes = AyahShapes,
            content = content,
        )
    }
}

object AyahThemeTokens {
    val colors: AyahExtendedColors
        @Composable get() = LocalAyahColors.current
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
