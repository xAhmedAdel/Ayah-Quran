package com.ayah.quran.ui.prayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerLocation

/**
 * Prayer & Adhan settings: location (auto/manual), calculation method +
 * Asr + high-latitude rule + per-prayer minute adjustments, adhan modes per
 * prayer, custom adhan audio (user file), Hijri adjustment, and the
 * Advanced permission/battery status section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(
    onBack: () -> Unit,
    onOpenVoices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCityPicker by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.refreshDeviceLocation() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Keep access across reboots for scheduled adhan playback.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCustomAdhanUri(uri.toString())
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.prayer_settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.prayer_back),
                    )
                }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.m),
        ) {
            val prefs = state.preferences
            if (prefs == null) return@Column

            // ---- Location ----
            SectionCard(title = stringResource(R.string.prayer_settings_location)) {
                val location = prefs.location
                val arabic = AppLocale.isArabic
                val label = location?.let {
                    val city = if (arabic) it.cityAr else it.cityEn
                    val country = if (arabic) it.countryAr else it.countryEn
                    if (city.isBlank()) {
                        stringResource(R.string.prayer_enable_auto_location)
                    } else {
                        listOf(city, country).filter { c -> c.isNotBlank() }.joinToString(" · ")
                    }
                } ?: stringResource(R.string.prayer_no_location)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                    ButtonLike(
                        label = stringResource(R.string.prayer_enable_auto_location),
                        onClick = {
                            if (viewModel.hasLocationPermission()) {
                                viewModel.refreshDeviceLocation()
                            } else {
                                locationPermissionLauncher.launch(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            }
                        },
                    )
                    ButtonLike(
                        label = stringResource(R.string.prayer_select_city),
                        onClick = { showCityPicker = true },
                    )
                }
                if (state.locationError) {
                    Text(
                        text = stringResource(R.string.prayer_location_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---- Calculation ----
            SectionCard(title = stringResource(R.string.prayer_settings_calculation)) {
                DropdownSetting(
                    label = stringResource(R.string.prayer_method_label),
                    options = CalculationMethod.entries,
                    selected = prefs.method,
                    labelFor = { methodLabel(it) },
                    onSelect = viewModel::setMethod,
                )
                DropdownSetting(
                    label = stringResource(R.string.prayer_asr_label),
                    options = AsrMethod.entries,
                    selected = prefs.asrMethod,
                    labelFor = { asrLabel(it) },
                    onSelect = viewModel::setAsrMethod,
                )
                DropdownSetting(
                    label = stringResource(R.string.prayer_high_lat_label),
                    options = HighLatitudeRulePref.entries,
                    selected = prefs.highLatitudeRule,
                    labelFor = { highLatLabel(it) },
                    onSelect = viewModel::setHighLatitudeRule,
                )
                Text(
                    text = stringResource(R.string.prayer_adjustments_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                listOf(
                    Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                ).forEach { prayer ->
                    val value = when (prayer) {
                        Prayer.FAJR -> prefs.adjustments.fajr
                        Prayer.DHUHR -> prefs.adjustments.dhuhr
                        Prayer.ASR -> prefs.adjustments.asr
                        Prayer.MAGHRIB -> prefs.adjustments.maghrib
                        Prayer.ISHA -> prefs.adjustments.isha
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(prayerName(prayer))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.adjustPrayer(prayer, -1) }) {
                                Text(stringResource(R.string.prayer_adjust_decrease))
                            }
                            Text(
                                text = "%+d".format(value),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = AyahSpacing.s),
                            )
                            TextButton(onClick = { viewModel.adjustPrayer(prayer, +1) }) {
                                Text(stringResource(R.string.prayer_adjust_increase))
                            }
                        }
                    }
                }
            }

            // ---- Adhan ----
            SectionCard(title = stringResource(R.string.prayer_settings_adhan)) {
                TextButton(onClick = onOpenVoices) {
                    Text(stringResource(R.string.prayer_voice_open_settings))
                }
                listOf(
                    Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA,
                ).forEach { prayer ->
                    Text(
                        text = prayerName(prayer),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs)) {
                        val modes = listOf(
                            AdhanMode.FULL_ADHAN to R.string.prayer_adhan_mode_full,
                            AdhanMode.NOTIFICATION_SOUND to R.string.prayer_adhan_mode_notification,
                            AdhanMode.SILENT to R.string.prayer_adhan_mode_silent,
                            AdhanMode.DISABLED to R.string.prayer_adhan_mode_disabled,
                        )
                        modes.forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = prefs.adhanConfig.modeOf(prayer) == mode,
                                onClick = { viewModel.setAdhanMode(prayer, mode) },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.prayer_custom_audio),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = prefs.customAdhanAudioUri
                        ?: stringResource(R.string.prayer_custom_audio_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                    ButtonLike(
                        label = stringResource(R.string.prayer_custom_audio_pick),
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    )
                    if (prefs.customAdhanAudioUri != null) {
                        ButtonLike(
                            label = stringResource(R.string.prayer_custom_audio_clear),
                            onClick = { viewModel.setCustomAdhanUri(null) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.prayer_adhan_library_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.prayer_adhan_library_empty_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Notifications ----
            SectionCard(title = stringResource(R.string.prayer_settings_notifications)) {
                NotificationPermissionRow(viewModel)
            }

            // ---- Advanced ----
            SectionCard(title = stringResource(R.string.prayer_settings_advanced)) {
                val exactGranted = viewModel.canScheduleExact()
                Text(
                    text = stringResource(
                        R.string.prayer_exact_alarm_status,
                        stringResource(
                            if (exactGranted) {
                                R.string.prayer_permission_granted
                            } else {
                                R.string.prayer_permission_denied
                            },
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!exactGranted) {
                    ButtonLike(
                        label = stringResource(R.string.prayer_exact_alarm_denied),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + context.packageName),
                                    ),
                                )
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.prayer_battery_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showCityPicker) {
        CityPickerDialog(
            cities = viewModel.cities,
            onSelect = {
                viewModel.selectCity(it)
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false },
        )
    }
}

@Composable
private fun NotificationPermissionRow(viewModel: PrayerTimesViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                R.string.prayer_notification_permission_status,
                stringResource(
                    if (granted) R.string.prayer_permission_granted else R.string.prayer_permission_denied,
                ),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!granted) {
            ButtonLike(
                label = stringResource(R.string.prayer_permission_granted),
                onClick = {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        }
    }
}

/** Compact action chip styled like the rest of the settings controls. */
@Composable
private fun ButtonLike(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun <T> DropdownSetting(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Segmented rows don't fit 10 methods; a compact chip-flow with an
        // explicit selected state keeps everything visible and honest.
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelFor(option)) },
            )
        }
    }
}

