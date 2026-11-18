package com.ayah.quran.data.prayer

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ayah.quran.domain.model.prayer.AdhanRecording
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Preview playback state exposed to the library UI. */
data class AdhanPreviewState(
    val recording: AdhanRecording? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0,
    val totalMs: Long = 0,
) {
    val isIdle: Boolean get() = recording == null
}

/**
 * In-app PREVIEW playback for the adhan voice library. Strictly scoped:
 *
 * - exactly one preview at a time (starting another stops the first),
 * - audio focus loss (incoming call, other media) stops the preview,
 * - [stop] releases the ExoPlayer instance and abandons focus,
 * - never touches the scheduled adhan pipeline (no alarms, no selections,
 *   no foreground service — previews live only while the screen is open).
 */
@Singleton
class AdhanPreviewController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: BundledAdhanCatalog,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(AdhanPreviewState())
    val state: StateFlow<AdhanPreviewState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var ticker: Job? = null

    fun start(recording: AdhanRecording) {
        scope.launch {
            stopInternal() // one preview at a time
            if (!requestFocus()) return@launch

            val newPlayer = ExoPlayer.Builder(context).build().also { player = it }
            newPlayer.setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false, // explicit focus handling below
            )
            newPlayer.setMediaItem(
                MediaItem.fromUri(
                    "android.resource://${context.packageName}/${AdhanRawResources.resIdOf(recording.id)}",
                ),
            )
            newPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        // Completion returns the button to its original state.
                        stopInternal()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (_state.value.recording?.id == recording.id) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }
                }
            })
            newPlayer.prepare()
            newPlayer.play()

            _state.value = AdhanPreviewState(
                recording = recording,
                isPlaying = true,
                totalMs = recording.durationSeconds * 1000L,
            )
            startTicker(recording)
        }
    }

    fun pause() {
        scope.launch {
            val p = player ?: return@launch
            p.pause()
            _state.value = _state.value.copy(isPlaying = false, isPaused = true)
        }
    }

    fun resume() {
        scope.launch {
            val p = player ?: return@launch
            p.play()
            _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    /** Called when the library screen goes away — releases Media3 resources. */
    fun release() {
        scope.launch { stopInternal() }
    }

    private fun startTicker(recording: AdhanRecording) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player != null &&
                _state.value.recording?.id == recording.id
            ) {
                player?.let { p ->
                    _state.value = _state.value.copy(
                        elapsedMs = p.currentPosition.coerceAtLeast(0),
                        totalMs = p.duration.takeIf { it > 0 }
                            ?: (recording.durationSeconds * 1000L),
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopInternal() {
        ticker?.cancel()
        ticker = null
        player?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        player = null
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
        _state.value = AdhanPreviewState()
    }

    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> stopInternal()
                }
            }
            .build()
        focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
}
