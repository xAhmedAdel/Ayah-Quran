package com.ayah.quran.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.background.CompanionReminderNotifier
import com.ayah.quran.domain.model.companion.CompanionReminderType
import com.ayah.quran.domain.model.companion.CompanionRemindersPreferences
import com.ayah.quran.domain.repository.companion.CompanionReminderScheduler
import com.ayah.quran.domain.repository.companion.CompanionRemindersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompanionRemindersUiState(
    val preferences: CompanionRemindersPreferences = CompanionRemindersPreferences(),
    val notificationsAllowed: Boolean = true,
)

@HiltViewModel
class CompanionRemindersViewModel internal constructor(
    private val repository: CompanionRemindersRepository,
    private val scheduler: CompanionReminderScheduler,
    private val permissionChecker: () -> Boolean,
) : ViewModel() {

    @Inject
    constructor(
        repository: CompanionRemindersRepository,
        scheduler: CompanionReminderScheduler,
        notifier: CompanionReminderNotifier,
    ) : this(
        repository = repository,
        scheduler = scheduler,
        permissionChecker = notifier::canPostNotifications,
    )

    private val permissionGrantedFlow = MutableStateFlow(permissionChecker())

    val uiState: StateFlow<CompanionRemindersUiState> = combine(
        repository.preferences,
        permissionGrantedFlow,
    ) { prefs, allowed ->
        CompanionRemindersUiState(
            preferences = prefs,
            notificationsAllowed = allowed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CompanionRemindersUiState(notificationsAllowed = permissionChecker()),
    )

    fun refreshPermissionStatus() {
        permissionGrantedFlow.value = permissionChecker()
    }

    fun setReminderEnabled(type: CompanionReminderType, enabled: Boolean) {
        viewModelScope.launch {
            val updated = repository.setEnabled(type, enabled)
            if (updated.enabled) {
                scheduler.schedule(updated)
            } else {
                scheduler.cancel(type)
            }
        }
    }

    fun setReminderTime(type: CompanionReminderType, hour: Int, minute: Int) {
        viewModelScope.launch {
            val updated = repository.setTime(type, hour, minute)
            if (updated.enabled) {
                scheduler.schedule(updated)
            }
        }
    }

    fun markPermissionRequested() {
        viewModelScope.launch {
            repository.markPermissionRequested()
        }
    }
}