@Composable
private fun methodLabel(method: CalculationMethod): String = stringResource(
    when (method) {
        CalculationMethod.EGYPTIAN -> R.string.prayer_method_egyptian
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> R.string.prayer_method_mwl
        CalculationMethod.UMM_AL_QURA -> R.string.prayer_method_umm_al_qura
        CalculationMethod.KARACHI -> R.string.prayer_method_karachi
        CalculationMethod.ISNA -> R.string.prayer_method_isna
        CalculationMethod.DUBAI -> R.string.prayer_method_dubai
        CalculationMethod.KUWAIT -> R.string.prayer_method_kuwait
        CalculationMethod.QATAR -> R.string.prayer_method_qatar
        CalculationMethod.SINGAPORE -> R.string.prayer_method_singapore
        CalculationMethod.MOONSIGHTING_COMMITTEE -> R.string.prayer_method_moonsighting
    },
)

@Composable
private fun asrLabel(method: AsrMethod): String = stringResource(
    when (method) {
        AsrMethod.STANDARD -> R.string.prayer_asr_standard
        AsrMethod.HANAFI -> R.string.prayer_asr_hanafi
    },
)

@Composable
private fun highLatLabel(rule: HighLatitudeRulePref): String = stringResource(
    when (rule) {
        HighLatitudeRulePref.MIDDLE_OF_NIGHT -> R.string.prayer_high_lat_middle
        HighLatitudeRulePref.SEVENTH_OF_NIGHT -> R.string.prayer_high_lat_seventh
        HighLatitudeRulePref.ANGLE_BASED -> R.string.prayer_high_lat_angle
    },
)

@Composable
private fun prayerName(prayer: Prayer): String = stringResource(
    when (prayer) {
        Prayer.FAJR -> R.string.prayer_name_fajr
        Prayer.DHUHR -> R.string.prayer_name_dhuhr
        Prayer.ASR -> R.string.prayer_name_asr
        Prayer.MAGHRIB -> R.string.prayer_name_maghrib
        Prayer.ISHA -> R.string.prayer_name_isha
    },
)
