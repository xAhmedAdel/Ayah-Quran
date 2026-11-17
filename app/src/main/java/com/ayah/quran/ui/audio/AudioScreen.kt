package com.ayah.quran.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.ui.audio.component.PlaybackControls
import com.ayah.quran.ui.audio.component.SeekBarRow

/** Audio state label — text resources (never color alone). */
@Composable
private fun stateLabel(state: PlaybackState): String = when (state) {
    is PlaybackState.Error -> stringResource(R.string.audio_state_error, state.reason)
    PlaybackState.Buffering -> stringResource(R.string.audio_state_buffering)
    PlaybackState.Ended -> stringResource(R.string.audio_state_ended)
    PlaybackState.Idle -> stringResource(R.string.audio_state_idle)
    PlaybackState.Loading -> stringResource(R.string.audio_state_loading)
    PlaybackState.Paused -> stringResource(R.string.audio_state_paused)
    PlaybackState.Playing -> stringResource(R.string.audio_state_playing)
    PlaybackState.Ready -> stringResource(R.string.audio_state_ready)
}

/**
 * Audio tab foundation (Phase 5): premium header, reciter section with an
 * honest empty state, and a playback panel whose controls reflect the
 * ACTUAL player state — disabled with an explanation until a licensed,
 * playable recitation catalog exists.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.audio_title)) })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            // Header.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(40.dp),
                )
                Text(
                    text = stringResource(R.string.audio_header_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reciters section.
            SectionCard(title = stringResource(R.string.audio_reciters_section)) {
                if (state.reciters.isNotEmpty()) {
                    state.reciters.forEach { reciter ->
                        Text(
                            text = reciter.nameArabic,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    EmptyState(
                        icon = Icons.Outlined.GraphicEq,
                        title = stringResource(R.string.audio_reciters_empty),
                        message = stringResource(R.string.audio_reciters_empty_detail),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Playback foundation: reflects ACTUAL player state only.
            SectionCard(title = stringResource(R.string.audio_player_section)) {
                Text(
                    text = stringResource(R.string.audio_playback_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val playback = state.playback
                val statusLabel = stateLabel(playback.playbackState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.audio_state_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Selection slots — populated when a licensed catalog exists.
                Text(
                    text = stringResource(R.string.audio_selected_reciter) + ": —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.audio_selected_surah) + ": —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SeekBarRow(
                    playbackState = playback.playbackState,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    onSeek = { /* seeking activates with real playback */ },
                )

                PlaybackControls(
                    playbackState = playback.playbackState,
                    onPlayPause = viewModel::playPause,
                    onPrevious = { /* track navigation activates with a catalog */ },
                    onNext = { /* track navigation activates with a catalog */ },
                )

                // Repeat preference is persisted now and applies to the
                // player; the chip reflects the ACTUAL stored preference.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.audio_repeat),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                        listOf(
                            RepeatMode.OFF to "Off",
                            RepeatMode.REPEAT_ONE to "One",
                            RepeatMode.REPEAT_ALL to "All",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = playback.repeatMode == mode,
                                onClick = { viewModel.setRepeatMode(mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
    }
}
