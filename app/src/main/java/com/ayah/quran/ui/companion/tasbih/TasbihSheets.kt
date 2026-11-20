package com.ayah.quran.ui.companion.tasbih

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.companion.TasbihCounter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasbihPresetsSheet(
    counters: List<TasbihCounter>,
    activeCounterId: Long?,
    onSelect: (Long) -> Unit,
    onAddCustom: () -> Unit,
    onEditCustom: (TasbihCounter) -> Unit,
    onDeleteCustom: (TasbihCounter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = remember(counters) { counters.filter { !it.isCustom } }
    val custom = remember(counters) { counters.filter { it.isCustom } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tasbih_presets_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = onAddCustom,
                    shape = RoundedCornerShape(AyahRadii.pill),
                    modifier = Modifier
                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                        .testTag("tasbih_add_custom"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(AyahSpacing.xs))
                    Text(stringResource(R.string.tasbih_add_custom))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = AyahSpacing.m,
                    vertical = AyahSpacing.s,
                ),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                if (presets.isNotEmpty()) {
                    item(key = "presets_hdr") {
                        SectionHeader(text = stringResource(R.string.tasbih_presets_section))
                    }
                    items(presets, key = { it.id }) { counter ->
                        TasbihCounterRow(
                            counter = counter,
                            selected = counter.id == activeCounterId,
                            onSelect = { onSelect(counter.id) },
                            onEdit = null,
                            onDelete = null,
                        )
                        HorizontalDivider(
                            thickness = AyahTouchTargets.hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }

                if (custom.isNotEmpty()) {
                    item(key = "custom_hdr") {
                        Spacer(Modifier.height(AyahSpacing.s))
                        SectionHeader(text = stringResource(R.string.tasbih_custom_section))
                    }
                    items(custom, key = { it.id }) { counter ->
                        TasbihCounterRow(
                            counter = counter,
                            selected = counter.id == activeCounterId,
                            onSelect = { onSelect(counter.id) },
                            onEdit = { onEditCustom(counter) },
                            onDelete = { onDeleteCustom(counter) },
                        )
                        HorizontalDivider(
                            thickness = AyahTouchTargets.hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TasbihCounterRow(
    counter: TasbihCounter,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val countBadge = if (counter.targetCount > 0) {
        AyahNumbering.isolateLtr("${counter.currentCount} / ${counter.targetCount}")
    } else {
        AyahNumbering.isolateLtr(counter.currentCount.toString())
    }
    val rowTag = counter.stableKey?.let { "tasbih_preset_$it" } ?: "tasbih_custom_${counter.id}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AyahTouchTargets.min)
            .clickable(onClick = onSelect)
            .padding(vertical = AyahSpacing.sm)
            .testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = counter.dhikrTextAr,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = QuranTextFontFamily,
                    textDirection = TextDirection.Rtl,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!AppLocale.isArabic && counter.titleEn.isNotBlank() && counter.titleEn != counter.dhikrTextAr) {
                Text(
                    text = counter.titleEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AyahStatusBadge(
            label = countBadge,
            kind = if (selected) AyahStatusKind.SUCCESS else AyahStatusKind.NEUTRAL,
            icon = if (selected) Icons.Filled.Check else null,
        )

        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("tasbih_edit_${counter.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.tasbih_edit_custom),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("tasbih_delete_${counter.id}"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.tasbih_delete_custom),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun CustomDhikrDialog(
    initialCounter: TasbihCounter?,
    onSave: (arabicText: String, englishLabel: String, targetCount: Int) -> TasbihValidationError?,
    onDismiss: () -> Unit,
) {
    var arabicText by remember(initialCounter?.id) { mutableStateOf(initialCounter?.dhikrTextAr ?: "") }
    var englishLabel by remember(initialCounter?.id) {
        val initialEn = initialCounter?.titleEn.orEmpty()
        mutableStateOf(if (initialEn == initialCounter?.dhikrTextAr) "" else initialEn)
    }
    var goalText by remember(initialCounter?.id) {
        mutableStateOf((initialCounter?.targetCount ?: 33).toString())
    }
    var error by remember { mutableStateOf<TasbihValidationError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initialCounter == null) R.string.tasbih_add_custom else R.string.tasbih_edit_custom,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
            ) {
                OutlinedTextField(
                    value = arabicText,
                    onValueChange = {
                        arabicText = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.tasbih_arabic_label)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = QuranTextFontFamily,
                        textDirection = TextDirection.Rtl,
                    ),
                    isError = error == TasbihValidationError.EMPTY_TEXT ||
                        error == TasbihValidationError.TEXT_TOO_LONG ||
                        error == TasbihValidationError.DUPLICATE_DHIKR,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasbih_custom_ar_input"),
                )

                OutlinedTextField(
                    value = englishLabel,
                    onValueChange = {
                        englishLabel = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.tasbih_english_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasbih_custom_en_input"),
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs)) {
                    items(TasbihViewModel.COMMON_GOALS) { presetGoal ->
                        val selected = goalText.trim().toIntOrNull() == presetGoal
                        FilterChip(
                            selected = selected,
                            onClick = {
                                goalText = presetGoal.toString()
                                error = null
                            },
                            label = {
                                Text(
                                    if (presetGoal == 0) {
                                        stringResource(R.string.tasbih_goal_none)
                                    } else {
                                        AyahNumbering.isolateLtr(presetGoal.toString())
                                    },
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = goalText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            goalText = input
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.tasbih_goal_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error == TasbihValidationError.INVALID_GOAL,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasbih_custom_goal_input"),
                )

                error?.let { err ->
                    Text(
                        text = validationErrorMessage(err),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("tasbih_custom_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedGoal = if (goalText.isBlank()) 0 else goalText.trim().toIntOrNull() ?: -1
                    val validationError = onSave(arabicText, englishLabel, parsedGoal)
                    if (validationError == null) {
                        onDismiss()
                    } else {
                        error = validationError
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = AyahTouchTargets.min)
                    .testTag("tasbih_custom_save"),
            ) {
                Text(stringResource(R.string.tasbih_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
            ) {
                Text(stringResource(R.string.tasbih_cancel))
            }
        },
    )
}

@Composable
fun CustomGoalDialog(
    initialGoal: Int,
    onSave: (Int) -> TasbihValidationError?,
    onDismiss: () -> Unit,
) {
    var goalText by remember(initialGoal) {
        mutableStateOf(if (initialGoal > 0) initialGoal.toString() else "")
    }
    var error by remember { mutableStateOf<TasbihValidationError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasbih_custom_goal_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 5) {
                            goalText = input
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.tasbih_custom_goal_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tasbih_goal_input"),
                )
                error?.let { err ->
                    Text(
                        text = validationErrorMessage(err),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = goalText.trim().toIntOrNull() ?: -1
                    val err = if (parsed !in 1..TasbihViewModel.MAX_GOAL) {
                        TasbihValidationError.INVALID_GOAL
                    } else {
                        onSave(parsed)
                    }
                    if (err == null) {
                        onDismiss()
                    } else {
                        error = err
                    }
                },
                modifier = Modifier
                    .defaultMinSize(minHeight = AyahTouchTargets.min)
                    .testTag("tasbih_goal_save"),
            ) {
                Text(stringResource(R.string.tasbih_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
            ) {
                Text(stringResource(R.string.tasbih_cancel))
            }
        },
    )
}

@Composable
private fun validationErrorMessage(error: TasbihValidationError): String = when (error) {
    TasbihValidationError.EMPTY_TEXT -> stringResource(R.string.tasbih_error_empty_text)
    TasbihValidationError.TEXT_TOO_LONG -> stringResource(
        R.string.tasbih_error_text_too_long,
        TasbihViewModel.MAX_DHIKR_LENGTH,
    )
    TasbihValidationError.DUPLICATE_DHIKR -> stringResource(R.string.tasbih_error_duplicate)
    TasbihValidationError.INVALID_GOAL -> stringResource(R.string.tasbih_error_invalid_goal)
}
