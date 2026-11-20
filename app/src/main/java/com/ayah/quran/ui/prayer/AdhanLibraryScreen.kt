package com.ayah.quran.ui.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahSearchBar
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.AdhanDownloadState
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.Prayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanLibraryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhanLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val prayerName = state.prayer?.let { prayer ->
                        stringResource(
                            when (prayer) {
                                Prayer.FAJR -> R.string.prayer_name_fajr
                                Prayer.DHUHR -> R.string.prayer_name_dhuhr
                                Prayer.ASR -> R.string.prayer_name_asr
                                Prayer.MAGHRIB -> R.string.prayer_name_maghrib
                                Prayer.ISHA -> R.string.prayer_name_isha
                            },
                        )
                    } ?: stringResource(R.string.prayer_voice_title)
                    Text(stringResource(R.string.prayer_library_title_format, prayerName))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopPreview()
                        onBack()
                    }) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.prayer_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            AyahSearchBar(
                query = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = stringResource(R.string.prayer_library_search_hint),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_all),
                        selected = state.categoryFilter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                    )
                }
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_makkah),
                        selected = state.categoryFilter == AdhanCategory.MAKKAH,
                        onClick = { viewModel.setCategoryFilter(AdhanCategory.MAKKAH) },
                    )
                }
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_madinah),
                        selected = state.categoryFilter == AdhanCategory.MADINAH,
                        onClick = { viewModel.setCategoryFilter(AdhanCategory.MADINAH) },
                    )
                }
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_famous),
                        selected = state.categoryFilter == AdhanCategory.FAMOUS,
                        onClick = { viewModel.setCategoryFilter(AdhanCategory.FAMOUS) },
                    )
                }
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_regions),
                        selected = state.categoryFilter == AdhanCategory.REGIONS,
                        onClick = { viewModel.setCategoryFilter(AdhanCategory.REGIONS) },
                    )
                }
                item {
                    CategoryChip(
                        label = stringResource(R.string.prayer_library_category_bundled),
                        selected = state.categoryFilter == AdhanCategory.BUNDLED,
                        onClick = { viewModel.setCategoryFilter(AdhanCategory.BUNDLED) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.downloadedOnly,
                        onClick = { viewModel.setDownloadedOnly(!state.downloadedOnly) },
                        label = { Text(stringResource(R.string.prayer_library_downloaded_only)) },
                    )
                }
            }

            if (state.categoryFilter == AdhanCategory.MAKKAH) {
                Text(
                    text = stringResource(R.string.prayer_library_makkah_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.m),
                )
            }

            if (state.selectedVoiceNeedsDownload) {
                AyahBannerStrip(
                    message = stringResource(R.string.prayer_library_needs_download_warning),
                    tone = AyahBannerTone.ERROR,
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                modifier = Modifier.weight(1f),
            ) {
                items(state.rows, key = { it.recording.id }) { row ->
                    RecordingCard(
                        row = row,
                        preview = preview,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 1) },
    )
}

@Composable
private fun RecordingCard(
    row: AdhanLibraryRow,
    preview: AdhanPreviewState,
    viewModel: AdhanLibraryViewModel,
) {
    val arabic = AppLocale.isArabic
    val recording = row.recording
    val isPreviewing = preview.recording?.id == recording.id

    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalIconButton(
                    onClick = {
                        when {
                            isPreviewing && preview.isPlaying -> viewModel.pausePreview()
                            isPreviewing && preview.isPaused -> viewModel.resumePreview()
                            else -> viewModel.startPreview(recording)
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isPreviewing && preview.isPlaying) {
                            AyahIcons.Pause
                        } else {
                            AyahIcons.Play
                        },
                        contentDescription = stringResource(
                            if (isPreviewing && preview.isPlaying) {
                                R.string.prayer_voice_preview_pause
                            } else {
                                R.string.prayer_voice_preview_play
                            },
                        ),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (arabic) recording.displayNameAr else recording.displayNameEn,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = QuranTextFontFamily,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val location = if (arabic) recording.mosqueCityAr else recording.mosqueCityEn
                    val muadhin = if (arabic) recording.muadhinNameAr else recording.muadhinNameEn
                    listOfNotNull(muadhin, location).joinToString(" · ")
                        .takeIf { it.isNotBlank() }
                        ?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    val durationText = AyahNumbering.isolateLtr(
                        "${recording.durationSeconds / 60}:" +
                            "%02d".format(recording.durationSeconds % 60),
                    )
                    Text(
                        text = listOfNotNull(durationText, recording.license).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.isSelectedForPrayer) {
                    AyahStatusBadge(
                        label = stringResource(R.string.prayer_voice_selected_cd),
                        kind = AyahStatusKind.SUCCESS,
                    )
                }
            }

            if (isPreviewing) {
                Text(
                    text = AyahNumbering.isolateLtr(
                        formatMs(preview.elapsedMs) + " / " + formatMs(
                            preview.totalMs.coerceAtLeast(recording.durationSeconds * 1000L),
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when (val download = row.downloadState) {
                is AdhanDownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(AyahRadii.pill)),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = AyahNumbering.isolateLtr("%d%%".format((download.progress * 100).toInt())),
                            style = MaterialTheme.typography.labelSmall.withTabularNumbers(),
                        )
                        IconButton(onClick = { viewModel.cancelDownload(recording) }) {
                            Icon(
                                imageVector = AyahIcons.Close,
                                contentDescription = stringResource(R.string.prayer_download_cancel),
                            )
                        }
                    }
                }

                is AdhanDownloadState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.prayer_download_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        IconButton(onClick = { viewModel.download(recording) }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.prayer_download_retry),
                            )
                        }
                    }
                }

                is AdhanDownloadState.Downloaded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (!recording.isBundled) {
                            AyahStatusBadge(
                                label = stringResource(R.string.prayer_download_done),
                                kind = AyahStatusKind.SUCCESS,
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!recording.isBundled) {
                                IconButton(onClick = { viewModel.deleteDownload(recording) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.prayer_download_delete),
                                    )
                                }
                            }
                            if (!row.isSelectedForPrayer) {
                                FilledTonalButton(
                                    onClick = { viewModel.select(recording) },
                                    shape = RoundedCornerShape(AyahRadii.pill),
                                ) {
                                    Text(stringResource(R.string.prayer_voice_select))
                                }
                            }
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (recording.isBundled) {
                            Spacer(Modifier.width(1.dp))
                            if (!row.isSelectedForPrayer) {
                                FilledTonalButton(
                                    onClick = { viewModel.select(recording) },
                                    shape = RoundedCornerShape(AyahRadii.pill),
                                ) {
                                    Text(stringResource(R.string.prayer_voice_select))
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.prayer_library_select_needs_download),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(
                                onClick = { viewModel.download(recording) },
                                shape = RoundedCornerShape(AyahRadii.pill),
                            ) {
                                Icon(
                                    imageVector = AyahIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(AyahSpacing.xs))
                                Text(
                                    text = "${stringResource(R.string.prayer_download)} · ${
                                        AyahNumbering.isolateLtr(formatSize(recording.expectedSizeBytes ?: 0L))
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    else -> "%d KB".format(bytes / 1000)
}
