package com.ayah.quran.prayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ayah.quran.MainActivity
import com.ayah.quran.R
import com.ayah.quran.data.prayer.AdhanPlaybackSource
import com.ayah.quran.data.prayer.AdhanRawResources
import com.ayah.quran.data.prayer.AdhanVoiceResolver
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.AdhanMode
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface AdhanPlaybackHandle {
    fun pause()
    fun play()
    fun stopAndRelease()
    fun postPlaybackLooperBarrier(action: () -> Unit): Boolean = true.also { action() }
}

internal class HandoverTokenTracker(
    private val wakeLockProvider: () -> PrayerAlarmWakeLock?,
) {
    private val lock = Any()
    private val byGeneration = LinkedHashMap<Long, Long>()
    private val allPending = LinkedHashSet<Long>()

    fun register(generationToken: Long, wakeLockTokenId: Long) {
        val superseded: List<Long>
        synchronized(lock) {
            superseded = byGeneration.values.toList()
            byGeneration.clear()
            allPending.removeAll(superseded.toSet())
            if (wakeLockTokenId > PrayerAlarmWakeLock.INVALID_TOKEN_ID) {
                byGeneration[generationToken] = wakeLockTokenId
                allPending.add(wakeLockTokenId)
            }
        }
        if (superseded.isNotEmpty()) {
            wakeLockProvider()?.releaseTokens(superseded)
        }
    }

    fun releaseForGeneration(generationToken: Long) {
        val wakeLockTokenId = synchronized(lock) {
            byGeneration.remove(generationToken)?.also { allPending.remove(it) }
        }
        if (wakeLockTokenId != null) {
            wakeLockProvider()?.releaseToken(wakeLockTokenId)
        }
    }

    fun releaseAll() {
        val drained: List<Long>
        synchronized(lock) {
            drained = allPending.toList()
            allPending.clear()
            byGeneration.clear()
        }
        if (drained.isNotEmpty()) {
            wakeLockProvider()?.releaseTokens(drained)
        }
    }

    internal fun pendingCountForTest(): Int = synchronized(lock) { allPending.size }
}

