package com.ayah.quran.ui.prayer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayah.quran.prayer.AdhanPlaybackService
import com.ayah.quran.data.prayer.AdhanPreviewController
import com.ayah.quran.data.prayer.AdhanPreviewState
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.prayer.DiagnosticAlarmScheduler
import com.ayah.quran.data.prayer.PrayerSystemStatusProbe
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerStatusBoard
import com.ayah.quran.domain.model.prayer.PrayerStatusReducer
import com.ayah.quran.domain.model.prayer.VendorKind
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import com.ayah.quran.prayer.PrayerNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live permission/status board (Phase 14). Every status is RE-VERIFIED by
 * [refresh] (called on resume and after settings actions) — the UI never
 * shows success without an actual check.
 */
@HiltViewModel
class PrayerPermissionsViewModel @Inject constructor(
    private val preferencesRepository: PrayerPreferencesRepository,
    private val probe: PrayerSystemStatusProbe,
    private val diagnosticScheduler: DiagnosticAlarmScheduler,
    private val notifications: PrayerNotifications,
    private val previewController: AdhanPreviewController,
    val catalog: BundledAdhanCatalog,
) : ViewModel() {

    private val _refreshKey = MutableStateFlow(0)

    fun refresh() {
        _refreshKey.value += 1
    }

    val board: StateFlow<PrayerStatusBoard> = combine(
        preferencesRepository.preferences,
        preferencesRepository.onboardingCompleted,
        preferencesRepository.vendorGuidanceAcknowledged,
        _refreshKey,
    ) { prefs, onboardingDone, vendorDone, _ ->
        buildBoard(prefs, onboardingDone, vendorDone)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialBoard(),
    )

    val previewState: StateFlow<AdhanPreviewState> = previewController.state

    private fun buildBoard(
        prefs: PrayerPreferences,
        onboardingDone: Boolean,
        vendorDone: Boolean,
    ): PrayerStatusBoard = PrayerStatusBoard(
        notificationsGranted = probe.notificationsGranted(),
        adhanChannelEnabled = probe.adhanChannelEnabled(),
        exactAlarmsGranted = probe.exactAlarmsGranted(),
        vendorGuidanceAcknowledged = vendorDone,
        onboardingCompleted = onboardingDone,
        adhanConfig = prefs.adhanConfig,
        vendor = probe.vendorKind(),
    )

    private fun initialBoard(): PrayerStatusBoard = PrayerStatusBoard(
        notificationsGranted = false,
        adhanChannelEnabled = false,
        exactAlarmsGranted = false,
        vendorGuidanceAcknowledged = false,
        onboardingCompleted = false,
        adhanConfig = PrayerAdhanConfig(),
        vendor = probe.vendorKind(),
    )

    /** Eager flag for the app-start gate (True once finished/skipped). */
    val onboardingCompleted: StateFlow<Boolean> = preferencesRepository.onboardingCompleted
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    /** First unsatisfied onboarding step (1..4); 4 = essentials verified. */
    fun nextStep(board: PrayerStatusBoard): Int = PrayerStatusReducer.nextRequiredStep(
        board.notificationsGranted,
        board.adhanChannelEnabled,
        board.exactAlarmsGranted,
    )

    fun setOnboardingCompleted() {
        viewModelScope.launch { preferencesRepository.setOnboardingCompleted() }
    }

    fun acknowledgeVendorGuidance() {
        viewModelScope.launch { preferencesRepository.setVendorGuidanceAcknowledged() }
    }

    /** Immediate clear-labeled TEST notification on the real alert channel. */
    fun testNotification() = notifications.postTest()

    /** Starts the playback service in TEST mode — never touches scheduling. */
    fun testAdhan(context: Context) {
        val intent = android.content.Intent(context, AdhanPlaybackService::class.java)
            .putExtra(AdhanPlaybackService.EXTRA_PRAYER, Prayer.FAJR.name)
            .putExtra(AdhanPlaybackService.EXTRA_TEST, true)
        runCatching { context.startForegroundService(intent) }
    }

    /** Arms a REAL diagnostic alarm ~60s out. Returns whether it is exact. */
    fun scheduleDiagnosticAlarm(): Boolean = diagnosticScheduler.scheduleIn(60_000)

    fun cancelDiagnosticAlarm() = diagnosticScheduler.cancel()

    fun stopPreview() = previewController.stop()

    override fun onCleared() {
        previewController.stop()
        super.onCleared()
    }
}
