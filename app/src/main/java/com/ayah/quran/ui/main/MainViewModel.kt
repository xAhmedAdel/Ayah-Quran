package com.ayah.quran.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.usecase.ObserveThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level state observed by [com.ayah.quran.MainActivity] for the lifetime
 * of the process (currently: the active appearance).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeThemeMode: ObserveThemeModeUseCase,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )
}
