package com.ayah.quran.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.BuildConfig
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSelectionSheet
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahTouchTargets
import com.ayah.quran.core.designsystem.theme.ObsidianCanvas
import com.ayah.quran.core.designsystem.theme.ParchmentCanvas
import com.ayah.quran.core.designsystem.theme.SanctuaryEmerald
import com.ayah.quran.core.designsystem.theme.Gold
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.update.AppUpdateInstaller
import com.ayah.quran.data.update.AppUpdateState
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.update.AppUpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenPrayerSettings: (() -> Unit)? = null,
    onOpenAudio: (() -> Unit)? = null,
    onOpenWallpapers: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    hubViewModel: SettingsHubViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val summary by hubViewModel.summary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isArabic = AppLocale.isArabic
    var showReadingSheet by remember { mutableStateOf(false) }
    var showLicensesSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.languageApplied.collect {
            context.findActivity()?.recreate()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = AyahIcons.Back,
                                contentDescription = stringResource(R.string.reader_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                SectionHeader(text = stringResource(R.string.settings_appearance))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
                    ThemePreviewCard(
                        mode = ThemeMode.SYSTEM,
                        label = stringResource(R.string.settings_theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemePreviewCard(
                        mode = ThemeMode.LIGHT,
                        label = stringResource(R.string.settings_theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemePreviewCard(
                        mode = ThemeMode.DARK,
                        label = stringResource(R.string.settings_theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AyahGroupCard(title = stringResource(R.string.settings_language)) {
                LanguageOptionRow(
                    label = stringResource(R.string.settings_language_arabic),
                    selected = languageTag == AppLocale.ARABIC,
                    onSelect = { viewModel.setLanguage(AppLocale.ARABIC) },
                )
                AyahInsetDivider()
                LanguageOptionRow(
                    label = stringResource(R.string.settings_language_english),
                    selected = languageTag == AppLocale.ENGLISH,
                    onSelect = { viewModel.setLanguage(AppLocale.ENGLISH) },
                )
            }

            AyahGroupCard(title = stringResource(R.string.settings_preferences_section)) {
                val modeLabel = stringResource(
                    if (summary.readingMode == ReadingMode.MUSHAF) {
                        R.string.settings_reading_mode_mushaf
                    } else {
                        R.string.settings_reading_mode_continuous
                    },
                )
                AyahSettingRow(
                    title = stringResource(R.string.settings_reading_title),
                    subtitle = stringResource(
                        R.string.settings_reading_summary,
                        modeLabel,
                        AyahNumbering.isolateLtr("${summary.fontScalePercent}%"),
                    ),
                    leadingIcon = AyahIcons.MushafMode,
                    onClick = { showReadingSheet = true },
                    modifier = Modifier.testTag("settings_reading_row"),
                )
                if (onOpenPrayerSettings != null) {
                    AyahInsetDivider(startIndent = 64.dp)
                    val cityLabel = (if (isArabic) summary.prayerCityAr else summary.prayerCityEn)
                        ?: stringResource(R.string.settings_prayer_not_set)
                    AyahSettingRow(
                        title = stringResource(R.string.prayer_settings_title),
                        subtitle = cityLabel,
                        leadingIcon = AyahIcons.Prayer,
                        onClick = onOpenPrayerSettings,
                    )
                }
                if (onOpenAudio != null) {
                    AyahInsetDivider(startIndent = 64.dp)
                    val reciter = if (isArabic) summary.reciterNameAr else summary.reciterNameEn
                    AyahSettingRow(
                        title = stringResource(R.string.settings_audio_title),
                        subtitle = "$reciter · ${AyahNumbering.isolateLtr("${summary.playbackSpeed}x")}",
                        leadingIcon = AyahIcons.Audio,
                        onClick = onOpenAudio,
                    )
                }
                if (onOpenWallpapers != null) {
                    AyahInsetDivider(startIndent = 64.dp)
                    val wpSub = if (summary.wallpaperSchedule.isScheduled &&
                        summary.wallpaperSchedule.intervalMinutes != null
                    ) {
                        stringResource(
                            R.string.home_wallpaper_automation_on,
                            summary.wallpaperSchedule.intervalMinutes ?: 60,
                        )
                    } else {
                        stringResource(R.string.home_wallpaper_automation_off)
                    }
                    AyahSettingRow(
                        title = stringResource(R.string.wallpapers_title),
                        subtitle = wpSub,
                        leadingIcon = AyahIcons.Wallpaper,
                        onClick = onOpenWallpapers,
                    )
                }
            }

            CompanionRemindersSection()

            UpdatesSection(viewModel)

            AyahGroupCard(title = stringResource(R.string.settings_about)) {
                AyahSettingRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = stringResource(R.string.app_tagline),
                    showChevron = false,
                    trailing = {
                        Text(
                            text = AyahNumbering.isolateLtr(BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("settings_version_value"),
                        )
                    },
                )
                AyahInsetDivider()
                AyahSettingRow(
                    title = stringResource(R.string.settings_licenses),
                    onClick = { showLicensesSheet = true },
                    modifier = Modifier.testTag("settings_licenses_row"),
                    trailing = {
                        Text(
                            text = stringResource(R.string.settings_licenses_view),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    if (showReadingSheet) {
        AyahSelectionSheet(
            title = stringResource(R.string.settings_reading_title),
            options = ReadingMode.entries,
            selected = summary.readingMode,
            labelFor = { mode ->
                stringResource(
                    if (mode == ReadingMode.MUSHAF) {
                        R.string.settings_reading_mode_mushaf
                    } else {
                        R.string.settings_reading_mode_continuous
                    },
                )
            },
            onSelect = hubViewModel::setReadingMode,
            onDismiss = { showReadingSheet = false },
        )
    }

    if (showLicensesSheet) {
        LicensesSheet(onDismiss = { showLicensesSheet = false })
    }
}

@Composable
private fun ThemePreviewCard(
    mode: ThemeMode,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(AyahRadii.m))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AyahRadii.m),
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else AyahTouchTargets.hairline, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AyahSpacing.s, vertical = AyahSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            ThemeThumb(mode = mode)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThemeThumb(mode: ThemeMode) {
    val shape = RoundedCornerShape(AyahRadii.xs)
    Row(
        modifier = Modifier
            .width(56.dp)
            .height(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .padding(1.dp)
            .clip(shape),
    ) {
        when (mode) {
            ThemeMode.SYSTEM -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ParchmentCanvas),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanctuaryEmerald),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ObsidianCanvas),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Gold),
                    )
                }
            }

            ThemeMode.LIGHT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ParchmentCanvas)
                        .padding(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SanctuaryEmerald),
                    )
                }
            }

            ThemeMode.DARK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianCanvas)
                        .padding(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Gold),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = AyahRadii.sheet, topEnd = AyahRadii.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AyahSpacing.ml, vertical = AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.settings_licenses),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_licenses_summary),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun UpdatesSection(viewModel: SettingsViewModel) {
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
        SectionHeader(text = stringResource(R.string.settings_updates))

        AyahSurfaceCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                when (val state = update) {
                    is AppUpdateState.Idle, AppUpdateState.Checking -> {
                        Text(
                            text = stringResource(R.string.settings_updates_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is AppUpdateState.UpToDate -> {
                        Text(
                            text = stringResource(R.string.settings_updates_uptodate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AppUpdateState.NoReleases -> {
                        Text(
                            text = stringResource(R.string.settings_updates_noreleases),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is AppUpdateState.Available -> UpdateAvailableRows(
                        info = state.info,
                        viewModel = viewModel,
                    )

                    is AppUpdateState.Downloading -> {
                        Text(
                            text = stringResource(R.string.settings_updates_downloading) +
                                " " + "%d%%".format((state.progress * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                        )
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(AyahRadii.pill)),
                        )
                    }

                    is AppUpdateState.Downloaded -> {
                        Text(
                            text = stringResource(
                                R.string.settings_updates_ready,
                                AyahNumbering.isolateLtr(state.info.tagName),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Button(
                            onClick = {
                                val expectedSize = state.info.apkSizeBytes.takeIf { it > 0L }
                                val result = AppUpdateInstaller.installOrGuide(
                                    context = context,
                                    apk = state.apkFile,
                                    expectedByteCount = expectedSize,
                                )
                                if (result is com.ayah.quran.data.update.AppUpdateInstallResult.Rejected) {
                                    viewModel.reportInstallValidationFailure(result.reason)
                                }
                            },
                            shape = RoundedCornerShape(AyahRadii.pill),
                        ) {
                            Text(stringResource(R.string.settings_updates_install))
                        }
                    }

                    is AppUpdateState.Failed -> {
                        Text(
                            text = stringResource(
                                R.string.settings_updates_error,
                                state.reason ?: "",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                TextButton(onClick = { viewModel.checkForUpdate(force = true) }) {
                    Text(stringResource(R.string.settings_updates_check))
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableRows(
    info: AppUpdateInfo,
    viewModel: SettingsViewModel,
) {
    Text(
        text = stringResource(
            R.string.settings_updates_available,
            AyahNumbering.isolateLtr(info.tagName),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(
            R.string.settings_updates_size,
            AyahNumbering.isolateLtr(formatUpdateSize(info.apkSizeBytes)),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { viewModel.downloadUpdate(info) },
        shape = RoundedCornerShape(AyahRadii.pill),
    ) {
        Text(stringResource(R.string.settings_updates_download))
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "%d B".format(bytes)
}
