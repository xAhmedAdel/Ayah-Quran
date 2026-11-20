package com.ayah.quran.di

import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.dao.companion.AdhkarDao
import com.ayah.quran.core.database.dao.companion.HadithDao
import com.ayah.quran.core.database.dao.companion.TasbihDao
import com.ayah.quran.core.time.CompanionDateProvider
import com.ayah.quran.core.time.SystemCompanionDateProvider
import com.ayah.quran.data.companion.AdhkarRepositoryImpl
import com.ayah.quran.data.companion.DataStoreCompanionRemindersRepository
import com.ayah.quran.data.companion.DataStoreTasbihPreferencesRepository
import com.ayah.quran.data.companion.HadithRepositoryImpl
import com.ayah.quran.data.companion.TasbihRepositoryImpl
import com.ayah.quran.data.companion.WorkManagerCompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.AdhkarRepository
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import com.ayah.quran.domain.repository.companion.HadithRepository
import com.ayah.quran.domain.repository.companion.TasbihPreferencesRepository
import com.ayah.quran.domain.repository.companion.TasbihRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CompanionModule {

    @Binds
    @Singleton
    abstract fun bindAdhkarRepository(impl: AdhkarRepositoryImpl): AdhkarRepository

    @Binds
    @Singleton
    abstract fun bindTasbihRepository(impl: TasbihRepositoryImpl): TasbihRepository

    @Binds
    @Singleton
    abstract fun bindTasbihPreferencesRepository(
        impl: DataStoreTasbihPreferencesRepository,
    ): TasbihPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindHadithRepository(impl: HadithRepositoryImpl): HadithRepository

    @Binds
    @Singleton
    abstract fun bindCompanionDateProvider(impl: SystemCompanionDateProvider): CompanionDateProvider

    @Binds
    @Singleton
    abstract fun bindCompanionRemindersRepository(
        impl: DataStoreCompanionRemindersRepository,
    ): CompanionRemindersRepository

    @Binds
    @Singleton
    abstract fun bindCompanionReminderScheduler(
        impl: WorkManagerCompanionReminderScheduler,
    ): CompanionReminderScheduler

    companion object {
        @Provides
        fun provideAdhkarDao(database: AyahDatabase): AdhkarDao = database.adhkarDao()

        @Provides
        fun provideTasbihDao(database: AyahDatabase): TasbihDao = database.tasbihDao()

        @Provides
        fun provideHadithDao(database: AyahDatabase): HadithDao = database.hadithDao()
    }
}
