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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.data.audio.QuranSleepMode

/**
 * Now Playing screen (Phase 17 slice 2): reciter, surah, current ayah,
 * per-ayah navigation, repeat-ayah, sleep timer, playback speed — every
 * control reflects ACTUAL playback state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback = state.playback

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.audio_now_playing_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
            },
        )
    }) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            if (state.isIdle) {
                AyahCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.audio_state_idle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            // ---- identity card ----
            AyahCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.surahNameAr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = QuranTextFontFamily,
                            fontSize = 34.sp,
                        ),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.reciterNameAr,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = AyahSpacing.s),
                    )
                    if (state.blockedByAdhan) {
                        Text(
                            text = stringResource(R.string.audio_blocked_by_adhan),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = AyahSpacing.s),
                        )
                    }
                }
            }

            // ---- current ayah + per-ayah navigation ----
            AyahCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (playback.totalAyahs > 0) {
                            stringResource(
                                R.string.audio_ayah_counter,
                                playback.ayahIndex + 1,
                                playback.totalAyahs,
                            )
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.l),
                        modifier = Modifier.padding(top = AyahSpacing.m),
                    ) {
                        IconButton(onClick = viewModel::seekPreviousAyah) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription =
                                    stringResource(R.string.audio_previous_ayah),
                            )
                        }
                        IconButton(onClick = {
                            if (playback.isPlaying) viewModel.pause() else viewModel.resume()
                        }) {
                            Icon(
                                if (playback.isPlaying) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = stringResource(R.string.audio_pause),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(40.dp),
                            )
                        }
                        IconButton(onClick = viewModel::seekNextAyah) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription =
                                    stringResource(R.string.audio_next_ayah),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::stop) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.audio_stop),
                        )
                    }
                }
            }

            // ---- repeat + sleep timer ----
            SectionCard(title = stringResource(R.string.audio_repeat_and_sleep)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = playback.repeatAyah,
                        onClick = viewModel::toggleRepeatAyah,
                        label = { Text(stringResource(R.string.audio_repeat_ayah)) },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AyahSpacing.s),
                ) {
                    SleepChip(
                        label = stringResource(R.string.audio_sleep_off),
                        selected = playback.sleepMode == QuranSleepMode.NONE,
                        onClick = { viewModel.setSleepTimer(QuranSleepMode.NONE) },
                    )
                    SleepChip(
                        label = stringResource(R.string.audio_sleep_after_ayah),
                        selected = playback.sleepMode == QuranSleepMode.AFTER_AYAH,
                        onClick = {
                            viewModel.setSleepTimer(QuranSleepMode.AFTER_AYAH)
                        },
                    )
                    SleepChip(
                        label = stringResource(R.string.audio_sleep_after_surah),
                        selected = playback.sleepMode == QuranSleepMode.AFTER_SURA,
                        onClick = {
                            viewModel.setSleepTimer(QuranSleepMode.AFTER_SURA)
                        },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AyahSpacing.s),
                ) {
                    listOf(10, 20, 30).forEach { minutes ->
                        SleepChip(
                            label = stringResource(
                                R.string.audio_sleep_minutes, minutes,
                            ),
                            selected = playback.sleepMode == QuranSleepMode.MINUTES &&
                                playback.sleepMinutes == minutes,
                            onClick = {
                                viewModel.setSleepTimer(QuranSleepMode.MINUTES, minutes)
                            },
                        )
                    }
                }
            }

            // ---- playback speed (persisted) ----
            SectionCard(title = stringResource(R.string.audio_speed_section)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                        FilterChip(
                            selected = state.playbackSpeed == speed,
                            onClick = { viewModel.setPlaybackSpeed(speed) },
                            label = { Text("${speed}x") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
