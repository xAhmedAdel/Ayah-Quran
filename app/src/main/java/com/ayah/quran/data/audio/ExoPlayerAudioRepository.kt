package com.ayah.quran.data.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.di.DefaultDispatcher
import com.ayah.quran.domain.model.audio.AudioTrack
import com.ayah.quran.domain.model.audio.PlaybackState
import com.ayah.quran.domain.model.audio.RepeatMode
import com.ayah.quran.domain.repository.AudioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Media3/ExoPlayer-backed [AudioRepository].
 *
 * Lifecycle ownership: this singleton owns THE one platform player instance
 * for the app. The player is created lazily on the main looper, accessed
 * only on the main thread (Media3 requirement), and released only via
 * [release] (used by tests and process teardown — never mid-session).
 *
 * State mapping is explicit and honest: a track without a playable source
 * or a player error yields [PlaybackState.Error]; nothing synthesizes a
 * playing state.
 *
 * A MediaSessionService is deliberately DEFERRED (see PHASE-5.md): until a
 * licensed source exists there is no background playback to serve, and a
 * service would add untestable complexity.
 */
@Singleton
class ExoPlayerAudioRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : AudioRepository {

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** Guards creation/access of the player; all player access happens on main. */
    private val playerLock = Any()

    private var player: ExoPlayer? = null

    /** Set when onPlayerError fires; keeps Error sticky until the next prepare. */
    @Volatile
    private var errorSticky = false

    private var positionTicker: Job? = null

    /** Ensures the player exists; MUST be called on the main thread. */
    private fun obtainPlayer(): ExoPlayer {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ExoPlayerAudioRepository must be used from the main thread"
        }
        player?.let { return it }
        return ExoPlayer.Builder(context).build().also { player ->
            this.player = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (errorSticky && playbackState == Player.STATE_IDLE) {
                        return // keep the Error state set by onPlayerError
                    }
                    publishMappedState(playbackState, player.playWhenReady)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!errorSticky) {
                        publishMappedState(player.playbackState, player.playWhenReady)
                    }
                    if (isPlaying) startPositionTicker() else stopPositionTicker()
                }

                override fun onPlayerError(error: PlaybackException) {
                    errorSticky = true
                    stopPositionTicker()
                    _playbackState.value = PlaybackState.Error(
                        reason = error.errorCodeName,
                    )
                }
            })
        }
    }

    private fun publishMappedState(playbackState: Int, playWhenReady: Boolean) {
        _playbackState.value = when (playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> if (playWhenReady) PlaybackState.Playing else PlaybackState.Ready
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
        }
        if (playbackState == Player.STATE_READY) {
            _durationMs.value = max(0L, player?.duration ?: 0L)
            _positionMs.value = max(0L, player?.currentPosition ?: 0L)
        }
    }

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = appScope.launch(defaultDispatcher) {
            while (isActive) {
                val position = withContext(kotlinx.coroutines.Dispatchers.Main) {
                    player?.currentPosition ?: 0L
                }
                _positionMs.value = max(0L, position)
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    override suspend fun prepare(track: AudioTrack) {
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (!track.hasPlayableSource) {
                errorSticky = true
                stopPositionTicker()
                _playbackState.value = PlaybackState.Error(
                    reason = "This recitation has no available audio source yet.",
                )
                return@withContext
            }
            errorSticky = false
            _playbackState.value = PlaybackState.Loading
            _durationMs.value = track.durationMs ?: 0L
            _positionMs.value = 0L

            val uri = when (track.sourceType) {
                com.ayah.quran.domain.model.audio.AudioSourceType.LOCAL_FILE ->
                    Uri.fromFile(java.io.File(track.source!!))
                com.ayah.quran.domain.model.audio.AudioSourceType.REMOTE_URL ->
                    Uri.parse(track.source)
                com.ayah.quran.domain.model.audio.AudioSourceType.NONE ->
                    throw IllegalStateException("unreachable: checked hasPlayableSource")
            }
            val player = obtainPlayer()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun play() {
        withMainPlayer { it.play() }
    }

    override fun pause() {
        withMainPlayer { it.pause() }
    }

    override fun stop() {
        // Run the platform stop on the main thread and WAIT for it, so that
        // the Idle write below lands AFTER Media3 processed the stop
        // (deterministic ordering for observers).
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(Looper.getMainLooper()).post {
            synchronized(playerLock) {
                player?.let {
                    it.stop()
                    it.clearMediaItems()
                }
            }
            latch.countDown()
        }
        runCatching { latch.await(1, java.util.concurrent.TimeUnit.SECONDS) }

        stopPositionTicker()
        if (!errorSticky) {
            _playbackState.value = PlaybackState.Idle
            _positionMs.value = 0L
            _durationMs.value = 0L
        }
    }

    override fun release() {
        stopPositionTicker()
        // Media3 requires all player access on its application thread;
        // releasing asynchronously is acceptable (no further calls follow).
        android.os.Handler(Looper.getMainLooper()).post {
            synchronized(playerLock) {
                player?.release()
                player = null
            }
        }
        errorSticky = false
        _playbackState.value = PlaybackState.Idle
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    override fun setRepeatMode(mode: RepeatMode) {
        withMainPlayer {
            it.repeatMode = when (mode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            }
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        withMainPlayer {
            it.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
        }
    }

    /** Runs [block] with the player on the main thread when it exists. */
    private inline fun withMainPlayer(crossinline block: (ExoPlayer) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            android.os.Handler(Looper.getMainLooper()).post {
                player?.let(block)
            }
            return
        }
        player?.let(block)
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
