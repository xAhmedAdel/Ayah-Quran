package com.ayah.quran.ui.audio.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ayah.quran.ui.audio.formatTime

/**
 * Seek bar with current/total time. Disabled unless the state reports a
 * prepared source; times always come from the actual player state.
 */
@Composable
fun SeekBarRow(
    playbackState: com.ayah.quran.domain.model.audio.PlaybackState,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prepared = playbackState is com.ayah.quran.domain.model.audio.PlaybackState.Ready ||
        playbackState is com.ayah.quran.domain.model.audio.PlaybackState.Playing ||
        playbackState is com.ayah.quran.domain.model.audio.PlaybackState.Paused ||
        playbackState is com.ayah.quran.domain.model.audio.PlaybackState.Buffering ||
        playbackState is com.ayah.quran.domain.model.audio.PlaybackState.Ended

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                0f
            },
            onValueChange = { fraction -> onSeek((fraction * durationMs).toLong()) },
            enabled = prepared && durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
