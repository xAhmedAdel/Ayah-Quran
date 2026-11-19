package com.ayah.quran.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.BuildConfig
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.update.AppUpdateInfo
import com.ayah.quran.data.update.AppUpdateInstaller
import com.ayah.quran.data.update.AppUpdateState
import com.ayah.quran.domain.model.ThemeMode

/**
 * Settings screen. Appearance (Phase 1) and app language (Phase 10) are fully
 * functional; wallpaper and data sections join in later phases.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.languageApplied.collect {
            (context.findActivity() as? Activity)?.recreate()
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
        SectionHeader(text = stringResource(R.string.settings_appearance))

        AyahCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
            ) {
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_system),
                    selected = themeMode == ThemeMode.SYSTEM,
                    onSelect = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_light),
                    selected = themeMode == ThemeMode.LIGHT,
                    onSelect = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                )
                ThemeOptionRow(
                    label = stringResource(R.string.settings_theme_dark),
                    selected = themeMode == ThemeMode.DARK,
                    onSelect = { viewModel.setThemeMode(ThemeMode.DARK) },
                )
            }
        }

        SectionHeader(text = stringResource(R.string.settings_language))

        AyahCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.s),
            ) {
                LanguageOptionRow(
                    label = stringResource(R.string.settings_language_arabic),
                    selected = languageTag == AppLocale.ARABIC,
                    onSelect = { viewModel.setLanguage(AppLocale.ARABIC) },
                )
                LanguageOptionRow(
                    label = stringResource(R.string.settings_language_english),
                    selected = languageTag == AppLocale.ENGLISH,
                    onSelect = { viewModel.setLanguage(AppLocale.ENGLISH) },
                )
            }
        }

        SectionHeader(text = stringResource(R.string.settings_about))

        AyahCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                AboutRow(
                    label = stringResource(R.string.settings_version),
                    value = BuildConfig.VERSION_NAME,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        UpdatesSection(viewModel)

        AyahCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AyahSpacing.m),
                verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
            ) {
                Text(
                    text = stringResource(R.string.settings_licenses),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_licenses_summary),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = AyahSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            .padding(vertical = AyahSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.m),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Unwraps [ContextWrapper]s until the owning activity, if any, is found. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun AboutRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * In-app updater (GitHub Releases): auto-checks once per process when the
 * section becomes visible, then drives check → download → install states.
 */
@Composable
private fun UpdatesSection(viewModel: SettingsViewModel) {
    val update by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // One silent check per app process; manual re-checks stay available.
    LaunchedEffect(Unit) { viewModel.checkForUpdate() }

    SectionHeader(text = stringResource(R.string.settings_updates))

    AyahCard {
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is AppUpdateState.Downloaded -> {
                    Text(
                        text = stringResource(
                            R.string.settings_updates_ready, state.info.tagName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Button(
                        onClick = { AppUpdateInstaller.installOrGuide(context, state.apkFile) },
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

            // Manual re-check is always available (force = true).
            TextButton(onClick = { viewModel.checkForUpdate(force = true) }) {
                Text(stringResource(R.string.settings_updates_check))
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
        text = stringResource(R.string.settings_updates_available, info.tagName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(
            R.string.settings_updates_size,
            formatUpdateSize(info.apkSizeBytes),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = { viewModel.downloadUpdate(info) }) {
        Text(stringResource(R.string.settings_updates_download))
    }
}

private fun formatUpdateSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "%d B".format(bytes)
}
