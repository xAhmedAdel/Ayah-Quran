package com.ayah.quran.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.BuildConfig
import com.ayah.quran.core.locale.AppLocale
import com.ayah.quran.domain.update.AppUpdateInfo
import com.ayah.quran.data.update.AppUpdateRepository
import com.ayah.quran.data.update.AppUpdateState
import com.ayah.quran.domain.model.ThemeMode
import com.ayah.quran.domain.usecase.ObserveAppLanguageUseCase
import com.ayah.quran.domain.usecase.ObserveThemeModeUseCase
import com.ayah.quran.domain.usecase.SetAppLanguageUseCase
import com.ayah.quran.domain.usecase.SetThemeModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Owns the settings state that is already functional: appearance (Phase 1)
 * and app language (Phase 10).
 *
 * [languageApplied] fires once after a language choice is persisted; the
 * screen recreates the activity in response so the new locale applies
 * immediately.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeMode: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    observeAppLanguage: ObserveAppLanguageUseCase,
    private val setAppLanguageUseCase: SetAppLanguageUseCase,
    private val updateRepository: AppUpdateRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = observeThemeMode()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    /** Resolved tag; null storage resolves to the Arabic default. */
    val languageTag: StateFlow<String> = observeAppLanguage()
        .map { it ?: AppLocale.ARABIC }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppLocale.ARABIC,
        )

    private val _languageApplied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val languageApplied: SharedFlow<Unit> = _languageApplied

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            setThemeModeUseCase(mode)
        }
    }

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            setAppLanguageUseCase(tag)
            _languageApplied.tryEmit(Unit)
        }
    }

    // ---- in-app updater (GitHub Releases) ----

    val updateState: StateFlow<AppUpdateState> = updateRepository.state

    /** True once per process so opening Settings doesn't spam the GitHub API. */
    private var autoChecked = false

    fun checkForUpdate(force: Boolean = false) {
        if (!force && autoChecked) return
        autoChecked = true
        updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
    }

    /** A previously downloaded update APK, if the app was killed mid-flow. */
    fun pendingUpdateFile(): File? = updateRepository.pendingUpdateFile()

    fun downloadUpdate(info: AppUpdateInfo) = updateRepository.downloadUpdate(info)
}
