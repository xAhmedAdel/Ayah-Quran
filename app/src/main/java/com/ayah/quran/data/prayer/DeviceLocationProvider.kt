package com.ayah.quran.data.prayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.ayah.quran.domain.model.prayer.PrayerLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot coarse location lookup via the framework [LocationManager] (no
 * Play Services dependency). Never tracks continuously: a single fix is read
 * from the best available provider (last-known first), then cached by the
 * caller in [PrayerPreferencesRepositoryImpl].
 */
@Singleton
class DeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** True when at least coarse location permission is already granted. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Best-effort one-shot fix mapped to a device location. Returns null when
     * no fix is obtainable — callers must show a clear message and offer the
     * manual city picker (never fabricated coordinates).
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): PrayerLocation? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val fix: Location? = sequenceOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filterNotNull().maxByOrNull { it.time }

        val location = fix ?: return@withContext null
        val zone = java.util.TimeZone.getDefault().getID()
        PrayerLocation(
            cityAr = "",
            cityEn = "",
            countryAr = "",
            countryEn = "",
            latitude = location.latitude,
            longitude = location.longitude,
            timezoneId = zone,
        )
    }
}
