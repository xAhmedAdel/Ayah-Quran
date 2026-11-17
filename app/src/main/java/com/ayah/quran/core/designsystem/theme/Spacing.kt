package com.ayah.quran.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * The 4-pt spacing scale used across Ayah. Referencing this object instead of
 * scattered dp literals keeps layout rhythm consistent.
 */
object AyahSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** Corner radius scale matching MaterialTheme.shapes usage in Ayah screens. */
object AyahRadii {
    val card = 20.dp
    val sheet = 28.dp
    val pill = 100.dp
}
