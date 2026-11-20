package com.ayah.quran.ui.prayer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.AyahIcons
import com.ayah.quran.core.designsystem.AyahNumbering
import com.ayah.quran.core.designsystem.component.AyahBannerStrip
import com.ayah.quran.core.designsystem.component.AyahBannerTone
import com.ayah.quran.core.designsystem.component.AyahHeroCard
import com.ayah.quran.core.designsystem.component.AyahInsetDivider
import com.ayah.quran.core.designsystem.component.AyahSelectionSheet
import com.ayah.quran.core.designsystem.component.AyahStatusBadge
import com.ayah.quran.core.designsystem.component.AyahStatusKind
import com.ayah.quran.core.designsystem.component.AyahSurfaceCard
import com.ayah.quran.core.designsystem.component.SectionHeader
import com.ayah.quran.core.designsystem.theme.AyahRadii
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.designsystem.theme.AyahThemeTokens
import com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
import com.ayah.quran.core.designsystem.theme.withTabularNumbers
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCityPicker by remember { mutableStateOf(false) }
    var editingAdhanPrayer by remember { mutableStateOf<Prayer?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.refreshDeviceLocation()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prayer_title)) },
                actions = {
                    if (state.preferences?.location != null) {
                        IconButton(onClick = { showCityPicker = true }) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = stringResource(R.string.prayer_change_city),
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = AyahIcons.Settings,
                            contentDescription = stringResource(R.string.prayer_open_settings),
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
            val prefs = state.preferences
            val location = prefs?.location

            if (location == null) {
                NoLocationCard(
                    busy = state.autoLocationBusy,
                    error = state.locationError,
                    onUseDeviceLocation = {
                        if (viewModel.hasLocationPermission()) {
                            viewModel.refreshDeviceLocation()
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    onPickCity = { showCityPicker = true },
                )
            } else {
                NextPrayerHeroCard(
                    state = state,
                    location = location,
                    onPickCity = { showCityPicker = true },
                )
                TimetableCard(
                    state = state,
                    onPrayerClick = { editingAdhanPrayer = it },
                )
                if (!state.exactAlarmGranted) {
                    AyahBannerStrip(
                        message = stringResource(R.string.prayer_exact_alarm_denied),
                        tone = AyahBannerTone.WARNING,
                        actionLabel = stringResource(R.string.prayer_open_settings),
                        onAction = onOpenSettings,
                    )
                }
                Text(
                    text = stringResource(R.string.prayer_battery_guidance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AyahSpacing.xs),
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

    val activePrayer = editingAdhanPrayer
    val currentConfig = state.preferences?.adhanConfig
    if (activePrayer != null && currentConfig != null) {
        AyahSelectionSheet(
            title = prayerName(activePrayer),
            options = listOf(
                AdhanMode.FULL_ADHAN,
                AdhanMode.NOTIFICATION_SOUND,
                AdhanMode.SILENT,
                AdhanMode.DISABLED,
            ),
            selected = currentConfig.modeOf(activePrayer),
            labelFor = { mode -> adhanModeLabel(mode) },
            onSelect = { mode -> viewModel.setAdhanMode(activePrayer, mode) },
            onDismiss = { editingAdhanPrayer = null },
        )
    }
}

@Composable
private fun NoLocationCard(
    busy: Boolean,
    error: Boolean,
    onUseDeviceLocation: () -> Unit,
    onPickCity: () -> Unit,
) {
    AyahSurfaceCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.l),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AyahRadii.m))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = stringResource(R.string.prayer_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.prayer_no_location),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error) {
                Text(
                    text = stringResource(R.string.prayer_location_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onUseDeviceLocation,
                    enabled = !busy,
                    shape = RoundedCornerShape(AyahRadii.pill),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(AyahSpacing.xs))
                    }
                    Text(stringResource(R.string.prayer_enable_auto_location))
                }
                OutlinedButton(
                    onClick = onPickCity,
                    shape = RoundedCornerShape(AyahRadii.pill),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.prayer_select_city))
                }
            }
            Text(
                text = stringResource(R.string.prayer_location_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextPrayerHeroCard(
    state: PrayerUiState,
    location: PrayerLocation,
    onPickCity: () -> Unit,
) {
    val tokens = AyahThemeTokens.colors
    val prefs = state.preferences
    val adhanOn = prefs?.adhanConfig?.let { config ->
        Prayer.entries.any { config.modeOf(it) != AdhanMode.DISABLED }
    } ?: false
    val arabic = AppLocale.isArabic
    val city = if (arabic) {
        location.cityAr.ifEmpty { stringResource(R.string.prayer_enable_auto_location) }
    } else {
        location.cityEn.ifEmpty { stringResource(R.string.prayer_enable_auto_location) }
    }
    val country = if (arabic) location.countryAr else location.countryEn
    val locationLabel = listOf(city, country).filter { it.isNotBlank() }.joinToString(" · ")

    AyahHeroCard(onClick = onPickCity) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = tokens.heroAccent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.heroOnSurface,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
            ) {
                Icon(
                    imageVector = if (adhanOn) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = tokens.heroAccent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(
                        if (adhanOn) R.string.prayer_adhan_status_on else R.string.prayer_adhan_status_off,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.heroMuted,
                )
            }
        }

        Spacer(Modifier.height(AyahSpacing.sm))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            Text(
                text = listOfNotNull(state.hijriText, state.gregorianText).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.heroMuted,
                textAlign = TextAlign.Center,
            )
            state.countdown?.let { countdown ->
                Text(
                    text = stringResource(R.string.prayer_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.heroAccent,
                )
                Text(
                    text = prayerName(countdown.next),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = QuranTextFontFamily,
                        fontSize = 34.sp,
                    ),
                    color = tokens.heroOnSurface,
                )
                Text(
                    text = AyahNumbering.isolateLtr(state.countdownText ?: ""),
                    style = MaterialTheme.typography.displaySmall.withTabularNumbers(),
                    color = tokens.heroAccent,
                )
                Text(
                    text = AyahNumbering.isolateLtr(formatTime(countdown.nextTime)),
                    style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                    color = tokens.heroMuted,
                )
            }
        }
    }
}