internal class AdhanPlaybackLifecycleController(
    private val onActiveChanged: (Boolean) -> Unit,
    private val onRequestFocus: () -> Boolean,
    private val onAbandonFocus: () -> Unit,
    private val onFallbackNotify: (Prayer) -> Unit,
    private val onStopQuran: () -> Unit,
    private val onStopSelf: () -> Unit,
    private val createAndStartPlayer: (uri: String, onEnded: () -> Unit, onError: (Throwable?) -> Unit) -> AdhanPlaybackHandle,
    private val onReleaseHandover: (Long) -> Unit = {},
    private val onCancelPreparationWatchdog: () -> Unit = {},
) {
    private var currentHandle: AdhanPlaybackHandle? = null
    private var currentPrayer: Prayer? = null
    private var activeGeneration: Long = 0L
    private var generation: Long = 0L
    private var playbackConfirmed: Boolean = false

    fun nextGeneration(): Long {
        generation += 1L
        playbackConfirmed = false
        return generation
    }

    fun currentGeneration(): Long = generation

    fun isCurrentGeneration(token: Long): Boolean = token == generation

    fun hasActivePlayback(): Boolean = currentHandle != null || activeGeneration > 0L

    fun startPlayback(token: Long, prayer: Prayer, audioUri: String?) {
        if (!isCurrentGeneration(token)) {
            onReleaseHandover(token)
            return
        }
        cleanup()
        if (audioUri.isNullOrBlank()) {
            generation += 1L
            onCancelPreparationWatchdog()
            onFallbackNotify(prayer)
            onReleaseHandover(token)
            onStopSelf()
            return
        }
        if (!onRequestFocus()) {
            generation += 1L
            onCancelPreparationWatchdog()
            onFallbackNotify(prayer)
            onReleaseHandover(token)
            onStopSelf()
            return
        }
        try {
            currentPrayer = prayer
            activeGeneration = token
            val handle = createAndStartPlayer(
                audioUri,
                {
                    if (isCurrentGeneration(token)) {
                        generation += 1L
                        onCancelPreparationWatchdog()
                        cleanup()
                        onReleaseHandover(token)
                        onStopSelf()
                    }
                },
                {
                    if (isCurrentGeneration(token)) {
                        generation += 1L
                        onCancelPreparationWatchdog()
                        onFallbackNotify(prayer)
                        cleanup()
                        onReleaseHandover(token)
                        onStopSelf()
                    }
                },
            )
            if (!isCurrentGeneration(token)) {
                runCatching { handle.stopAndRelease() }
                onAbandonFocus()
                onReleaseHandover(token)
                return
            }
            currentHandle = handle
            onActiveChanged(true)
            onStopQuran()
        } catch (t: Throwable) {
            if (isCurrentGeneration(token)) {
                generation += 1L
            }
            onCancelPreparationWatchdog()
            onFallbackNotify(prayer)
            cleanup()
            onReleaseHandover(token)
            onStopSelf()
        }
    }

    fun onIsPlayingStarted(token: Long) {
        if (!isCurrentGeneration(token) || playbackConfirmed) return
        playbackConfirmed = true
        onCancelPreparationWatchdog()
        val handle = currentHandle
        if (handle == null) {
            onReleaseHandover(token)
            return
        }
        val posted = handle.postPlaybackLooperBarrier {
            onReleaseHandover(token)
        }
        if (!posted) {
            val fallbackPrayer = currentPrayer
            generation += 1L
            if (fallbackPrayer != null) {
                onFallbackNotify(fallbackPrayer)
            }
            cleanup()
            onReleaseHandover(token)
            onStopSelf()
        }
    }

    fun onPreparationTimeout(token: Long, prayer: Prayer): Boolean {
        if (!isCurrentGeneration(token) || playbackConfirmed) return false
        generation += 1L
        onCancelPreparationWatchdog()
        onFallbackNotify(prayer)
        cleanup()
        onReleaseHandover(token)
        onStopSelf()
        return true
    }

    fun onFocusLossPermanent() {
        val pendingToken = activeGeneration
        val fallbackPrayer = if (!playbackConfirmed) currentPrayer else null
        generation += 1L
        onCancelPreparationWatchdog()
        if (fallbackPrayer != null) {
            onFallbackNotify(fallbackPrayer)
        }
        cleanup()
        if (pendingToken > 0L) {
            onReleaseHandover(pendingToken)
        }
        onStopSelf()
    }

    fun onFocusLossTransient() {
        currentHandle?.pause()
    }

    fun onFocusGain() {
        currentHandle?.play()
    }

    fun stopAll() {
        val previousToken = generation
        generation += 1L
        onCancelPreparationWatchdog()
        cleanup()
        if (previousToken > 0L) {
            onReleaseHandover(previousToken)
        }
        onStopSelf()
    }

    fun cleanup() {
        playbackConfirmed = false
        currentPrayer = null
        activeGeneration = 0L
        onActiveChanged(false)
        currentHandle?.let { handle ->
            runCatching { handle.stopAndRelease() }
        }
        currentHandle = null
        onAbandonFocus()
    }
}

