package com.ayah.quran.di

import com.ayah.quran.data.audio.AudioCatalogSource
import com.ayah.quran.data.audio.EmptyAudioCatalogSource
import com.ayah.quran.data.audio.ExoPlayerAudioRepository
import com.ayah.quran.data.audio.ReciterRepositoryImpl
import com.ayah.quran.data.audio.AudioPreferencesRepositoryImpl
import com.ayah.quran.domain.repository.AudioPreferencesRepository
import com.ayah.quran.domain.repository.AudioRepository
import com.ayah.quran.domain.repository.ReciterRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Audio foundation bindings (Phase 5).
 *
 * The catalog source is deliberately the empty implementation until licensed
 * recitation assets are integrated; swapping it later requires no changes
 * anywhere else.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioCatalogSource(
        implementation: EmptyAudioCatalogSource,
    ): AudioCatalogSource

    @Binds
    @Singleton
    abstract fun bindReciterRepository(
        implementation: ReciterRepositoryImpl,
    ): ReciterRepository

    @Binds
    @Singleton
    abstract fun bindAudioRepository(
        implementation: ExoPlayerAudioRepository,
    ): AudioRepository

    @Binds
    @Singleton
    abstract fun bindAudioPreferencesRepository(
        implementation: AudioPreferencesRepositoryImpl,
    ): AudioPreferencesRepository
}
