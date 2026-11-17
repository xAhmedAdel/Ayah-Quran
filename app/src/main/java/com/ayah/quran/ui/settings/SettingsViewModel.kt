package com.ayah.quran.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.usecase.ObserveThemeModeUseCase
import com.ayah.quran.domain.usecase.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Owns the settings state that is already functional (appearance). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            setThemeModeUseCase(mode)
        }
    }
}
