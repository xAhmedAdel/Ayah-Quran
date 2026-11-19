package com.ayah.quran.ui.audio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.theme.AyahSpacing

/**
 * Persistent mini player shown above the bottom bar on top-level screens
 * (hidden on the Audio tab, whose Now Playing surface owns full controls).
 * Tap opens the Now Playing screen. Arabic RTL / English LTR via the app
 * locale as everywhere else.
 */
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onOpen: () -> Unit,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isIdle) return

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            when {
                state.playback.restorable != null && state.playback.surah == 0 -> {
                    // Restored snapshot from a previous process — resume offer.
                    Text(
                        text = state.surahNameAr,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.audio_resume_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.blockedByAdhan -> Text(
                    text = stringResource(R.string.audio_blocked_by_adhan),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> {
                    Text(
                        text = state.reciterNameAr +
                            " — " + state.surahNameAr,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val ayahLabel = if (state.playback.totalAyahs > 0) {
                        stringResource(
                            R.string.audio_ayah_counter,
                            state.playback.ayahIndex + 1,
                            state.playback.totalAyahs,
                        )
                    } else {
                        ""
                    }
                    if (ayahLabel.isNotEmpty()) {
                        Text(
                            text = ayahLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        IconButton(onClick = onPauseResume) {
            val playing = state.playback.isPlaying
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.audio_pause else R.string.audio_play_surah,
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
