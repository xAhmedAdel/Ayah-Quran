package com.ayah.quran.ui.wallpapers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.getDefault())

/**
 * Wallpaper control center: live preview (same renderer as the final
 * wallpaper), template/typography/position controls, explicit apply,
 * automation scheduling with honest status, and managed history.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpapersScreen(
    onOpenFullscreenPreview: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.wallpapers_title)) })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            PreviewCard(
                state = state,
                viewModel = viewModel,
                onOpenFullscreen = onOpenFullscreenPreview,
            )
            TemplateSection(state = state, viewModel = viewModel)
            VerseTextSection(state = state, viewModel = viewModel)
            ApplySection(state = state, viewModel = viewModel)
            AutomationSection(state = state, viewModel = viewModel)
            HistorySection(state = state, viewModel = viewModel, applying = state.applying)
        }
    }
}

@Composable
private fun PreviewCard(
    state: WallpapersUiState,
    viewModel: WallpapersViewModel,
    onOpenFullscreen: (surahId: Int, ayahNumber: Int) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            val verse = state.previewVerse
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(9f / 16f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = state.previewBitmap
                if (bitmap != null) {
                    // Tapping the preview opens the immersive full-screen mode.
                    val openLabel = stringResource(R.string.wallpapers_preview_caption)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = openLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickablePreviewIn(verse, openLabel, onOpenFullscreen),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            verse?.let {
                Text(
                    text = it.referenceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            AssistChip(
                onClick = viewModel::shufflePreviewVerse,
                label = { Text(stringResource(R.string.wallpapers_shuffle_verse)) },
                leadingIcon = {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

/** Preview tap → immersive full-screen route for the currently shown verse. */
@Composable
private fun Modifier.clickablePreviewIn(
    verse: PreviewVerse?,
    openLabel: String,
    onOpenFullscreen: (surahId: Int, ayahNumber: Int) -> Unit,
): Modifier =
    if (verse == null) {
        this
    } else {
        this.clickable(onClickLabel = openLabel) { onOpenFullscreen(verse.surahId, verse.ayahNumber) }
    }

