package com.ayah.quran.ui.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.data.prayer.CityCatalog
import com.ayah.quran.data.prayer.DeviceLocationProvider
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerCountdown
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import javax.inject.Inject

/** What the prayer screen renders. */
data class PrayerUiState(
    val preferences: PrayerPreferences? = null,
    val today: DayPrayerTimes? = null,
    val tomorrowFajr: LocalDateTime? = null,
    val countdown: PrayerCountdown? = null,
    val countdownText: String? = null,
    val gregorianText: String? = null,
    val hijriText: String? = null,
    val exactAlarmGranted: Boolean = true,
    val autoLocationBusy: Boolean = false,
    val locationError: Boolean = false,
)

@HiltViewModel
class PrayerTimesViewModel @Inject constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
    private val manager: com.ayah.quran.prayer.PrayerTimeManager,
    private val cityCatalog: CityCatalog,
    private val locationProvider: DeviceLocationProvider,
    private val scheduler: PrayerAlarmScheduler,
) : ViewModel() {

    private val _autoLocationBusy = MutableStateFlow(false)
    private val _locationError = MutableStateFlow(false)

    /** One-second ticker that runs ONLY while the screen is subscribed. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState: StateFlow<PrayerUiState> = combine(
        preferencesRepository.preferences,
        manager.todayTimes,
        manager.tomorrowFajr,
        manager.exactAlarmGranted,
        combine(ticker, _autoLocationBusy, _locationError) { _, busy, err -> busy to err },
    ) { prefs, today, tomorrowFajr, exactGranted, (busy, err) ->
        buildState(prefs, today, tomorrowFajr, exactGranted, busy, err)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PrayerUiState(),
    )

    val cities: List<com.ayah.quran.domain.model.prayer.PrayerLocation> get() = cityCatalog.cities

    private fun buildState(
        prefs: PrayerPreferences,
        today: DayPrayerTimes?,
        tomorrowFajr: LocalDateTime?,
        exactGranted: Boolean,
        autoBusy: Boolean,
        locationError: Boolean,
    ): PrayerUiState {
        val location = prefs.location
        val zone = location?.let { ZoneId.of(it.timezoneId) } ?: ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val countdown = if (today != null && tomorrowFajr != null) {
            manager.countdown(today, tomorrowFajr, zone)
        } else {
            null
        }
        val countdownText = countdown?.let {
            val total = it.durationMillis.coerceAtLeast(0)
            val h = total / 3_600_000
            val m = (total / 60_000) % 60
            val s = (total / 1_000) % 60
            "%02d:%02d:%02d".format(h, m, s)
        }
        val locale = java.util.Locale.forLanguageTag(
            if (com.ayah.quran.core.locale.AppLocale.isArabic) {
                com.ayah.quran.core.locale.AppLocale.ARABIC
            } else {
                com.ayah.quran.core.locale.AppLocale.ENGLISH
            },
        )
        val gregorian = LocalDate.now(zone)
            .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale))
        val hijri = hijriText(LocalDate.now(zone).plusDays(prefs.hijriAdjustmentDays.toLong()), locale)
        return PrayerUiState(
            preferences = prefs,
            today = today,
            tomorrowFajr = tomorrowFajr,
            countdown = countdown,
            countdownText = countdownText,
            gregorianText = gregorian,
            hijriText = hijri,
            exactAlarmGranted = exactGranted,
            autoLocationBusy = autoBusy,
            locationError = locationError,
        )
    }

    private fun hijriText(localDate: LocalDate, locale: java.util.Locale): String {
        val hijri = HijrahDate.from(localDate)
        val formatter = DateTimeFormatterBuilder()
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(" ")
            .appendPattern("MMMM")
            .appendLiteral(" ")
            .appendValue(ChronoField.YEAR_OF_ERA)
            .toFormatter(locale)
            .withChronology(java.time.chrono.HijrahChronology.INSTANCE)
        val suffix = if (com.ayah.quran.core.locale.AppLocale.isArabic) " هـ" else " AH"
        return formatter.format(hijri) + suffix
    }

    // ---- user actions ----

    fun selectCity(location: com.ayah.quran.domain.model.prayer.PrayerLocation) {
        viewModelScope.launch {
            preferencesRepository.setLocation(location)
            preferencesRepository.setAutoLocationEnabled(false)
            manager.reconcile()
        }
    }

    fun refreshDeviceLocation() {
        if (_autoLocationBusy.value) return
        viewModelScope.launch {
            _autoLocationBusy.value = true
            _locationError.value = false
            val fix = if (locationProvider.hasPermission()) {
                locationProvider.currentLocation()
            } else {
                null
            }
            if (fix != null) {
                preferencesRepository.setLocation(fix)
                manager.reconcile()
            } else {
                _locationError.value = true
            }
            _autoLocationBusy.value = false
        }
    }

    fun setMethod(method: com.ayah.quran.domain.model.prayer.CalculationMethod) {
        viewModelScope.launch {
            preferencesRepository.setMethod(method)
            manager.reconcile()
        }
    }

    fun setAsrMethod(method: com.ayah.quran.domain.model.prayer.AsrMethod) {
        viewModelScope.launch {
            preferencesRepository.setAsrMethod(method)
            manager.reconcile()
        }
    }

    fun setHighLatitudeRule(rule: com.ayah.quran.domain.model.prayer.HighLatitudeRulePref) {
        viewModelScope.launch {
            preferencesRepository.setHighLatitudeRule(rule)
            manager.reconcile()
        }
    }

    fun setAdhanMode(prayer: Prayer, mode: com.ayah.quran.domain.model.prayer.AdhanMode) {
        viewModelScope.launch {
            preferencesRepository.setAdhanConfig(
                uiState.value.preferences?.adhanConfig?.withMode(prayer, mode)
                    ?: com.ayah.quran.domain.model.prayer.PrayerAdhanConfig(),
            )
            manager.reconcile()
        }
    }

    fun adjustPrayer(prayer: Prayer, delta: Int) {
        val current = uiState.value.preferences?.adjustments ?: PrayerAdjustments()
        val updated = when (prayer) {
            Prayer.FAJR -> current.copy(fajr = (current.fajr + delta).coerceIn(-120, 120))
            Prayer.DHUHR -> current.copy(dhuhr = (current.dhuhr + delta).coerceIn(-120, 120))
            Prayer.ASR -> current.copy(asr = (current.asr + delta).coerceIn(-120, 120))
            Prayer.MAGHRIB -> current.copy(maghrib = (current.maghrib + delta).coerceIn(-120, 120))
            Prayer.ISHA -> current.copy(isha = (current.isha + delta).coerceIn(-120, 120))
        }
        viewModelScope.launch {
            preferencesRepository.setAdjustments(updated)
            manager.reconcile()
        }
    }

    fun setHijriAdjustment(days: Int) {
        viewModelScope.launch {
            preferencesRepository.setHijriAdjustmentDays(days.coerceIn(-2, 2))
        }
    }

    fun setCustomAdhanUri(uri: String?) {
        viewModelScope.launch {
            preferencesRepository.setCustomAdhanAudioUri(uri)
            manager.reconcile()
        }
    }

    /** Exposed for the settings screen's status rows. */
    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    /** True when at least coarse location permission is granted. */
    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()
}
