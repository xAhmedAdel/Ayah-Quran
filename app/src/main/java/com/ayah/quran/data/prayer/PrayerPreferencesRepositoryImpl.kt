package com.ayah.quran.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerLocation
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed prayer/adhan preferences. Read failure is treated as
 * "no preference stored" only for the expected IOException, like every
 * other store in the app.
 */
@Singleton
class PrayerPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PrayerPreferencesRepository {

    override val preferences: Flow<PrayerPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toPrayerPreferences() }

    override suspend fun setLocation(location: PrayerLocation) {
        dataStore.edit { prefs ->
            prefs[KEY_CITY_AR] = location.cityAr
            prefs[KEY_CITY_EN] = location.cityEn
            prefs[KEY_COUNTRY_AR] = location.countryAr
            prefs[KEY_COUNTRY_EN] = location.countryEn
            prefs[KEY_LAT] = java.lang.Double.doubleToRawLongBits(location.latitude)
            prefs[KEY_LNG] = java.lang.Double.doubleToRawLongBits(location.longitude)
            prefs[KEY_TZ] = location.timezoneId
            prefs[KEY_AUTO_LOCATION] = false
        }
    }

    override suspend fun setAutoLocationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_AUTO_LOCATION] = enabled }
    }

    override suspend fun setMethod(method: CalculationMethod) {
        dataStore.edit { prefs -> prefs[KEY_METHOD] = method.name }
    }

    override suspend fun setAsrMethod(method: AsrMethod) {
        dataStore.edit { prefs -> prefs[KEY_ASR] = method.name }
    }

    override suspend fun setHighLatitudeRule(rule: HighLatitudeRulePref) {
        dataStore.edit { prefs -> prefs[KEY_HIGH_LAT] = rule.name }
    }

    override suspend fun setAdjustments(adjustments: PrayerAdjustments) {
        dataStore.edit { prefs ->
            prefs[KEY_ADJ_FAJR] = adjustments.fajr
            prefs[KEY_ADJ_DHUHR] = adjustments.dhuhr
            prefs[KEY_ADJ_ASR] = adjustments.asr
            prefs[KEY_ADJ_MAGHRIB] = adjustments.maghrib
            prefs[KEY_ADJ_ISHA] = adjustments.isha
        }
    }

    override suspend fun setAdhanConfig(config: PrayerAdhanConfig) {
        dataStore.edit { prefs ->
            Prayer.entries.forEach { prayer ->
                prefs[adhanKey(prayer)] = config.modeOf(prayer).name
            }
        }
    }

    override suspend fun setHijriAdjustmentDays(days: Int) {
        dataStore.edit { prefs -> prefs[KEY_HIJRI_ADJUSTMENT] = days }
    }

    override val voiceSelection: Flow<PrayerVoiceSelection> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toVoiceSelection() }

    override suspend fun setVoice(prayer: Prayer, recordingId: String) {
        dataStore.edit { prefs ->
            prefs[voiceKey(prayer)] = recordingId
        }
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs[KEY_ONBOARDING_DONE] ?: false }

    override suspend fun setOnboardingCompleted() {
        dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = true }
    }

    override val vendorGuidanceAcknowledged: Flow<Boolean> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs[KEY_VENDOR_GUIDANCE_DONE] ?: false }

    override suspend fun setVendorGuidanceAcknowledged() {
        dataStore.edit { prefs -> prefs[KEY_VENDOR_GUIDANCE_DONE] = true }
    }

    override val downloadedAdhanIds: Flow<Set<String>> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs[KEY_DOWNLOADED_ADHAN_IDS] ?: emptySet() }

    override suspend fun setAdhanDownloaded(id: String, downloaded: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_DOWNLOADED_ADHAN_IDS] ?: emptySet()
            prefs[KEY_DOWNLOADED_ADHAN_IDS] =
                if (downloaded) current + id else current - id
        }
    }

    override suspend fun setCustomAdhanAudioUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(KEY_CUSTOM_ADHAN_URI) else prefs[KEY_CUSTOM_ADHAN_URI] = uri
        }
    }

    private fun Preferences.toPrayerPreferences(): PrayerPreferences = PrayerPreferences(
        location = locationOrNull(),
        autoLocationEnabled = this[KEY_AUTO_LOCATION] ?: false,
        method = this[KEY_METHOD]?.let { runCatching { CalculationMethod.valueOf(it) }.getOrNull() }
            ?: CalculationMethod.EGYPTIAN,
        asrMethod = this[KEY_ASR]?.let { runCatching { AsrMethod.valueOf(it) }.getOrNull() }
            ?: AsrMethod.STANDARD,
        highLatitudeRule = this[KEY_HIGH_LAT]?.let { runCatching { HighLatitudeRulePref.valueOf(it) }.getOrNull() }
            ?: HighLatitudeRulePref.ANGLE_BASED,
        adjustments = PrayerAdjustments(
            fajr = this[KEY_ADJ_FAJR] ?: 0,
            dhuhr = this[KEY_ADJ_DHUHR] ?: 0,
            asr = this[KEY_ADJ_ASR] ?: 0,
            maghrib = this[KEY_ADJ_MAGHRIB] ?: 0,
            isha = this[KEY_ADJ_ISHA] ?: 0,
        ),
        adhanConfig = PrayerAdhanConfig(
            fajr = adhanMode(Prayer.FAJR),
            dhuhr = adhanMode(Prayer.DHUHR),
            asr = adhanMode(Prayer.ASR),
            maghrib = adhanMode(Prayer.MAGHRIB),
            isha = adhanMode(Prayer.ISHA),
        ),
        voiceSelection = toVoiceSelection(),
        hijriAdjustmentDays = this[KEY_HIJRI_ADJUSTMENT] ?: 0,
        customAdhanAudioUri = this[KEY_CUSTOM_ADHAN_URI],
    )

    private fun Preferences.locationOrNull(): PrayerLocation? {
        val lat = this[KEY_LAT] ?: return null
        val lng = this[KEY_LNG] ?: return null
        val tz = this[KEY_TZ] ?: return null
        return PrayerLocation(
            cityAr = this[KEY_CITY_AR] ?: "",
            cityEn = this[KEY_CITY_EN] ?: "",
            countryAr = this[KEY_COUNTRY_AR] ?: "",
            countryEn = this[KEY_COUNTRY_EN] ?: "",
            latitude = java.lang.Double.longBitsToDouble(lat),
            longitude = java.lang.Double.longBitsToDouble(lng),
            timezoneId = tz,
        )
    }

    private fun Preferences.adhanMode(prayer: Prayer): AdhanMode =
        this[adhanKey(prayer)]?.let { runCatching { AdhanMode.valueOf(it) }.getOrNull() }
            ?: AdhanMode.NOTIFICATION_SOUND

    private fun adhanKey(prayer: Prayer) = stringPreferencesKey("prayer_adhan_${prayer.name}")

    private fun Preferences.toVoiceSelection(): PrayerVoiceSelection = PrayerVoiceSelection(
        fajr = this[voiceKey(Prayer.FAJR)] ?: AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
        dhuhr = this[voiceKey(Prayer.DHUHR)] ?: AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
        asr = this[voiceKey(Prayer.ASR)] ?: AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
        maghrib = this[voiceKey(Prayer.MAGHRIB)] ?: AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
        isha = this[voiceKey(Prayer.ISHA)] ?: AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
    )

    private fun voiceKey(prayer: Prayer) = stringPreferencesKey("prayer_voice_${prayer.name}")

    private companion object {
        val KEY_CITY_AR = stringPreferencesKey("prayer_city_ar")
        val KEY_CITY_EN = stringPreferencesKey("prayer_city_en")
        val KEY_COUNTRY_AR = stringPreferencesKey("prayer_country_ar")
        val KEY_COUNTRY_EN = stringPreferencesKey("prayer_country_en")
        val KEY_LAT = longPreferencesKey("prayer_lat_bits") // raw double bits
        val KEY_LNG = longPreferencesKey("prayer_lng_bits")
        val KEY_TZ = stringPreferencesKey("prayer_tz")
        val KEY_AUTO_LOCATION = booleanPreferencesKey("prayer_auto_location")
        val KEY_METHOD = stringPreferencesKey("prayer_method")
        val KEY_ASR = stringPreferencesKey("prayer_asr")
        val KEY_HIGH_LAT = stringPreferencesKey("prayer_high_lat")
        val KEY_ADJ_FAJR = intPreferencesKey("prayer_adj_fajr")
        val KEY_ADJ_DHUHR = intPreferencesKey("prayer_adj_dhuhr")
        val KEY_ADJ_ASR = intPreferencesKey("prayer_adj_asr")
        val KEY_ADJ_MAGHRIB = intPreferencesKey("prayer_adj_maghrib")
        val KEY_ADJ_ISHA = intPreferencesKey("prayer_adj_isha")
        val KEY_HIJRI_ADJUSTMENT = intPreferencesKey("prayer_hijri_adjustment")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("prayer_onboarding_done")
        val KEY_VENDOR_GUIDANCE_DONE = booleanPreferencesKey("prayer_vendor_guidance_done")
        val KEY_DOWNLOADED_ADHAN_IDS = stringSetPreferencesKey("prayer_adhan_downloaded_ids")
        val KEY_CUSTOM_ADHAN_URI = stringPreferencesKey("prayer_custom_adhan_uri")
    }
}
