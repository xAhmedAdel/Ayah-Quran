package com.ayah.quran

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ayah.quran.data.quran.QuranImporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point; initializes the Hilt dependency graph, kicks off
 * the Quran dataset import on a background scope, and provides on-demand
 * WorkManager initialization with Hilt's worker factory.
 */
@HiltAndroidApp
class AyahApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var quranImporter: QuranImporter

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        quranImporter.startAsync()
    }
}
