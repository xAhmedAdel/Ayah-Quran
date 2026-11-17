package com.ayah.quran.di

import com.ayah.quran.data.quran.BookmarkRepositoryImpl
import com.ayah.quran.data.quran.QuranRepositoryImpl
import com.ayah.quran.data.quran.ReadingProgressRepositoryImpl
import com.ayah.quran.data.settings.DataStoreUserPreferencesRepository
import com.ayah.quran.data.wallpapers.WallpaperRepositoryImpl
import com.ayah.quran.data.wallpapers.WallpaperSchedulingRepositoryImpl
import com.ayah.quran.data.wallpapers.WallpaperSettingsRepositoryImpl
import com.ayah.quran.domain.repository.BookmarkRepository
import com.ayah.quran.domain.repository.QuranRepository
import com.ayah.quran.domain.repository.ReadingProgressRepository
import com.ayah.quran.domain.repository.UserPreferencesRepository
import com.ayah.quran.domain.repository.WallpaperRepository
import com.ayah.quran.domain.repository.WallpaperSchedulingRepository
import com.ayah.quran.domain.repository.WallpaperSettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Interface-to-implementation bindings for repositories. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        implementation: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindQuranRepository(
        implementation: QuranRepositoryImpl,
    ): QuranRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(
        implementation: BookmarkRepositoryImpl,
    ): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(
        implementation: ReadingProgressRepositoryImpl,
    ): ReadingProgressRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(
        implementation: WallpaperRepositoryImpl,
    ): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperSettingsRepository(
        implementation: WallpaperSettingsRepositoryImpl,
    ): WallpaperSettingsRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperSchedulingRepository(
        implementation: WallpaperSchedulingRepositoryImpl,
    ): WallpaperSchedulingRepository
}
