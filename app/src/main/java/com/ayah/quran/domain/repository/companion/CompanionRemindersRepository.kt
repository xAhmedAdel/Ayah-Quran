package com.ayah.quran.domain.repository.companion

import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import kotlinx.coroutines.flow.Flow

interface CompanionRemindersRepository {
    val preferences: Flow<CompanionRemindersPreferences>
    suspend fun setEnabled(type: CompanionReminderType, enabled: Boolean): CompanionReminderConfig
    suspend fun setTime(type: CompanionReminderType, hour: Int, minute: Int): CompanionReminderConfig
    suspend fun markPermissionRequested()
}

interface CompanionReminderScheduler {
    fun schedule(config: CompanionReminderConfig)
    fun cancel(type: CompanionReminderType)
    fun reconcileAll(preferences: CompanionRemindersPreferences)
}
