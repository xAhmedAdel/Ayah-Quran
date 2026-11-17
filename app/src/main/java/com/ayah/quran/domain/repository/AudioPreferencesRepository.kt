package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.audio.RepeatMode
import kotlinx.coroutines.flow.Flow

/**
 * Persisted audio preferences (selected reciter, repeat, speed). Transient
 * playback state (position, playing flag) is deliberately NOT persisted.
 */
data class AudioPreferences(
    val selectedReciterId: String?,
    val repeatMode: RepeatMode,
    val playbackSpeed: Float,
)

interface AudioPreferencesRepository {

    /** Emits the current preferences and every subsequent change. */
    val preferences: Flow<AudioPreferences>

    /** Atomically applies a transformation to the stored preferences. */
    suspend fun update(transform: (AudioPreferences) -> AudioPreferences)
}
