package com.ayah.quran.data.prayer

import android.content.Context
import com.ayah.quran.domain.model.prayer.PrayerLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled city catalog (assets/prayer/cities.json), derived from
 * GeoNames (CC-BY 4.0 — attribution in Settings → Licenses). Fully offline.
 */
@Singleton
class CityCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val cities: List<PrayerLocation> by lazy { load() }

    private fun load(): List<PrayerLocation> {
        val json = context.assets.open("prayer/cities.json").bufferedReader().use { it.readText() }
        // Compact file: {"source":…, "cities":[…]}
        val array = JSONObject(json).getJSONArray("cities")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PrayerLocation(
                cityAr = o.getString("ar"),
                cityEn = o.getString("en"),
                countryAr = o.getString("countryAr"),
                countryEn = o.getString("countryEn"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lng"),
                timezoneId = o.getString("tz"),
            )
        }
    }
}
