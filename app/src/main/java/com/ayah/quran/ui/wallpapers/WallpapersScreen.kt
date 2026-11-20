package com.ayah.quran.ui.wallpapers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahOutlinedButton
import com.ayah.quran.core.designsystem.component.AyahPrimaryButton
import com.ayah.quran.core.designsystem.component.AyahSelectionSheet
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.AyahTonalButton
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.theme.AntiqueGold
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.core.wallpaper.WallpaperTemplate
import com.ayah.quran.domain.model.SelectionMode
import com.ayah.quran.domain.model.TextVerticalPosition
import com.ayah.quran.domain.model.WallpaperApplyStatus
import com.ayah.quran.domain.model.WallpaperDestination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INTERVAL_OPTIONS = listOf(30, 60, 180, 360, 720, 1440)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WallpapersScreen(
    onOpenFullscreenPreview: (surahId: Int, ayahNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallpapersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallpapers_title)) },
                actions = {
                    val autoBadgeLabel = if (state.scheduleStatus.isScheduled) {
                        val minutes = state.scheduleStatus.intervalMinutes ?: state.settings.intervalMinutes
                        stringResource(
                            R.string.wallpapers_badge_auto_on,
                            stringResource(intervalLabelRes(minutes), intervalLabelValue(minutes)),
                        )
                    } else {
                        stringResource(R.string.wallpapers_badge_auto_off)
                    }
                    AyahStatusBadge(
                        label = autoBadgeLabel,
                        kind = if (state.scheduleStatus.isScheduled) AyahStatusKind.SUCCESS else AyahStatusKind.NEUTRAL,
                        icon = null,
                        modifier = Modifier.padding(end = AyahSpacing.m),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            PreviewStage(
                state = state,
                viewModel = viewModel,
                onOpenFullscreen = onOpenFullscreenPreview,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                TemplateSection(state = state, viewModel = viewModel)
                VerseTextSection(state = state, viewModel = viewModel)
                AutomationSection(state = state, viewModel = viewModel)
                HistorySection(state = state, viewModel = viewModel, applying = state.applying)
            }

            PinnedApplyBar(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun PreviewStage(
    state: WallpapersUiState,
    viewModel: WallpapersViewModel,
    onOpenFullscreen: (surahId: Int, ayahNumber: Int) -> Unit,
) {
    val verse = state.previewVerse
    val openLabel = stringResource(R.string.wallpapers_preview_caption)
    val frameShape = RoundedCornerShape(20.dp)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                Box(
                    modifier = Modifier
                        .height(176.dp)
                        .aspectRatio(9f / 16f)
                        .clip(frameShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, frameShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, frameShape)
                        .clickablePreviewIn(verse, openLabel, onOpenFullscreen),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = state.previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = openLabel,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    Text(
                        text = stringResource(labelResForId(state.settings.templateId)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    verse?.let {
                        Text(
                            text = it.referenceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AyahTonalButton(
                        text = stringResource(R.string.wallpapers_shuffle_verse),
                        onClick = viewModel::shufflePreviewVerse,
                        icon = Icons.Filled.Shuffle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AyahOutlinedButton(
                        text = openLabel,
                        onClick = {
                            if (verse != null) {
                                onOpenFullscreen(verse.surahId, verse.ayahNumber)
                            }
                        },
                        enabled = verse != null && state.previewBitmap != null,
                        icon = Icons.Outlined.Fullscreen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(
                thickness = AyahTouchTargets.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

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
    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.wallpapers_template_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val selectedId = state.settings.templateId
            WallpaperTemplate.ALL.chunked(2).forEach { rowTemplates ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    rowTemplates.forEach { template ->
                        TemplateSwatchCard(
                            template = template,
                            selected = selectedId == template.id,
                            onClick = { viewModel.selectTemplate(template.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowTemplates.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateSwatchCard(
    template: WallpaperTemplate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AyahRadii.card)
    val swatchShape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 2.dp else AyahTouchTargets.hairline

    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.s),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(swatchShape)
                    .then(
                        when (val bg = template.background) {
                            is WallpaperTemplate.BackgroundSpec.Gradient -> Modifier.background(
                                Brush.verticalGradient(listOf(Color(bg.topColor), Color(bg.bottomColor))),
                            )
                            is WallpaperTemplate.BackgroundSpec.Solid -> Modifier.background(Color(bg.color))
                        },
                    )
                    .border(1.dp, Color(template.accentColor).copy(alpha = 0.55f), swatchShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "آية",
                    fontFamily = QuranTextFontFamily,
                    fontSize = 18.sp,
                    color = Color(template.textColor),
                )
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AntiqueGold),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF0C1210),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(template.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VerseTextSection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.wallpapers_text_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.wallpapers_font_size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = AyahNumbering.isolateLtr("${(state.settings.fontScale * 100).toInt()}%"),
                    style = MaterialTheme.typography.labelLarge.withTabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            AyahInsetDivider()
            VersePicker(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun VersePicker(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    var showSurahSheet by remember { mutableStateOf(false) }
    var ayahText by remember(state.settings.fixedSurahId, state.settings.fixedAyahNumber) {
        mutableStateOf(state.settings.fixedAyahNumber.toString())
    }
    val selectedSurah = state.surahs.firstOrNull { it.id == state.settings.fixedSurahId }

    Text(
        text = stringResource(R.string.wallpapers_verse_source),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            onClick = { showSurahSheet = true },
            shape = RoundedCornerShape(AyahRadii.card),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(2f)
                .height(56.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AyahSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wallpapers_pick_surah),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${selectedSurah?.id ?: 1} · " + (selectedSurah?.localizedDisplayName() ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = AyahIcons.ChevronEnd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val ayahError = ayahText.isNotEmpty() &&
            (ayahText.toIntOrNull()?.let { it < 1 || it > (selectedSurah?.ayahCount ?: 1) } != false)
        OutlinedTextField(
            value = ayahText,
            onValueChange = { input ->
                val digitsOnly = input.filter(Char::isDigit).take(3)
                ayahText = digitsOnly
                val entered = digitsOnly.toIntOrNull()
                if (entered != null && selectedSurah != null && entered in 1..selectedSurah.ayahCount) {
                    viewModel.pickPreviewVerse(selectedSurah.id, entered)
                    viewModel.setFixedVerse(selectedSurah.id, entered)
                }
            },
            label = { Text(stringResource(R.string.wallpapers_pick_ayah)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = ayahError,
            supportingText = if (ayahError) {
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
        text = stringResource(R.string.wallpapers_selection_mode),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    listOf(
        SelectionMode.RANDOM to R.string.wallpapers_selection_random,
        SelectionMode.RANDOM_FROM_SELECTION to R.string.wallpapers_selection_from_selection,
        SelectionMode.SEQUENTIAL_FROM_SELECTION to R.string.wallpapers_selection_sequential,
        SelectionMode.DAILY to R.string.wallpapers_selection_daily,
    ).chunked(2).forEach { rowModes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            rowModes.forEach { (mode, labelRes) ->
                FilterChip(
                    selected = state.settings.selectionMode == mode,
                    onClick = { viewModel.setSelectionMode(mode) },
                    label = { Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (rowModes.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (showSurahSheet && state.surahs.isNotEmpty()) {
        AyahSelectionSheet(
            title = stringResource(R.string.wallpapers_pick_surah),
            options = state.surahs,
            selected = selectedSurah ?: state.surahs.first(),
            labelFor = { "${it.id} · ${it.localizedDisplayName()}" },
            onSelect = { surah ->
                val clampedAyah = state.settings.fixedAyahNumber.coerceIn(1, surah.ayahCount)
                viewModel.setFixedVerse(surah.id, clampedAyah)
                viewModel.pickPreviewVerse(surah.id, clampedAyah)
            },
            onDismiss = { showSurahSheet = false },
        )
    }
}

@Composable
private fun PinnedApplyBar(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = AyahTouchTargets.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val destinations = listOf(
                        WallpaperDestination.SYSTEM to R.string.wallpapers_dest_home,
                        WallpaperDestination.LOCK to R.string.wallpapers_dest_lock,
                        WallpaperDestination.BOTH to R.string.wallpapers_dest_both,
                    )
                    destinations.forEachIndexed { index, (destination, labelRes) ->
                        SegmentedButton(
                            selected = state.settings.destinations == destination,
                            onClick = { viewModel.setDestinations(destination) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = destinations.size,
                            ),
                            label = { Text(stringResource(labelRes), maxLines = 1) },
                        )
                    }
                }

                AyahPrimaryButton(
                    text = if (state.applying) {
                        stringResource(R.string.wallpapers_apply_working)
                    } else {
                        stringResource(R.string.wallpapers_apply_now)
                    },
                    onClick = viewModel::applyPreviewVerse,
                    enabled = !state.applying && state.previewVerse != null,
                    loading = state.applying,
                    modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = state.applyOutcome != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    state.applyOutcome?.let { outcome ->
                        val success = outcome == WallpaperApplyStatus.SUCCESS
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Filled.Check else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (success) {
                                    stringResource(
                                        R.string.wallpapers_apply_success,
                                        wallpaperDestinationLabel(state.settings.destinations),
                                    )
                                } else {
                                    stringResource(
                                        R.string.wallpapers_apply_failed,
                                        state.history.firstOrNull()?.entry?.failureReason ?: "-",
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (success) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun intervalLabelRes(minutes: Int): Int =
    if (minutes < 720) R.string.wallpapers_interval_minutes else R.string.wallpapers_interval_hours

private fun intervalLabelValue(minutes: Int): Int =
    if (minutes < 720) minutes else minutes / 60

@Composable
private fun rememberLocalizedTimeFormatter(): DateTimeFormatter {
    val locale = Locale.getDefault()
    return remember(locale) {
        DateTimeFormatter.ofPattern("MMM d · HH:mm", locale)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomationSection(state: WallpapersUiState, viewModel: WallpapersViewModel) {
    val timeFormatter = rememberLocalizedTimeFormatter()
    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = stringResource(R.string.wallpapers_auto_updates),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val statusText = if (state.scheduleStatus.isScheduled) {
                val minutes = state.scheduleStatus.intervalMinutes ?: state.settings.intervalMinutes
                stringResource(
                    R.string.wallpapers_auto_every_format,
                    stringResource(intervalLabelRes(minutes), intervalLabelValue(minutes)),
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AyahSpacing.s))
                Switch(
                    checked = state.settings.autoUpdateEnabled,
                    onCheckedChange = viewModel::setAutoUpdateEnabled,
                )
            }

            if (state.settings.autoUpdateEnabled) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                ) {
                    INTERVAL_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = state.settings.intervalMinutes == minutes,
                            onClick = { viewModel.setInterval(minutes) },
                            label = {
                                Text(
                                    stringResource(
                                        intervalLabelRes(minutes),
                                        intervalLabelValue(minutes),
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            Text(
                text = state.lastAppliedAtMillis?.let {
                    stringResource(
                        R.string.wallpapers_last_applied_format,
                        timeFormatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())),
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
}

@Composable
private fun HistorySection(
    state: WallpapersUiState,
    viewModel: WallpapersViewModel,
    applying: Boolean,
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val timeFormatter = rememberLocalizedTimeFormatter()

    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.wallpapers_history_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.history.isNotEmpty()) {
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
            } else {
                state.history.forEachIndexed { index, item ->
                    HistoryRow(
                        item = item,
                        timeFormatter = timeFormatter,
                        applying = applying,
                        onReapply = { viewModel.reapply(item) },
                        onDelete = { viewModel.deleteHistoryEntry(item) },
                    )
                    if (index != state.history.lastIndex) {
                        HorizontalDivider(
                            thickness = AyahTouchTargets.hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
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
    timeFormatter: DateTimeFormatter,
    applying: Boolean,
    onReapply: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = item.entry
    val thumbShape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AyahSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 72.dp)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, thumbShape)
                .border(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant, thumbShape),
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
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.wallpapers_history_ayah_format,
                    stringResource(labelResForId(entry.templateId)),
                    entry.surahId,
                    entry.ayahNumber,
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeFormatter.format(Instant.ofEpochMilli(entry.appliedAtMillis).atZone(ZoneId.systemDefault())) +
                    " · " + wallpaperDestinationLabel(entry.destinations) + " · " +
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextButton(onClick = onReapply, enabled = !applying) {
            Text(stringResource(R.string.wallpapers_history_reapply))
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.history_delete_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
