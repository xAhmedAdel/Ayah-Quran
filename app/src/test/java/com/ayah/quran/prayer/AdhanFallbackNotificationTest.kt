package com.ayah.quran.prayer

import com.ayah.quran.domain.model.prayer.AdhanMode
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AdhanFallbackNotificationTest {

    @Test
    fun fallbackModeAndChannelMappingResolveToAudibleAlertChannel() {
        assertEquals(AdhanMode.NOTIFICATION_SOUND, PrayerNotifications.FULL_ADHAN_FALLBACK_MODE)
        assertEquals(
            PrayerNotifications.CHANNEL_ALERT,
            PrayerNotifications.channelIdFor(PrayerNotifications.FULL_ADHAN_FALLBACK_MODE),
        )
        assertEquals(
            PrayerNotifications.CHANNEL_ALERT,
            PrayerNotifications.channelIdFor(AdhanMode.NOTIFICATION_SOUND),
        )
        assertEquals(
            PrayerNotifications.CHANNEL_SILENT,
            PrayerNotifications.channelIdFor(AdhanMode.SILENT),
        )
        assertEquals(
            PrayerNotifications.CHANNEL_SILENT,
            PrayerNotifications.channelIdFor(AdhanMode.FULL_ADHAN),
        )
    }

    @Test
    fun receiverFallbackPostsAudibleAlertAndLogsPostedOnlyWhenNotificationSucceeds() {
        val wakeLock = createWakeLock()
        val postedChannels = mutableListOf<String>()
        val suppressedReasons = mutableListOf<String>()
        val fallbackModes = mutableListOf<AdhanMode>()

        val succeeded = PrayerAlarmReceiver.dispatchFullAdhan(
            voiceId = "default",
            correlationId = "fgs-allowed-fallback",
            wakeLock = wakeLock,
            startService = { throw IllegalStateException("ForegroundServiceStartNotAllowed") },
            onFallbackAlert = { mode ->
                fallbackModes += mode
                true
            },
            onLogNotificationPosted = { postedChannels += it },
            onLogNotificationSuppressed = { suppressedReasons += it },
        )

        assertTrue(succeeded)
        assertEquals(listOf(AdhanMode.NOTIFICATION_SOUND), fallbackModes)
        assertEquals(listOf(PrayerNotifications.CHANNEL_ALERT), postedChannels)
        assertTrue(suppressedReasons.isEmpty())
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        postedChannels.clear()
        suppressedReasons.clear()
        fallbackModes.clear()

        val permissionDenied = PrayerAlarmReceiver.dispatchFullAdhan(
            voiceId = "default",
            correlationId = "fgs-denied-notif",
            wakeLock = wakeLock,
            startService = { throw SecurityException("fgs blocked") },
            onFallbackAlert = { mode ->
                fallbackModes += mode
                false
            },
            onLogNotificationPosted = { postedChannels += it },
            onLogNotificationSuppressed = { suppressedReasons += it },
        )

        assertFalse(permissionDenied)
        assertEquals(listOf(AdhanMode.NOTIFICATION_SOUND), fallbackModes)
        assertTrue(postedChannels.isEmpty())
        assertEquals(1, suppressedReasons.size)
        assertTrue(suppressedReasons.single().contains(PrayerNotifications.CHANNEL_ALERT))
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        postedChannels.clear()
        suppressedReasons.clear()

        val postThrew = PrayerAlarmReceiver.dispatchFullAdhan(
            voiceId = "default",
            correlationId = "fgs-post-threw",
            wakeLock = wakeLock,
            startService = { throw IllegalStateException("fgs blocked") },
            onFallbackAlert = { throw RuntimeException("notification service failure") },
            onLogNotificationPosted = { postedChannels += it },
            onLogNotificationSuppressed = { suppressedReasons += it },
        )

        assertFalse(postThrew)
        assertTrue(postedChannels.isEmpty())
        assertEquals(1, suppressedReasons.size)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        postedChannels.clear()
        suppressedReasons.clear()

        PrayerAlarmReceiver.routePrayerAlert(
            mode = AdhanMode.NOTIFICATION_SOUND,
            voiceId = "default",
            correlationId = "direct-denied",
            wakeLock = wakeLock,
            onPostNotification = { false },
            onStartFullAdhanService = { throw AssertionError("should not start service") },
            onFallbackAlert = { throw AssertionError("should not fallback") },
            onLogNotificationPosted = { postedChannels += it },
            onLogNotificationSuppressed = { suppressedReasons += it },
        )
        assertTrue(postedChannels.isEmpty())
        assertEquals(1, suppressedReasons.size)

        PrayerAlarmReceiver.routePrayerAlert(
            mode = AdhanMode.SILENT,
            voiceId = "default",
            correlationId = "direct-allowed",
            wakeLock = wakeLock,
            onPostNotification = { true },
            onStartFullAdhanService = { throw AssertionError("should not start service") },
            onFallbackAlert = { throw AssertionError("should not fallback") },
            onLogNotificationPosted = { postedChannels += it },
            onLogNotificationSuppressed = { suppressedReasons += it },
        )
        assertEquals(listOf(PrayerNotifications.CHANNEL_SILENT), postedChannels)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun receiverDispatchRethrowsFatalErrorWithoutConvertingToFallback() {
        val wakeLock = createWakeLock()
        var fallbackCalls = 0
        var postedLogs = 0

        try {
            PrayerAlarmReceiver.dispatchFullAdhan(
                voiceId = "default",
                correlationId = "start-fatal",
                wakeLock = wakeLock,
                startService = { throw AssertionError("fatal start error") },
                onFallbackAlert = {
                    fallbackCalls++
                    true
                },
                onLogNotificationPosted = { postedLogs++ },
            )
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertEquals(0, fallbackCalls)
        assertEquals(0, postedLogs)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        try {
            PrayerAlarmReceiver.dispatchFullAdhan(
                voiceId = "default",
                correlationId = "fallback-fatal",
                wakeLock = wakeLock,
                startService = { throw IllegalStateException("fgs rejected") },
                onFallbackAlert = { throw AssertionError("fatal fallback error") },
                onLogNotificationPosted = { postedLogs++ },
            )
            fail("Expected AssertionError from fallback to propagate")
        } catch (_: AssertionError) {
        }
        assertEquals(0, postedLogs)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun receiverDuplicateDeliveryPreventsSecondFallbackAndContinuesRescheduleWhenFallbackThrows() = runTest {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 21)
        val fajrMillis = day.atTime(4, 30).toInstant(zone.rules.getOffset(day.atTime(4, 30))).toEpochMilli()
        val clock = FixedClock(Instant.ofEpochMilli(fajrMillis), zone)
        val repo = FakePrayerPreferencesRepository(
            location = testLocation(zone.id),
            adhanConfig = PrayerAdhanConfig(fajr = AdhanMode.FULL_ADHAN),
        )
        val scheduled = mutableListOf<PrayerAlarm>()
        val manager = PrayerTimeManager(repo, fixedCalculator(), recordingScheduler(scheduled), clock)
        val guard = PrayerAlarmOccurrenceGuard(repo)
        val wakeLock = createWakeLock()
        val fallbackModes = mutableListOf<AdhanMode>()
        val finishCount = AtomicInteger(0)

        repeat(2) {
            PrayerAlarmReceiver.handleAlarm(
                prayer = Prayer.FAJR,
                dayKey = day.toString(),
                scheduledAtMillis = fajrMillis,
                correlationId = "dup-fajr",
                preferencesRepository = repo,
                occurrenceGuard = guard,
                manager = manager,
                onDispatch = { mode, voiceId ->
                    PrayerAlarmReceiver.routePrayerAlert(
                        mode = mode,
                        voiceId = voiceId,
                        correlationId = "dup-fajr",
                        wakeLock = wakeLock,
                        onPostNotification = { true },
                        onStartFullAdhanService = {
                            throw IllegalStateException("ForegroundServiceStartNotAllowed")
                        },
                        onFallbackAlert = { fallbackMode ->
                            fallbackModes += fallbackMode
                            true
                        },
                    )
                },
                onFinish = { finishCount.incrementAndGet() },
                clock = clock,
            )
        }

        assertEquals(listOf(AdhanMode.NOTIFICATION_SOUND), fallbackModes)
        assertEquals(2, finishCount.get())
        assertEquals(2, scheduled.size)
        assertTrue(scheduled.all { it.prayer == Prayer.DHUHR })
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val dhuhrMillis = day.atTime(12, 0).toInstant(zone.rules.getOffset(day.atTime(12, 0))).toEpochMilli()
        val dhuhrClock = FixedClock(Instant.ofEpochMilli(dhuhrMillis), zone)
        val repo2 = FakePrayerPreferencesRepository(
            location = testLocation(zone.id),
            adhanConfig = PrayerAdhanConfig(dhuhr = AdhanMode.FULL_ADHAN),
        )
        val scheduled2 = mutableListOf<PrayerAlarm>()
        val manager2 = PrayerTimeManager(repo2, fixedCalculator(), recordingScheduler(scheduled2), dhuhrClock)
        val guard2 = PrayerAlarmOccurrenceGuard(repo2)
        val finish2 = AtomicInteger(0)

        PrayerAlarmReceiver.handleAlarm(
            prayer = Prayer.DHUHR,
            dayKey = day.toString(),
            scheduledAtMillis = dhuhrMillis,
            correlationId = "dhuhr-post-throw",
            preferencesRepository = repo2,
            occurrenceGuard = guard2,
            manager = manager2,
            onDispatch = { mode, voiceId ->
                PrayerAlarmReceiver.routePrayerAlert(
                    mode = mode,
                    voiceId = voiceId,
                    correlationId = "dhuhr-post-throw",
                    wakeLock = wakeLock,
                    onPostNotification = { true },
                    onStartFullAdhanService = { throw IllegalStateException("fgs blocked") },
                    onFallbackAlert = { throw RuntimeException("notif post error") },
                )
            },
            onFinish = { finish2.incrementAndGet() },
            clock = dhuhrClock,
        )

        assertEquals(1, finish2.get())
        assertEquals(1, scheduled2.size)
        assertEquals(Prayer.ASR, scheduled2.single().prayer)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun servicePostFallbackAlertUsesAudibleModeAndHandlesDeniedOrThrowingPostWithoutSwallowingError() {
        val posted = mutableListOf<Pair<Prayer, AdhanMode>>()

        assertTrue(
            AdhanPlaybackService.postFallbackAlert(Prayer.FAJR) { prayer, mode ->
                posted += prayer to mode
                true
            },
        )
        assertEquals(listOf(Prayer.FAJR to AdhanMode.NOTIFICATION_SOUND), posted)

        assertFalse(
            AdhanPlaybackService.postFallbackAlert(Prayer.DHUHR) { prayer, mode ->
                posted += prayer to mode
                false
            },
        )
        assertEquals(Prayer.DHUHR to AdhanMode.NOTIFICATION_SOUND, posted.last())

        assertFalse(
            AdhanPlaybackService.postFallbackAlert(Prayer.ASR) { _, _ ->
                throw RuntimeException("oem notification error")
            },
        )

        try {
            AdhanPlaybackService.postFallbackAlert(Prayer.MAGHRIB) { _, _ ->
                throw AssertionError("fatal error during fallback post")
            }
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
    }

    @Test
    fun beginReEntrantStartFallbackPostsAudibleAlertAndSurvivesPostExceptionWithoutSwallowingError() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val posted = mutableListOf<Pair<Prayer, AdhanMode>>()
        var stoppedIfIdle = 0
        val controller = createBasicController(tracker)

        val l1 = wakeLock.acquireHandover()
        val r1 = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.FAJR,
            incomingWakeLockToken = l1.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw IllegalStateException("ForegroundServiceStartNotAllowed") },
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { p ->
                AdhanPlaybackService.postFallbackAlert(p) { prayer, mode ->
                    posted += prayer to mode
                    true
                }
            },
            onCancelPriorJobs = {},
            onStopSelfIfIdle = { stoppedIfIdle++ },
        )
        assertEquals(null, r1)
        assertEquals(listOf(Prayer.FAJR to AdhanMode.NOTIFICATION_SOUND), posted)
        assertEquals(1, stoppedIfIdle)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val l2 = wakeLock.acquireHandover()
        val r2 = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.DHUHR,
            incomingWakeLockToken = l2.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw SecurityException("fgs denied") },
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { throw RuntimeException("fallback crash") },
            onCancelPriorJobs = {},
            onStopSelfIfIdle = { stoppedIfIdle++ },
        )
        assertEquals(null, r2)
        assertEquals(2, stoppedIfIdle)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val l3 = wakeLock.acquireHandover()
        try {
            AdhanPlaybackService.beginReEntrantStart(
                prayer = Prayer.ASR,
                incomingWakeLockToken = l3.tokenId,
                lifecycleController = controller,
                handoverTokens = tracker,
                onEnterForeground = { throw SecurityException("fgs denied") },
                onReleaseIncomingToken = { wakeLock.releaseToken(it) },
                onFallbackNotify = { throw AssertionError("fatal post error") },
                onCancelPriorJobs = {},
                onStopSelfIfIdle = { stoppedIfIdle++ },
            )
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertEquals(3, stoppedIfIdle)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val l4 = wakeLock.acquireHandover()
        val postedBeforeFatalFgs = posted.size
        try {
            AdhanPlaybackService.beginReEntrantStart(
                prayer = Prayer.MAGHRIB,
                incomingWakeLockToken = l4.tokenId,
                lifecycleController = controller,
                handoverTokens = tracker,
                onEnterForeground = { throw AssertionError("fatal enterForeground") },
                onReleaseIncomingToken = { wakeLock.releaseToken(it) },
                onFallbackNotify = { p -> posted += p to AdhanMode.NOTIFICATION_SOUND },
                onCancelPriorJobs = {},
                onStopSelfIfIdle = { stoppedIfIdle++ },
            )
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertEquals(postedBeforeFatalFgs, posted.size)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun allSevenControllerFailurePathsPostAudibleFallbackOnceAndCleanUpEvenWhenPostThrows() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val posted = mutableListOf<Pair<Prayer, AdhanMode>>()
        var active = false
        var focusGranted = true
        var throwInCreate = false
        var barrierResult = true
        var stoppedSelf = 0
        var releasedPlayers = 0
        var watchdogsCancelled = 0
        var errorCallback: ((Throwable?) -> Unit)? = null
        var postBehavior: (Prayer, AdhanMode) -> Boolean = { prayer, mode ->
            posted += prayer to mode
            true
        }

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { focusGranted },
            onAbandonFocus = {},
            onFallbackNotify = { prayer ->
                AdhanPlaybackService.postFallbackAlert(prayer, postBehavior)
            },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, _, onError ->
                if (throwInCreate) throw IllegalStateException("create failed")
                errorCallback = onError
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }

                    override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                        if (barrierResult) action()
                        return barrierResult
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = { watchdogsCancelled++ },
            onCancelMaxDurationWatchdog = { watchdogsCancelled++ },
            onCancelTransientFocusWatchdog = { watchdogsCancelled++ },
        )

        fun startGen(prayer: Prayer, uri: String?): Long {
            val lease = wakeLock.acquireHandover()
            val gen = controller.nextGeneration()
            tracker.register(gen, lease.tokenId)
            controller.startPlayback(gen, prayer, uri)
            return gen
        }

        startGen(Prayer.FAJR, null)
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        focusGranted = false
        startGen(Prayer.DHUHR, "file:///ok.mp3")
        focusGranted = true
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        throwInCreate = true
        startGen(Prayer.ASR, "file:///ok.mp3")
        throwInCreate = false
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startGen(Prayer.MAGHRIB, "file:///corrupt.mp3")
        errorCallback?.invoke(RuntimeException("codec error"))
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        barrierResult = false
        val g5 = startGen(Prayer.ISHA, "file:///ok.mp3")
        controller.onIsPlayingStarted(g5)
        barrierResult = true
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val g6 = startGen(Prayer.FAJR, "file:///slow.mp3")
        assertTrue(controller.onPreparationTimeout(g6, Prayer.FAJR))
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startGen(Prayer.DHUHR, "file:///focus-lost.mp3")
        controller.onFocusLossPermanent()
        assertFalse(active)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        assertEquals(
            listOf(
                Prayer.FAJR to AdhanMode.NOTIFICATION_SOUND,
                Prayer.DHUHR to AdhanMode.NOTIFICATION_SOUND,
                Prayer.ASR to AdhanMode.NOTIFICATION_SOUND,
                Prayer.MAGHRIB to AdhanMode.NOTIFICATION_SOUND,
                Prayer.ISHA to AdhanMode.NOTIFICATION_SOUND,
                Prayer.FAJR to AdhanMode.NOTIFICATION_SOUND,
                Prayer.DHUHR to AdhanMode.NOTIFICATION_SOUND,
            ),
            posted,
        )
        assertEquals(7, stoppedSelf)
        assertEquals(4, releasedPlayers)
        assertTrue(watchdogsCancelled >= 21)

        postBehavior = { _, _ -> throw RuntimeException("post threw") }
        startGen(Prayer.ASR, "file:///post-throws.mp3")
        errorCallback?.invoke(RuntimeException("player died"))
        assertFalse(active)
        assertEquals(8, stoppedSelf)
        assertEquals(5, releasedPlayers)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        postBehavior = { _, _ -> throw AssertionError("fatal post error") }
        startGen(Prayer.MAGHRIB, "file:///post-fatal.mp3")
        try {
            errorCallback?.invoke(RuntimeException("player died"))
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertFalse(active)
        assertEquals(9, stoppedSelf)
        assertEquals(6, releasedPlayers)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun startPlaybackCatchGuardsObsoleteGenerationPreventsDuplicateFallbackAndRethrowsError() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var active = false
        var stoppedSelf = 0
        var abandonedFocus = 0
        var watchdogsCancelled = 0
        val releasedHandles = mutableListOf<String>()
        lateinit var controller: AdhanPlaybackLifecycleController
        var createAction: (String, () -> Unit, (Throwable?) -> Unit) -> AdhanPlaybackHandle = { _, _, _ ->
            error("unset")
        }

        controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = { abandonedFocus++ },
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { uri, onEnded, onError -> createAction(uri, onEnded, onError) },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
            onCancelPreparationWatchdog = { watchdogsCancelled++ },
            onCancelMaxDurationWatchdog = { watchdogsCancelled++ },
            onCancelTransientFocusWatchdog = { watchdogsCancelled++ },
        )

        createAction = { _, _, onError ->
            onError(RuntimeException("sync player error"))
            throw IllegalStateException("follow-up throw")
        }
        val l1 = wakeLock.acquireHandover()
        val g1 = controller.nextGeneration()
        tracker.register(g1, l1.tokenId)
        controller.startPlayback(g1, Prayer.FAJR, "file:///sync-error.mp3")
        assertEquals(listOf(Prayer.FAJR), fallbacks)
        assertEquals(1, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        fallbacks.clear()
        stoppedSelf = 0
        watchdogsCancelled = 0
        createAction = { uri, _, _ ->
            if (uri == "file:///old.mp3") {
                val lNew = wakeLock.acquireHandover()
                val gNew = controller.nextGeneration()
                tracker.register(gNew, lNew.tokenId)
                controller.startPlayback(gNew, Prayer.DHUHR, "file:///new.mp3")
                throw IllegalStateException("old create failed after superseding")
            }
            namedHandle("new-handle", releasedHandles)
        }
        val lOld = wakeLock.acquireHandover()
        val gOld = controller.nextGeneration()
        tracker.register(gOld, lOld.tokenId)
        controller.startPlayback(gOld, Prayer.FAJR, "file:///old.mp3")
        assertTrue(active)
        assertTrue(fallbacks.isEmpty())
        assertEquals(0, stoppedSelf)
        assertEquals(0, watchdogsCancelled)
        assertTrue(releasedHandles.isEmpty())
        assertEquals(1, wakeLock.activeLeaseCountForTest())
        controller.stopAll()
        assertEquals(listOf("new-handle"), releasedHandles)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        releasedHandles.clear()
        fallbacks.clear()
        stoppedSelf = 0
        abandonedFocus = 0
        watchdogsCancelled = 0
        createAction = { _, _, _ ->
            controller.nextGeneration()
            throw IllegalStateException("failed after nextGeneration only")
        }
        val lSuperseded = wakeLock.acquireHandover()
        val gSuperseded = controller.nextGeneration()
        tracker.register(gSuperseded, lSuperseded.tokenId)
        controller.startPlayback(gSuperseded, Prayer.ASR, "file:///superseded.mp3")
        assertFalse(active)
        assertTrue(fallbacks.isEmpty())
        assertEquals(0, stoppedSelf)
        assertEquals(0, watchdogsCancelled)
        assertTrue(abandonedFocus >= 2)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        releasedHandles.clear()
        fallbacks.clear()
        stoppedSelf = 0
        createAction = { uri, _, _ ->
            if (uri == "file:///return-old.mp3") {
                val lNew = wakeLock.acquireHandover()
                val gNew = controller.nextGeneration()
                tracker.register(gNew, lNew.tokenId)
                controller.startPlayback(gNew, Prayer.MAGHRIB, "file:///return-new.mp3")
                namedHandle("stale-handle", releasedHandles)
            } else {
                namedHandle("active-handle", releasedHandles)
            }
        }
        val focusBefore = abandonedFocus
        val lRetOld = wakeLock.acquireHandover()
        val gRetOld = controller.nextGeneration()
        tracker.register(gRetOld, lRetOld.tokenId)
        controller.startPlayback(gRetOld, Prayer.FAJR, "file:///return-old.mp3")
        assertTrue(active)
        assertEquals(listOf("stale-handle"), releasedHandles)
        assertEquals(focusBefore + 2, abandonedFocus)
        controller.stopAll()
        assertEquals(listOf("stale-handle", "active-handle"), releasedHandles)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        fallbacks.clear()
        stoppedSelf = 0
        watchdogsCancelled = 0
        abandonedFocus = 0
        createAction = { _, _, _ -> throw AssertionError("fatal decoder error") }
        val lFatal = wakeLock.acquireHandover()
        val gFatal = controller.nextGeneration()
        tracker.register(gFatal, lFatal.tokenId)
        try {
            controller.startPlayback(gFatal, Prayer.ISHA, "file:///fatal.mp3")
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertFalse(active)
        assertTrue(fallbacks.isEmpty())
        assertEquals(1, stoppedSelf)
        assertTrue(watchdogsCancelled >= 3)
        assertTrue(abandonedFocus >= 2)
        assertFalse(controller.isCurrentGeneration(gFatal))
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        releasedHandles.clear()
        fallbacks.clear()
        stoppedSelf = 0
        watchdogsCancelled = 0
        createAction = { uri, _, _ ->
            if (uri == "file:///old-fatal.mp3") {
                val lNew = wakeLock.acquireHandover()
                val gNew = controller.nextGeneration()
                tracker.register(gNew, lNew.tokenId)
                controller.startPlayback(gNew, Prayer.DHUHR, "file:///survivor.mp3")
                throw AssertionError("fatal error on obsolete gen")
            }
            namedHandle("survivor-handle", releasedHandles)
        }
        val lOldFatal = wakeLock.acquireHandover()
        val gOldFatal = controller.nextGeneration()
        tracker.register(gOldFatal, lOldFatal.tokenId)
        try {
            controller.startPlayback(gOldFatal, Prayer.FAJR, "file:///old-fatal.mp3")
            fail("Expected AssertionError to propagate")
        } catch (_: AssertionError) {
        }
        assertTrue(active)
        assertTrue(fallbacks.isEmpty())
        assertEquals(0, stoppedSelf)
        assertEquals(0, watchdogsCancelled)
        assertTrue(releasedHandles.isEmpty())
        assertEquals(1, wakeLock.activeLeaseCountForTest())
        controller.stopAll()
        assertEquals(listOf("survivor-handle"), releasedHandles)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun terminateWithFallbackIsolatesTeardownFailuresPreservesPrimaryErrorAndHandlesDirectFallbackThrow() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        var throwOnActiveFalse = false
        var throwOnAbandonFocus = false
        var throwOnReleaseHandover = false
        var directFallbackAction: (Prayer) -> Unit = {}
        var releasedPlayers = 0
        var handoverReleaseCalls = 0
        var stoppedSelf = 0
        var errorCallback: ((Throwable?) -> Unit)? = null

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { isActive ->
                if (!isActive && throwOnActiveFalse) {
                    throw RuntimeException("onActiveChanged failed")
                }
            },
            onRequestFocus = { true },
            onAbandonFocus = {
                if (throwOnAbandonFocus) {
                    throw RuntimeException("abandonFocus failed")
                }
            },
            onFallbackNotify = { prayer -> directFallbackAction(prayer) },
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
            onReleaseHandover = { token ->
                handoverReleaseCalls++
                tracker.releaseForGeneration(token)
                if (throwOnReleaseHandover) {
                    throw RuntimeException("releaseHandover failed")
                }
            },
        )

        fun startActive(prayer: Prayer) {
            val lease = wakeLock.acquireHandover()
            val gen = controller.nextGeneration()
            tracker.register(gen, lease.tokenId)
            controller.startPlayback(gen, prayer, "file:///adhan.mp3")
        }

        startActive(Prayer.FAJR)
        throwOnAbandonFocus = true
        errorCallback?.invoke(RuntimeException("player error"))
        throwOnAbandonFocus = false
        assertEquals(1, releasedPlayers)
        assertEquals(1, handoverReleaseCalls)
        assertEquals(1, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startActive(Prayer.DHUHR)
        throwOnActiveFalse = true
        errorCallback?.invoke(RuntimeException("player error"))
        throwOnActiveFalse = false
        assertEquals(2, releasedPlayers)
        assertEquals(2, handoverReleaseCalls)
        assertEquals(2, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startActive(Prayer.ASR)
        throwOnReleaseHandover = true
        errorCallback?.invoke(RuntimeException("player error"))
        throwOnReleaseHandover = false
        assertEquals(3, releasedPlayers)
        assertEquals(3, handoverReleaseCalls)
        assertEquals(3, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startActive(Prayer.MAGHRIB)
        directFallbackAction = { throw RuntimeException("direct fallback threw") }
        errorCallback?.invoke(RuntimeException("player error"))
        directFallbackAction = {}
        assertEquals(4, releasedPlayers)
        assertEquals(4, handoverReleaseCalls)
        assertEquals(4, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        startActive(Prayer.ISHA)
        val primaryError = AssertionError("primary fallback fatal error")
        directFallbackAction = { throw primaryError }
        throwOnAbandonFocus = true
        throwOnReleaseHandover = true
        try {
            errorCallback?.invoke(RuntimeException("player error"))
            fail("Expected primary AssertionError to propagate")
        } catch (caught: AssertionError) {
            assertSame(primaryError, caught)
            assertEquals(2, caught.suppressed.size)
        } finally {
            directFallbackAction = {}
            throwOnAbandonFocus = false
            throwOnReleaseHandover = false
        }
        assertEquals(5, releasedPlayers)
        assertEquals(5, handoverReleaseCalls)
        assertEquals(5, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        var obsoleteWatchdogsCancelled = 0
        var obsoleteHandoverReleased = 0
        var obsoleteStopSelf = 0
        lateinit var obsoleteController: AdhanPlaybackLifecycleController
        obsoleteController = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = { throw RuntimeException("obsolete cleanup abandonFocus failed") },
            onFallbackNotify = { throw AssertionError("obsolete must not fallback") },
            onStopQuran = {},
            onStopSelf = { obsoleteStopSelf++ },
            createAndStartPlayer = { _, _, _ ->
                obsoleteController.nextGeneration()
                throw IllegalStateException("obsolete create failed")
            },
            onReleaseHandover = { token ->
                obsoleteHandoverReleased++
                tracker.releaseForGeneration(token)
            },
            onCancelPreparationWatchdog = { obsoleteWatchdogsCancelled++ },
            onCancelMaxDurationWatchdog = { obsoleteWatchdogsCancelled++ },
            onCancelTransientFocusWatchdog = { obsoleteWatchdogsCancelled++ },
        )
        val obsoleteLease = wakeLock.acquireHandover()
        val obsoleteGen = obsoleteController.nextGeneration()
        tracker.register(obsoleteGen, obsoleteLease.tokenId)
        obsoleteController.startPlayback(obsoleteGen, Prayer.FAJR, "file:///obsolete.mp3")
        assertEquals(1, obsoleteHandoverReleased)
        assertEquals(0, obsoleteWatchdogsCancelled)
        assertEquals(0, obsoleteStopSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun reEntrantBeginStartFailurePreservesActivePriorGenerationWithAudibleFallbackAndTeardownIsolation() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val posted = mutableListOf<Pair<Prayer, AdhanMode>>()
        var active = false
        var releasedPlayers = 0
        var stoppedIfIdle = 0
        var cancelledPriorJobs = 0

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = { active = it },
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() {
                        releasedPlayers++
                    }
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
        )

        val priorLease = wakeLock.acquireHandover()
        val priorGen = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.DHUHR,
            incomingWakeLockToken = priorLease.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = {},
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = {},
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = { if (!controller.hasActivePlayback()) stoppedIfIdle++ },
        )!!
        controller.startPlayback(priorGen, Prayer.DHUHR, "file:///dhuhr.mp3")
        assertTrue(active)
        assertEquals(1, wakeLock.activeLeaseCountForTest())

        val incomingLease = wakeLock.acquireHandover()
        val failedReEntry = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.ASR,
            incomingWakeLockToken = incomingLease.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw IllegalStateException("ForegroundServiceStartNotAllowed") },
            onReleaseIncomingToken = { wakeLock.releaseToken(it) },
            onFallbackNotify = { failedPrayer ->
                AdhanPlaybackService.postFallbackAlert(failedPrayer) { prayer, mode ->
                    posted += prayer to mode
                    true
                }
            },
            onCancelPriorJobs = { cancelledPriorJobs++ },
            onStopSelfIfIdle = { if (!controller.hasActivePlayback()) stoppedIfIdle++ },
        )

        assertNull(failedReEntry)
        assertEquals(listOf(Prayer.ASR to AdhanMode.NOTIFICATION_SOUND), posted)
        assertTrue(controller.isCurrentGeneration(priorGen))
        assertTrue(active)
        assertEquals(0, releasedPlayers)
        assertEquals(1, cancelledPriorJobs)
        assertEquals(0, stoppedIfIdle)
        assertEquals(1, wakeLock.activeLeaseCountForTest())

        controller.stopAll()
        assertEquals(1, releasedPlayers)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lTokenThrow = wakeLock.acquireHandover()
        val postedBeforeTokenThrow = posted.size
        val resTokenThrow = AdhanPlaybackService.beginReEntrantStart(
            prayer = Prayer.MAGHRIB,
            incomingWakeLockToken = lTokenThrow.tokenId,
            lifecycleController = controller,
            handoverTokens = tracker,
            onEnterForeground = { throw SecurityException("fgs blocked") },
            onReleaseIncomingToken = { token ->
                wakeLock.releaseToken(token)
                throw RuntimeException("release failed")
            },
            onFallbackNotify = { failedPrayer ->
                AdhanPlaybackService.postFallbackAlert(failedPrayer) { prayer, mode ->
                    posted += prayer to mode
                    true
                }
            },
            onCancelPriorJobs = {},
            onStopSelfIfIdle = { stoppedIfIdle++ },
        )
        assertNull(resTokenThrow)
        assertEquals(postedBeforeTokenThrow + 1, posted.size)
        assertEquals(Prayer.MAGHRIB to AdhanMode.NOTIFICATION_SOUND, posted.last())
        assertEquals(1, stoppedIfIdle)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lTokenFatal = wakeLock.acquireHandover()
        val tokenFatal = AssertionError("fatal release error")
        try {
            AdhanPlaybackService.beginReEntrantStart(
                prayer = Prayer.ISHA,
                incomingWakeLockToken = lTokenFatal.tokenId,
                lifecycleController = controller,
                handoverTokens = tracker,
                onEnterForeground = { throw SecurityException("fgs blocked") },
                onReleaseIncomingToken = { token ->
                    wakeLock.releaseToken(token)
                    throw tokenFatal
                },
                onFallbackNotify = { throw AssertionError("fallback must not run after fatal token error") },
                onCancelPriorJobs = {},
                onStopSelfIfIdle = { stoppedIfIdle++ },
            )
            fail("Expected fatal token release Error to propagate")
        } catch (caught: AssertionError) {
            assertSame(tokenFatal, caught)
        }
        assertEquals(2, stoppedIfIdle)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lStopFatal = wakeLock.acquireHandover()
        val stopFatal = AssertionError("fatal stopSelf error")
        try {
            AdhanPlaybackService.beginReEntrantStart(
                prayer = Prayer.FAJR,
                incomingWakeLockToken = lStopFatal.tokenId,
                lifecycleController = controller,
                handoverTokens = tracker,
                onEnterForeground = { throw SecurityException("fgs blocked") },
                onReleaseIncomingToken = { wakeLock.releaseToken(it) },
                onFallbackNotify = {},
                onCancelPriorJobs = {},
                onStopSelfIfIdle = { throw stopFatal },
            )
            fail("Expected fatal stopSelfIfIdle Error to propagate")
        } catch (caught: AssertionError) {
            assertSame(stopFatal, caught)
        }
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun resolveAudioUriFatalErrorCancelsCurrentWatchdogOnlyAndReleasesGenerationToken() = runTest {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val controller = createBasicController(tracker)

        val l1 = wakeLock.acquireHandover()
        val g1 = controller.nextGeneration()
        tracker.register(g1, l1.tokenId)
        var prepareJob: Job? = launch { delay(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS) }
        val fatalCurrent = AssertionError("fatal current resolve error")
        try {
            AdhanPlaybackService.resolveAudioUriForGeneration(
                token = g1,
                isCurrentGeneration = { controller.isCurrentGeneration(it) },
                resolveUri = { throw fatalCurrent },
                onCancelCurrentPreparationWatchdog = {
                    prepareJob?.cancel()
                    prepareJob = null
                },
                onReleaseGenerationHandover = { tracker.releaseForGeneration(it) },
            )
            fail("Expected fatal Error to propagate")
        } catch (caught: AssertionError) {
            assertSame(fatalCurrent, caught)
        }
        assertNull(prepareJob)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lOld = wakeLock.acquireHandover()
        val gOld = controller.nextGeneration()
        tracker.register(gOld, lOld.tokenId)
        val lNew = wakeLock.acquireHandover()
        val gNew = controller.nextGeneration()
        tracker.register(gNew, lNew.tokenId)
        var newerPrepareJob: Job? = launch { delay(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS) }
        val fatalObsolete = AssertionError("fatal obsolete resolve error")
        try {
            AdhanPlaybackService.resolveAudioUriForGeneration(
                token = gOld,
                isCurrentGeneration = { controller.isCurrentGeneration(it) },
                resolveUri = { throw fatalObsolete },
                onCancelCurrentPreparationWatchdog = {
                    newerPrepareJob?.cancel()
                    newerPrepareJob = null
                },
                onReleaseGenerationHandover = { tracker.releaseForGeneration(it) },
            )
            fail("Expected fatal obsolete Error to propagate")
        } catch (caught: AssertionError) {
            assertSame(fatalObsolete, caught)
        }
        assertTrue(newerPrepareJob?.isActive == true)
        assertEquals(1, wakeLock.activeLeaseCountForTest())
        newerPrepareJob?.cancel()
        tracker.releaseForGeneration(gNew)
        assertEquals(0, wakeLock.activeLeaseCountForTest())

        val lIo = wakeLock.acquireHandover()
        val gIo = controller.nextGeneration()
        tracker.register(gIo, lIo.tokenId)
        var ioPrepareJob: Job? = launch { delay(AdhanPlaybackService.PREPARATION_TIMEOUT_MILLIS) }
        val resolved = AdhanPlaybackService.resolveAudioUriForGeneration(
            token = gIo,
            isCurrentGeneration = { controller.isCurrentGeneration(it) },
            resolveUri = { throw RuntimeException("datastore io error") },
            onCancelCurrentPreparationWatchdog = {
                ioPrepareJob?.cancel()
                ioPrepareJob = null
            },
            onReleaseGenerationHandover = { tracker.releaseForGeneration(it) },
        )
        assertNull(resolved)
        assertTrue(ioPrepareJob?.isActive == true)
        ioPrepareJob?.cancel()
        tracker.releaseForGeneration(gIo)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    @Test
    fun nonFailureTerminalPathsNeverGenerateFallbackNotifications() {
        val wakeLock = createWakeLock()
        val tracker = HandoverTokenTracker { wakeLock }
        val fallbacks = mutableListOf<Prayer>()
        var endedCallback: (() -> Unit)? = null
        var stoppedSelf = 0

        val controller = AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = { fallbacks += it },
            onStopQuran = {},
            onStopSelf = { stoppedSelf++ },
            createAndStartPlayer = { _, onEnded, _ ->
                endedCallback = onEnded
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
        )

        fun startConfirmed(prayer: Prayer): Long {
            val lease = wakeLock.acquireHandover()
            val gen = controller.nextGeneration()
            tracker.register(gen, lease.tokenId)
            controller.startPlayback(gen, prayer, "file:///ok.mp3")
            controller.onIsPlayingStarted(gen)
            return gen
        }

        startConfirmed(Prayer.FAJR)
        endedCallback?.invoke()

        val gMax = startConfirmed(Prayer.DHUHR)
        assertTrue(controller.onMaxDurationTimeout(gMax))

        val gTransient = startConfirmed(Prayer.ASR)
        controller.onFocusLossTransient()
        assertTrue(controller.onTransientFocusTimeout(gTransient))

        startConfirmed(Prayer.MAGHRIB)
        controller.stopAll()

        startConfirmed(Prayer.ISHA)
        controller.onFocusLossPermanent()

        assertTrue(fallbacks.isEmpty())
        assertEquals(5, stoppedSelf)
        assertEquals(0, wakeLock.activeLeaseCountForTest())
    }

    private fun createWakeLock(): PrayerAlarmWakeLock {
        val clock = AtomicLong(1_000L)
        return PrayerAlarmWakeLock(
            wakeLockFactory = PlatformWakeLockFactory { FakePlatformWakeLock() },
            elapsedRealtimeMs = clock::get,
        )
    }

    private fun createBasicController(tracker: HandoverTokenTracker): AdhanPlaybackLifecycleController =
        AdhanPlaybackLifecycleController(
            onActiveChanged = {},
            onRequestFocus = { true },
            onAbandonFocus = {},
            onFallbackNotify = {},
            onStopQuran = {},
            onStopSelf = {},
            createAndStartPlayer = { _, _, _ ->
                object : AdhanPlaybackHandle {
                    override fun pause() = Unit
                    override fun play() = Unit
                    override fun stopAndRelease() = Unit
                }
            },
            onReleaseHandover = { token -> tracker.releaseForGeneration(token) },
        )

    private fun namedHandle(name: String, released: MutableList<String>): AdhanPlaybackHandle =
        object : AdhanPlaybackHandle {
            override fun pause() = Unit
            override fun play() = Unit
            override fun stopAndRelease() {
                released += name
            }
        }

    private class FakePlatformWakeLock : PlatformWakeLock {
        private var held = false
        override fun acquire(timeoutMs: Long) {
            held = true
        }

        override fun release() {
            held = false
        }

        override fun isHeld(): Boolean = held
    }

    private class FixedClock(
        private val fixedInstant: Instant,
        private val zoneId: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = FixedClock(fixedInstant, zone)
        override fun instant(): Instant = fixedInstant
    }

    private class FakePrayerPreferencesRepository(
        location: PrayerLocation,
        adhanConfig: PrayerAdhanConfig,
    ) : PrayerPreferencesRepository {
        private val prefsFlow = MutableStateFlow(
            PrayerPreferences(
                location = location,
                autoLocationEnabled = false,
                method = CalculationMethod.EGYPTIAN,
                asrMethod = AsrMethod.STANDARD,
                highLatitudeRule = HighLatitudeRulePref.ANGLE_BASED,
                adjustments = PrayerAdjustments(),
                adhanConfig = adhanConfig,
                voiceSelection = PrayerVoiceSelection(),
                hijriAdjustmentDays = 0,
                customAdhanAudioUri = null,
            ),
        )
        private val firedKeys = LinkedHashSet<String>()

        override val preferences: Flow<PrayerPreferences> = prefsFlow
        override val onboardingCompleted: Flow<Boolean> = MutableStateFlow(true)
        override val vendorGuidanceAcknowledged: Flow<Boolean> = MutableStateFlow(true)
        override val voiceSelection: Flow<PrayerVoiceSelection> = MutableStateFlow(PrayerVoiceSelection())
        override val downloadedAdhanIds: Flow<Set<String>> = MutableStateFlow(emptySet())

        override suspend fun setLocation(location: PrayerLocation) = Unit
        override suspend fun setAutoLocationEnabled(enabled: Boolean) = Unit
        override suspend fun setMethod(method: CalculationMethod) = Unit
        override suspend fun setAsrMethod(method: AsrMethod) = Unit
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
        ): Boolean = synchronized(firedKeys) { firedKeys.add(occurrenceKey) }
    }

    private fun testLocation(timezoneId: String) = PrayerLocation(
        cityAr = "القاهرة",
        cityEn = "Cairo",
        countryAr = "مصر",
        countryEn = "Egypt",
        latitude = 30.0444,
        longitude = 31.2357,
        timezoneId = timezoneId,
    )

    private fun recordingScheduler(target: MutableList<PrayerAlarm>) = object : PrayerAlarmScheduler {
        override fun canScheduleExact(): Boolean = true
        override fun schedule(alarm: PrayerAlarm): Boolean {
            synchronized(target) { target += alarm }
            return true
        }

        override fun cancel() = Unit
    }

    private fun fixedCalculator() = object : PrayerTimesCalculator {
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
            fajr = date.atTime(4, 30),
            sunrise = date.atTime(6, 0),
            dhuhr = date.atTime(12, 0),
            asr = date.atTime(15, 30),
            maghrib = date.atTime(18, 0),
            isha = date.atTime(19, 30),
        )
    }
}
