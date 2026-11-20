package com.ayah.quran.ui.companion.adhkar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahHeroCard
import com.ayah.quran.core.designsystem.component.AyahOutlinedButton
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahStepProgress
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.motion.rememberMotionEnabled
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.LocalAyahColors
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarSessionScreen(
    onBack: () -> Unit,
    onOpenSurahInMushaf: (surahId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdhkarSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDayKey()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val title = state.category?.let { localizedCategoryTitle(it) } ?: stringResource(R.string.adhkar_title)
    val copiedMessage = stringResource(R.string.adhkar_copied)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    if (state.rows.isNotEmpty()) {
                        IconButton(onClick = viewModel::toggleListViewMode) {
                            Icon(
                                imageVector = if (state.listViewMode) {
                                    Icons.Outlined.ViewCarousel
                                } else {
                                    Icons.AutoMirrored.Outlined.FormatListBulleted
                                },
                                contentDescription = stringResource(
                                    if (state.listViewMode) R.string.adhkar_focus_view else R.string.adhkar_list_view,
                                ),
                            )
                        }
                        IconButton(
                            onClick = { showResetConfirm = true },
                            enabled = state.completedCount > 0 || state.rows.any { it.currentCount > 0 },
                            modifier = Modifier.testTag("adhkar_session_reset"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = stringResource(R.string.adhkar_reset_session),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading || state.rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val stepDesc = stringResource(
                R.string.adhkar_step_a11y,
                state.completedCount,
                state.rows.size,
            )
            AyahStepProgress(
                currentStep = state.completedCount,
                totalSteps = state.rows.size,
                label = AyahNumbering.isolateLtr("${state.currentIndex + 1} / ${state.rows.size}"),
                modifier = Modifier
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s)
                    .semantics { contentDescription = stepDesc },
            )

            when {
                state.listViewMode -> {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(AyahSpacing.m),
                        verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    ) {
                        itemsIndexed(state.rows, key = { _, r -> r.item.id }) { idx, row ->
                            SessionOverviewRow(
                                index = idx,
                                row = row,
                                selected = idx == state.currentIndex,
                                onClick = {
                                    viewModel.selectPage(idx)
                                    viewModel.toggleListViewMode()
                                },
                            )
                        }
                    }
                }

                state.allCompleted -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(AyahSpacing.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        SessionCompleteCard(
                            onBackToHub = onBack,
                            onRepeat = { showResetConfirm = true },
                        )
                    }
                }

                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = state.currentIndex,
                        pageCount = { state.rows.size },
                    )

                    LaunchedEffect(state.currentIndex) {
                        if (pagerState.currentPage != state.currentIndex) {
                            if (motionEnabled) {
                                pagerState.animateScrollToPage(state.currentIndex)
                            } else {
                                pagerState.scrollToPage(state.currentIndex)
                            }
                        }
                    }

                    LaunchedEffect(pagerState.currentPage) {
                        if (pagerState.currentPage != state.currentIndex) {
                            viewModel.selectPage(pagerState.currentPage)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { page ->
                        val row = state.rows[page]
                        DhikrReadingPage(
                            row = row,
                            fontScale = state.fontScale,
                            onAdjustFont = viewModel::adjustFontScale,
                            onToggleFavorite = { viewModel.toggleFavorite(row.item.id) },
                            onCopy = {
                                copyDhikrText(context, row.item.textAr, localizedItemSource(row.item))
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            },
                            onOpenSurah = row.item.surahId?.let { surahId ->
                                { onOpenSurahInMushaf(surahId) }
                            },
                        )
                    }

                    val activeRow = state.rows[state.currentIndex]
                    SessionCountingDock(
                        row = activeRow,
                        canUndo = state.canUndo,
                        canPrevious = state.currentIndex > 0,
                        canNext = state.currentIndex < state.rows.lastIndex,
                        onIncrement = {
                            val willComplete = activeRow.currentCount + 1 >= activeRow.targetCount
                            haptic.performHapticFeedback(
                                if (willComplete) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                            )
                            viewModel.incrementCurrent(
                                autoAdvanceDelayMs = if (motionEnabled) {
                                    AdhkarSessionViewModel.AUTO_ADVANCE_DELAY_MS
                                } else {
                                    0L
                                },
                            )
                        },
                        onUndo = viewModel::undo,
                        onPrevious = viewModel::stepPrevious,
                        onNext = viewModel::stepNext,
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.adhkar_reset_confirm_title),
            message = stringResource(R.string.adhkar_reset_confirm_message),
            confirmText = stringResource(R.string.adhkar_reset_confirm_action),
            onConfirm = {
                viewModel.resetSession()
                showResetConfirm = false
            },
            onDismiss = { showResetConfirm = false },
        )
    }
}

@Composable
private fun DhikrReadingPage(
    row: SessionDhikrRow,
    fontScale: Float,
    onAdjustFont: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onOpenSurah: (() -> Unit)?,
) {
    val extended = LocalAyahColors.current
    val item = row.item
    var virtueExpanded by remember(item.id) { mutableStateOf(false) }
    val sourceText = localizedItemSource(item)
    val virtueText = (if (AppLocale.isArabic) item.virtueAr else (item.virtueEn ?: item.virtueAr))
        ?.takeIf { it.isNotBlank() && it.trim() != sourceText.trim() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
    ) {
        Surface(
            shape = RoundedCornerShape(AyahRadii.card),
            color = extended.mushafCanvas,
            border = BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.ml),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AyahStatusBadge(
                        label = AyahNumbering.isolateLtr("${row.currentCount} / ${row.targetCount}"),
                        kind = if (row.completed) AyahStatusKind.SUCCESS else AyahStatusKind.GOLD,
                        icon = if (row.completed) Icons.Filled.Check else null,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onAdjustFont(-0.1f) },
                            contentPadding = PaddingValues(horizontal = AyahSpacing.s),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = AyahNumbering.isolateLtr("A-"),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        TextButton(
                            onClick = { onAdjustFont(0.1f) },
                            contentPadding = PaddingValues(horizontal = AyahSpacing.s),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = AyahNumbering.isolateLtr("A+"),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                Text(
                    text = item.textAr,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = (22f * fontScale).sp,
                        lineHeight = (38f * fontScale).sp,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!item.textEn.isNullOrBlank() && !AppLocale.isArabic) {
                    HorizontalDivider(
                        thickness = AyahTouchTargets.hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = item.textEn,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = localizedItemSource(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!virtueText.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AyahTouchTargets.min)
                                .clickable { virtueExpanded = !virtueExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.adhkar_virtue_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = if (virtueExpanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        AnimatedVisibility(visible = virtueExpanded) {
                            Text(
                                text = virtueText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AyahOutlinedButton(
                        text = stringResource(R.string.adhkar_copy),
                        onClick = onCopy,
                        icon = Icons.Outlined.ContentCopy,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .defaultMinSize(minWidth = AyahTouchTargets.min, minHeight = AyahTouchTargets.min)
                            .testTag("adhkar_session_favorite"),
                    ) {
                        Icon(
                            imageVector = if (row.isFavorite) {
                                AyahIcons.BookmarkFilled
                            } else {
                                AyahIcons.BookmarkOutline
                            },
                            contentDescription = stringResource(
                                if (row.isFavorite) R.string.adhkar_unfavorite else R.string.adhkar_favorite,
                            ),
                            tint = if (row.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (onOpenSurah != null) {
                    AyahOutlinedButton(
                        text = stringResource(R.string.adhkar_open_in_mushaf),
                        onClick = onOpenSurah,
                        icon = AyahIcons.MushafMode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCountingDock(
    row: SessionDhikrRow,
    canUndo: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onIncrement: () -> Unit,
    onUndo: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val a11yCount = stringResource(R.string.adhkar_counter_a11y, row.currentCount, row.targetCount)
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
                    .navigationBarsPadding()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                Button(
                    onClick = onIncrement,
                    enabled = !row.completed,
                    shape = RoundedCornerShape(AyahRadii.pill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .testTag("adhkar_count_button")
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = a11yCount
                        },
                ) {
                    if (row.completed) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(AyahSpacing.s))
                    }
                    Text(
                        text = when {
                            row.completed -> stringResource(R.string.adhkar_item_completed)
                            row.targetCount == 1 -> stringResource(R.string.adhkar_mark_read)
                            else -> stringResource(R.string.adhkar_tap_to_count)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(AyahSpacing.sm))
                    Text(
                        text = AyahNumbering.isolateLtr("${row.currentCount}/${row.targetCount}"),
                        style = MaterialTheme.typography.labelLarge.withTabularNumbers(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier
                            .defaultMinSize(minHeight = AyahTouchTargets.min)
                            .testTag("adhkar_undo_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AyahSpacing.xs))
                        Text(stringResource(R.string.adhkar_undo))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs)) {
                        TextButton(
                            onClick = onPrevious,
                            enabled = canPrevious,
                            modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
                        ) {
                            Text(stringResource(R.string.adhkar_previous))
                        }
                        TextButton(
                            onClick = onNext,
                            enabled = canNext,
                            modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
                        ) {
                            Text(stringResource(R.string.adhkar_next))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCompleteCard(
    onBackToHub: () -> Unit,
    onRepeat: () -> Unit,
) {
    val tokens = AyahThemeTokens.colors
    AyahHeroCard(modifier = Modifier.testTag("adhkar_session_complete")) {
        Text(
            text = stringResource(R.string.adhkar_session_complete_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = QuranTextFontFamily),
            color = tokens.heroOnSurface,
        )
        Text(
            text = stringResource(R.string.adhkar_session_complete_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.heroMuted,
        )
        Spacer(Modifier.height(AyahSpacing.s))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Button(
                onClick = onBackToHub,
                shape = RoundedCornerShape(AyahRadii.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.heroAccent,
                    contentColor = tokens.heroSurfaceDeep,
                ),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = AyahTouchTargets.min),
            ) {
                Text(stringResource(R.string.adhkar_back_to_hub))
            }
            TextButton(
                onClick = onRepeat,
                modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
            ) {
                Text(
                    text = stringResource(R.string.adhkar_session_repeat),
                    color = tokens.heroOnSurface,
                )
            }
        }
    }
}

@Composable
private fun SessionOverviewRow(
    index: Int,
    row: SessionDhikrRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AyahSurfaceCard(
        onClick = onClick,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Text(
                text = AyahNumbering.isolateLtr("${index + 1}"),
                style = MaterialTheme.typography.labelLarge.withTabularNumbers(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = localizedItemTitle(row.item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AyahStatusBadge(
                label = AyahNumbering.isolateLtr("${row.currentCount}/${row.targetCount}"),
                kind = if (row.completed) AyahStatusKind.SUCCESS else AyahStatusKind.NEUTRAL,
                icon = if (row.completed) Icons.Filled.Check else null,
            )
        }
    }
}

internal fun copyDhikrText(context: Context, text: String, source: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val formatted = "$text\n\n— $source"
    clipboard.setPrimaryClip(ClipData.newPlainText("Ayah Dhikr", formatted))
}
