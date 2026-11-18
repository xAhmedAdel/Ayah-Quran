package com.ayah.quran.ui.prayer

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.AdhanDownloadState
import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels

/**
 * Phase 15 — the premium adhan audio library: category filters, search,
 * downloaded-only filter, per-recording preview and download controls, and
 * per-prayer selection. Remote recordings stream for preview before
 * download and must be downloaded before they can be assigned for offline
 * scheduled playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanLibraryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhanLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preview by kotlinx.coroutines.flow.MutableStateFlow(com.ayah.quran.data.prayer.AdhanPreviewState()).collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()

    // Leaving the library always stops preview playback.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    Scaffold(topBar = {
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.prayer_back),
                    )
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                label = { Text(stringResource(R.string.prayer_library_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryChip(
                    label = stringResource(R.string.prayer_library_category_all),
                    selected = state.categoryFilter == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                )
                CategoryChip(
                    label = stringResource(R.string.prayer_library_category_madinah),
                    selected = state.categoryFilter == AdhanCategory.MADINAH,
                    onClick = { viewModel.setCategoryFilter(AdhanCategory.MADINAH) },
                )
                CategoryChip(
                    label = stringResource(R.string.prayer_library_category_famous),
                    selected = state.categoryFilter == AdhanCategory.FAMOUS,
                    onClick = { viewModel.setCategoryFilter(AdhanCategory.FAMOUS) },
                )
                CategoryChip(
                    label = stringResource(R.string.prayer_library_category_regions),
                    selected = state.categoryFilter == AdhanCategory.REGIONS,
                    onClick = { viewModel.setCategoryFilter(AdhanCategory.REGIONS) },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryChip(
                    label = stringResource(R.string.prayer_library_category_bundled),
                    selected = state.categoryFilter == AdhanCategory.BUNDLED,
                    onClick = { viewModel.setCategoryFilter(AdhanCategory.BUNDLED) },
                )
                FilterChip(
                    selected = state.downloadedOnly,
                    onClick = { viewModel.setDownloadedOnly(!state.downloadedOnly) },
                    label = { Text(stringResource(R.string.prayer_library_downloaded_only)) },
                )
            }

            // Honest Makkah state: no legally verified recording exists yet.
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
                Text(
                    text = stringResource(R.string.prayer_library_needs_download_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.s),
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
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
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun RecordingCard(
    row: AdhanLibraryRow,
    preview: com.ayah.quran.data.prayer.AdhanPreviewState,
    viewModel: AdhanLibraryViewModel,
) {
    val context = LocalContext.current
    val arabic = AppLocale.isArabic
    val recording = row.recording
    val isPreviewing = preview.recording?.id == recording.id

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (arabic) recording.displayNameAr else recording.displayNameEn,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = QuranTextFontFamily,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isSelectedForPrayer) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.prayer_voice_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val location = if (arabic) {
                recording.mosqueCityAr
            } else {
                recording.mosqueCityEn
            }
            val muadhin = if (arabic) recording.muadhinNameAr else recording.muadhinNameEn
            listOfNotNull(muadhin, location).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recording.descriptionAr?.takeIf { arabic }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            recording.descriptionEn?.takeIf { !arabic }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = listOfNotNull(
                    "${recording.durationSeconds / 60}:" +
                        "%02d".format(recording.durationSeconds % 60),
                    recording.license,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Download controls (remote recordings only).
            when (val download = row.downloadState) {
                is AdhanDownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "%d%%".format((download.progress * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        IconButton(onClick = { viewModel.cancelDownload(recording) }) {
                            Icon(
                                Icons.Filled.Close,
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

                else -> if (!recording.isBundled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = formatSize(recording.expectedSizeBytes ?: 0L),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.download(recording) }) {
                                Icon(
                                    Icons.Filled.Download,
                                    contentDescription = stringResource(R.string.prayer_download),
                                )
                            }
                            IconButton(onClick = { viewModel.deleteDownload(recording) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.prayer_download_delete),
                                )
                            }
                        }
                    }
                }
            }

            // Preview + select row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = {
                    when {
                        isPreviewing && preview.isPlaying -> viewModel.pausePreview()
                        isPreviewing && preview.isPaused -> viewModel.resumePreview()
                        else -> viewModel.startPreview(recording)
                    }
                }) {
                    when {
                        isPreviewing && preview.isPlaying -> Icon(
                            Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.prayer_voice_preview_pause),
                        )

                        else -> Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.prayer_voice_preview_play),
                        )
                    }
                }
                if (isPreviewing) {
                    Text(
                        text = formatMs(preview.elapsedMs) + " / " + formatMs(
                            preview.totalMs.coerceAtLeast(
                                recording.durationSeconds * 1000L,
                            ),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { viewModel.select(recording) },
                    enabled = row.isDownloaded,
                ) {
                    Text(stringResource(R.string.prayer_voice_select))
                }
            }
            if (!row.isDownloaded && !recording.isBundled) {
                Text(
                    text = stringResource(R.string.prayer_library_select_needs_download),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
