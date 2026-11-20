package com.ayah.quran.di

import android.content.Context
import androidx.room.Room
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.AyahMigrations
import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.BookmarkDao
import com.ayah.quran.core.database.dao.ReadingProgressDao
import com.ayah.quran.core.database.dao.SurahDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAyahDatabase(@ApplicationContext context: Context): AyahDatabase =
        Room.databaseBuilder(context, AyahDatabase::class.java, AyahDatabase.NAME)
            .addMigrations(AyahMigrations.MIGRATION_3_4)
            .build()

    @Provides
    @Singleton
    fun provideQuranTextParser(): com.ayah.quran.data.quran.QuranTextParser =
        com.ayah.quran.data.quran.QuranTextParser()

    @Provides
    fun provideSurahDao(database: AyahDatabase): SurahDao = database.surahDao()

    @Provides
    fun provideAyahDao(database: AyahDatabase): AyahDao = database.ayahDao()

    @Provides
    fun provideBookmarkDao(database: AyahDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideReadingProgressDao(database: AyahDatabase): ReadingProgressDao =
        database.readingProgressDao()

    @Provides
    fun provideWallpaperHistoryDao(database: AyahDatabase): com.ayah.quran.core.database.dao.WallpaperHistoryDao =
        database.wallpaperHistoryDao()

    @Provides
    fun provideSelectionHistoryDao(database: AyahDatabase): com.ayah.quran.core.database.dao.SelectionHistoryDao =
        database.selectionHistoryDao()
}
