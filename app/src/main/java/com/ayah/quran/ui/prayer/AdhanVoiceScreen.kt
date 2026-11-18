package com.ayah.quran.ui.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection

/**
 * Per-prayer adhan voice selection (Phase 13): five prayer cards, each
 * showing the currently selected recording with a button that opens the
 * built-in library. The library offers Play Preview (one at a time, with
 * progress, stopping when the screen closes) and per-prayer selection.
 * Preview never modifies selections and never touches scheduled playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanVoiceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhanVoiceViewModel = hiltViewModel(),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val preview by viewModel.previewState.collectAsStateWithLifecycle()
    var libraryFor by remember { mutableStateOf<Prayer?>(null) }

    // Leaving the screen always stops preview playback (spec §5).
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPreview() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.prayer_voice_title)) },
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
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            val selection = prefs?.voiceSelection ?: PrayerVoiceSelection()
            listOf(
                Prayer.FAJR,
                Prayer.DHUHR,
                Prayer.ASR,
                Prayer.MAGHRIB,
                Prayer.ISHA,
            ).forEach { prayer ->
                VoiceCard(
                    prayer = prayer,
                    voiceId = selection.voiceOf(prayer),
                    catalog = viewModel.catalog,
                    onOpenLibrary = { libraryFor = prayer },
                )
            }
        }
    }

    libraryFor?.let { prayer ->
        LibraryDialog(
            prayer = prayer,
            voiceId = prefs?.voiceSelection?.voiceOf(prayer),
            catalog = viewModel.catalog,
            preview = preview,
            customAudioUri = prefs?.customAdhanAudioUri,
            viewModel = viewModel,
            onDismiss = { libraryFor = null },
        )
    }
}

@Composable
private fun VoiceCard(
    prayer: Prayer,
    voiceId: String,
    catalog: BundledAdhanCatalog,
    onOpenLibrary: () -> Unit,
) {
    val arabic = AppLocale.isArabic
    val recording = catalog.byId(voiceId)
    val voiceName = recording?.let {
        if (arabic) it.displayNameAr else it.displayNameEn
    } ?: stringResource(R.string.prayer_voice_user_file)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Text(
                text = prayerName(prayer),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = QuranTextFontFamily,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.prayer_voice_selected_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = voiceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = AyahSpacing.xs),
                    )
                }
                TextButton(onClick = onOpenLibrary) {
                    Text(stringResource(R.string.prayer_voice_open_library))
                }
            }
        }
    }
}

@Composable
private fun LibraryDialog(
    prayer: Prayer,
    voiceId: String?,
    catalog: BundledAdhanCatalog,
    preview: AdhanPreviewState,
    customAudioUri: String?,
    viewModel: AdhanVoiceViewModel,
    onDismiss: () -> Unit,
) {
    val arabic = AppLocale.isArabic
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.prayer_voice_open_library),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.size(width = 340.dp, height = 380.dp),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                items(catalog.recordings, key = { it.id }) { recording ->
                    val isSelected = voiceId == recording.id
                    val isPreviewing = preview.recording?.id == recording.id
                    RecordingRow(
                        name = if (arabic) recording.displayNameAr else recording.displayNameEn,
                        description = if (arabic) {
                            recording.descriptionAr
                        } else {
                            recording.descriptionEn
                        },
                        durationText = stringResource(
                            R.string.prayer_voice_duration_format,
                            recording.durationSeconds / 60,
                            recording.durationSeconds % 60,
                        ),
                        license = recording.license,
                        isSelected = isSelected,
                        isPreviewing = isPreviewing,
                        previewPlaying = preview.isPlaying,
                        previewElapsedMs = preview.elapsedMs,
                        previewTotalMs = preview.totalMs,
                        onPreviewToggle = {
                            when {
                                isPreviewing && preview.isPlaying -> viewModel.pausePreview()
                                isPreviewing && preview.isPaused -> viewModel.resumePreview()
                                else -> viewModel.startPreview(recording)
                            }
                        },
                        onSelect = {
                            viewModel.selectVoice(prayer, recording.id)
                            onDismiss()
                        },
                    )
                }
                // Legacy Phase 12 user file remains selectable per prayer.
                // Must live inside item { } — LazyListScope's receiver is not
                // a composable context.
                if (customAudioUri != null) {
                    item(key = com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults.USER_FILE_ID) {
                        val isUserFile = voiceId == com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults.USER_FILE_ID
                        RecordingRow(
                            name = stringResource(R.string.prayer_voice_user_file),
                            description = stringResource(R.string.prayer_voice_user_file_desc),
                            durationText = null,
                            license = null,
                            isSelected = isUserFile,
                            isPreviewing = false,
                            previewPlaying = false,
                            previewElapsedMs = 0,
                            previewTotalMs = 0,
                            onPreviewToggle = null,
                            onSelect = {
                                viewModel.selectVoice(
                                    prayer,
                                    com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults.USER_FILE_ID,
                                )
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                viewModel.stopPreview()
                onDismiss()
            }) { Text(stringResource(R.string.prayer_back)) }
        },
    )
}

@Composable
private fun RecordingRow(
    name: String,
    description: String?,
    durationText: String?,
    license: String?,
    isSelected: Boolean,
    isPreviewing: Boolean,
    previewPlaying: Boolean,
    previewElapsedMs: Long,
    previewTotalMs: Long,
    onPreviewToggle: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.prayer_voice_selected_cd),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (isSelected) {
                    Text(
                        text = stringResource(R.string.prayer_voice_select),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = listOfNotNull(durationText, license).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onPreviewToggle != null) {
                    IconButton(onClick = onPreviewToggle) {
                        when {
                            isPreviewing && previewPlaying -> Icon(
                                Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.prayer_voice_preview_pause),
                            )

                            else -> Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.prayer_voice_preview_play),
                            )
                        }
                    }
                }
            }
            if (isPreviewing) {
                LinearProgressIndicator(
                    progress = {
                        if (previewTotalMs > 0) {
                            (previewElapsedMs.toFloat() / previewTotalMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatMs(previewElapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMs(previewTotalMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isPreviewing) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp))
            }
            TextButton(onClick = onSelect, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.prayer_voice_select))
            }
        }
    }
}

/** "m:ss" for preview elapsed/total. */
private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun prayerName(prayer: Prayer): String = stringResource(
    when (prayer) {
        Prayer.FAJR -> R.string.prayer_name_fajr
        Prayer.DHUHR -> R.string.prayer_name_dhuhr
        Prayer.ASR -> R.string.prayer_name_asr
        Prayer.MAGHRIB -> R.string.prayer_name_maghrib
        Prayer.ISHA -> R.string.prayer_name_isha
    },
)
