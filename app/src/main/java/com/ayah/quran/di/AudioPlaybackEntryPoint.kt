package com.ayah.quran.di

import android.content.Context
import com.ayah.quran.data.audio.ExoPlayerAyahPlaylistPlayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Access to the audio playback singleton from non-Hilt contexts
 * (instrumentation tests) so tests can reset persisted playback state.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioPlaybackEntryPoint {

    fun ayahPlaylistPlayer(): ExoPlayerAyahPlaylistPlayer

    companion object {
        fun from(context: Context): AudioPlaybackEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AudioPlaybackEntryPoint::class.java,
            )
    }
}
