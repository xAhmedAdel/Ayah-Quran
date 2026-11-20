package com.ayah.quran.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.data.prayer.PermissionIntents
import com.ayah.quran.domain.model.prayer.AdhanMode

@Composable
fun PermissionsDashboardSection(
    permissionsViewModel: PrayerPermissionsViewModel,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val board by permissionsViewModel.board.collectAsStateWithLifecycle()
    var diagnosticsExpanded by remember { mutableStateOf(false) }
    var scheduledText by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionsViewModel.refresh() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        AyahGroupCard(title = stringResource(R.string.prayer_settings_advanced)) {
            Text(
                text = stringResource(R.string.prayer_dashboard_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = AyahSpacing.m,
                    end = AyahSpacing.m,
                    top = AyahSpacing.sm,
                    bottom = AyahSpacing.xs,
                ),
            )
            StatusRow(
                label = stringResource(R.string.prayer_dashboard_notifications_row),
                ok = board.notificationsGranted,
            )
            AyahInsetDivider()
            StatusRow(
                label = stringResource(R.string.prayer_channel_row),
                ok = board.adhanChannelEnabled,
            )
            AyahInsetDivider()
            StatusRow(
                label = stringResource(R.string.prayer_dashboard_exact_row),
                ok = board.exactAlarmsGranted,
            )
            AyahInsetDivider()
            StatusRow(
                label = stringResource(R.string.prayer_dashboard_background_row),
                ok = board.vendorGuidanceAcknowledged,
                pendingText = stringResource(R.string.prayer_dashboard_background_needs_attention),
                doneText = stringResource(R.string.prayer_dashboard_background_done),
                warnOnly = true,
            )
            AyahInsetDivider()
            val anyAdhanEnabled = board.adhanConfig.let { config ->
                listOf(config.fajr, config.dhuhr, config.asr, config.maghrib, config.isha)
                    .any { it != AdhanMode.DISABLED }
            }
            StatusRow(
                label = stringResource(R.string.prayer_dashboard_adhan_mode_row),
                ok = anyAdhanEnabled,
            )
            AyahInsetDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                if (!board.notificationsGranted) {
                    ActionRow(stringResource(R.string.prayer_dashboard_enable_notifications)) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    ActionRow(stringResource(R.string.prayer_dashboard_open_notification_settings)) {
                        runCatching {
                            context.startActivity(PermissionIntents.appNotificationSettings(context))
                        }
                    }
                }
                if (!board.exactAlarmsGranted) {
                    ActionRow(stringResource(R.string.prayer_dashboard_allow_exact)) {
                        runCatching {
                            context.startActivity(PermissionIntents.exactAlarmSettings(context))
                        }
                    }
                }
                if (!board.vendorGuidanceAcknowledged) {
                    ActionRow(stringResource(R.string.prayer_onboarding_open_vendor)) {
                        onOpenOnboarding()
                    }
                }
                Text(
                    text = stringResource(R.string.prayer_battery_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AyahInsetDivider()
            AyahSettingRow(
                title = stringResource(R.string.prayer_diagnostics_section),
                leadingIcon = AyahIcons.Tune,
                onClick = { diagnosticsExpanded = !diagnosticsExpanded },
            )
            AnimatedVisibility(visible = diagnosticsExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = AyahSpacing.m,
                            end = AyahSpacing.m,
                            bottom = AyahSpacing.m,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    ActionRow(stringResource(R.string.prayer_onboarding_title)) {
                        onOpenOnboarding()
                    }
                    ActionRow(stringResource(R.string.prayer_dashboard_open_battery_settings)) {
                        runCatching { context.startActivity(PermissionIntents.appDetails(context)) }
                    }
                    ActionRow(stringResource(R.string.prayer_test_notification_button)) {
                        permissionsViewModel.testNotification()
                    }
                    ActionRow(stringResource(R.string.prayer_test_adhan_button)) {
                        permissionsViewModel.testAdhan(context)
                    }
                    ActionRow(stringResource(R.string.prayer_test_diagnostic_button)) {
                        permissionsViewModel.scheduleDiagnosticAlarm()
                        scheduledText = true
                    }
                    if (scheduledText) {
                        Text(
                            text = stringResource(R.string.prayer_test_diagnostic_scheduled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    pendingText: String? = null,
    doneText: String? = null,
    warnOnly: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        AyahStatusBadge(
            label = if (ok) {
                doneText ?: stringResource(R.string.prayer_status_enabled)
            } else {
                pendingText ?: stringResource(R.string.prayer_status_not_enabled)
            },
            kind = when {
                ok -> AyahStatusKind.SUCCESS
                warnOnly -> AyahStatusKind.WARNING
                else -> AyahStatusKind.ERROR
            },
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(AyahRadii.pill),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
