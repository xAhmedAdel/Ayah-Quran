package com.ayah.quran.ui.companion.tasbih

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.motion.rememberMotionEnabled
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.TasbihCounter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TasbihViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val motionEnabled = rememberMotionEnabled()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showPresetsSheet by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingCounter by remember { mutableStateOf<TasbihCounter?>(null) }
    var deletingCounter by remember { mutableStateOf<TasbihCounter?>(null) }
    var showCustomGoalDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDayKey()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            performSafeHaptic(
                haptic = haptic,
                type = when (event) {
                    TasbihFeedbackEvent.GOAL_REACHED -> HapticFeedbackType.LongPress
                    TasbihFeedbackEvent.TAP -> HapticFeedbackType.TextHandleMove
                },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasbih_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
                actions = {
                    val hapticsDesc = stringResource(
                        if (state.hapticsEnabled) R.string.tasbih_haptics_on else R.string.tasbih_haptics_off,
                    )
                    IconButton(
                        onClick = { viewModel.setHapticsEnabled(!state.hapticsEnabled) },
                        modifier = Modifier
                            .testTag("tasbih_haptics_toggle")
                            .semantics { contentDescription = hapticsDesc },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Vibration,
                            contentDescription = null,
                            tint = if (state.hapticsEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                        )
                    }
                    IconButton(
                        onClick = { showPresetsSheet = true },
                        modifier = Modifier.testTag("tasbih_presets_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            contentDescription = stringResource(R.string.tasbih_presets_title),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val activeCounter = state.activeCounter
        if (state.loading || activeCounter == null) {
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
            ) {
                ActiveDhikrCard(
                    counter = activeCounter,
                    onClick = { showPresetsSheet = true },
                )

                GoalSelectorRow(
                    currentTarget = activeCounter.targetCount,
                    onSelectGoal = { goal -> viewModel.setActiveGoal(goal) },
                    onOpenCustomGoal = { showCustomGoalDialog = true },
                )

                TasbihTapSurface(
                    counter = activeCounter,
                    todayTotal = state.todayTotal,
                    goalReached = state.goalReached,
                    remainingToGoal = state.remainingToGoal,
                    progressFraction = state.progressFraction,
                    motionEnabled = motionEnabled,
                    onTap = viewModel::increment,
                )
            }

            TasbihUtilityDock(
                canUndo = activeCounter.currentCount > 0,
                canReset = activeCounter.currentCount > 0,
                canCycle = state.counters.size > 1,
                onUndo = viewModel::undo,
                onNextPreset = viewModel::selectNextCounter,
                onReset = { showResetConfirm = true },
            )
        }
    }

    if (showPresetsSheet) {
        TasbihPresetsSheet(
            counters = state.counters,
            activeCounterId = state.activeCounter?.id,
            onSelect = { id ->
                viewModel.selectCounter(id)
                showPresetsSheet = false
            },
            onAddCustom = {
                editingCounter = null
                showCustomDialog = true
            },
            onEditCustom = { counter ->
                editingCounter = counter
                showCustomDialog = true
            },
            onDeleteCustom = { counter ->
                deletingCounter = counter
            },
            onDismiss = { showPresetsSheet = false },
        )
    }

    if (showCustomDialog) {
        val editing = editingCounter
        CustomDhikrDialog(
            initialCounter = editing,
            onSave = { ar, en, target ->
                if (editing == null) {
                    val err = viewModel.createCustomDhikr(ar, en, target)
                    if (err == null) showPresetsSheet = false
                    err
                } else {
                    viewModel.updateCustomDhikr(editing.id, ar, en, target)
                }
            },
            onDismiss = {
                showCustomDialog = false
                editingCounter = null
            },
        )
    }

    if (showCustomGoalDialog) {
        val active = state.activeCounter
        if (active != null) {
            CustomGoalDialog(
                initialGoal = active.targetCount,
                onSave = { goal -> viewModel.setActiveGoal(goal) },
                onDismiss = { showCustomGoalDialog = false },
            )
        }
    }

    if (showResetConfirm) {
        val active = state.activeCounter
        if (active != null) {
            ConfirmDialog(
                title = stringResource(R.string.tasbih_reset_confirm_title),
                message = stringResource(R.string.tasbih_reset_confirm_message, active.dhikrTextAr),
                confirmText = stringResource(R.string.tasbih_reset_confirm_action),
                onConfirm = {
                    viewModel.resetActive()
                    showResetConfirm = false
                },
                onDismiss = { showResetConfirm = false },
            )
        }
    }

    deletingCounter?.let { target ->
        val hasProgress = target.currentCount > 0 || target.lifetimeCount > 0L || target.completedRounds > 0
        val progressSummary = AyahNumbering.isolateLtr(
            maxOf(target.currentCount.toLong(), target.lifetimeCount).toString(),
        )
        ConfirmDialog(
            title = stringResource(R.string.tasbih_delete_confirm_title),
            message = if (hasProgress) {
                stringResource(R.string.tasbih_delete_confirm_progress_message, progressSummary)
            } else {
                stringResource(R.string.tasbih_delete_confirm_message)
            },
            confirmText = stringResource(R.string.tasbih_delete_confirm_action),
            onConfirm = {
                viewModel.deleteCustomDhikr(target.id)
                deletingCounter = null
            },
            onDismiss = { deletingCounter = null },
        )
    }
}

@Composable
private fun ActiveDhikrCard(
    counter: TasbihCounter,
    onClick: () -> Unit,
) {
    AyahSurfaceCard(
        onClick = onClick,
        modifier = Modifier.testTag("tasbih_active_selector"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
            ) {
                Text(
                    text = counter.dhikrTextAr,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = QuranTextFontFamily,
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasbih_active_dhikr_text"),
                )
                if (!AppLocale.isArabic && counter.titleEn.isNotBlank() && counter.titleEn != counter.dhikrTextAr) {
                    Text(
                        text = counter.titleEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = stringResource(R.string.tasbih_change_dhikr),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalSelectorRow(
    currentTarget: Int,
    onSelectGoal: (Int) -> Unit,
    onOpenCustomGoal: () -> Unit,
) {
    val isCustomGoal = currentTarget > 0 && currentTarget !in TasbihViewModel.COMMON_GOALS
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
    ) {
        items(TasbihViewModel.COMMON_GOALS) { goal ->
            FilterChip(
                selected = currentTarget == goal,
                onClick = { onSelectGoal(goal) },
                label = {
                    Text(
                        text = if (goal == 0) {
                            stringResource(R.string.tasbih_goal_none)
                        } else {
                            AyahNumbering.isolateLtr(goal.toString())
                        },
                        maxLines = 1,
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = AyahTouchTargets.min)
                    .testTag("tasbih_goal_$goal"),
            )
        }
        item(key = "custom_goal") {
            FilterChip(
                selected = isCustomGoal,
                onClick = onOpenCustomGoal,
                label = {
                    Text(
                        text = if (isCustomGoal) {
                            AyahNumbering.isolateLtr(currentTarget.toString())
                        } else {
                            stringResource(R.string.tasbih_goal_custom)
                        },
                        maxLines = 1,
                    )
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = AyahTouchTargets.min)
                    .testTag("tasbih_goal_custom"),
            )
        }
    }
}

@Composable
private fun TasbihUtilityDock(
    canUndo: Boolean,
    canReset: Boolean,
    canCycle: Boolean,
    onUndo: () -> Unit,
    onNextPreset: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = AyahTouchTargets.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                        .testTag("tasbih_undo_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AyahSpacing.xs))
                    Text(stringResource(R.string.tasbih_undo))
                }

                TextButton(
                    onClick = onNextPreset,
                    enabled = canCycle,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                        .testTag("tasbih_next_preset"),
                ) {
                    Text(stringResource(R.string.tasbih_next_preset))
                    Spacer(Modifier.width(AyahSpacing.xs))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }

                TextButton(
                    onClick = onReset,
                    enabled = canReset,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                        .testTag("tasbih_reset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AyahSpacing.xs))
                    Text(stringResource(R.string.tasbih_reset))
                }
            }
        }
    }
}

internal fun performSafeHaptic(
    haptic: HapticFeedback,
    type: HapticFeedbackType,
) {
    runCatching {
        haptic.performHapticFeedback(type)
    }
}
