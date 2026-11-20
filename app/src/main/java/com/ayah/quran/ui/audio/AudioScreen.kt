package com.ayah.quran.ui.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.component.EmptyState
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.component.SurahNumberEmblem
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.audio.QuranAudioState
import com.ayah.quran.data.audio.QuranReciterEdition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isArabic = AppLocale.isArabic
    var query by rememberSaveable { mutableStateOf("") }
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRow by remember { mutableStateOf<SurahAudioRow?>(null) }

    val downloadedCount = remember(state.surahRows) {
        state.surahRows.count { it.isDownloaded }
    }
    val filteredRows = remember(state.surahRows, query, downloadedOnly) {
        val q = query.trim()
        state.surahRows.filter { row ->
            val matchesFilter = !downloadedOnly || row.isDownloaded
            val matchesQuery = q.isEmpty() ||
                row.nameArabic.contains(q) ||
                row.nameTransliterated.contains(q, ignoreCase = true) ||
                row.surah.toString().contains(q)
            matchesFilter && matchesQuery
        }
    }

    pendingDeleteRow?.let { target ->
        val surahName = if (isArabic) target.nameArabic else target.nameTransliterated.ifBlank { target.nameArabic }
        ConfirmDialog(
            title = stringResource(R.string.audio_delete_confirm_title),
            message = stringResource(R.string.audio_delete_confirm_message, surahName),
            confirmText = stringResource(R.string.audio_delete_download),
            dismissTextRes = R.string.action_cancel,
            onConfirm = {
                viewModel.deleteSurah(target.surah)
                pendingDeleteRow = null
            },
            onDismiss = { pendingDeleteRow = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.audio_title)) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .testTag("audio_lazy_list"),
            contentPadding = PaddingValues(bottom = AyahSpacing.xl),
        ) {
            if (state.ayahPlayback.blockedByAdhan) {
                item(key = "adhan_banner") {
                    AyahBannerStrip(
                        message = stringResource(R.string.audio_blocked_by_adhan),
                        tone = AyahBannerTone.WARNING,
                        modifier = Modifier.padding(
                            horizontal = AyahSpacing.m,
                            vertical = AyahSpacing.xs,
                        ),
                    )
                }
            }

            item(key = "reciters_header") {
                SectionHeader(
                    text = stringResource(R.string.audio_reciters_section),
                    modifier = Modifier.padding(
                        start = AyahSpacing.m,
                        end = AyahSpacing.m,
                        top = AyahSpacing.xs,
                        bottom = AyahSpacing.s,
                    ),
                )
            }

            item(key = "reciters_carousel") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = AyahSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.quranReciters, key = { it.editionId }) { reciter ->
                        ReciterCard(
                            reciter = reciter,
                            selected = reciter.editionId == state.selectedEditionId,
                            isArabic = isArabic,
                            onClick = { viewModel.selectReciter(reciter.editionId) },
                        )
                    }
                }
            }

            item(key = "attribution_note") {
                Text(
                    text = stringResource(R.string.audio_attribution_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = AyahSpacing.m,
                        vertical = AyahSpacing.s,
                    ),
                )
            }

            item(key = "search_and_filter") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    AyahSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.quran_search_hint),
                        modifier = Modifier.testTag("audio_search_bar"),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = !downloadedOnly,
                            onClick = { downloadedOnly = false },
                            label = {
                                Text(stringResource(R.string.audio_filter_all, state.surahRows.size))
                            },
                            modifier = Modifier.testTag("audio_filter_all"),
                        )
                        FilterChip(
                            selected = downloadedOnly,
                            onClick = { downloadedOnly = true },
                            label = {
                                Text(stringResource(R.string.audio_filter_downloaded, downloadedCount))
                            },
                            modifier = Modifier.testTag("audio_filter_downloaded"),
                        )
                    }
                }
            }

            if (state.surahRows.isEmpty()) {
                item(key = "empty_catalog") {
                    EmptyState(
                        icon = AyahIcons.Audio,
                        title = stringResource(R.string.audio_reciters_empty),
                        message = stringResource(R.string.audio_reciters_empty_detail),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.xl),
                    )
                }
            } else if (filteredRows.isEmpty()) {
                item(key = "empty_filter") {
                    EmptyState(
                        icon = AyahIcons.Audio,
                        title = stringResource(R.string.audio_header_tagline),
                        message = if (downloadedOnly) {
                            stringResource(R.string.audio_downloaded_empty)
                        } else {
                            stringResource(R.string.quran_search_no_results, query)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.xl),
                    )
                }
            } else {
                items(filteredRows, key = { it.surah }) { row ->
                    SurahAudioRowView(
                        row = row,
                        isArabic = isArabic,
                        onPlay = { viewModel.playSurah(row.surah) },
                        onPause = viewModel::pauseAyah,
                        onDownload = { viewModel.downloadSurah(row.surah) },
                        onCancelDownload = { viewModel.cancelDownload(row.surah) },
                        onRequestDelete = { pendingDeleteRow = row },
                    )
                    AyahInsetDivider(startIndent = 70.dp)
                }
            }
        }
    }
}

