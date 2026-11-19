package com.ayah.quran.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.MushafSurahBanner
import com.ayah.quran.core.designsystem.component.ReaderAyahActionPill
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.locale.localizedDisplayName
import com.ayah.quran.core.locale.revelationLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onNavigateSurah: (Int) -> Unit,
    onSwitchToMushaf: (surahId: Int, ayah: Int) -> Unit,
    modifier: Modifier = Modifier,
    onCreateWallpaper: ((surahId: Int, ayah: Int) -> Unit)? = null,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoScroll by viewModel.autoScroll.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isSelectedBookmarked.collectAsStateWithLifecycle()
    val repeatRange by viewModel.repeatRange.collectAsStateWithLifecycle()
    var showFontControls by remember { mutableStateOf(false) }
    var showRepeatDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val extendedColors = LocalAyahColors.current

    LaunchedEffect(state.ayahs, viewModel.targetAyah) {
        if (state.ayahs.isNotEmpty() && viewModel.targetAyah in 1..state.ayahs.size) {
            scrollState.scrollTo(viewModel.targetAyah * 40)
        }
    }

    LaunchedEffect(state.playingAyah, autoScroll) {
        val ayah = state.playingAyah ?: return@LaunchedEffect
        if (autoScroll && ayah in 1..state.ayahs.size) {
            scrollState.animateScrollTo(ayah * 40)
        }
    }

    val selectedAyah = state.selectedAyah
    if (showRepeatDialog && selectedAyah != null) {
        val maxAyahs = state.surah?.ayahCount ?: state.ayahs.size.coerceAtLeast(1)
        RepeatRangeDialog(
            initialFromAyah = selectedAyah,
            maxAyahCount = maxAyahs,
            hasActiveRepeatRange = repeatRange != null,
            onStartRepeat = { range ->
                showRepeatDialog = false
                viewModel.startRepeatRange(range)
                viewModel.clearSelection()
            },
            onClearRepeat = viewModel::clearRepeatRange,
            onDismiss = { showRepeatDialog = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(extendedColors.mushafCanvas),
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = extendedColors.mushafCanvas,
            ),
            title = {
                Text(
                    text = state.surah?.let { surah ->
                        stringResource(R.string.reader_surah_format, surah.localizedDisplayName())
                    } ?: stringResource(R.string.quran_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = AyahIcons.Back,
                        contentDescription = stringResource(R.string.reader_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { showFontControls = !showFontControls },
                    modifier = Modifier.testTag("reader_font_controls_button"),
                ) {
                    Icon(
                        imageVector = AyahIcons.FontSize,
                        contentDescription = stringResource(R.string.reader_font_size_cd),
                    )
                }
                IconButton(onClick = viewModel::toggleAutoScroll) {
                    Icon(
                        imageVector = AyahIcons.AutoFollowRecitation,
                        contentDescription = stringResource(R.string.reader_auto_scroll_cd),
                        tint = if (autoScroll) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.setReadingMode(com.ayah.quran.domain.model.ReadingMode.MUSHAF)
                        onSwitchToMushaf(viewModel.surahId, viewModel.targetAyah.coerceAtLeast(1))
                    },
                    modifier = Modifier.testTag("reader_switch_mushaf"),
                ) {
                    Icon(
                        imageVector = AyahIcons.MushafMode,
                        contentDescription = stringResource(R.string.mushaf_mode_cd),
                    )
                }
            },
        )

        HorizontalDivider(
            thickness = AyahTouchTargets.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(AyahSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            state.isNotFound -> Text(
                text = stringResource(R.string.reader_surah_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(AyahSpacing.xl),
                textAlign = TextAlign.Center,
            )

            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                val surah = state.surah
                if (surah != null) {
                    val juzLabel = if (state.juzs.size <= 1) {
                        "${state.juzs.firstOrNull() ?: 1}"
                    } else {
                        "${state.juzs.first()}–${state.juzs.last()}"
                    }
                    val metaText = stringResource(
                        R.string.reader_meta_format,
                        stringResource(revelationLabel(surah.revelationType)),
                        surah.ayahCount,
                        juzLabel,
                        state.firstPage,
                        state.lastPage,
                    )
                    MushafSurahBanner(
                        surahNameArabic = surah.nameArabic,
                        subtitle = metaText,
                        bismillah = state.bismillahHeader,
                    )
                }

                AnimatedVisibility(visible = showFontControls) {
                    FontSizeControls(
                        fontScale = state.fontScale,
                        autoScroll = autoScroll,
                        onScaleChanged = viewModel::setFontScale,
                        onToggleAutoScroll = viewModel::toggleAutoScroll,
                        onClose = { showFontControls = false },
                    )
                }

                MushafText(
                    ayahs = state.ayahs.map {
                        MushafAyahData(
                            surahId = viewModel.surahId,
                            ayahNumber = it.ayahNumber,
                            displayText = it.displayText,
                        )
                    },
                    highlightSurahId = viewModel.surahId,
                    highlightAyahNumber = state.selectedAyah ?: state.playingAyah,
                    fontSize = (state.fontScale * 26).sp,
                    onAyahTap = { range -> viewModel.onAyahTap(range.ayahNumber) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.sm, vertical = AyahSpacing.s),
                )

                Spacer(Modifier.height(AyahSpacing.l))
            }
        }

        AnimatedVisibility(visible = selectedAyah != null) {
            if (selectedAyah != null) {
                val surahLabel = state.surah?.localizedDisplayName() ?: ""
                val refLabel = "$surahLabel · ${AyahNumbering.isolateLtr("${viewModel.surahId}:$selectedAyah")}"
                ReaderAyahActionPill(
                    ayahReferenceLabel = refLabel,
                    isBookmarked = isBookmarked,
                    onPlay = {
                        viewModel.playFromAyah(selectedAyah)
                        viewModel.clearSelection()
                    },
                    onToggleBookmark = { viewModel.toggleBookmark(selectedAyah) },
                    onRepeatRange = { showRepeatDialog = true },
                    onCreateWallpaper = onCreateWallpaper?.let { cb ->
                        { cb(viewModel.surahId, selectedAyah) }
                    },
                    onDismiss = viewModel::clearSelection,
                    modifier = Modifier.padding(
                        horizontal = AyahSpacing.m,
                        vertical = AyahSpacing.xs,
                    ),
                )
            }
        }

        HorizontalDivider(
            thickness = AyahTouchTargets.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        ReaderSurahNavBar(
            surahId = viewModel.surahId,
            surahTitle = state.surah?.localizedDisplayName() ?: "",
            onPrevSurah = { onNavigateSurah(viewModel.surahId - 1) },
            onNextSurah = { onNavigateSurah(viewModel.surahId + 1) },
        )
    }
}

@Composable
private fun ReaderSurahNavBar(
    surahId: Int,
    surahTitle: String,
    onPrevSurah: () -> Unit,
    onNextSurah: () -> Unit,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.l, vertical = AyahSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevSurah, enabled = surahId > 1) {
            Icon(
                imageVector = AyahIcons.mushafPreviousArrow(isRtl),
                contentDescription = stringResource(R.string.reader_prev_surah),
            )
        }
        Surface(
            shape = RoundedCornerShape(AyahRadii.pill),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = if (surahTitle.isNotBlank()) {
                    "$surahTitle · ${AyahNumbering.isolateLtr("$surahId / 114")}"
                } else {
                    AyahNumbering.isolateLtr("$surahId / 114")
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
            )
        }
        IconButton(onClick = onNextSurah, enabled = surahId < 114) {
            Icon(
                imageVector = AyahIcons.mushafNextArrow(isRtl),
                contentDescription = stringResource(R.string.reader_next_surah),
            )
        }
    }
}

@Composable
private fun FontSizeControls(
    fontScale: Float,
    autoScroll: Boolean,
    onScaleChanged: (Float) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onClose: () -> Unit,
) {
    AyahSurfaceCard(modifier = Modifier.testTag("reader_font_controls_card")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.reader_font_size_cd),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("reader_close_font_controls"),
                ) {
                    Icon(
                        imageVector = AyahIcons.Close,
                        contentDescription = stringResource(R.string.reader_close_font_controls_cd),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fontScale,
                    onValueChange = onScaleChanged,
                    valueRange = 0.5f..2.0f,
                    steps = 14,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AyahSpacing.m),
                )
                Text(
                    text = "A",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.reader_auto_scroll_cd),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { onToggleAutoScroll() },
                )
            }
        }
    }
}
