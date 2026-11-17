package com.ayah.quran.domain.model.audio

/**
 * Explicit playback state of the audio engine. Immutable; emitted by
 * [com.ayah.quran.domain.repository.AudioRepository.playbackState].
 */
sealed interface PlaybackState {

    /** No source prepared yet. */
    data object Idle : PlaybackState

    /** A source is being prepared. */
    data object Loading : PlaybackState

    /** Prepared and ready; playback has not started. */
    data object Ready : PlaybackState

    /** Actively playing. */
    data object Playing : PlaybackState

    /** Prepared and paused. */
    data object Paused : PlaybackState

    /** The player is buffering data. */
    data object Buffering : PlaybackState

    /** Playback of the prepared source finished naturally. */
    data object Ended : PlaybackState

    /** Preparation or playback failed; [reason] is user-presentable. */
    data class Error(val reason: String) : PlaybackState
}