@Composable
private fun ReciterCard(
    reciter: QuranReciterEdition,
    selected: Boolean,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val border = if (selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        modifier = Modifier
            .width(172.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(AyahRadii.m))
            .clickable(onClick = onClick)
            .testTag("reciter_card_${reciter.editionId}"),
        shape = RoundedCornerShape(AyahRadii.m),
        color = container,
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(AyahSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Filled.Check else AyahIcons.Audio,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp),
                    )
                }
                AyahStatusBadge(
                    label = AyahNumbering.isolateLtr("${reciter.bitrateKbps} kbps"),
                    kind = if (selected) AyahStatusKind.GOLD else AyahStatusKind.NEUTRAL,
                )
            }
            Text(
                text = if (isArabic) reciter.nameAr else reciter.nameEn,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SurahAudioRowView(
    row: SurahAudioRow,
    isArabic: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val displayName = if (isArabic) {
        row.nameArabic
    } else {
        row.nameTransliterated.ifBlank { row.nameArabic }
    }
    val ayahCountText = pluralStringResource(
        R.plurals.ayah_count_plurals,
        row.ayahCount,
        row.ayahCount,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SurahNumberEmblem(
                number = row.surah,
                tint = if (row.isCurrent) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (row.isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (val dl = row.downloadState) {
                    is QuranAudioState.Downloading -> {
                        Text(
                            text = stringResource(
                                R.string.audio_downloading_progress,
                                dl.downloadedAyahs,
                                dl.totalAyahs,
                            ),
                            style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val progress = if (dl.totalAyahs > 0) {
                            (dl.downloadedAyahs.toFloat() / dl.totalAyahs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(AyahRadii.pill)),
                        )
                    }

                    QuranAudioState.Downloaded -> Text(
                        text = "$ayahCountText · ${stringResource(R.string.audio_downloaded)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    is QuranAudioState.Failed -> Text(
                        text = stringResource(R.string.audio_download_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> Text(
                        text = ayahCountText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = if (row.isPlaying) onPause else onPlay,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (row.isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (row.isPlaying) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ),
            ) {
                Icon(
                    imageVector = if (row.isPlaying) AyahIcons.Pause else AyahIcons.Play,
                    contentDescription = stringResource(
                        if (row.isPlaying) R.string.audio_pause else R.string.audio_play_surah,
                    ),
                )
            }
            when (row.downloadState) {
                is QuranAudioState.Downloading -> IconButton(onClick = onCancelDownload) {
                    Icon(
                        imageVector = AyahIcons.Close,
                        contentDescription = stringResource(R.string.audio_cancel_download),
                    )
                }

                QuranAudioState.Downloaded -> IconButton(onClick = onRequestDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.audio_delete_download),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = AyahIcons.Download,
                        contentDescription = stringResource(R.string.audio_download),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
