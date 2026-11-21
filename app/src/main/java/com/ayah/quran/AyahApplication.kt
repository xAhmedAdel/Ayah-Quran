package com.ayah.quran

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ayah.quran.background.CompanionReminderNotifier
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.data.companion.CompanionContentImporter
import com.ayah.quran.data.quran.QuranImporter
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import com.ayah.quran.prayer.PrayerNotifications
import com.ayah.quran.prayer.PrayerTimeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    lateinit var companionContentImporter: CompanionContentImporter

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var prayerTimeManager: PrayerTimeManager

    @Inject
    lateinit var prayerNotifications: PrayerNotifications

    @Inject
    lateinit var companionReminderNotifier: CompanionReminderNotifier

    @Inject
    lateinit var companionRemindersRepository: CompanionRemindersRepository

    @Inject
    lateinit var companionReminderScheduler: CompanionReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    /** Applies the persisted app language process-wide (defaults to Arabic). */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        quranImporter.startAsync()
        companionContentImporter.startAsync()
        prayerNotifications.createChannels()
        prayerTimeManager.reconcile()
        companionReminderNotifier.createChannel()
        appScope.launch {
            try {
                val prefs = companionRemindersRepository.preferences.first()
                companionReminderScheduler.reconcileAll(prefs, replaceExisting = false)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w("AyahApplication", "Reminder reconciliation skipped on startup (${t.javaClass.simpleName})")
            }
        }
    }
}
