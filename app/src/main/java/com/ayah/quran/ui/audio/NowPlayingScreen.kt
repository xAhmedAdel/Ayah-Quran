package com.ayah.quran.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahHeroCard
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.audio.QuranSleepMode
import com.ayah.quran.ui.reader.RepeatRangeDialog
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenInMushaf: ((surahId: Int, ayahNumber: Int) -> Unit)? = null,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playback = state.playback
    val isArabic = AppLocale.isArabic
    val tokens = AyahThemeTokens.colors
    var showRepeatRangeDialog by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playback.isPlaying, playback.surah, playback.ayahIndex) {
        while (true) {
            positionMs = viewModel.positionMs()
            durationMs = viewModel.durationMs()
            if (!playback.isPlaying) break
            delay(500L)
        }
    }

    if (showRepeatRangeDialog && playback.totalAyahs > 0) {
        RepeatRangeDialog(
            initialFromAyah = playback.ayahIndex + 1,
            maxAyahCount = playback.totalAyahs,
            hasActiveRepeatRange = playback.repeatRange != null,
            onStartRepeat = { range ->
                showRepeatRangeDialog = false
                viewModel.setRepeatRange(range)
                viewModel.seekToAyah(range.fromAyah - 1)
            },
            onClearRepeat = { viewModel.setRepeatRange(null) },
            onDismiss = { showRepeatRangeDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audio_now_playing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    if (!state.isIdle && playback.surah > 0 && onOpenInMushaf != null) {
                        FilledTonalButton(
                            onClick = { onOpenInMushaf(playback.surah, playback.ayahIndex + 1) },
                            shape = RoundedCornerShape(AyahRadii.pill),
                            modifier = Modifier
                                .padding(end = AyahSpacing.s)
                                .testTag("now_playing_open_mushaf"),
                        ) {
                            Icon(
                                imageVector = AyahIcons.MushafMode,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(AyahSpacing.xs))
                            Text(
                                text = stringResource(R.string.audio_open_in_reader),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            if (state.isIdle) {
                EmptyState(
                    icon = AyahIcons.Audio,
                    title = stringResource(R.string.audio_now_playing_title),
                    message = stringResource(R.string.audio_state_idle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AyahSpacing.xl),
                )
                return@Column
            }

            if (state.blockedByAdhan) {
                AyahBannerStrip(
                    message = stringResource(R.string.audio_blocked_by_adhan),
                    tone = AyahBannerTone.WARNING,
                )
            }

            AyahHeroCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                ) {
                    Text(
                        text = state.surahNameAr,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = QuranTextFontFamily,
                            fontSize = 34.sp,
                            lineHeight = 48.sp,
                        ),
                        textAlign = TextAlign.Center,
                        color = tokens.heroOnSurface,
                    )
                    if (!isArabic && state.surahNameTransliterated.isNotBlank()) {
                        Text(
                            text = state.surahNameTransliterated,
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.heroAccent,
                        )
                    }
                    Text(
                        text = if (isArabic) state.reciterNameAr else state.reciterNameEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.heroMuted,
                    )
                }
            }

            AyahSurfaceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.l),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
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
                        style = MaterialTheme.typography.titleLarge.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (playback.totalAyahs > 1) {
                        Slider(
                            value = (playback.ayahIndex + 1).toFloat().coerceIn(1f, playback.totalAyahs.toFloat()),
                            onValueChange = { v ->
                                val idx = (v.toInt() - 1).coerceIn(0, playback.totalAyahs - 1)
                                if (idx != playback.ayahIndex) {
                                    viewModel.seekToAyah(idx)
                                }
                            },
                            valueRange = 1f..playback.totalAyahs.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (durationMs > 0L) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = AyahNumbering.isolateLtr(formatAudioMs(positionMs)),
                                style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = AyahNumbering.isolateLtr(formatAudioMs(durationMs)),
                                style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(AyahSpacing.xs))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.l),
                    ) {
                        IconButton(onClick = viewModel::seekPreviousAyah) {
                            Icon(
                                Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.audio_previous_ayah),
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                if (playback.isPlaying) viewModel.pause() else viewModel.resume()
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = if (playback.isPlaying) AyahIcons.Pause else AyahIcons.Play,
                                contentDescription = stringResource(
                                    if (playback.isPlaying) R.string.audio_pause else R.string.audio_play,
                                ),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        IconButton(onClick = viewModel::seekNextAyah) {
                            Icon(
                                Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.audio_next_ayah),
                            )
                        }
                    }

                    IconButton(onClick = viewModel::stop) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.audio_stop),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                SectionHeader(text = stringResource(R.string.audio_repeat_and_sleep))
                AyahSurfaceCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.m),
                        verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                        ) {
                            FilterChip(
                                selected = playback.repeatAyah,
                                onClick = viewModel::toggleRepeatAyah,
                                label = { Text(stringResource(R.string.audio_repeat_ayah)) },
                            )
                            val rangeLabel = playback.repeatRange?.let { r ->
                                "${stringResource(R.string.reader_repeat_range)} · ${AyahNumbering.isolateLtr("${r.fromAyah}–${r.toAyah}")}"
                            } ?: stringResource(R.string.reader_repeat_range)
                            FilterChip(
                                selected = playback.repeatRange != null,
                                onClick = { showRepeatRangeDialog = true },
                                label = { Text(rangeLabel) },
                                modifier = Modifier.testTag("now_playing_repeat_range"),
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                        ) {
                            SleepChip(
                                label = stringResource(R.string.audio_sleep_off),
                                selected = playback.sleepMode == QuranSleepMode.NONE,
                                onClick = { viewModel.setSleepTimer(QuranSleepMode.NONE) },
                            )
                            SleepChip(
                                label = stringResource(R.string.audio_sleep_after_ayah),
                                selected = playback.sleepMode == QuranSleepMode.AFTER_AYAH,
                                onClick = { viewModel.setSleepTimer(QuranSleepMode.AFTER_AYAH) },
                            )
                            SleepChip(
                                label = stringResource(R.string.audio_sleep_after_surah),
                                selected = playback.sleepMode == QuranSleepMode.AFTER_SURA,
                                onClick = { viewModel.setSleepTimer(QuranSleepMode.AFTER_SURA) },
                            )
                            listOf(10, 20, 30).forEach { minutes ->
                                SleepChip(
                                    label = stringResource(R.string.audio_sleep_minutes, minutes),
                                    selected = playback.sleepMode == QuranSleepMode.MINUTES &&
                                        playback.sleepMinutes == minutes,
                                    onClick = { viewModel.setSleepTimer(QuranSleepMode.MINUTES, minutes) },
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                SectionHeader(text = stringResource(R.string.audio_speed_section))
                AyahSurfaceCard {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                        verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.m),
                    ) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
                            FilterChip(
                                selected = state.playbackSpeed == speed,
                                onClick = { viewModel.setPlaybackSpeed(speed) },
                                label = { Text(AyahNumbering.isolateLtr("${speed}x")) },
                            )
                        }
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

private fun formatAudioMs(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
