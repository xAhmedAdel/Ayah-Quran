package com.ayah.quran.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranAudioCatalogTest {

    @Test
    fun ayahCountsSumToTheCanonical6236() {
        assertEquals(114, QuranAudioCatalog.AYAH_COUNTS.size)
        assertEquals(QuranAudioCatalog.TOTAL_AYAHS, QuranAudioCatalog.AYAH_COUNTS.sum())
    }

    @Test
    fun ayahCountsArePositive() {
        assertTrue(QuranAudioCatalog.AYAH_COUNTS.all { it > 0 })
    }

    @Test
    fun firstGlobalAyahOffsetsMatchHafsAccumulation() {
        assertEquals(1, QuranAudioCatalog.firstGlobalAyah(1))
        assertEquals(8, QuranAudioCatalog.firstGlobalAyah(2)) // 7 + 1
        // API-verified: global 262 = Al-Baqara:255 (7 + 255), and global
        // 287 = Al-Baqara:280. Al-Imran therefore starts at 7 + 286 + 1.
        assertEquals(262, QuranAudioCatalog.firstGlobalAyah(2) + 254)
        assertEquals(294, QuranAudioCatalog.firstGlobalAyah(3))
        // Last surah starts at 6231 (Al-Nas has 6 ayahs, total 6236).
        assertEquals(6231, QuranAudioCatalog.firstGlobalAyah(114))
    }

    @Test
    fun ayahUrlMatchesVerifiedCdnScheme() {
        val alafasy = QuranAudioCatalog.byEdition("ar.alafasy")
        assertEquals(
            "https://cdn.islamic.network/quran/audio/128/ar.alafasy/262.mp3",
            QuranAudioCatalog.ayahUrl(alafasy, 262),
        )
        val basfar = QuranAudioCatalog.byEdition("ar.abdullahbasfar")
        assertEquals(
            "https://cdn.islamic.network/quran/audio/64/ar.abdullahbasfar/1.mp3",
            QuranAudioCatalog.ayahUrl(basfar, 1),
        )
    }

    @Test
    fun unknownEditionFallsBackToDefault() {
        assertEquals("ar.alafasy", QuranAudioCatalog.byEdition("nonsense").editionId)
        assertEquals("ar.alafasy", QuranAudioCatalog.byEdition(null).editionId)
    }
}
