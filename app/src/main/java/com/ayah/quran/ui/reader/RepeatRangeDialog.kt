package com.ayah.quran.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.data.audio.QuranRepeatRange

@Composable
fun RepeatRangeDialog(
    initialFromAyah: Int,
    maxAyahCount: Int,
    hasActiveRepeatRange: Boolean,
    onStartRepeat: (QuranRepeatRange) -> Unit,
    onClearRepeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val safeMax = maxAyahCount.coerceAtLeast(1)
    val safeStart = initialFromAyah.coerceIn(1, safeMax)
    val defaultEnd = (safeStart + 4).coerceAtMost(safeMax)
    var fromText by rememberSaveable(safeStart) { mutableStateOf(safeStart.toString()) }
    var toText by rememberSaveable(safeStart, safeMax) { mutableStateOf(defaultEnd.toString()) }

    val fromVal = fromText.trim().toIntOrNull()
    val toVal = toText.trim().toIntOrNull()
    val valid = fromVal != null &&
        toVal != null &&
        fromVal in 1..safeMax &&
        toVal in fromVal..safeMax

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_repeat_range)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { value -> fromText = value.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.reader_repeat_from)) },
                        singleLine = true,
                        isError = fromText.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { value -> toText = value.filter { it.isDigit() } },
                        label = { Text(stringResource(R.string.reader_repeat_to)) },
                        singleLine = true,
                        isError = toText.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!valid && (fromText.isNotEmpty() || toText.isNotEmpty())) {
                    Text(
                        text = stringResource(R.string.reader_repeat_invalid, safeMax),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (hasActiveRepeatRange) {
                    TextButton(
                        onClick = {
                            onClearRepeat()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(R.string.reader_repeat_clear))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fromVal != null && toVal != null && valid) {
                        onStartRepeat(QuranRepeatRange(fromVal, toVal))
                    }
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.reader_repeat_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
