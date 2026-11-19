package com.ayah.quran.data.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.prayer.AdhanPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the audio tab, mini player, and reader need about playback. */
data class AyahPlaybackState(
    val editionId: String? = null,
    val surah: Int = 0,
    /** 0-based index into the surah's ayah list; -1 when nothing plays. */
    val ayahIndex: Int = -1,
    val totalAyahs: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val repeatAyah: Boolean = false,
    // ---- slice 2 additions (additive; slice-1 fields unchanged) ----
    /** Quran playback refused: the adhan owns the moment. */
    val blockedByAdhan: Boolean = false,
    /** Active sleep timer (mirrors what the UI shows). */
    val sleepMode: QuranSleepMode = QuranSleepMode.NONE,
    val sleepMinutes: Int = 0,
    /** Active closed repeat range, if any. */
    val repeatRange: QuranRepeatRange? = null,
    /** Last playback snapshot from a previous process (resume affordance). */
    val restorable: SavedPlayback? = null,
) {
    val isIdle: Boolean get() = editionId == null || surah == 0

    /** 1-based ayah number being recited within [otherSurah], or null. */
    fun playingAyahFor(otherSurah: Int): Int? =
        if (surah == otherSurah && ayahIndex >= 0) ayahIndex + 1 else null
}

/** Persisted playback snapshot for post-process-recreation resume. */
data class SavedPlayback(
    val editionId: String,
    val surah: Int,
    val ayahIndex: Int,
    val positionMs: Long,
) {
    val ayahInSurah: Int get() = ayahIndex + 1
}

/**
 * Phase 17 playback client (slice 1 API preserved, slice 2 internals):
 *
 * Slice 1 built its own ExoPlayer; slice 2 moves the player INTO
 * [QuranPlaybackService] (MediaSessionService) so recitation continues in
 * the background with the system media notification and lock-screen
 * controls, and audio focus (calls, other apps) is handled by Media3.
 * This class became an in-process MediaController client — it still owns
 * the per-ayah playlist construction, the sleep timer, the repeat range
 * and the progress persistence (all valid in background because the
 * foreground service keeps the process alive).
 *
 * Public API unchanged from slice 1: [state], [play], [pause], [resume],
 * [stop], [seekToAyah], [toggleRepeatAyah] — slice-1 callers untouched.
 */
