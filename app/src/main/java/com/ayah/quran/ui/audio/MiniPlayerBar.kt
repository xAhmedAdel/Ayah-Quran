package com.ayah.quran.ui.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale

@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onOpen: () -> Unit,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isIdle) return

    val isRestorableOnly = state.playback.restorable != null && state.playback.surah == 0
    val isArabic = AppLocale.isArabic
    val surahTitle = if (isArabic) {
        state.surahNameAr
    } else {
        state.surahNameTransliterated.ifEmpty { state.surahNameAr }
    }
    val reciterTitle = if (isArabic) {
        state.reciterNameAr
    } else {
        state.reciterNameEn.ifEmpty { state.reciterNameAr }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.sm, vertical = AyahSpacing.xs)
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(AyahRadii.m),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isRestorableOnly && state.playback.totalAyahs > 0) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = AyahSpacing.sm, vertical = AyahSpacing.s),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AyahRadii.s))
                        .background(
                            when {
                                state.blockedByAdhan -> MaterialTheme.colorScheme.errorContainer
                                isRestorableOnly -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            state.blockedByAdhan -> AyahIcons.StatusError
                            else -> AyahIcons.Audio
                        },
                        contentDescription = null,
                        tint = when {
                            state.blockedByAdhan -> MaterialTheme.colorScheme.onErrorContainer
                            isRestorableOnly -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
                ) {
                    when {
                        isRestorableOnly -> {
                            Text(
                                text = surahTitle,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.audio_resume_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        state.blockedByAdhan -> {
                            Text(
                                text = stringResource(R.string.audio_blocked_by_adhan),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        else -> {
                            Text(
                                text = "$reciterTitle — $surahTitle",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (state.playback.totalAyahs > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.audio_ayah_counter,
                                        state.playback.ayahIndex + 1,
                                        state.playback.totalAyahs,
                                    ),
                                    style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("mini_player_play_pause"),
                ) {
                    val playing = state.playback.isPlaying
                    Icon(
                        imageVector = if (playing) AyahIcons.Pause else AyahIcons.Play,
                        contentDescription = stringResource(
                            if (playing) R.string.audio_pause else R.string.audio_play,
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
