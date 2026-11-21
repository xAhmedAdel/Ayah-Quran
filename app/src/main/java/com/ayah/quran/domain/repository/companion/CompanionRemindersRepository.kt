package com.ayah.quran.domain.repository.companion

import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CompanionRemindersRepository {
    val preferences: Flow<CompanionRemindersPreferences>
    suspend fun setEnabled(type: CompanionReminderType, enabled: Boolean): CompanionReminderConfig
    suspend fun setTime(type: CompanionReminderType, hour: Int, minute: Int): CompanionReminderConfig
    suspend fun markHandled(type: CompanionReminderType, epochDay: Long, expectedVersion: Long)
    suspend fun markPermissionRequested()
}

interface CompanionReminderScheduler {
    suspend fun schedule(config: CompanionReminderConfig)
    suspend fun cancel(type: CompanionReminderType)
    suspend fun reconcileAll(
        preferences: CompanionRemindersPreferences,
        replaceExisting: Boolean = true,
    )
    suspend fun reconcile(
        type: CompanionReminderType,
        replaceExisting: Boolean = false,
    )
    suspend fun scheduleNextFromWorker(
        type: CompanionReminderType,
        expectedVersion: Long,
        currentWorkId: UUID,
    ): Boolean
}
