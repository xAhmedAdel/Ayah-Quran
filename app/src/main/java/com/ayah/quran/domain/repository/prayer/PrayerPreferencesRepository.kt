package com.ayah.quran.domain.repository.prayer

import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerLocation
import kotlinx.coroutines.flow.Flow

/**
 * All prayer/adhan user preferences, backed by DataStore. The selected
 * location is either a manual city (cityId) or a cached one-shot device fix.
 */
data class PrayerPreferences(
    val location: PrayerLocation?,
    val autoLocationEnabled: Boolean,
    val method: CalculationMethod,
    val asrMethod: AsrMethod,
    val highLatitudeRule: HighLatitudeRulePref,
    val adjustments: PrayerAdjustments,
    val adhanConfig: PrayerAdhanConfig,
    val hijriAdjustmentDays: Int,
    val customAdhanAudioUri: String?,
)

interface PrayerPreferencesRepository {

    val preferences: Flow<PrayerPreferences>

    suspend fun setLocation(location: PrayerLocation)

    suspend fun setAutoLocationEnabled(enabled: Boolean)

    suspend fun setMethod(method: CalculationMethod)

    suspend fun setAsrMethod(method: AsrMethod)

    suspend fun setHighLatitudeRule(rule: HighLatitudeRulePref)

    suspend fun setAdjustments(adjustments: PrayerAdjustments)

    suspend fun setAdhanConfig(config: PrayerAdhanConfig)

    suspend fun setHijriAdjustmentDays(days: Int)

    suspend fun setCustomAdhanAudioUri(uri: String?)
}
