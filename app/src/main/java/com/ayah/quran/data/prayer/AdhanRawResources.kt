package com.ayah.quran.data.prayer

import androidx.annotation.RawRes
import com.ayah.quran.R
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults

/**
 * Static recording-id → res/raw mapping. Explicit `when` (not runtime
 * getIdentifier) so R8 keeps the raw resources and typos fail at compile.
 */
object AdhanRawResources {

    @RawRes
    fun resIdOf(recordingId: String): Int = when (recordingId) {
        "adhan_cc0_beautiful" -> R.raw.adhan_cc0_beautiful
        "adhan_pd_sabah_fakhri" -> R.raw.adhan_pd_sabah_fakhri
        "adhan_by_sa_azan" -> R.raw.adhan_by_sa_azan
        "adhan_by_sa_call_to_worship" -> R.raw.adhan_by_sa_call_to_worship
        else -> R.raw.adhan_cc0_beautiful // safe fallback = default recording
    }

    fun isValid(recordingId: String): Boolean = when (recordingId) {
        AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
        "adhan_pd_sabah_fakhri",
        "adhan_by_sa_azan",
        "adhan_by_sa_call_to_worship",
        -> true

        else -> false
    }
}
