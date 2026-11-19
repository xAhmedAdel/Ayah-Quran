package com.ayah.quran.data.audio

/**
 * One Quran reciter edition offered for streaming/download (Phase 17).
 *
 * Licensing posture (owner-approved "community standard + attribution"):
 * these are the community-distribution recordings mirrored by the AlQuran
 * Cloud CDN (origin: everyayah.com) — the de-facto free-distribution sets
 * used across open-source Quran apps. The app is free and non-commercial;
 * every reciter is credited in-app and the sources are documented in
 * docs/QURAN-AUDIO-SOURCES.md. This is NOT a formal CC license — if a
 * rights holder objects, the edition is removed.
 */
data class QuranReciterEdition(
    /** CDN edition identifier, e.g. "ar.alafasy". */
    val editionId: String,
    val nameAr: String,
    val nameEn: String,
    /** Per-ayah CDN bitrate (kbps) verified available for this edition. */
    val bitrateKbps: Int,
    val styleAr: String,
) {
    val attributionAr: String
        get() = "$nameAr — $styleAr (توزيع مجتمعي حر، عبر AlQuran Cloud / EveryAyah)"
}

/**
 * The verified Quran recitation catalog (Phase 17).
 *
 * Every edition/bitrate pair below was probed live (HTTP 200) before
 * inclusion; editions whose endpoints returned 403 are excluded. Sources:
 * - per-ayah:   https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{ayahGlobal}.mp3
 * - whole-surah exists only for a few editions and is intentionally unused
 *   (per-ayah is the uniform path enabling ayah highlight + repeat modes).
 */
object QuranAudioCatalog {

    val reciters: List<QuranReciterEdition> = listOf(
        QuranReciterEdition(
            editionId = "ar.alafasy",
            nameAr = "مشاري العفاسي",
            nameEn = "Mishary Alafasy",
            bitrateKbps = 128,
            styleAr = "مرتل",
        ),
        QuranReciterEdition(
            editionId = "ar.husary",
            nameAr = "محمود خليل الحصري",
            nameEn = "Mahmoud Khalil Al-Husary",
            bitrateKbps = 128,
            styleAr = "مرتل",
        ),
        QuranReciterEdition(
            editionId = "ar.minshawi",
            nameAr = "محمد صديق المنشاوي",
            nameEn = "Muhammad Siddiq Al-Minshawi",
            bitrateKbps = 128,
            styleAr = "مرتل",
        ),
        QuranReciterEdition(
            editionId = "ar.hudhaify",
            nameAr = "علي الحذيفي",
            nameEn = "Ali Al-Hudhaify",
            bitrateKbps = 128,
            styleAr = "مرتل",
        ),
        QuranReciterEdition(
            editionId = "ar.abdullahbasfar",
            nameAr = "عبد الله بصفر",
            nameEn = "Abdullah Basfar",
            bitrateKbps = 64, // the 128 kbps per-ayah set 403s; 64 verified
            styleAr = "مرتل",
        ),
    )

    val defaultReciter: QuranReciterEdition get() = reciters.first()

    fun byEdition(editionId: String?): QuranReciterEdition =
        reciters.firstOrNull { it.editionId == editionId } ?: defaultReciter

    /** Hafs 'an 'Asim ayah count per surah (1..114); canonical, sums to 6236. */
    val AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, // 1-10
        123, 111, 43, 52, 99, 128, 111, 110, 98, 135, // 11-20
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, // 21-30
        34, 30, 73, 54, 45, 83, 182, 88, 75, 85, // 31-40
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, // 41-50
        60, 49, 62, 55, 78, 96, 29, 22, 24, 13, // 51-60
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, // 61-70
        28, 28, 20, 56, 40, 31, 50, 40, 46, 42, // 71-80
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, // 81-90
        15, 21, 11, 8, 8, 19, 5, 8, 8, 11, // 91-100
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, // 101-110
        5, 4, 5, 6, // 111-114
    )

    const val TOTAL_AYAHS = 6236

    fun ayahCount(surah: Int): Int = AYAH_COUNTS[surah - 1]

    /** First global ayah number of [surah] (surah 1 starts at 1). */
    fun firstGlobalAyah(surah: Int): Int =
        AYAH_COUNTS.copyOfRange(0, surah - 1).sum() + 1

    /** CDN URL of one ayah of [edition]. [ayahGlobal] is 1..6236 (Hafs). */
    fun ayahUrl(edition: QuranReciterEdition, ayahGlobal: Int): String =
        "$AUDIO_BASE/audio/${edition.bitrateKbps}/${edition.editionId}/$ayahGlobal.mp3"

    private const val AUDIO_BASE = "https://cdn.islamic.network/quran"
}
