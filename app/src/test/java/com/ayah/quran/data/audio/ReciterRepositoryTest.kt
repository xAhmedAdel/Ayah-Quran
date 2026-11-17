package com.ayah.quran.data.audio

import com.ayah.quran.domain.model.audio.AudioSourceType
import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.Reciter
import com.ayah.quran.domain.repository.ReciterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository behavior over FAKE catalog sources (never fake production
 * assets): honest empty catalog, populated lookup, and playability rules.
 */
class ReciterRepositoryTest {

    private val emptyRepository = ReciterRepositoryImpl(EmptyAudioCatalogSource())

    @Test
    fun `empty catalog exposes an honest empty reciter list`() = runTest {
        val reciters = emptyRepository.observeReciters().first()
        assertEquals(emptyList<Reciter>(), reciters)
    }

    @Test
    fun `empty catalog yields no tracks and no surah lookup`() = runTest {
        assertEquals(emptyList<AudioTrack>(), emptyRepository.observeTracks("any").first())
        assertNull(emptyRepository.getTrackForSurah("any", 1))
    }

    @Test
    fun `populated fake source resolves reciter and track lookups`() = runTest {
        val reciter = Reciter(
            id = "test_reciter",
            nameArabic = "قارئ اختبار",
            nameEnglish = "Test Reciter",
            attribution = "Test attribution",
            licenseName = "Test License",
            isAvailable = true,
        )
        val track = AudioTrack(
            id = "test_reciter_001",
            reciterId = "test_reciter",
            surahNumber = 1,
            source = "/tmp/test.wav",
            sourceType = AudioSourceType.LOCAL_FILE,
        )
        val repository = ReciterRepositoryImpl(
            object : AudioCatalogSource {
                override fun observeReciters() = flowOf(listOf(reciter))
                override fun observeTracks(reciterId: String) =
                    if (reciterId == reciter.id) flowOf(listOf(track)) else flowOf(emptyList())
                override suspend fun getTrack(reciterId: String, surahNumber: Int): AudioTrack? =
                    if (reciterId == reciter.id && surahNumber == 1) track else null
                override suspend fun hasPlayableSource(track: AudioTrack): Boolean =
                    track.hasPlayableSource && track.sourceType != AudioSourceType.NONE
            },
        )

        assertEquals(listOf(reciter), repository.observeReciters().first())
        assertEquals(reciter, repository.getReciter("test_reciter"))
        assertEquals(track, repository.getTrackForSurah("test_reciter", 1))
        assertNull(repository.getTrackForSurah("test_reciter", 2))
        assertEquals(listOf(track), repository.observeTracks("test_reciter").first())
        assertTrue(repository.hasPlayableSource(track))
    }

    @Test
    fun `hasPlayableSource is false for a track with no source`() = runTest {
        val unplayable = AudioTrack(id = "x", reciterId = "r", surahNumber = 1)
        assertFalse(emptyRepository.hasPlayableSource(unplayable))
    }
}
