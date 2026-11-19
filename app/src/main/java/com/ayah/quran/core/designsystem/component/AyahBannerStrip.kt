package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors

enum class AyahBannerTone {
    WARNING,
    ERROR,
    INFO,
}

@Composable
fun AyahBannerStrip(
    message: String,
    tone: AyahBannerTone,
    modifier: Modifier = Modifier,
    icon: ImageVector = when (tone) {
        AyahBannerTone.WARNING -> AyahIcons.StatusWarning
        AyahBannerTone.ERROR -> AyahIcons.StatusError
        AyahBannerTone.INFO -> AyahIcons.StatusSuccess
    },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val extended = LocalAyahColors.current
    val (container, content) = when (tone) {
        AyahBannerTone.WARNING -> extended.statusWarningContainer to extended.statusWarning
        AyahBannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AyahBannerTone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AyahRadii.m),
        color = container,
        contentColor = content,
        border = BorderStroke(AyahTouchTargets.hairline, content.copy(alpha = 0.2f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(AyahRadii.pill),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
