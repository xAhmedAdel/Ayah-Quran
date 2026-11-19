package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily

@Composable
fun MushafSurahBanner(
    surahNameArabic: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bismillah: String? = null,
) {
    val extended = LocalAyahColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AyahRadii.xs))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalDivider(thickness = 1.5.dp, color = extended.mushafMarker)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
            ) {
                Text(
                    text = surahNameArabic,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = 28.sp,
                        lineHeight = 40.sp,
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = 1.5.dp, color = extended.mushafMarker)
        }

        if (bismillah != null) {
            Text(
                text = bismillah,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = QuranTextFontFamily,
                    fontSize = 23.sp,
                    lineHeight = 36.sp,
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AyahSpacing.xs),
            )
        }
    }
}
