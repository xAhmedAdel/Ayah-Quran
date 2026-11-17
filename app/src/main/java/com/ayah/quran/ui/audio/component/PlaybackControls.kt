package com.ayah.quran.ui.audio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ayah.quran.R
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.ui.audio.formatTime

/**
 * Play/Pause + Previous/Next row. Every control reflects the ACTUAL
 * [PlaybackState]: without a playable, prepared source all controls are
 * disabled and exposed to accessibility services as disabled.
 */
@Composable
fun PlaybackControls(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = playbackState is PlaybackState.Ready ||
        playbackState is PlaybackState.Playing ||
        playbackState is PlaybackState.Paused ||
        playbackState is PlaybackState.Buffering ||
        playbackState is PlaybackState.Ended
    val isPlaying = playbackState is PlaybackState.Playing

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        FilledIconButton(
            onClick = onPrevious,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.audio_previous),
            )
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = com.ayah.quran.core.designsystem.theme.AyahSpacing.l),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (isPlaying) R.string.audio_pause else R.string.audio_play,
                ),
            )
        }
        FilledIconButton(
            onClick = onNext,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.audio_next),
            )
        }
    }
}
