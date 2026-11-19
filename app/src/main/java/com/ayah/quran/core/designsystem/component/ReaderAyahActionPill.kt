package com.ayah.quran.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets

@Composable
fun ReaderAyahActionPill(
    ayahReferenceLabel: String,
    isBookmarked: Boolean,
    onPlay: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
    onRepeatRange: (() -> Unit)? = null,
    onCreateWallpaper: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val hasSecondaryActions = onRepeatRange != null || onCreateWallpaper != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reader_ayah_action_pill"),
        shape = RoundedCornerShape(AyahRadii.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.sm, vertical = AyahSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Text(
                text = ayahReferenceLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AyahSpacing.xs),
            )
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(AyahRadii.pill),
                contentPadding = PaddingValues(horizontal = AyahSpacing.sm, vertical = AyahSpacing.xs),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag("reader_pill_play"),
            ) {
                Icon(
                    imageVector = AyahIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(AyahSpacing.xs))
                Text(
                    text = stringResource(R.string.audio_play),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            FilledTonalButton(
                onClick = onToggleBookmark,
                shape = RoundedCornerShape(AyahRadii.pill),
                contentPadding = PaddingValues(horizontal = AyahSpacing.sm, vertical = AyahSpacing.xs),
                modifier = Modifier.testTag("reader_pill_bookmark"),
            ) {
                Icon(
                    imageVector = if (isBookmarked) {
                        AyahIcons.BookmarkFilled
                    } else {
                        AyahIcons.BookmarkOutline
                    },
                    contentDescription = stringResource(R.string.reader_bookmark),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (hasSecondaryActions) {
                Box {
                    IconButton(
                        onClick = { moreExpanded = true },
                        modifier = Modifier.testTag("reader_pill_more"),
                    ) {
                        Icon(
                            imageVector = AyahIcons.More,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                    ) {
                        if (onRepeatRange != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reader_repeat_range)) },
                                leadingIcon = {
                                    Icon(AyahIcons.Repeat, contentDescription = null)
                                },
                                onClick = {
                                    moreExpanded = false
                                    onRepeatRange()
                                },
                            )
                        }
                        if (onCreateWallpaper != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reader_make_wallpaper)) },
                                leadingIcon = {
                                    Icon(AyahIcons.Wallpaper, contentDescription = null)
                                },
                                onClick = {
                                    moreExpanded = false
                                    onCreateWallpaper()
                                },
                            )
                        }
                    }
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reader_pill_dismiss"),
                ) {
                    Icon(
                        imageVector = AyahIcons.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