@Composable
private fun TimetableCard(
    state: PrayerUiState,
    onPrayerClick: (Prayer) -> Unit,
) {
    val next = state.countdown?.next
    val config = state.preferences?.adhanConfig
    Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
        SectionHeader(text = stringResource(R.string.prayer_title))
        AyahSurfaceCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                val rows = listOf(
                    Prayer.FAJR,
                    Prayer.DHUHR,
                    Prayer.ASR,
                    Prayer.MAGHRIB,
                    Prayer.ISHA,
                )
                rows.forEachIndexed { index, prayer ->
                    val mode = config?.modeOf(prayer) ?: AdhanMode.NOTIFICATION_SOUND
                    PrayerRow(
                        name = prayerName(prayer),
                        time = state.today?.let { formatTime(it.timeOf(prayer)) } ?: "",
                        highlighted = prayer == next,
                        adhanMode = mode,
                        onClick = { onPrayerClick(prayer) },
                    )
                    if (index != rows.lastIndex) {
                        AyahInsetDivider()
                    }
                }
                AyahInsetDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AyahSpacing.xxs),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.prayer_name_sunrise),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = AyahNumbering.isolateLtr(
                                state.today?.let { formatTime(it.sunrise) } ?: "",
                            ),
                            style = MaterialTheme.typography.bodyMedium.withTabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.prayer_sunrise_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerRow(
    name: String,
    time: String,
    highlighted: Boolean,
    adhanMode: AdhanMode,
    onClick: () -> Unit,
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = AyahSpacing.m, vertical = AyahSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AyahSpacing.sm),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = QuranTextFontFamily,
                fontSize = 20.sp,
            ),
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (highlighted) {
            AyahStatusBadge(
                label = stringResource(R.string.prayer_next),
                kind = AyahStatusKind.GOLD,
            )
        }
        Text(
            text = AyahNumbering.isolateLtr(time),
            style = MaterialTheme.typography.titleMedium.withTabularNumbers(),
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Icon(
            imageVector = when (adhanMode) {
                AdhanMode.FULL_ADHAN -> Icons.Filled.VolumeUp
                AdhanMode.NOTIFICATION_SOUND -> Icons.Filled.NotificationsActive
                AdhanMode.SILENT -> Icons.Filled.VolumeOff
                AdhanMode.DISABLED -> Icons.Filled.NotificationsOff
            },
            contentDescription = adhanModeLabel(adhanMode),
            tint = if (adhanMode == AdhanMode.DISABLED) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun CityPickerDialog(
    cities: List<PrayerLocation>,
    onSelect: (PrayerLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val arabic = AppLocale.isArabic
    val filtered = if (query.isBlank()) {
        cities
    } else {
        cities.filter {
            it.cityEn.contains(query, ignoreCase = true) ||
                it.cityAr.contains(query) ||
                it.countryEn.contains(query, ignoreCase = true) ||
                it.countryAr.contains(query)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prayer_select_city)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.prayer_search_city_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.size(width = 320.dp, height = 360.dp)) {
                    items(filtered) { city ->
                        TextButton(onClick = { onSelect(city) }) {
                            Text(
                                text = if (arabic) {
                                    "${city.cityAr} · ${city.countryAr}"
                                } else {
                                    "${city.cityEn} · ${city.countryEn}"
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.prayer_back)) }
        },
    )
}

@Composable
internal fun adhanModeLabel(mode: AdhanMode): String = stringResource(
    when (mode) {
        AdhanMode.FULL_ADHAN -> R.string.prayer_adhan_mode_full
        AdhanMode.NOTIFICATION_SOUND -> R.string.prayer_adhan_mode_notification
        AdhanMode.SILENT -> R.string.prayer_adhan_mode_silent
        AdhanMode.DISABLED -> R.string.prayer_adhan_mode_disabled
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

private fun formatTime(time: java.time.LocalDateTime): String =
    "%02d:%02d".format(time.hour, time.minute)
