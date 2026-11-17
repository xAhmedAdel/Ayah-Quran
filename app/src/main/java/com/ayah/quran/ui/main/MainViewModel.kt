package com.ayah.quran.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.domain.model.ReadingMode
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.repository.ReaderPreferencesRepository
import com.ayah.quran.domain.usecase.ObserveThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * App-level state observed by [com.ayah.quran.MainActivity] for the lifetime
 * of the process: the active appearance and the reading mode used to route
 * reader entry points (mushaf pages vs adjustable text).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeThemeMode: ObserveThemeModeUseCase,
    readerPreferences: ReaderPreferencesRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    val readingMode: StateFlow<ReadingMode> = readerPreferences.settings
        .map { it.readingMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ReadingMode.MUSHAF,
        )
}
