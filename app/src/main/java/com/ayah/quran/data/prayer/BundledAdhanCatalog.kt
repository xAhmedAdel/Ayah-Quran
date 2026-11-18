package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.model.prayer.Prayer
import javax.inject.Inject

/**
 * The built-in adhan audio catalog: four recordings whose redistribution
 * rights were VERIFIED on Wikimedia Commons before bundling (see
 * docs/ADHAN-AUDIO-LICENSES.md for the per-recording licensing decision).
 *
 * Pure Kotlin — no Android dependencies — so JVM tests can validate the
 * catalog invariants directly.
 */
class BundledAdhanCatalog @javax.inject.Inject constructor() {

    val recordings: List<AdhanRecording> = listOf(
        AdhanRecording(
            id = "adhan_cc0_beautiful",
            displayNameAr = "أذان — تسجيل مجتمعي (CC0)",
            displayNameEn = "Community recording (CC0)",
            descriptionAr = "تسجيل أذان كامل من تركية، أُصدر للملك العام (CC0).",
            descriptionEn = "A complete adhan recording from Türkiye, dedicated to the public domain (CC0).",
            durationSeconds = 154,
            license = "CC0 1.0 Universal",
            attribution = "Recording by Adam-synagda (Wikimedia Commons), CC0 1.0.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Beautiful_adhan.ogg",
        ),
        AdhanRecording(
            id = "adhan_pd_sabah_fakhri",
            displayNameAr = "صبح فخري",
            displayNameEn = "Sabah Fakhri",
            descriptionAr = "تسجيل تاريخي لنداء الصلاة بصوت صبح فخري، الملكية العامة.",
            descriptionEn = "A historical call-to-prayer recording by Sabah Fakhri, public domain.",
            durationSeconds = 158,
            license = "Public domain",
            attribution = "Performance by Sabah Fakhri; Wikimedia Commons file, public domain.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Call_to_prayer_by_Sabah_Fakhry.mp3",
        ),
        AdhanRecording(
            id = "adhan_by_sa_azan",
            displayNameAr = "أذان — تسجيل مجتمعي (CC BY-SA)",
            displayNameEn = "Community recording (CC BY-SA)",
            descriptionAr = "تسجيل أذان كامل بترخيص المشاع الإبداعي نسب-الترخيص بالمثل 4.0.",
            descriptionEn = "A complete adhan recording under CC BY-SA 4.0.",
            durationSeconds = 183,
            license = "CC BY-SA 4.0",
            attribution = "Recording by Andrewler (Wikimedia Commons), CC BY-SA 4.0.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Azan.ogg",
        ),
        AdhanRecording(
            id = "adhan_by_sa_call_to_worship",
            displayNameAr = "نداء الصلاة — الجزائر (CC BY-SA)",
            displayNameEn = "Call to worship — Algeria (CC BY-SA)",
            descriptionAr = "تسجيل أذان من الجزائر بترخيص المشاع الإبداعي نسب-الترخيص بالمثل 4.0.",
            descriptionEn = "An adhan recording from Algeria under CC BY-SA 4.0.",
            durationSeconds = 210,
            license = "CC BY-SA 4.0",
            attribution = "Recording by Mahfoudou (Wikimedia Commons), CC BY-SA 4.0.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Islamic_call_to_worship.oga",
        ),
    )

    /** Ships as the voice for ALL five prayers on first install (Phase 13 spec). */
    val defaultRecording: AdhanRecording = recordings.first { it.id == AdhanVoiceDefaults.DEFAULT_RECORDING_ID }

    fun byId(id: String?): AdhanRecording? = recordings.firstOrNull { it.id == id }

}

class AdhanVoiceResolver @Inject constructor(private val catalog: BundledAdhanCatalog) {

    /** The recording a stored voice id resolves to (null id → default). */
    fun recordingFor(voiceId: String?): com.ayah.quran.domain.model.prayer.AdhanRecording? {
        if (voiceId == AdhanVoiceDefaults.USER_FILE_ID) return null // caller handles user file
        return catalog.byId(voiceId) ?: catalog.defaultRecording
    }

    fun isDefault(voiceId: String?): Boolean =
        voiceId == null || voiceId == AdhanVoiceDefaults.DEFAULT_RECORDING_ID

    /** All five prayers with their effective recording (user file resolved by caller). */
    fun resolveAll(voiceOf: (Prayer) -> String?): Map<Prayer, com.ayah.quran.domain.model.prayer.AdhanRecording?> =
        Prayer.entries.associateWith { recordingFor(voiceOf(it)) }
}
