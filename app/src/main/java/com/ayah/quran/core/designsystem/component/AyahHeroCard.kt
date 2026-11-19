package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors

@Composable
fun AyahHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalAyahColors.current
    val outerShape = RoundedCornerShape(AyahRadii.hero)
    val innerShape = RoundedCornerShape(AyahRadii.m)
    val gradient = Brush.verticalGradient(
        colors = listOf(tokens.heroSurface, tokens.heroSurfaceDeep),
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = outerShape,
        border = BorderStroke(AyahTouchTargets.hairline, tokens.heroAccent.copy(alpha = 0.28f)),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = AyahTouchTargets.hairline,
                        color = tokens.heroAccent.copy(alpha = 0.14f),
                        shape = innerShape,
                    )
                    .padding(AyahSpacing.m),
            ) {
                CompositionLocalProvider(LocalContentColor provides tokens.heroOnSurface) {
                    content()
                }
            }
        }
    }
}
