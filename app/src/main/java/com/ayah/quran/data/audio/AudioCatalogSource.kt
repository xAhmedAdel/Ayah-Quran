package com.ayah.quran.data.audio

import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.Reciter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Pluggable source of the licensed reciter catalog.
 *
 * The current implementation is [EmptyAudioCatalogSource] because no
 * licensed recitation assets are bundled yet. Future implementations
 * (bundled offline assets, or a verified remote catalog) plug in here
 * WITHOUT touching repositories, use cases, or UI.
 */
interface AudioCatalogSource {

    /** All reciters published by this source (possibly empty). */
    fun observeReciters(): Flow<List<Reciter>>

    /** All tracks published by [reciterId] (possibly empty). */
    fun observeTracks(reciterId: String): Flow<List<AudioTrack>>

    /** Direct track lookup, or null. */
    suspend fun getTrack(reciterId: String, surahNumber: Int): AudioTrack?

    /** Whether [track]'s source resolves right now (e.g. file exists). */
    suspend fun hasPlayableSource(track: AudioTrack): Boolean
}

/**
 * The honest starting point: no licensed recitation assets exist yet, so
 * the catalog is empty and every track lookup yields null.
 */
class EmptyAudioCatalogSource @Inject constructor() : AudioCatalogSource {

    override fun observeReciters(): Flow<List<Reciter>> = flowOf(emptyList())

    override fun observeTracks(reciterId: String): Flow<List<AudioTrack>> = flowOf(emptyList())

    override suspend fun getTrack(reciterId: String, surahNumber: Int): AudioTrack? = null

    override suspend fun hasPlayableSource(track: AudioTrack): Boolean = false
}
