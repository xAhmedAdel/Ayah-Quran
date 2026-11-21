package com.ayah.quran.prayer

import android.app.AlarmManager
import android.content.Intent
import com.ayah.quran.domain.model.prayer.AsrMethod
import com.ayah.quran.domain.model.prayer.CalculationMethod
import com.ayah.quran.domain.model.prayer.DayPrayerTimes
import com.ayah.quran.domain.model.prayer.HighLatitudeRulePref
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.PrayerAdhanConfig
import com.ayah.quran.domain.model.prayer.PrayerAdjustments
import com.ayah.quran.domain.model.prayer.PrayerLocation
import com.ayah.quran.domain.model.prayer.PrayerVoiceSelection
import com.ayah.quran.domain.prayer.PrayerAlarm
import com.ayah.quran.domain.prayer.PrayerAlarmScheduler
import com.ayah.quran.domain.prayer.PrayerTimesCalculator
import com.ayah.quran.domain.repository.prayer.PrayerPreferences
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PrayerReceiverAndAdhanLifecycleTest {

    @Test
    fun prayerSystemReceiverAcceptsAllExpectedSystemAndExactAlarmActions() {
        val expected = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        expected.forEach { action ->
            assertTrue("Expected supported action: $action", PrayerSystemReceiver.isSupportedAction(action))
        }
        assertFalse(PrayerSystemReceiver.isSupportedAction(null))
        assertFalse(PrayerSystemReceiver.isSupportedAction("com.example.UNKNOWN"))
    }

    @Test
    fun systemReceiverCompletesAsyncReconcileBeforeFinishingBroadcast() = runTest {
        val events = mutableListOf<String>()
        PrayerSystemReceiver.executeReconciliation(
            action = Intent.ACTION_BOOT_COMPLETED,
            reconcile = {
                delay(50)
                events += "reconciled"
            },
            onFinish = {
                events += "finished"
            },
        )
        assertEquals(listOf("reconciled", "finished"), events)
    }

    @Test
    fun systemReceiverStillFinishesBroadcastWhenReconcileThrows() = runTest {
        var finished = false
        PrayerSystemReceiver.executeReconciliation(
            action = AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            reconcile = { error("simulated storage error") },
            onFinish = { finished = true },
        )
        assertTrue(finished)
    }

    @Test
    fun prayerTimeManagerReconcileNowSchedulesNextAlarmAndReflectsDeniedExactPermission() = runTest {
        val scheduled = mutableListOf<PrayerAlarm>()
        val fakeScheduler = object : PrayerAlarmScheduler {
            var grantExact = false
            override fun canScheduleExact(): Boolean = grantExact
            override fun schedule(alarm: PrayerAlarm): Boolean {
                scheduled += alarm
                return grantExact
            }
            override fun cancel() = Unit
        }
        val fakeCalculator = object : PrayerTimesCalculator {
            override fun calculate(
                date: LocalDate,
                latitude: Double,
                longitude: Double,
                timezoneId: ZoneId,
                method: CalculationMethod,
                asrMethod: AsrMethod,
                highLatitudeRule: HighLatitudeRulePref,
                adjustments: PrayerAdjustments,
            ): DayPrayerTimes = DayPrayerTimes(
                date = date,
                fajr = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 4, 30),
                sunrise = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 6, 0),
                dhuhr = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 12, 0),
                asr = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 15, 30),
                maghrib = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 18, 0),
                isha = LocalDateTime.of(date.year, date.month, date.dayOfMonth, 19, 30),
            )
        }
        val prefsFlow = MutableStateFlow(
            PrayerPreferences(
                location = PrayerLocation(
                    cityAr = "القاهرة",
                    cityEn = "Cairo",
                    countryAr = "مصر",
                    countryEn = "Egypt",
                    latitude = 30.0444,
                    longitude = 31.2357,
                    timezoneId = "UTC",
                ),
                autoLocationEnabled = false,
                method = CalculationMethod.EGYPTIAN,
                asrMethod = AsrMethod.STANDARD,
                highLatitudeRule = HighLatitudeRulePref.ANGLE_BASED,
                adjustments = PrayerAdjustments(),
                adhanConfig = PrayerAdhanConfig(),
                voiceSelection = PrayerVoiceSelection(),
                hijriAdjustmentDays = 0,
                customAdhanAudioUri = null,
            ),
        )
        val fakeRepo = object : PrayerPreferencesRepository {
            override val preferences: Flow<PrayerPreferences> = prefsFlow
            override val onboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
            override val vendorGuidanceAcknowledged: Flow<Boolean> = MutableStateFlow(true)
            override val voiceSelection: Flow<PrayerVoiceSelection> = MutableStateFlow(PrayerVoiceSelection())
            override val downloadedAdhanIds: Flow<Set<String>> = MutableStateFlow(emptySet())
            override suspend fun setLocation(location: PrayerLocation) = Unit
            override suspend fun setAutoLocationEnabled(enabled: Boolean) = Unit
            override suspend fun setMethod(method: CalculationMethod) = Unit
            override suspend fun setAsrMethod(asr: AsrMethod) = Unit
            override suspend fun setHighLatitudeRule(rule: HighLatitudeRulePref) = Unit
            override suspend fun setAdjustments(adjustments: PrayerAdjustments) = Unit
            override suspend fun setAdhanConfig(config: PrayerAdhanConfig) = Unit
            override suspend fun setCustomAdhanAudioUri(uri: String?) = Unit
            override suspend fun setVoice(prayer: Prayer, recordingId: String) = Unit
            override suspend fun setAdhanDownloaded(id: String, downloaded: Boolean) = Unit
            override suspend fun setHijriAdjustmentDays(days: Int) = Unit
            override suspend fun setOnboardingCompleted() = Unit
            override suspend fun setVendorGuidanceAcknowledged() = Unit
            override suspend fun tryMarkAlarmOccurrenceFired(
                occurrenceKey: String,
                retentionCutoffDate: LocalDate?,
            ): Boolean = true
        }

        val manager = PrayerTimeManager(fakeRepo, fakeCalculator, fakeScheduler)
        manager.onAlarmFiredNow()

        assertEquals(1, scheduled.size)
        assertFalse(manager.exactAlarmGranted.value)

        fakeScheduler.grantExact = true
        manager.reconcileNow()
        assertEquals(2, scheduled.size)
        assertTrue(manager.exactAlarmGranted.value)
    }

    @Test
    fun adhanPlaybackErrorCleansUpPlayerFocusActiveFlagAndStopsService() {
        var active = false
        var abandonedFocus = 0
        var stoppedSelf = 0
        var fallbackNotified = 0
        var releasedPlayers = 0
        var errorCallback: ((Throwable?) -> Unit)? = null

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = { abandonedFocus++ },
            onFallbackNotify = { fallbackNotified++ },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, _, onError ->
                errorCallback = onError
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }
                }
            },
        )

        val token = controller.nextGeneration()
        controller.startPlayback(token, Prayer.MAGHRIB, "content://revoked/adhan.mp3")
        assertTrue(active)

        errorCallback?.invoke(IllegalStateException("source error"))

        assertFalse(active)
        assertEquals(1, releasedPlayers)
        assertEquals(1, fallbackNotified)
        assertEquals(1, stoppedSelf)
        assertTrue(abandonedFocus >= 1)
    }

    @Test
    fun adhanReEntryCleansUpPreviousPlayerBeforeCreatingNewPlayer() {
        var active = false
        val releasedOrder = mutableListOf<String>()
        var createdCount = 0

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { uri, _, _ ->
                createdCount++
                val id = "player#$createdCount($uri)"
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedOrder += id
                    }
                }
            },
        )

        val firstToken = controller.nextGeneration()
        controller.startPlayback(firstToken, Prayer.FAJR, "uri://first")
        assertTrue(active)

        val secondToken = controller.nextGeneration()
        controller.startPlayback(secondToken, Prayer.DHUHR, "uri://second")

        assertEquals(listOf("player#1(uri://first)"), releasedOrder)
        assertTrue(active)

        controller.stopAll()
        assertFalse(active)
        assertEquals(listOf("player#1(uri://first)", "player#2(uri://second)"), releasedOrder)
    }

    @Test
    fun staleGenerationTokenIsIgnoredAfterStopAll() {
        var created = 0
        var active = false
        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                created++
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                }
            },
        )

        val staleToken = controller.nextGeneration()
        controller.stopAll()
        controller.startPlayback(staleToken, Prayer.ASR, "uri://late")

        assertEquals(0, created)
        assertFalse(active)
    }
}
