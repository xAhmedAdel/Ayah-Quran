package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets

@Composable
fun AyahGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            SectionHeader(
                text = title,
                trailing = trailing,
                modifier = Modifier.padding(bottom = AyahSpacing.xs),
            )
        }
        AyahSurfaceCard(content = content)
    }
}

@Composable
fun AyahInsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = AyahSpacing.m,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = AyahSpacing.m),
        thickness = AyahTouchTargets.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
