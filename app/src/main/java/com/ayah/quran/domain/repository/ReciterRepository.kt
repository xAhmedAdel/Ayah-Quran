package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.Reciter
import kotlinx.coroutines.flow.Flow

/**
 * Read access to the licensed reciter catalog.
 *
 * Implementations must return ONLY verified, licensed reciters and tracks;
 * when no licensed assets exist the flows emit empty lists (an honest empty
 * state), never fabricated entries.
 */
interface ReciterRepository {

    /** All reciters with currently resolvable audio, in display order. */
    fun observeReciters(): Flow<List<Reciter>>

    suspend fun getReciter(reciterId: String): Reciter?

    /** All tracks published by [reciterId]. */
    fun observeTracks(reciterId: String): Flow<List<AudioTrack>>

    /** The track covering [surahNumber] for [reciterId], or null. */
    suspend fun getTrackForSurah(reciterId: String, surahNumber: Int): AudioTrack?

    /** Whether [track] resolves to a usable audio source right now. */
    suspend fun hasPlayableSource(track: AudioTrack): Boolean
}
