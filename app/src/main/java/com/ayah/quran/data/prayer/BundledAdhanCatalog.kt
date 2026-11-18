package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.model.prayer.Prayer
import javax.inject.Inject

/**
 * The adhan audio catalog: BUNDLED recordings (Phase 13, inside the APK) and
 * REMOTE downloadable recordings (Phase 15).
 *
 * Every entry — bundled or remote — was license-verified on its source page
 * before inclusion (see docs/ADHAN-AUDIO-LICENSES.md and
 * docs/ADHAN-AUDIO-SOURCES.md). No Makkah audio exists with verifiable
 * redistribution rights at this time: the Makkah category is therefore shown
 * with an honest unavailable state instead of an unverified recording.
 *
 * Pure Kotlin — no Android dependencies — so JVM tests can validate the
 * catalog invariants directly.
 */
class BundledAdhanCatalog @javax.inject.Inject constructor() {

    val recordings: List<AdhanRecording> = listOf(
        // ---- Bundled (Phase 13) ----
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
            category = AdhanCategory.BUNDLED,
            audioQuality = "OGG Vorbis",
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
            category = AdhanCategory.FAMOUS,
            muadhinNameAr = "صبح فخري",
            muadhinNameEn = "Sabah Fakhri",
            audioQuality = "MP3",
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
            category = AdhanCategory.BUNDLED,
            audioQuality = "OGG Vorbis",
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
            category = AdhanCategory.BUNDLED,
            mosqueCityAr = "الجزائر",
            mosqueCityEn = "Algeria",
            audioQuality = "OGG Vorbis",
        ),
        // ---- Remote downloads (Phase 15) ----
        AdhanRecording(
            id = "adhan_madinah_prophet_mosque",
            displayNameAr = "أذان المسجد النبوي",
            displayNameEn = "Adhan of the Prophet's Mosque",
            descriptionAr = "نداء الصلاة من المسجد النبوي في المدينة المنورة، بترخيص المشاع الإبداعي نسب 3.0.",
            descriptionEn = "The call to prayer from the Prophet's Mosque in Madinah, CC BY 3.0.",
            durationSeconds = 186,
            license = "CC BY 3.0",
            attribution = "Recording by ejaz215 (Wikimedia Commons), CC BY 3.0.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
            category = AdhanCategory.MADINAH,
            mosqueCityAr = "المسجد النبوي، المدينة المنورة",
            mosqueCityEn = "The Prophet's Mosque, Madinah",
            isBundled = false,
            downloadUrl = "https://upload.wikimedia.org/wikipedia/commons/7/7c/33937_ejaz215_call-to-prayer-from-the-prophet-s-mo.ogg",
            fileExtension = "ogg",
            expectedSizeBytes = 2_974_689L,
            audioQuality = "OGG Vorbis",
        ),
        AdhanRecording(
            id = "adhan_casablanca_hassan2",
            displayNameAr = "أذان مسجد الحسن الثاني — الدار البيضاء",
            displayNameEn = "Adhan of Hassan II Mosque — Casablanca",
            descriptionAr = "تسجيل أذان مسجد الحسن الثاني بالدار البيضاء، المغرب (CC BY-SA 4.0) — ملف كبير (~31MB).",
            descriptionEn = "An adhan recording at Hassan II Mosque, Casablanca (CC BY-SA 4.0) — large file (~31MB).",
            durationSeconds = 175,
            license = "CC BY-SA 4.0",
            attribution = "Recording by Fraguando (Wikimedia Commons), CC BY-SA 4.0.",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Llamada_a_oraci%C3%B3n_Mezquita_Hassan_II.wav",
            category = AdhanCategory.REGIONS,
            mosqueCityAr = "مسجد الحسن الثاني، الدار البيضاء",
            mosqueCityEn = "Hassan II Mosque, Casablanca",
            isBundled = false,
            downloadUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9c/Llamada_a_oraci%C3%B3n_Mezquita_Hassan_II.wav",
            fileExtension = "wav",
            expectedSizeBytes = 30_990_030L,
            audioQuality = "WAV PCM",
        ),
    )

    /** Ships as the voice for ALL five prayers on first install (Phase 13 spec). */
    val defaultRecording: AdhanRecording = recordings.first { it.id == AdhanVoiceDefaults.DEFAULT_RECORDING_ID }

    fun byId(id: String?): AdhanRecording? = recordings.firstOrNull { it.id == id }

    fun bundled(): List<AdhanRecording> = recordings.filter { it.isBundled }

    fun remote(): List<AdhanRecording> = recordings.filterNot { it.isBundled }
}

/**
 * Pure per-prayer voice resolution. If a stored voice id no longer exists in
 * the catalog (e.g. a future app update removed a recording), resolution
 * falls back to the DEFAULT recording — never to silence — per Phase 13 §3.
 */
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
