package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.LocalAyahColors

enum class AyahStatusKind {
    SUCCESS,
    WARNING,
    ERROR,
    GOLD,
    NEUTRAL,
}

@Composable
fun AyahStatusBadge(
    label: String,
    kind: AyahStatusKind,
    modifier: Modifier = Modifier,
    icon: ImageVector? = defaultStatusIcon(kind),
) {
    val extended = LocalAyahColors.current
    val (containerColor, contentColor) = when (kind) {
        AyahStatusKind.SUCCESS -> extended.statusSuccessContainer to extended.statusSuccess
        AyahStatusKind.WARNING -> extended.statusWarningContainer to extended.statusWarning
        AyahStatusKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AyahStatusKind.GOLD -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AyahStatusKind.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AyahRadii.pill),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AyahSpacing.sm, vertical = AyahSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

private fun defaultStatusIcon(kind: AyahStatusKind): ImageVector? = when (kind) {
    AyahStatusKind.SUCCESS -> AyahIcons.StatusSuccess
    AyahStatusKind.WARNING -> AyahIcons.StatusWarning
    AyahStatusKind.ERROR -> AyahIcons.StatusError
    AyahStatusKind.GOLD, AyahStatusKind.NEUTRAL -> null
}