@Singleton
class ExoPlayerAyahPlaylistPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: QuranAudioDownloadRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _state = MutableStateFlow(AyahPlaybackState())
    val state: StateFlow<AyahPlaybackState> = _state.asStateFlow()

    // Slice-2 playback settings owned here so they survive in background.
    private var sleepMode: QuranSleepMode = QuranSleepMode.NONE
    private var sleepMinutes: Int = 0
    private var sleepJob: Job? = null
    private var repeatRange: QuranRepeatRange? = null

    /** Identity of the currently loaded playlist (for progress persistence). */
    private var currentEditionId: String? = null
    private var currentSurah: Int = 0
    private var currentTotal: Int = 0

    private var controller: MediaController? = null

    /** Queued play() from before the controller finished connecting. */
    private var pendingPlay: Triple<QuranReciterEdition, Int, Int>? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? =
        null

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val index = c.currentMediaItemIndex
            when (QuranPlaybackPolicy.onTransition(index, repeatRange, sleepMode)) {
                is QuranPlaybackPolicy.TransitionAction.Stop -> stop()
                is QuranPlaybackPolicy.TransitionAction.WrapToRangeStart -> {
                    // Re-evaluate without the sleep so a range wrap is NOT
                    // treated as "left the current ayah" (sleep handled above).
                    c.seekTo(repeatRange!!.fromAyah - 1, 0L)
                    _state.value = _state.value.copy(ayahIndex = repeatRange!!.fromAyah - 1)
                }
                QuranPlaybackPolicy.TransitionAction.Continue ->
                    _state.value = _state.value.copy(ayahIndex = index)
            }
            persistProgress()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isPaused = !isPlaying &&
                    _state.value.ayahIndex >= 0 &&
                    controller?.playbackState != Player.STATE_ENDED &&
                    controller?.mediaItemCount != 0,
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                if (sleepMode == QuranSleepMode.AFTER_SURA) {
                    stop()
                } else {
                    _state.value = _state.value.copy(isPlaying = false, isPaused = false)
                    persistProgress()
                }
            }
        }
    }

    init {
        connect()
        appScope.launch { restoreSnapshot() }
    }

    private fun connect() {
        val token = SessionToken(
            context,
            ComponentName(context, QuranPlaybackService::class.java),
        )
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture!!.addListener({
            runCatching { controllerFuture!!.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(controllerListener)
                applyRepeatMode()
                syncFromController()
                pendingPlay?.let { (edition, surah, startAyah) ->
                    pendingPlay = null
                    play(edition, surah, startAyah)
                }
            } ?: run {
                // Retry once after a delay — the service can be temporarily
                // unreachable during process teardown races.
                appScope.launch {
                    delay(2_000)
                    if (controller == null) connect()
                }
            }
        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
    }

    /** Mirrors the service-side player into our published state. */
    private fun syncFromController() {
        val c = controller ?: return
        _state.value = _state.value.copy(
            ayahIndex = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            isPaused = !c.isPlaying && c.mediaItemCount != 0 &&
                c.playbackState != Player.STATE_ENDED,
        )
    }

    fun play(
        edition: QuranReciterEdition,
        surah: Int,
        startAyahInSurah: Int = 1,
    ) {
        // Conflict policy: the adhan owns the moment — refuse, don't overlap.
        if (AdhanPlaybackService.isAdhanActive) {
            _state.value = _state.value.copy(blockedByAdhan = true)
            return
        }
        _state.value = _state.value.copy(blockedByAdhan = false)

        if (controller == null) {
            // Controller still connecting (rare, e.g. cold start + immediate
            // tap): queue and run once connected — never runBlocking on main.
            pendingPlay = Triple(edition, surah, startAyahInSurah)
            return
        }
        val c = controller ?: return
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
        currentEditionId = edition.editionId
        currentSurah = surah
        currentTotal = total

        c.run {
            setMediaItems(items, (startAyahInSurah - 1).coerceIn(0, total - 1), 0L)
            prepare()
            play()
        }
        _state.value = _state.value.copy(
            editionId = edition.editionId,
            surah = surah,
            ayahIndex = startAyahInSurah - 1,
            totalAyahs = total,
            isPlaying = true,
            isPaused = false,
        )
        applyRepeatMode()
        persistProgress()
    }

    fun pause() {
        controller?.pause()
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
        persistProgress()
    }

    fun resume() {
        controller?.play()
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
    }

    fun stop() {
        clearTimer()
        persistProgress()
        controller?.run {
            stop()
            clearMediaItems()
        }
        // Context started the service implicitly via the controller — stop
        // it so the notification clears.
        context.startService(
            Intent(context, QuranPlaybackService::class.java)
                .setAction(QuranPlaybackService.ACTION_STOP_QURAN),
        )
        _state.value = AyahPlaybackState(
            repeatAyah = _state.value.repeatAyah,
            restorable = _state.value.restorable,
        )
    }

    fun seekToAyah(index: Int) {
        controller?.seekTo(index, 0L)
        _state.value = _state.value.copy(ayahIndex = index)
    }

    fun seekNextAyah() {
        val c = controller ?: return
        if (c.hasNextMediaItem()) seekToAyah(c.currentMediaItemIndex + 1)
    }

    fun seekPreviousAyah() {
        val c = controller ?: return
        if (c.hasPreviousMediaItem()) seekToAyah(c.currentMediaItemIndex - 1)
    }

    fun toggleRepeatAyah() {
        _state.value = _state.value.copy(repeatAyah = !_state.value.repeatAyah)
        applyRepeatMode()
    }

    /** Repeat a closed range of ayahs inside the current surah. */
    fun setRepeatRange(range: QuranRepeatRange?) {
        repeatRange = range
        _state.value = _state.value.copy(repeatRange = range)
    }

    // ---- sleep timer (works in background: foreground service holds us) ----

    fun setSleepTimer(mode: QuranSleepMode, minutes: Int = 0) {
        clearTimer()
        sleepMode = mode
        sleepMinutes = minutes
        _state.value = _state.value.copy(sleepMode = mode, sleepMinutes = minutes)
        if (mode == QuranSleepMode.MINUTES && minutes > 0) {
            sleepJob = appScope.launch {
                delay(minutes * 60_000L)
                if (isActive) stop()
            }
        }
    }

    private fun clearTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepMode = QuranSleepMode.NONE
        sleepMinutes = 0
        _state.value = _state.value.copy(
            sleepMode = QuranSleepMode.NONE, sleepMinutes = 0,
        )
    }

    private fun applyRepeatMode() {
        controller?.repeatMode = QuranPlaybackPolicy.playerRepeatMode(
            _state.value.repeatAyah,
        )
    }

    // ---- process-recreation restore (resume affordance, never auto-play) --

    private fun persistProgress() {
        val index = _state.value.ayahIndex
        if (currentEditionId == null || currentSurah == 0 || index < 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EDITION, currentEditionId)
            .putInt(KEY_SURAH, currentSurah)
            .putInt(KEY_AYAH_INDEX, index)
            .putLong(KEY_POSITION_MS, 0L) // resume restarts the ayah (honest sync)
            .apply()
        publishRestorable()
    }

    private fun publishRestorable() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editionId = prefs.getString(KEY_EDITION, null) ?: return
        val surah = prefs.getInt(KEY_SURAH, 0)
        val ayahIndex = prefs.getInt(KEY_AYAH_INDEX, -1)
        if (surah in 1..114 && ayahIndex >= 0) {
            _state.value = _state.value.copy(
                restorable = SavedPlayback(editionId, surah, ayahIndex, 0L),
            )
        }
    }

    private suspend fun restoreSnapshot() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editionId = prefs.getString(KEY_EDITION, null) ?: return
        val surah = prefs.getInt(KEY_SURAH, 0)
        val ayahIndex = prefs.getInt(KEY_AYAH_INDEX, -1)
        if (surah !in 1..114 || ayahIndex < 0) return
        val edition = QuranAudioCatalog.byEdition(editionId)
        _state.value = _state.value.copy(
            restorable = SavedPlayback(edition.editionId, surah, ayahIndex, 0L),
        )
    }

    /** Forget the persisted resume snapshot (tests, explicit user stop). */
    fun clearRestore() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        _state.value = _state.value.copy(restorable = null)
    }

    /** Resume the persisted snapshot from the beginning of its ayah. */
    fun resumeLast() {
        val saved = _state.value.restorable ?: return
        val edition = QuranAudioCatalog.byEdition(saved.editionId)
        play(edition, saved.surah, saved.ayahInSurah)
    }

    /** Current ayah position/duration in ms for seek bars (main thread OK). */
    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    /** Playback speed applied to the session player (persisted by the VM). */
    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    private companion object {
        const val PREFS = "quran_playback"
        const val KEY_EDITION = "edition"
        const val KEY_SURAH = "surah"
        const val KEY_AYAH_INDEX = "ayahIndex"
        const val KEY_POSITION_MS = "positionMs"
    }
}
