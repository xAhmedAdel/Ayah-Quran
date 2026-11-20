package com.ayah.quran.ui.prayer

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahGroupCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSelectionSheet
import com.ayah.quran.core.designsystem.component.AyahSettingRow
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdjustments

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(
    onBack: () -> Unit,
    onOpenVoices: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
    permissionsViewModel: PrayerPermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCityPicker by remember { mutableStateOf(false) }
    var showMethodSheet by remember { mutableStateOf(false) }
    var showAsrSheet by remember { mutableStateOf(false) }
    var showHighLatSheet by remember { mutableStateOf(false) }
    var showAdjustmentsSheet by remember { mutableStateOf(false) }
    var editingAdhanPrayer by remember { mutableStateOf<Prayer?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.refreshDeviceLocation() }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setCustomAdhanUri(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prayer_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            val prefs = state.preferences ?: return@Column

            AyahGroupCard(title = stringResource(R.string.prayer_settings_location)) {
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (viewModel.hasLocationPermission()) {
                                    viewModel.refreshDeviceLocation()
                                } else {
                                    locationPermissionLauncher.launch(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(AyahRadii.pill),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.prayer_enable_auto_location))
                        }
                        OutlinedButton(
                            onClick = { showCityPicker = true },
                            shape = RoundedCornerShape(AyahRadii.pill),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.prayer_select_city))
                        }
                    }
                    if (state.locationError) {
                        Text(
                            text = stringResource(R.string.prayer_location_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            AyahGroupCard(title = stringResource(R.string.prayer_settings_calculation)) {
                AyahSettingRow(
                    title = stringResource(R.string.prayer_method_label),
                    subtitle = methodLabel(prefs.method),
                    onClick = { showMethodSheet = true },
                )
                AyahInsetDivider()
                AyahSettingRow(
                    title = stringResource(R.string.prayer_asr_label),
                    subtitle = asrLabel(prefs.asrMethod),
                    onClick = { showAsrSheet = true },
                )
                AyahInsetDivider()
                AyahSettingRow(
                    title = stringResource(R.string.prayer_high_lat_label),
                    subtitle = highLatLabel(prefs.highLatitudeRule),
                    onClick = { showHighLatSheet = true },
                )
                AyahInsetDivider()
                AyahSettingRow(
                    title = stringResource(R.string.prayer_adjustments_label),
                    subtitle = adjustmentsSummary(prefs.adjustments),
                    onClick = { showAdjustmentsSheet = true },
                )
                AyahInsetDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.prayer_hijri_adjust_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { viewModel.setHijriAdjustment(prefs.hijriAdjustmentDays - 1) },
                            enabled = prefs.hijriAdjustmentDays > -2,
                        ) {
                            Text(stringResource(R.string.prayer_adjust_decrease))
                        }
                        Text(
                            text = AyahNumbering.isolateLtr("%+d".format(prefs.hijriAdjustmentDays)),
                            style = MaterialTheme.typography.titleSmall.withTabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = AyahSpacing.xs),
                        )
                        TextButton(
                            onClick = { viewModel.setHijriAdjustment(prefs.hijriAdjustmentDays + 1) },
                            enabled = prefs.hijriAdjustmentDays < 2,
                        ) {
                            Text(stringResource(R.string.prayer_adjust_increase))
                        }
                    }
                }
            }

            AyahGroupCard(title = stringResource(R.string.prayer_settings_adhan)) {
                AyahSettingRow(
                    title = stringResource(R.string.prayer_voice_open_settings),
                    leadingIcon = AyahIcons.Audio,
                    onClick = onOpenVoices,
                )
                listOf(
                    Prayer.FAJR,
                    Prayer.DHUHR,
                    Prayer.ASR,
                    Prayer.MAGHRIB,
                    Prayer.ISHA,
                ).forEach { prayer ->
                    AyahInsetDivider()
                    val mode = prefs.adhanConfig.modeOf(prayer)
                    AyahSettingRow(
                        title = prayerName(prayer),
                        onClick = { editingAdhanPrayer = prayer },
                        trailing = {
                            AyahStatusBadge(
                                label = adhanModeLabel(mode),
                                kind = if (mode == AdhanMode.DISABLED) {
                                    AyahStatusKind.NEUTRAL
                                } else {
                                    AyahStatusKind.SUCCESS
                                },
                            )
                        },
                    )
                }
                AyahInsetDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AyahSpacing.m),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                ) {
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
                        OutlinedButton(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            shape = RoundedCornerShape(AyahRadii.pill),
                        ) {
                            Text(stringResource(R.string.prayer_custom_audio_pick))
                        }
                        if (prefs.customAdhanAudioUri != null) {
                            OutlinedButton(
                                onClick = { viewModel.setCustomAdhanUri(null) },
                                shape = RoundedCornerShape(AyahRadii.pill),
                            ) {
                                Text(stringResource(R.string.prayer_custom_audio_clear))
                            }
                        }
                    }
                }
            }

            PermissionsDashboardSection(
                permissionsViewModel = permissionsViewModel,
                onOpenOnboarding = onOpenOnboarding,
            )
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

    val prefs = state.preferences
    if (showMethodSheet && prefs != null) {
        AyahSelectionSheet(
            title = stringResource(R.string.prayer_method_label),
            options = CalculationMethod.entries,
            selected = prefs.method,
            labelFor = { methodLabel(it) },
            onSelect = viewModel::setMethod,
            onDismiss = { showMethodSheet = false },
        )
    }

    if (showAsrSheet && prefs != null) {
        AyahSelectionSheet(
            title = stringResource(R.string.prayer_asr_label),
            options = AsrMethod.entries,
            selected = prefs.asrMethod,
            labelFor = { asrLabel(it) },
            onSelect = viewModel::setAsrMethod,
            onDismiss = { showAsrSheet = false },
        )
    }

    if (showHighLatSheet && prefs != null) {
        AyahSelectionSheet(
            title = stringResource(R.string.prayer_high_lat_label),
            options = HighLatitudeRulePref.entries,
            selected = prefs.highLatitudeRule,
            labelFor = { highLatLabel(it) },
            onSelect = viewModel::setHighLatitudeRule,
            onDismiss = { showHighLatSheet = false },
        )
    }

    if (showAdjustmentsSheet && prefs != null) {
        AdjustmentsSheet(
            adjustments = prefs.adjustments,
            onAdjust = viewModel::adjustPrayer,
            onDismiss = { showAdjustmentsSheet = false },
        )
    }

    val activePrayer = editingAdhanPrayer
    if (activePrayer != null && prefs != null) {
        AyahSelectionSheet(
            title = prayerName(activePrayer),
            options = listOf(
                AdhanMode.FULL_ADHAN,
                AdhanMode.NOTIFICATION_SOUND,
                AdhanMode.SILENT,
                AdhanMode.DISABLED,
            ),
            selected = prefs.adhanConfig.modeOf(activePrayer),
            labelFor = { adhanModeLabel(it) },
            onSelect = { mode -> viewModel.setAdhanMode(activePrayer, mode) },
            onDismiss = { editingAdhanPrayer = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentsSheet(
    adjustments: PrayerAdjustments,
    onAdjust: (Prayer, Int) -> Unit,
    onDismiss: () -> Unit,
) {
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
                .padding(horizontal = AyahSpacing.ml, vertical = AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = stringResource(R.string.prayer_adjustments_label),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            listOf(
                Prayer.FAJR to adjustments.fajr,
                Prayer.DHUHR to adjustments.dhuhr,
                Prayer.ASR to adjustments.asr,
                Prayer.MAGHRIB to adjustments.maghrib,
                Prayer.ISHA to adjustments.isha,
            ).forEach { (prayer, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = prayerName(prayer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onAdjust(prayer, -1) }) {
                            Text(stringResource(R.string.prayer_adjust_decrease))
                        }
                        Text(
                            text = AyahNumbering.isolateLtr("%+d".format(value)),
                            style = MaterialTheme.typography.titleSmall.withTabularNumbers(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = AyahSpacing.s),
                        )
                        TextButton(onClick = { onAdjust(prayer, +1) }) {
                            Text(stringResource(R.string.prayer_adjust_increase))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun adjustmentsSummary(adjustments: PrayerAdjustments): String {
    val nonZero = listOf(
        Prayer.FAJR to adjustments.fajr,
        Prayer.DHUHR to adjustments.dhuhr,
        Prayer.ASR to adjustments.asr,
        Prayer.MAGHRIB to adjustments.maghrib,
        Prayer.ISHA to adjustments.isha,
    ).filter { it.second != 0 }
    if (nonZero.isEmpty()) return stringResource(R.string.prayer_adjustments_default)
    val parts = mutableListOf<String>()
    for ((prayer, delta) in nonZero) {
        parts += "${prayerName(prayer)} ${AyahNumbering.isolateLtr("%+d".format(delta))}"
    }
    return parts.joinToString(" · ")
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
