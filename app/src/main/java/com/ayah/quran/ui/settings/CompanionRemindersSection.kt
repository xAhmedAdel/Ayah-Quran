package com.ayah.quran.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.component.ConfirmDialog
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.data.prayer.PermissionIntents
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionRemindersSection(
    modifier: Modifier = Modifier,
    viewModel: CompanionRemindersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var pendingEnableType by remember { mutableStateOf<CompanionReminderType?>(null) }
    var showPermissionExplanation by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf<CompanionReminderConfig?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshPermissionStatus()
        val type = pendingEnableType
        pendingEnableType = null
        if (granted && type != null) {
            viewModel.setReminderEnabled(type, true)
        }
    }

    fun openNotificationSettings() {
        runCatching {
            context.startActivity(PermissionIntents.appNotificationSettings(context))
        }.onFailure {
            runCatching { context.startActivity(PermissionIntents.appDetails(context)) }
        }
    }

    fun handleToggle(type: CompanionReminderType, targetEnabled: Boolean) {
        if (!targetEnabled) {
            viewModel.setReminderEnabled(type, false)
            return
        }
        viewModel.refreshPermissionStatus()
        if (state.notificationsAllowed) {
            viewModel.setReminderEnabled(type, true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !state.preferences.permissionRequestedBefore
        ) {
            pendingEnableType = type
            showPermissionExplanation = true
        } else {
            openNotificationSettings()
        }
    }

    val anyEnabled = state.preferences.allConfigs().any { it.enabled }
    val showDeniedBanner = !state.notificationsAllowed &&
        (state.preferences.permissionRequestedBefore || anyEnabled)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_reminders_section"),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
    ) {
        if (showDeniedBanner) {
            AyahBannerStrip(
                message = stringResource(R.string.reminders_permission_denied_banner),
                tone = AyahBannerTone.WARNING,
                actionLabel = stringResource(R.string.reminders_permission_open_settings),
                onAction = ::openNotificationSettings,
                modifier = Modifier.testTag("reminders_permission_denied_banner"),
            )
        }

        AyahGroupCard(title = stringResource(R.string.reminders_section_title)) {
            val configs = state.preferences.allConfigs()
            configs.forEachIndexed { index, config ->
                val icon = when (config.type) {
                    CompanionReminderType.MORNING_ADHKAR -> Icons.Outlined.WbSunny
                    CompanionReminderType.EVENING_ADHKAR -> Icons.Outlined.NightsStay
                    CompanionReminderType.DAILY_REFLECTION -> Icons.Outlined.AutoAwesome
                }
                val title = stringResource(
                    when (config.type) {
                        CompanionReminderType.MORNING_ADHKAR -> R.string.reminders_morning_title
                        CompanionReminderType.EVENING_ADHKAR -> R.string.reminders_evening_title
                        CompanionReminderType.DAILY_REFLECTION -> R.string.reminders_reflection_title
                    },
                )
                val formatted = AyahNumbering.isolateLtr(config.formattedTime())
                val subtitle = stringResource(
                    if (config.enabled) R.string.reminders_status_on else R.string.reminders_status_off,
                    formatted,
                )

                AyahSettingRow(
                    title = title,
                    subtitle = subtitle,
                    leadingIcon = icon,
                    showChevron = false,
                    onClick = { timePickerTarget = config },
                    trailing = {
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { checked -> handleToggle(config.type, checked) },
                            modifier = Modifier.testTag("reminder_switch_${config.type.key}"),
                        )
                    },
                    modifier = Modifier.testTag("reminder_row_${config.type.key}"),
                )

                if (index != configs.lastIndex) {
                    AyahInsetDivider(startIndent = 64.dp)
                }
            }
        }
    }

    if (showPermissionExplanation) {
        ConfirmDialog(
            title = stringResource(R.string.reminders_permission_title),
            message = stringResource(R.string.reminders_permission_message),
            confirmText = stringResource(R.string.reminders_permission_allow),
            onConfirm = {
                showPermissionExplanation = false
                viewModel.markPermissionRequested()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = {
                showPermissionExplanation = false
                pendingEnableType = null
            },
        )
    }

    timePickerTarget?.let { target ->
        val pickerState = rememberTimePickerState(
            initialHour = target.hour,
            initialMinute = target.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(stringResource(R.string.reminders_time_picker_title)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setReminderTime(target.type, pickerState.hour, pickerState.minute)
                        timePickerTarget = null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = AyahTouchTargets.min)
                        .testTag("reminder_time_save"),
                ) {
                    Text(stringResource(R.string.tasbih_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { timePickerTarget = null },
                    modifier = Modifier.defaultMinSize(minHeight = AyahTouchTargets.min),
                ) {
                    Text(stringResource(R.string.tasbih_cancel))
                }
            },
        )
    }
}
