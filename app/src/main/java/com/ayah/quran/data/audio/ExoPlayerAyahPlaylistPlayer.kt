package com.ayah.quran.data.audio

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the audio tab (and later the reader) needs about playback. */
data class AyahPlaybackState(
    val editionId: String? = null,
    val surah: Int = 0,
    /** 0-based index into the surah's ayah list; -1 when nothing plays. */
    val ayahIndex: Int = -1,
    val totalAyahs: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val repeatAyah: Boolean = false,
) {
    val isIdle: Boolean get() = editionId == null || surah == 0
}

/**
 * Per-ayah Quran playback (Phase 17): an ExoPlayer PLAYLIST where each
 * item is one ayah — this is what makes ayah highlight and repeat-ayah
 * natural (current index == current ayah; repeat-one = player repeat mode
 * on the current item).
 *
 * Sources resolve per ayah: local file when downloaded (offline), otherwise
 * the CDN URL (streaming). The playlist is built once per play() call so
 * the same surah can fall back between offline/online sources at start.
 *
 * Foreground use only for now — the MediaSessionService (background
 * playback + notification) is the next milestone (PHASE-5 deferred it).
 */
@Singleton
class ExoPlayerAyahPlaylistPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: QuranAudioDownloadRepository,
) : AudioManager.OnAudioFocusChangeListener {

    private val _state = MutableStateFlow(AyahPlaybackState())
    val state: StateFlow<AyahPlaybackState> = _state.asStateFlow()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: ExoPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isPaused = !isPlaying &&
                    _state.value.ayahIndex >= 0 &&
                    player?.playbackState != Player.STATE_ENDED,
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = player?.currentMediaItemIndex ?: return
            _state.value = _state.value.copy(ayahIndex = index)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isPaused = false,
                )
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // A single failed ayah (network hiccup) skips to the next one
            // instead of killing the whole surah.
            val p = player ?: return
            if (p.hasNextMediaItem()) p.seekToNextMediaItem() else stop()
        }
    }

    /** Surah-aware audio focus: transient loss pauses, loss stops. */
    override fun onAudioFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            // CAN_DUCK: keep reciting at a lower stream — pause-less.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
        }
    }

    fun play(
        edition: QuranReciterEdition,
        surah: Int,
        startAyahInSurah: Int = 1,
    ) {
        ensurePlayer()
        val total = QuranAudioCatalog.ayahCount(surah)
        val firstGlobal = QuranAudioCatalog.firstGlobalAyah(surah)
        val items = (1..total).map { ayahInSurah ->
            val local: File? = downloadRepository.localAyahFile(
                edition.editionId, surah, ayahInSurah,
            )
            val uri = local?.let { android.net.Uri.fromFile(it) }
                ?: android.net.Uri.parse(
                    QuranAudioCatalog.ayahUrl(edition, firstGlobal + ayahInSurah - 1),
                )
            MediaItem.fromUri(uri)
        }

        if (!requestFocus()) return // incoming call etc.: don't fight for audio

        player?.run {
            setMediaItems(items, startAyahInSurah - 1, 0L)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            play()
        }
        _state.value = AyahPlaybackState(
            editionId = edition.editionId,
            surah = surah,
            ayahIndex = startAyahInSurah - 1,
            totalAyahs = total,
            isPlaying = true,
            repeatAyah = _state.value.repeatAyah,
        )
        applyRepeatMode()
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
    }

    fun resume() {
        player?.play()
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
    }

    fun seekToAyah(index: Int) {
        player?.seekTo(index, 0L)
        _state.value = _state.value.copy(ayahIndex = index)
    }

    fun toggleRepeatAyah() {
        _state.value = _state.value.copy(repeatAyah = !_state.value.repeatAyah)
        applyRepeatMode()
    }

    private fun applyRepeatMode() {
        player?.repeatMode = if (_state.value.repeatAyah) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        abandonFocus()
        _state.value = AyahPlaybackState(repeatAyah = _state.value.repeatAyah)
    }

    private fun ensurePlayer() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "ExoPlayerAyahPlaylistPlayer must be used from the main thread"
        }
        if (player != null) return
        player = ExoPlayer.Builder(context).build().also {
            it.addListener(listener)
            it.setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false, // focus handled explicitly above
            )
        }
    }

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN,
        ).setAudioAttributes(
            AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        ).setOnAudioFocusChangeListener(this).build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}
