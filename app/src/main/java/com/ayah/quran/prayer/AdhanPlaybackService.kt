package com.ayah.quran.prayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface AdhanPlaybackHandle {
    fun pause()
    fun play()
    fun stopAndRelease()
}

internal class AdhanPlaybackLifecycleController(
    private val onActiveChanged: (Boolean) -> Unit,
    private val onRequestFocus: () -> Boolean,
    private val onAbandonFocus: () -> Unit,
    private val onFallbackNotify: (Prayer) -> Unit,
    private val onStopQuran: () -> Unit,
    private val onStopSelf: () -> Unit,
    private val createAndStartPlayer: (uri: String, onEnded: () -> Unit, onError: (Throwable?) -> Unit) -> AdhanPlaybackHandle,
) {
    private var currentHandle: AdhanPlaybackHandle? = null
    private var generation: Long = 0L

    fun nextGeneration(): Long {
        generation += 1L
        return generation
    }

    fun isCurrentGeneration(token: Long): Boolean = token == generation

    fun startPlayback(token: Long, prayer: Prayer, audioUri: String?) {
        if (!isCurrentGeneration(token)) return
        cleanup()
        if (audioUri.isNullOrBlank()) {
            onFallbackNotify(prayer)
            onStopSelf()
            return
        }
        if (!onRequestFocus()) {
            onFallbackNotify(prayer)
            onStopSelf()
            return
        }
        try {
            val handle = createAndStartPlayer(
                audioUri,
                {
                    if (isCurrentGeneration(token)) {
                        cleanup()
                        onStopSelf()
                    }
                },
                {
                    if (isCurrentGeneration(token)) {
                        onFallbackNotify(prayer)
                        cleanup()
                        onStopSelf()
                    }
                },
            )
            if (!isCurrentGeneration(token)) {
                runCatching { handle.stopAndRelease() }
                onAbandonFocus()
                return
            }
            currentHandle = handle
            onActiveChanged(true)
            onStopQuran()
        } catch (t: Throwable) {
            onFallbackNotify(prayer)
            cleanup()
            onStopSelf()
        }
    }

    fun onFocusLossPermanent() {
        cleanup()
        onStopSelf()
    }

    fun onFocusLossTransient() {
        currentHandle?.pause()
    }

    fun onFocusGain() {
        currentHandle?.play()
    }

    fun stopAll() {
        generation += 1L
        cleanup()
        onStopSelf()
    }

    fun cleanup() {
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val lifecycleController by lazy {
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
                createExoPlayerHandle(uri, onEnded, onError)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            startJob?.cancel()
            startJob = null
            lifecycleController.stopAll()
            return START_NOT_STICKY
        }

        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER) ?: Prayer.FAJR.name
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrDefault(Prayer.FAJR)

        startInForeground(prayer, isTest)

        startJob?.cancel()
        val token = lifecycleController.nextGeneration()
        lifecycleController.cleanup()

        startJob = serviceScope.launch {
            val audioUri = try {
                withContext(Dispatchers.IO) {
                    resolveAudioUri(prayer)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to resolve Adhan preferences/audio source", t)
                null
            }
            if (!lifecycleController.isCurrentGeneration(token)) return@launch
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
        startJob?.cancel()
        startJob = null
        serviceScope.cancel()
        lifecycleController.cleanup()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AdhanPlaybackService"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TEST = "test"
        const val ACTION_STOP = "com.ayah.quran.prayer.STOP_ADHAN"

        @Volatile
        var isAdhanActive: Boolean = false
            private set
    }
}
