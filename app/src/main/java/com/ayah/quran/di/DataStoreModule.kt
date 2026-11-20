package com.ayah.quran.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val DATASTORE_TAG = "AyahDataStore"

internal fun createPreferencesCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler(
        produceNewData = { exception ->
            Log.w(
                DATASTORE_TAG,
                "Recovered corrupted preferences store with safe defaults (${exception.javaClass.simpleName})",
            )
            emptyPreferences()
        },
    )

private val Context.ayahDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ayah_preferences",
    corruptionHandler = createPreferencesCorruptionHandler(),
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.ayahDataStore
}