@Composable
private fun TemplateSection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    SectionCard(title = stringResource(R.string.wallpapers_template_section)) {
        val selectedId = state.settings.templateId
        WallpaperTemplate.ALL.chunked(2).forEach { rowTemplates ->
            Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                rowTemplates.forEach { template ->
                    FilterChip(
                        selected = selectedId == template.id,
                        onClick = { viewModel.selectTemplate(template.id) },
                        label = { Text(template.displayName) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowTemplates.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun VerseTextSection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    SectionCard(title = stringResource(R.string.wallpapers_text_section)) {
        Text(
            text = stringResource(R.string.wallpapers_font_size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = state.settings.fontScale,
            onValueChange = viewModel::setFontScale,
            valueRange = 0.6f..1.6f,
            steps = 9,
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val positions = listOf(
                TextVerticalPosition.TOP to stringResource(R.string.wallpapers_position_top),
                TextVerticalPosition.CENTER to stringResource(R.string.wallpapers_position_center),
                TextVerticalPosition.BOTTOM to stringResource(R.string.wallpapers_position_bottom),
            )
            positions.forEachIndexed { index, (position, label) ->
                SegmentedButton(
                    selected = state.settings.verticalPosition == position,
                    onClick = { viewModel.setVerticalPosition(position) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = positions.size),
                    label = { Text(label) },
                )
            }
        }

        VersePicker(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersePicker(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    var surahMenuOpen by remember { mutableStateOf(false) }
    var ayahText by remember(state.settings.fixedSurahId) { mutableStateOf("") }
    val selectedSurah = state.surahs.firstOrNull { it.id == state.settings.fixedSurahId }

    Text(
        text = stringResource(R.string.wallpapers_verse_source),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
        ExposedDropdownMenuBox(
            expanded = surahMenuOpen,
            onExpandedChange = { surahMenuOpen = it },
            modifier = Modifier.weight(2f),
        ) {
            OutlinedTextField(
                value = "${selectedSurah?.id ?: 1} · ${selectedSurah?.nameTransliterated ?: ""}",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.wallpapers_pick_surah)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = surahMenuOpen) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = surahMenuOpen, onDismissRequest = { surahMenuOpen = false }) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.size(width = 280.dp, height = 320.dp),
                ) {
                    items(state.surahs, key = { it.id }) { surah ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${surah.id} · ${surah.nameTransliterated}") },
                            onClick = {
                                viewModel.setFixedVerse(surah.id, state.settings.fixedAyahNumber)
                                surahMenuOpen = false
                            },
                        )
                    }
                }
            }
        }

        val ayahError = ayahText.toIntOrNull()?.let { entered -> entered < 1 || entered > (selectedSurah?.ayahCount ?: 1) }
        OutlinedTextField(
            value = ayahText,
            onValueChange = { input ->
                ayahText = input.take(3)
                val entered = input.toIntOrNull()
                if (entered != null && selectedSurah != null && entered in 1..selectedSurah.ayahCount) {
                    viewModel.pickPreviewVerse(selectedSurah.id, entered)
                    viewModel.setFixedVerse(selectedSurah.id, entered)
                }
            },
            label = { Text(stringResource(R.string.wallpapers_pick_ayah)) },
            isError = ayahError == true,
            supportingText = if (ayahError == true) {
                {
                    Text(
                        stringResource(
                            R.string.wallpapers_ayah_number_error,
                            selectedSurah?.ayahCount ?: 1,
                        ),
                    )
                }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = stringResource(R.string.wallpapers_auto_updates),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // FlowRow wraps instead of clipping on narrow screens / large fonts.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        listOf(
            SelectionMode.RANDOM to SelectionModeLabel.RANDOM,
            SelectionMode.SEQUENTIAL to SelectionModeLabel.SEQUENTIAL,
            SelectionMode.DAILY to SelectionModeLabel.DAILY,
            SelectionMode.SURAH to SelectionModeLabel.SURAH,
            SelectionMode.AYAH to SelectionModeLabel.AYAH,
        ).forEach { (mode, label) ->
            FilterChip(
                selected = state.settings.selectionMode == mode,
                onClick = { viewModel.setSelectionMode(mode) },
                label = { Text(label) },
            )
        }
    }
}

/** Selection-mode display labels (kept as constants for the FlowRow mapping). */
private object SelectionModeLabel {
    const val RANDOM = "Random"
    const val SEQUENTIAL = "Sequential"
    const val DAILY = "Daily"
    const val SURAH = "Surah"
    const val AYAH = "Ayah"
}

@Composable
private fun ApplySection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    SectionCard(title = stringResource(R.string.wallpapers_apply_now)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val destinations = listOf(
                WallpaperDestination.SYSTEM to stringResource(R.string.wallpapers_apply_home),
                WallpaperDestination.LOCK to stringResource(R.string.wallpapers_apply_lock),
                WallpaperDestination.BOTH to stringResource(R.string.wallpapers_apply_both),
            )
            destinations.forEachIndexed { index, (destination, label) ->
                SegmentedButton(
                    selected = state.settings.destinations == destination,
                    onClick = { viewModel.setDestinations(destination) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = destinations.size),
                    label = { Text(label) },
                )
            }
        }

        Button(
            onClick = viewModel::applyPreviewVerse,
            enabled = !state.applying && state.previewVerse != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AyahSpacing.s),
        ) {
            if (state.applying) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            }
            Text(
                text = if (state.applying) {
                    stringResource(R.string.wallpapers_apply_working)
                } else {
                    stringResource(R.string.wallpapers_apply_now)
                },
                modifier = Modifier.padding(start = AyahSpacing.s),
            )
        }

        // Outcome appears with an icon AND text (never color alone).
        androidx.compose.animation.AnimatedVisibility(
            visible = state.applyOutcome != null,
            enter = androidx.compose.animation.expandVertically() +
                androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() +
                androidx.compose.animation.fadeOut(),
        ) {
            state.applyOutcome?.let { outcome ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    modifier = Modifier.padding(top = AyahSpacing.s),
                ) {
                    if (outcome == WallpaperApplyStatus.SUCCESS) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.wallpapers_apply_success,
                                destinationLabel(state.settings.destinations),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.wallpapers_apply_failed,
                                state.history.firstOrNull()?.entry?.failureReason ?: "-",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutomationSection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    SectionCard(title = stringResource(R.string.wallpapers_auto_updates)) {
        val statusText = if (state.scheduleStatus.isScheduled) {
            stringResource(
                R.string.wallpapers_auto_every_format,
                "${state.scheduleStatus.intervalMinutes ?: state.settings.intervalMinutes} min",
            )
        } else {
            stringResource(R.string.wallpapers_auto_off)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.settings.autoUpdateEnabled,
                onCheckedChange = viewModel::setAutoUpdateEnabled,
            )
        }

        if (state.settings.autoUpdateEnabled) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val intervals = listOf(30, 60, 180, 360)
                intervals.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = state.settings.intervalMinutes == minutes,
                        onClick = { viewModel.setInterval(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size),
                        label = { Text("$minutes m") },
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val intervals = listOf(720, 1440)
                intervals.forEachIndexed { index, minutes ->
                    SegmentedButton(
                        selected = state.settings.intervalMinutes == minutes,
                        onClick = { viewModel.setInterval(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = intervals.size),
                        label = { Text("${minutes / 60} h") },
                    )
                }
            }
        }

        Text(
            text = state.lastAppliedAtMillis?.let {
                stringResource(
                    R.string.wallpapers_last_applied_format,
                    TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
                )
            } ?: stringResource(R.string.wallpapers_last_applied_never),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.wallpapers_auto_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySection(
    state: WallpapersUiState,
    viewModel: WallpapersViewModel,
    applying: Boolean,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.wallpapers_history_section)) {
        if (state.history.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = { showClearConfirm = true },
                    enabled = !applying,
                ) {
                    Text(stringResource(R.string.history_clear))
                }
            }
        }

        if (state.history.isEmpty()) {
            Text(
                text = stringResource(R.string.wallpapers_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        state.history.forEachIndexed { index, item ->
            HistoryRow(
                item = item,
                applying = applying,
                onReapply = { viewModel.reapply(item) },
                onDelete = { viewModel.deleteHistoryEntry(item) },
            )
            if (index != state.history.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_message),
            confirmText = stringResource(R.string.history_clear_confirm),
            onConfirm = {
                viewModel.clearHistory()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    applying: Boolean,
    onReapply: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = item.entry
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AyahSpacing.s),
    ) {
        // Thumbnail (rendered by the production renderer) or a placeholder.
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 88.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            item.thumbnail?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.wallpapers_history_ayah_format,
                    entry.templateName,
                    entry.surahId,
                    entry.ayahNumber,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = TIME_FORMAT.format(Instant.ofEpochMilli(entry.appliedAtMillis).atZone(ZoneId.systemDefault())) +
                    " · " + destinationLabel(entry.destinations) + " · " +
                    stringResource(
                        if (entry.status == WallpaperApplyStatus.SUCCESS) {
                            R.string.wallpapers_history_status_success
                        } else {
                            R.string.wallpapers_history_status_failed
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == WallpaperApplyStatus.SUCCESS) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }

        TextButton(onClick = onReapply, enabled = !applying) {
            Text(stringResource(R.string.wallpapers_history_reapply))
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.history_delete_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun destinationLabel(destination: WallpaperDestination): String = stringResource(
    when (destination) {
        WallpaperDestination.SYSTEM -> R.string.wallpapers_apply_home
        WallpaperDestination.LOCK -> R.string.wallpapers_apply_lock
        WallpaperDestination.BOTH -> R.string.wallpapers_apply_both
    },
)
