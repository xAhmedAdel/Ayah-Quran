package com.ayah.quran.core.designsystem.motion

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Motion helpers that respect the system animation-scale accessibility
 * setting: when animations are disabled (scale 0) — a common reduced-motion
 * preference — durations collapse to zero.
 */
object AyahMotion {

    /** Reads the system animator duration scale ("0" = reduced motion). */
    fun animationScale(context: android.content.Context): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
}

/**
 * True when motion should be animated (system animation scale > 0).
 * Callers multiply durations by [MotionDurations.scale] or branch on this.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { AyahMotion.animationScale(context) > 0f }
}

/** Standard Ayah motion durations (already in sp-of-time ms). */
object MotionDurations {
    const val FAST = 150
    const val MEDIUM = 220
    const val SLOW = 300
}
