package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Playback control and state for the audio engine.
 *
 * The implementation owns the single platform player instance (Media3
 * ExoPlayer in the data layer) and maps its lifecycle onto [PlaybackState].
 * Preparing an unplayable track yields PlaybackState.Error — never a fake
 * playing state.
 */
interface AudioRepository {

    /** Current playback state; emits on every transition. */
    val playbackState: StateFlow<PlaybackState>

    /** Current position within the prepared source, in milliseconds. */
    val positionMs: StateFlow<Long>

    /** Duration of the prepared source, in milliseconds (0 when unknown). */
    val durationMs: StateFlow<Long>

    /** Prepares [track]; a track without a playable source yields an Error state. */
    suspend fun prepare(track: AudioTrack)

    fun play()

    fun pause()

    /** Stops playback and releases the prepared source (player stays alive). */
    fun stop()

    /** Releases the platform player entirely; the repository must not be used afterwards. */
    fun release()

    /** Applies the repeat preference to the player. */
    fun setRepeatMode(mode: RepeatMode)

    /** Applies the playback speed preference (e.g. 0.75, 1.0, 1.25). */
    fun setPlaybackSpeed(speed: Float)
}
