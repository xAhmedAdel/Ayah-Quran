package com.ayah.quran.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.PermissionIntents
import com.ayah.quran.domain.model.prayer.PrayerStatusBoard
import com.ayah.quran.domain.model.prayer.VendorKind

/**
 * First-launch permission onboarding (Phase 14 §2): a SEQUENTIAL checklist —
 * notifications, exact alarms, vendor background settings, adhan config —
 * each with live verified status and one action at a time. Skippable, with
 * an honest warning that alerts may not be reliable until enabled. The
 * "ready" banner appears only when statuses are ACTUALLY verified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerOnboardingScreen(
    onSkip: () -> Unit,
    onOpenAdhanSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrayerPermissionsViewModel = hiltViewModel(),
) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showVendorGuide by remember { mutableStateOf(false) }

    // Refresh verified statuses when returning from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    val step = viewModel.nextStep(board)

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.prayer_onboarding_title)) })
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            Text(
                text = stringResource(R.string.prayer_onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Step 1 — notifications.
            OnboardingItem(
                index = 1,
                active = step == 1,
                done = board.notificationsGranted,
                title = stringResource(R.string.prayer_onboarding_notifications_title),
                description = stringResource(R.string.prayer_onboarding_notifications_desc),
                actionLabel = if (board.notificationsGranted) {
                    null
                } else {
                    stringResource(R.string.prayer_onboarding_enable)
                },
                onAction = {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )

            // Step 2 — adhan notification channel (auto-checked).
            OnboardingItem(
                index = 2,
                active = step == 2,
                done = board.adhanChannelEnabled,
                title = stringResource(R.string.prayer_onboarding_channel_title),
                description = stringResource(R.string.prayer_onboarding_channel_desc),
                actionLabel = if (board.adhanChannelEnabled) {
                    null
                } else {
                    stringResource(R.string.prayer_onboarding_open_channel)
                },
                onAction = {
                    runCatching {
                        context.startActivity(
                            PermissionIntents.channelSettings(
                                context,
                                com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels.ALERT,
                            ),
                        )
                    }
                },
            )

            // Step 3 — exact alarms.
            OnboardingItem(
                index = 3,
                active = step == 3,
                done = board.exactAlarmsGranted,
                title = stringResource(R.string.prayer_onboarding_exact_title),
                description = stringResource(R.string.prayer_onboarding_exact_desc),
                actionLabel = if (board.exactAlarmsGranted) {
                    null
                } else {
                    stringResource(R.string.prayer_onboarding_enable)
                },
                onAction = {
                    runCatching {
                        context.startActivity(PermissionIntents.exactAlarmSettings(context))
                    }
                },
            )

            // Step 4 — vendor background settings (recommended, not verifiable).
            OnboardingItem(
                index = 4,
                active = step == 4,
                done = board.vendorGuidanceAcknowledged,
                recommendedOnly = true,
                title = stringResource(
                    when (board.vendor) {
                        VendorKind.HONOR, VendorKind.HUAWEI -> R.string.prayer_onboarding_vendor_honor_title
                        else -> R.string.prayer_onboarding_vendor_generic_title
                    },
                ),
                description = stringResource(R.string.prayer_onboarding_vendor_desc),
                actionLabel = stringResource(R.string.prayer_onboarding_open_vendor),
                onAction = { showVendorGuide = true },
            )

            // Step 5 — adhan mode configuration.
            OnboardingItem(
                index = 5,
                active = step == 5,
                done = board.adhanConfig.let { config ->
                    config.fajr != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED &&
                        config.dhuhr != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED &&
                        config.asr != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED &&
                        config.maghrib != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED &&
                        config.isha != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED
                },
                title = stringResource(R.string.prayer_onboarding_adhan_mode_title),
                description = stringResource(R.string.prayer_onboarding_adhan_mode_desc),
                actionLabel = stringResource(R.string.prayer_onboarding_open_adhan_settings),
                onAction = {
                    viewModel.setOnboardingCompleted()
                    onOpenAdhanSettings()
                },
            )

            // Ready banner — only after VERIFIED essential statuses.
            if (board.readyForPrayerNotifications) {
                AyahCard {
                    Text(
                        text = stringResource(R.string.prayer_onboarding_ready),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(AyahSpacing.m),
                    )
                }
            }

            Text(
                text = stringResource(R.string.prayer_onboarding_skip_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = {
                    viewModel.setOnboardingCompleted()
                    onSkip()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.prayer_onboarding_skip))
            }
        }
    }

    if (showVendorGuide) {
        VendorGuidanceDialog(
            vendor = board.vendor,
            onOpenSettings = {
                // Best-effort vendor intents; always falls back safely.
                val intents = PermissionIntents.vendorAppLaunchCandidates(context)
                for (candidate in intents) {
                    if (runCatching {
                            context.startActivity(candidate)
                        }.isSuccess
                    ) {
                        break
                    }
                }
            },
            onAcknowledge = {
                viewModel.acknowledgeVendorGuidance()
                viewModel.refresh()
                showVendorGuide = false
            },
            onDismiss = { showVendorGuide = false },
        )
    }
}

/** One checklist row; only the CURRENT step exposes its action button. */
@Composable
private fun OnboardingItem(
    index: Int,
    active: Boolean,
    done: Boolean,
    title: String,
    description: String,
    recommendedOnly: Boolean = false,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.prayer_onboarding_step_format, index, title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = when {
                    done -> stringResource(R.string.prayer_status_enabled)
                    recommendedOnly -> stringResource(R.string.prayer_status_recommended)
                    else -> stringResource(R.string.prayer_status_not_enabled)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AyahSpacing.s),
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actionLabel?.takeIf { active || done == false && recommendedOnly }?.let { label ->
            if (active) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }
        }
    }
}

/** HONOR/MagicOS (or generic) step-by-step background settings guide. */
@Composable
fun VendorGuidanceDialog(
    vendor: VendorKind,
    onOpenSettings: () -> Unit,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val arabic = AppLocale.isArabic
    val honorSteps = if (arabic) {
        listOf(
            "افتح إعدادات الجهاز ← التطبيقات ← تشغيل التطبيقات ← آية",
            "أطفئ «إدارة تلقائياً»",
            "فعّل: التشغيل التلقائي، التشغيل الثانوي، التشغيل في الخلفية",
            "من إعدادات البطارية: اسمح لل تطبيق بالعمل في الخلفية",
        )
    } else {
        listOf(
            "Open device Settings → Apps → App launch → Ayah",
            "Turn off \"Manage automatically\"",
            "Enable: Auto-launch, Secondary launch, Run in background",
            "In battery settings: allow Ayah to run in the background",
        )
    }
    val genericSteps = if (arabic) {
        listOf(
            "افتح إعدادات الجهاز ← التطبيقات ← آية",
            "البطارية ← اسمح بالعمل في الخلفية / أزل قيود البطارية",
            "تأكد أن الإشعارات مفعّلة",
        )
    } else {
        listOf(
            "Open device Settings → Apps → Ayah",
            "Battery → Allow background activity / remove battery restrictions",
            "Make sure notifications are enabled",
        )
    }
    val steps = when (vendor) {
        VendorKind.HONOR, VendorKind.HUAWEI -> honorSteps
        else -> genericSteps
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prayer_onboarding_vendor_guide_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.prayer_onboarding_vendor_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onOpenSettings()
            }) { Text(stringResource(R.string.prayer_onboarding_open_vendor)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onAcknowledge()
            }) { Text(stringResource(R.string.prayer_onboarding_vendor_done)) }
        },
    )
}
