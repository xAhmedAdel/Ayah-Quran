package com.ayah.quran.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.BuildConfig
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.domain.model.ThemeMode

/**
 * Settings screen. Appearance is fully functional from Phase 1 (persisted
 * through DataStore); wallpaper and data sections join in later phases.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.l),
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
