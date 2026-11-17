package com.ayah.quran.di

import com.ayah.quran.data.prayer.AdhanPrayerTimesCalculator
import com.ayah.quran.data.prayer.AlarmPrayerAlarmScheduler
import com.ayah.quran.data.prayer.PrayerPreferencesRepositoryImpl
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Prayer-times bindings (Phase 12): calculator, scheduler, preferences. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrayerModule {

    @Binds
    @Singleton
    abstract fun bindPrayerTimesCalculator(impl: AdhanPrayerTimesCalculator): PrayerTimesCalculator

    @Binds
    @Singleton
    abstract fun bindPrayerAlarmScheduler(impl: AlarmPrayerAlarmScheduler): PrayerAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindPrayerPreferencesRepository(
        impl: PrayerPreferencesRepositoryImpl,
    ): PrayerPreferencesRepository
}
