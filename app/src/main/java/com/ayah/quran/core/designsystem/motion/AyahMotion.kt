package com.ayah.quran.core.designsystem.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object AyahMotion {

    fun animationScale(context: Context): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { AyahMotion.animationScale(context) > 0f }
}

object MotionDurations {
    const val MICRO = 120
    const val FAST = 180
    const val MEDIUM = 240
    const val SLOW = 300

    fun resolve(millis: Int, motionEnabled: Boolean): Int = if (motionEnabled) millis else 0
}

object AyahMotionSpecs {
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val SheetReveal: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    fun <T> standardTween(
        durationMillis: Int = MotionDurations.FAST,
        motionEnabled: Boolean = true,
    ): TweenSpec<T> = tween(
        durationMillis = MotionDurations.resolve(durationMillis, motionEnabled),
        easing = EmphasizedDecelerate,
    )

    fun <T> gentleSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