@AndroidEntryPoint
class AdhanPlaybackService : android.app.Service() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var notifications: PrayerNotifications
    @Inject lateinit var voiceResolver: AdhanVoiceResolver
    @Inject lateinit var sourceResolver: com.ayah.quran.data.prayer.AdhanPlaybackSourceResolver
    @Inject lateinit var catalog: BundledAdhanCatalog
    @Inject lateinit var wakeLock: PrayerAlarmWakeLock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var prepareTimeoutJob: Job? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val handoverTokens by lazy {
        HandoverTokenTracker { if (::wakeLock.isInitialized) wakeLock else null }
    }

    private val lifecycleController: AdhanPlaybackLifecycleController by lazy {
        AdhanPlaybackLifecycleController(
            onActiveChanged = { active -> isAdhanActive = active },
            onRequestFocus = { requestFocus() },
            onAbandonFocus = { abandonFocus() },
            onFallbackNotify = { prayer -> notifications.post(prayer, AdhanMode.SILENT) },
            onStopQuran = {
                runCatching {
                    stopService(Intent(this, com.ayah.quran.data.audio.QuranPlaybackService::class.java))
                }
            },
            onStopSelf = { stopSelf() },
            createAndStartPlayer = { uri, onEnded, onError ->
                createExoPlayerHandle(
                    token = lifecycleController.currentGeneration(),
                    audioUri = uri,
                    onEnded = onEnded,
                    onError = onError,
                )
            },
            onReleaseHandover = { token -> handoverTokens.releaseForGeneration(token) },
            onCancelPreparationWatchdog = {
                prepareTimeoutJob?.cancel()
                prepareTimeoutJob = null
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val wakeLockToken = intent?.getLongExtra(
            EXTRA_WAKE_LOCK_TOKEN,
            PrayerAlarmWakeLock.INVALID_TOKEN_ID,
        ) ?: PrayerAlarmWakeLock.INVALID_TOKEN_ID

        if (intent?.action == ACTION_STOP) {
            prepareTimeoutJob?.cancel()
            prepareTimeoutJob = null
            startJob?.cancel()
            startJob = null
            lifecycleController.stopAll()
            handoverTokens.releaseAll()
            if (wakeLockToken > PrayerAlarmWakeLock.INVALID_TOKEN_ID && ::wakeLock.isInitialized) {
                wakeLock.releaseToken(wakeLockToken)
            }
            return START_NOT_STICKY
        }

        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER) ?: Prayer.FAJR.name
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrDefault(Prayer.FAJR)

        val token = beginReEntrantStart(
            prayer = prayer,
            incomingWakeLockToken = wakeLockToken,
            lifecycleController = lifecycleController,
            handoverTokens = handoverTokens,
            onEnterForeground = { startInForeground(prayer, isTest) },
            onReleaseIncomingToken = { incoming ->
                if (incoming > PrayerAlarmWakeLock.INVALID_TOKEN_ID && ::wakeLock.isInitialized) {
                    wakeLock.releaseToken(incoming)
                }
            },
            onFallbackNotify = { failedPrayer ->
                runCatching { notifications.post(failedPrayer, AdhanMode.SILENT) }
            },
            onCancelPriorJobs = {
                prepareTimeoutJob?.cancel()
                prepareTimeoutJob = null
                startJob?.cancel()
                startJob = null
            },
            onStopSelfIfIdle = {
                val hasPendingJobs = startJob?.isActive == true || prepareTimeoutJob?.isActive == true
                if (!lifecycleController.hasActivePlayback() && !hasPendingJobs) {
                    stopSelf(startId)
                }
            },
        ) ?: return START_NOT_STICKY

        prepareTimeoutJob = serviceScope.launch {
            delay(PREPARATION_TIMEOUT_MILLIS)
            if (lifecycleController.onPreparationTimeout(token, prayer)) {
                startJob?.cancel()
            }
        }

        startJob = serviceScope.launch {
            val audioUri = try {
                withContext(Dispatchers.IO) {
                    resolveAudioUri(prayer)
                }
            } catch (ce: CancellationException) {
                handoverTokens.releaseForGeneration(token)
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to resolve Adhan preferences/audio source", t)
                null
            }
            if (!lifecycleController.isCurrentGeneration(token)) {
                handoverTokens.releaseForGeneration(token)
                return@launch
            }
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            lifecycleController.startPlayback(token, prayer, audioUri)
        }

        return START_NOT_STICKY
    }

    private suspend fun resolveAudioUri(prayer: Prayer): String? {
        val prefs = preferencesRepository.preferences.first()
        return when {
            prefs.voiceSelection.voiceOf(prayer) == AdhanVoiceDefaults.USER_FILE_ID ->
                prefs.customAdhanAudioUri

            else -> {
                val (recording, source) = sourceResolver.resolve(
                    prefs.voiceSelection.voiceOf(prayer),
                )
                when (source) {
                    is AdhanPlaybackSource.RawResource ->
                        "android.resource://$packageName/${source.resId}"

                    is AdhanPlaybackSource.LocalFile ->
                        android.net.Uri.fromFile(source.file).toString()

                    AdhanPlaybackSource.Missing -> null
                } ?: "android.resource://$packageName/" +
                    AdhanRawResources.resIdOf(recording.id)
            }
        } ?: prefs.customAdhanAudioUri
    }

    private fun createExoPlayerHandle(
        token: Long,
        audioUri: String,
        onEnded: () -> Unit,
        onError: (Throwable?) -> Unit,
    ): AdhanPlaybackHandle {
        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_ALARM)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            false,
        )
        exoPlayer.setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
        exoPlayer.setMediaItem(MediaItem.fromUri(audioUri))
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    lifecycleController.onIsPlayingStarted(token)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Adhan ExoPlayer playback error: ${error.errorCodeName}", error)
                onError(error)
            }
        })
        exoPlayer.prepare()
        exoPlayer.play()
        return object : AdhanPlaybackHandle {
            override fun pause() {
                exoPlayer.pause()
            }

            override fun play() {
                exoPlayer.play()
            }

            override fun stopAndRelease() {
                runCatching { exoPlayer.stop() }
                runCatching { exoPlayer.release() }
            }

            override fun postPlaybackLooperBarrier(action: () -> Unit): Boolean {
                return runCatching {
                    Handler(exoPlayer.playbackLooper).post(action)
                }.getOrDefault(false)
            }
        }
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> lifecycleController.onFocusLossPermanent()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> lifecycleController.onFocusLossTransient()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
                    AudioManager.AUDIOFOCUS_GAIN -> lifecycleController.onFocusGain()
                }
            }
            .build()
        focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { req ->
            runCatching { audioManager?.abandonAudioFocusRequest(req) }
        }
        focusRequest = null
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    private fun startInForeground(prayer: Prayer, isTest: Boolean): Notification {
        notifications.createChannels()
        val stopIntent = PendingIntent.getService(
            this,
            7001,
            Intent(this, AdhanPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            7002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val titleSuffix = if (isTest) {
            " " + getString(R.string.prayer_test_suffix)
        } else {
            ""
        }
        val notification = NotificationCompat.Builder(this, PrayerNotifications.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_prayer_notification)
            .setContentTitle(
                getString(R.string.prayer_notification_title, notifications.prayerName(prayer)) + titleSuffix,
            )
            .setContentText(getString(R.string.prayer_playback_notification_text))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_prayer_notification, getString(R.string.prayer_stop), stopIntent)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                PrayerNotifications.NOTIFICATION_ID_PLAYBACK,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(PrayerNotifications.NOTIFICATION_ID_PLAYBACK, notification)
        }
        return notification
    }

    override fun onDestroy() {
        prepareTimeoutJob?.cancel()
        prepareTimeoutJob = null
        startJob?.cancel()
        startJob = null
        serviceScope.cancel()
        lifecycleController.cleanup()
        handoverTokens.releaseAll()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdhanPlaybackService"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TEST = "test"
        const val EXTRA_WAKE_LOCK_TOKEN = "wake_lock_token"
        const val ACTION_STOP = "com.ayah.quran.prayer.STOP_ADHAN"
        internal const val PREPARATION_TIMEOUT_MILLIS = 8_000L

        @Volatile
        var isAdhanActive: Boolean = false
            private set

        internal fun beginReEntrantStart(
            prayer: Prayer,
            incomingWakeLockToken: Long,
            lifecycleController: AdhanPlaybackLifecycleController,
            handoverTokens: HandoverTokenTracker,
            onEnterForeground: () -> Unit,
            onReleaseIncomingToken: (Long) -> Unit,
            onFallbackNotify: (Prayer) -> Unit,
            onCancelPriorJobs: () -> Unit,
            onStopSelfIfIdle: () -> Unit,
        ): Long? {
            try {
                onEnterForeground()
            } catch (t: Throwable) {
                if (t is Error) {
                    runCatching { onReleaseIncomingToken(incomingWakeLockToken) }
                    throw t
                }
                Log.e(TAG, "Failed to enter foreground for AdhanPlaybackService", t)
                runCatching { onReleaseIncomingToken(incomingWakeLockToken) }
                runCatching { onFallbackNotify(prayer) }
                runCatching { onStopSelfIfIdle() }
                return null
            }

            onCancelPriorJobs()
            val token = lifecycleController.nextGeneration()
            handoverTokens.register(token, incomingWakeLockToken)
            lifecycleController.cleanup()
            return token
        }
    }
}
