package com.ayah.quran.data.companion

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.ayah.quran.domain.model.companion.CompanionReminderConfig
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreCompanionRemindersRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CompanionRemindersRepository {

    override val preferences: Flow<CompanionRemindersPreferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toReminderPreferences() }

    override suspend fun setEnabled(
        type: CompanionReminderType,
        enabled: Boolean,
    ): CompanionReminderConfig {
        dataStore.edit { prefs ->
            prefs[enabledKey(type)] = enabled
        }
        return preferences.first().configOf(type)
    }

    override suspend fun setTime(
        type: CompanionReminderType,
        hour: Int,
        minute: Int,
    ): CompanionReminderConfig {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        dataStore.edit { prefs ->
            prefs[hourKey(type)] = safeHour
            prefs[minuteKey(type)] = safeMinute
        }
        return preferences.first().configOf(type)
    }

    override suspend fun markPermissionRequested() {
        dataStore.edit { prefs ->
            prefs[KEY_PERMISSION_REQUESTED] = true
        }
    }

    private fun Preferences.toReminderPreferences(): CompanionRemindersPreferences =
        CompanionRemindersPreferences(
            morningAdhkar = configFrom(CompanionReminderType.MORNING_ADHKAR),
            eveningAdhkar = configFrom(CompanionReminderType.EVENING_ADHKAR),
            dailyReflection = configFrom(CompanionReminderType.DAILY_REFLECTION),
            permissionRequestedBefore = this[KEY_PERMISSION_REQUESTED] ?: false,
        )

    private fun Preferences.configFrom(type: CompanionReminderType): CompanionReminderConfig =
        CompanionReminderConfig(
            type = type,
            enabled = this[enabledKey(type)] ?: false,
            hour = (this[hourKey(type)] ?: type.defaultHour).coerceIn(0, 23),
            minute = (this[minuteKey(type)] ?: type.defaultMinute).coerceIn(0, 59),
        )

    private fun enabledKey(type: CompanionReminderType) =
        booleanPreferencesKey("companion_reminder_${type.key}_enabled")

    private fun hourKey(type: CompanionReminderType) =
        intPreferencesKey("companion_reminder_${type.key}_hour")

    private fun minuteKey(type: CompanionReminderType) =
        intPreferencesKey("companion_reminder_${type.key}_minute")

    companion object {
        val KEY_PERMISSION_REQUESTED = booleanPreferencesKey("companion_reminder_permission_requested")
    }
}
