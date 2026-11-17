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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayah.quran.R
import com.ayah.quran.core.designsystem.component.AyahCard
import com.ayah.quran.core.designsystem.component.SectionCard
import com.ayah.quran.core.designsystem.theme.AyahSpacing
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerLocation

/**
 * The Prayer Times screen: location, Hijri + Gregorian dates, the
 * current/next prayer with a live countdown (ticking only while visible),
 * the full daily timetable with the upcoming prayer highlighted, and the
 * adhan status. All times come from the verified local calculation engine —
 * without a location the screen asks the user instead of inventing times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrayerTimesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCityPicker by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
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
                NextPrayerCard(state = state, location = location)
                TimetableCard(state = state)
                AdhanStatusCard(state = state)
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
private fun NoLocationCard(
    busy: Boolean,
    error: Boolean,
    onUseDeviceLocation: () -> Unit,
    onPickCity: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.prayer_title)) {
        Text(
            text = stringResource(R.string.prayer_no_location),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (error) {
            Text(
                text = stringResource(R.string.prayer_location_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AyahSpacing.s)) {
            Button(onClick = onUseDeviceLocation, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                }
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Text(stringResource(R.string.prayer_enable_auto_location))
            }
            OutlinedButton(onClick = onPickCity) {
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

@Composable
private fun NextPrayerCard(
    state: PrayerUiState,
    location: PrayerLocation,
) {
    val prefs = state.preferences
    val adhanOn = prefs?.adhanConfig?.let { config ->
        Prayer.entries.any { config.modeOf(it) != com.ayah.quran.domain.model.prayer.AdhanMode.DISABLED }
    } ?: false
    val arabic = AppLocale.isArabic
    val city = if (arabic) location.cityAr.ifEmpty {
        stringResource(R.string.prayer_enable_auto_location)
    } else {
        location.cityEn.ifEmpty { stringResource(R.string.prayer_enable_auto_location) }
    }
    val country = if (arabic) location.countryAr else location.countryEn

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.s),
        ) {
            Text(
                text = listOf(city, country).filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                text = listOfNotNull(state.hijriText, state.gregorianText).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            state.countdown?.let { countdown ->
                val nextName = prayerName(countdown.next)
                Text(
                    text = stringResource(R.string.prayer_next),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = nextName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = com.ayah.quran.core.designsystem.theme.QuranTextFontFamily,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = state.countdownText ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatTime(countdown.nextTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (adhanOn) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        if (adhanOn) R.string.prayer_adhan_status_on else R.string.prayer_adhan_status_off,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TimetableCard(state: PrayerUiState) {
    SectionCard(title = stringResource(R.string.prayer_title)) {
        val next = state.countdown?.next
        val rows = listOf(
            Prayer.FAJR,
            Prayer.DHUHR,
            Prayer.ASR,
            Prayer.MAGHRIB,
            Prayer.ISHA,
        )
        rows.forEach { prayer ->
            PrayerRow(
                name = prayerName(prayer),
                time = state.today?.let { formatTime(it.timeOf(prayer)) } ?: "",
                highlighted = prayer == next,
            )
        }
        // Sunrise is displayed separately and clearly marked as not a prayer.
        Text(
            text = stringResource(R.string.prayer_sunrise_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AyahSpacing.xs),
        )
        PrayerRow(
            name = stringResource(R.string.prayer_name_sunrise),
            time = state.today?.let { formatTime(it.sunrise) } ?: "",
            highlighted = false,
            isSunrise = true,
        )
    }
}

@Composable
private fun PrayerRow(
    name: String,
    time: String,
    highlighted: Boolean,
    isSunrise: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AyahSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = if (isSunrise) {
                    MaterialTheme.typography.bodyLarge.fontFamily
                } else {
                    com.ayah.quran.core.designsystem.theme.QuranTextFontFamily
                },
            ),
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AdhanStatusCard(state: PrayerUiState) {
    val prefs = state.preferences ?: return
    AyahCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AyahSpacing.m),
            verticalArrangement = Arrangement.spacedBy(AyahSpacing.xs),
        ) {
            if (!state.exactAlarmGranted) {
                Text(
                    text = stringResource(R.string.prayer_exact_alarm_denied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
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
private fun prayerName(prayer: Prayer): String = stringResource(
    when (prayer) {
        Prayer.FAJR -> R.string.prayer_name_fajr
        Prayer.DHUHR -> R.string.prayer_name_dhuhr
        Prayer.ASR -> R.string.prayer_name_asr
        Prayer.MAGHRIB -> R.string.prayer_name_maghrib
        Prayer.ISHA -> R.string.prayer_name_isha
    },
)

/** HH:mm in the prayer's own timezone context (digits localized by format). */
private fun formatTime(time: java.time.LocalDateTime): String =
    "%02d:%02d".format(time.hour, time.minute)
