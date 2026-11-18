package com.ayah.quran.ui.prayer

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.prayer.AdhanPlaybackService
import com.ayah.quran.prayer.DiagnosticAlarmScheduler
import com.ayah.quran.data.prayer.PermissionIntents
import com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels
import com.ayah.quran.domain.model.prayer.PrayerStatusBoard
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection

/**
 * Phase 14 §6 — "Prayer Notifications & Reliability" dashboard: every row is
 * a REAL verified status (refreshed on resume and after returning from
 * system settings), each with its own action. Includes the two independent
 * diagnostic actions and the 60-second diagnostic alarm.
 */
@Composable
fun PermissionsDashboardSection(
    permissionsViewModel: PrayerPermissionsViewModel,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val board by permissionsViewModel.board.collectAsStateWithLifecycle()

    // Re-verify when the user returns from Android settings.
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

    SectionCard(title = stringResource(R.string.prayer_dashboard_title)) {
        StatusRow(
            label = stringResource(R.string.prayer_dashboard_notifications_row),
            ok = board.notificationsGranted,
        )
        StatusRow(
            label = stringResource(R.string.prayer_channel_row),
            ok = board.adhanChannelEnabled,
        )
        StatusRow(
            label = stringResource(R.string.prayer_dashboard_exact_row),
            ok = board.exactAlarmsGranted,
        )
        StatusRow(
            label = stringResource(R.string.prayer_dashboard_background_row),
            ok = board.vendorGuidanceAcknowledged,
            // Vendor settings cannot be auto-verified — honest labeling.
            successOnlyWhenVerified = false,
            pendingText = stringResource(R.string.prayer_dashboard_background_needs_attention),
            doneText = stringResource(R.string.prayer_dashboard_background_done),
        )
        StatusRow(
            label = stringResource(R.string.prayer_dashboard_adhan_mode_row),
            ok = board.adhanConfig.let { config ->
                listOf(
                    config.fajr, config.dhuhr, config.asr, config.maghrib, config.isha,
                ).any { it != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED }
            },
        )

        // ---- actions ----
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
        ActionRow(stringResource(R.string.prayer_dashboard_open_battery_settings)) {
            runCatching { context.startActivity(PermissionIntents.appDetails(context)) }
        }

        // ---- independent diagnostics ----
        ActionRow(stringResource(R.string.prayer_test_notification_button)) {
            permissionsViewModel.testNotification()
        }
        ActionRow(stringResource(R.string.prayer_test_adhan_button)) {
            permissionsViewModel.testAdhan(context)
        }
        var scheduledText by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(false)
        }
        ActionRow(stringResource(R.string.prayer_test_diagnostic_button)) {
            permissionsViewModel.scheduleDiagnosticAlarm()
            scheduledText = true
        }
        if (scheduledText) {
            Text(
                text = stringResource(R.string.prayer_test_diagnostic_scheduled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AyahSpacing.s, top = AyahSpacing.xs),
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    successOnlyWhenVerified: Boolean = true,
    pendingText: String? = null,
    doneText: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = AyahSpacing.s).size(18.dp),
        )
        Text(
            text = when {
                ok -> doneText
                    ?: stringResource(R.string.prayer_status_enabled)

                successOnlyWhenVerified -> pendingText
                    ?: stringResource(R.string.prayer_status_not_enabled)

                else -> pendingText
                    ?: stringResource(R.string.prayer_status_not_enabled)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (ok) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
