package com.ayah.quran.data.audio

import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.Reciter
import com.ayah.quran.domain.repository.ReciterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reciter catalog backed by the pluggable [AudioCatalogSource]. The source
 * decides which licensed reciters exist; this repository adds lookup
 * behavior shared by all sources.
 */
@Singleton
class ReciterRepositoryImpl @Inject constructor(
    private val catalogSource: AudioCatalogSource,
) : ReciterRepository {

    override fun observeReciters(): Flow<List<Reciter>> = catalogSource.observeReciters()

    override suspend fun getReciter(reciterId: String): Reciter? =
        catalogSource.observeReciters().first().firstOrNull { it.id == reciterId }

    override fun observeTracks(reciterId: String): Flow<List<AudioTrack>> =
        catalogSource.observeTracks(reciterId)

    override suspend fun getTrackForSurah(reciterId: String, surahNumber: Int): AudioTrack? =
        catalogSource.getTrack(reciterId, surahNumber)

    override suspend fun hasPlayableSource(track: AudioTrack): Boolean =
        track.hasPlayableSource && catalogSource.hasPlayableSource(track)
}
