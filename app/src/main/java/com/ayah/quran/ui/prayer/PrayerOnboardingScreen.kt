package com.ayah.quran.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahStepProgress
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.prayer.PermissionIntents
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.PrayerNotificationsChannels
import com.ayah.quran.domain.model.prayer.VendorKind

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    val step = viewModel.nextStep(board)
    val adhanAllConfigured = board.adhanConfig.let { config ->
        config.fajr != AdhanMode.DISABLED &&
            config.dhuhr != AdhanMode.DISABLED &&
            config.asr != AdhanMode.DISABLED &&
            config.maghrib != AdhanMode.DISABLED &&
            config.isha != AdhanMode.DISABLED
    }
    val completedSteps = listOf(
        board.notificationsGranted,
        board.adhanChannelEnabled,
        board.exactAlarmsGranted,
        board.vendorGuidanceAcknowledged,
        adhanAllConfigured,
    ).count { it }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prayer_onboarding_title)) },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(
                            imageVector = AyahIcons.Back,
                            contentDescription = stringResource(R.string.prayer_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            AyahStepProgress(
                currentStep = completedSteps,
                totalSteps = 5,
                label = stringResource(R.string.prayer_onboarding_title),
            )

            Text(
                text = stringResource(R.string.prayer_onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OnboardingStepCard(
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

            OnboardingStepCard(
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
                                PrayerNotificationsChannels.ALERT,
                            ),
                        )
                    }
                },
            )

            OnboardingStepCard(
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

            OnboardingStepCard(
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

            OnboardingStepCard(
                index = 5,
                active = step == 5,
                done = adhanAllConfigured,
                title = stringResource(R.string.prayer_onboarding_adhan_mode_title),
                description = stringResource(R.string.prayer_onboarding_adhan_mode_desc),
                actionLabel = stringResource(R.string.prayer_onboarding_open_adhan_settings),
                onAction = {
                    viewModel.setOnboardingCompleted()
                    onOpenAdhanSettings()
                },
            )

            if (board.readyForPrayerNotifications) {
                AyahBannerStrip(
                    message = stringResource(R.string.prayer_onboarding_ready),
                    tone = AyahBannerTone.INFO,
                )
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
                val intents = PermissionIntents.vendorAppLaunchCandidates(context)
                for (candidate in intents) {
                    if (runCatching { context.startActivity(candidate) }.isSuccess) {
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

@Composable
private fun OnboardingStepCard(
    index: Int,
    active: Boolean,
    done: Boolean,
    title: String,
    description: String,
    recommendedOnly: Boolean = false,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    val border = when {
        active -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else -> BorderStroke(AyahTouchTargets.hairline, MaterialTheme.colorScheme.outlineVariant)
    }
    val container = when {
        active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AyahRadii.l),
        color = container,
        border = border,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = AyahIcons.StatusSuccess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelLarge.withTabularNumbers(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AyahStatusBadge(
                    label = when {
                        done -> stringResource(R.string.prayer_status_enabled)
                        recommendedOnly -> stringResource(R.string.prayer_status_recommended)
                        else -> stringResource(R.string.prayer_status_not_enabled)
                    },
                    kind = when {
                        done -> AyahStatusKind.SUCCESS
                        recommendedOnly -> AyahStatusKind.WARNING
                        else -> AyahStatusKind.NEUTRAL
                    },
                )
            }
            if (!done || active) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actionLabel?.takeIf { active || (!done && recommendedOnly) }?.let { label ->
                if (active) {
                    Button(
                        onClick = onAction,
                        shape = RoundedCornerShape(AyahRadii.pill),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(AyahRadii.pill),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

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
            "من إعدادات البطارية: اسمح للتطبيق بالعمل في الخلفية",
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
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.prayer_onboarding_open_vendor))
            }
        },
        dismissButton = {
            TextButton(onClick = onAcknowledge) {
                Text(stringResource(R.string.prayer_onboarding_vendor_done))
            }
        },
    )
}
