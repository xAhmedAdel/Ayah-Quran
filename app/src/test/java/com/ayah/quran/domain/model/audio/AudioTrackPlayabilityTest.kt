package com.ayah.quran.domain.model.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure playability rules of [AudioTrack.hasPlayableSource]. */
class AudioTrackPlayabilityTest {

    @Test
    fun `NONE source is never playable`() {
        val track = AudioTrack(id = "t", reciterId = "r", surahNumber = 1, sourceType = AudioSourceType.NONE)
        assertFalse(track.hasPlayableSource)
    }

    @Test
    fun `blank source is never playable`() {
        val blank = AudioTrack(id = "t", reciterId = "r", surahNumber = 1, sourceType = AudioSourceType.REMOTE_URL, source = "")
        val whitespace = AudioTrack(id = "t", reciterId = "r", surahNumber = 1, sourceType = AudioSourceType.LOCAL_FILE, source = "   ")
        assertFalse(blank.hasPlayableSource)
        assertFalse(whitespace.hasPlayableSource)
    }

    @Test
    fun `typed non-blank source is playable`() {
        val local = AudioTrack(id = "t", reciterId = "r", surahNumber = 1, sourceType = AudioSourceType.LOCAL_FILE, source = "/audio/001.wav")
        val remote = AudioTrack(id = "t", reciterId = "r", surahNumber = 1, sourceType = AudioSourceType.REMOTE_URL, source = "https://example.org/001.mp3")
        assertTrue(local.hasPlayableSource)
        assertTrue(remote.hasPlayableSource)
    }
}
