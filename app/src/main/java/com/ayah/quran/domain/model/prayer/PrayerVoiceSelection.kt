package com.ayah.quran.domain.model.prayer

/** Per-prayer adhan voice ids (see [AdhanVoiceDefaults] for the default). */
data class PrayerVoiceSelection(
    val fajr: String = AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
    val dhuhr: String = AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
    val asr: String = AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
    val maghrib: String = AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
    val isha: String = AdhanVoiceDefaults.DEFAULT_RECORDING_ID,
) {
    fun voiceOf(prayer: Prayer): String = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }

    fun withVoice(prayer: Prayer, recordingId: String): PrayerVoiceSelection = when (prayer) {
        Prayer.FAJR -> copy(fajr = recordingId)
        Prayer.DHUHR -> copy(dhuhr = recordingId)
        Prayer.ASR -> copy(asr = recordingId)
        Prayer.MAGHRIB -> copy(maghrib = recordingId)
        Prayer.ISHA -> copy(isha = recordingId)
    }
}

/**
 * Stable voice ids shared by the domain and the bundled catalog. The bundled
 * catalog (data layer) declares these same ids; changing them changes stored
 * user selections, so they are frozen here.
 */
object AdhanVoiceDefaults {
    const val DEFAULT_RECORDING_ID = "adhan_cc0_beautiful"
    const val USER_FILE_ID = "adhan_user_file"
}
