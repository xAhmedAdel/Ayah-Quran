package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.theme.withTabularNumbers

@Composable
fun SurahNumberEmblem(
    number: Int,
    modifier: Modifier = Modifier,
    arabicDigits: Boolean = false,
    size: Dp = 38.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val label = AyahNumbering.formatForLocale(number, arabicDigits)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 1.3.dp.toPx()
            val inset = this.size.minDimension * 0.17f
            val boxSide = this.size.minDimension - (inset * 2f)
            val topLeft = Offset(inset, inset)
            val boxSize = Size(boxSide, boxSide)
            val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            val stroke = Stroke(width = strokePx)

            drawRoundRect(
                color = tint,
                topLeft = topLeft,
                size = boxSize,
                cornerRadius = corner,
                style = stroke,
            )
            rotate(degrees = 45f) {
                drawRoundRect(
                    color = tint,
                    topLeft = topLeft,
                    size = boxSize,
                    cornerRadius = corner,
                    style = stroke,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.withTabularNumbers(),
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}
