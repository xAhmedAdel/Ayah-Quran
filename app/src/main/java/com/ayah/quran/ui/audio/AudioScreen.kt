package com.ayah.quran.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ayah.quran.data.audio.QuranAudioState
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

            // Reciter chips (Phase 17 verified editions).
            SectionCard(title = stringResource(R.string.audio_reciters_section)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    state.quranReciters.forEach { reciter ->
                        FilterChip(
                            selected = reciter.editionId == state.selectedEditionId,
                            onClick = { viewModel.selectReciter(reciter.editionId) },
                            label = { Text(reciter.nameAr) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.audio_attribution_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Now-playing bar (ayah-level).
            if (!state.ayahPlayback.isIdle) {
                NowPlayingBar(
                    state = state.ayahPlayback,
                    surahName = state.surahRows
                        .firstOrNull { it.surah == state.ayahPlayback.surah }
                        ?.nameArabic ?: "",
                    onPause = viewModel::pauseAyah,
                    onResume = viewModel::resumeAyah,
                    onStop = viewModel::stopAyah,
                    onToggleRepeat = viewModel::toggleRepeatAyah,
                )
            }

            // Surah list with offline download + per-ayah playback.
            SectionCard(title = stringResource(R.string.audio_header_tagline)) {
                if (state.ayahPlayback.blockedByAdhan) {
                    Text(
                        text = stringResource(R.string.audio_blocked_by_adhan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.surahRows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.GraphicEq,
                        title = stringResource(R.string.audio_reciters_empty),
                        message = stringResource(R.string.audio_reciters_empty_detail),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    state.surahRows.take(114).forEach { row ->
                        SurahAudioRowView(row = row, viewModel = viewModel)
                    }
                }
            }
        }
    }
}


@Composable
private fun NowPlayingBar(
    state: com.ayah.quran.data.audio.AyahPlaybackState,
    surahName: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleRepeat: () -> Unit,
) {
    AyahCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = stringResource(
                    R.string.audio_now_playing,
                    surahName, state.ayahIndex + 1, state.totalAyahs,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = if (state.isPlaying) onPause else onResume) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.audio_pause),
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.audio_stop),
                    )
                }
                FilterChip(
                    selected = state.repeatAyah,
                    onClick = onToggleRepeat,
                    label = { Text(stringResource(R.string.audio_repeat_ayah)) },
                )
            }
        }
    }
}

@Composable
private fun SurahAudioRowView(
    row: SurahAudioRow,
    viewModel: AudioViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AyahSpacing.xs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = row.surah.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = row.nameArabic,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                when (val dl = row.downloadState) {
                    is QuranAudioState.Downloading -> Text(
                        text = stringResource(
                            R.string.audio_downloading_progress,
                            dl.downloadedAyahs, dl.totalAyahs,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuranAudioState.Downloaded -> Text(
                        text = stringResource(R.string.audio_downloaded),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is QuranAudioState.Failed -> Text(
                        text = stringResource(R.string.audio_download_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Text(
                        text = "\u0622\u064a\u0629 " + row.ayahCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.isPlaying) {
                IconButton(onClick = viewModel::pauseAyah) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.audio_pause),
                    )
                }
            } else {
                IconButton(onClick = { viewModel.playSurah(row.surah) }) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.audio_play_surah),
                    )
                }
            }
            when (val dl = row.downloadState) {
                is QuranAudioState.Downloading -> IconButton(
                    onClick = { viewModel.cancelDownload(row.surah) },
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.audio_cancel_download),
                    )
                }
                QuranAudioState.Downloaded -> IconButton(
                    onClick = { viewModel.deleteSurah(row.surah) },
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.audio_delete_download),
                    )
                }
                else -> IconButton(
                    onClick = { viewModel.downloadSurah(row.surah) },
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = stringResource(R.string.audio_download),
                    )
                }
            }
        }
    }
}
