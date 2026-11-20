package com.ayah.quran.data.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.prayer.AdhanPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

data class AyahPlaybackState(
    val editionId: String? = null,
    val surah: Int = 0,
    val ayahIndex: Int = -1,
    val totalAyahs: Int = 0,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val repeatAyah: Boolean = false,
    val blockedByAdhan: Boolean = false,
    val sleepMode: QuranSleepMode = QuranSleepMode.NONE,
    val sleepMinutes: Int = 0,
    val repeatRange: QuranRepeatRange? = null,
    val restorable: SavedPlayback? = null,
) {
    val isIdle: Boolean get() = editionId == null || surah == 0

    fun playingAyahFor(otherSurah: Int): Int? =
        if (surah == otherSurah && ayahIndex >= 0) ayahIndex + 1 else null
}

data class SavedPlayback(
    val editionId: String,
    val surah: Int,
    val ayahIndex: Int,
    val positionMs: Long,
) {
    val ayahInSurah: Int get() = ayahIndex + 1
}

internal class SleepTimerCoordinator(
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val onStopRequested: () -> Unit,
) {
    private val lock = Any()
    private var sleepJob: Job? = null
    private var generation: Long = 0L

    fun schedule(delayMillis: Long) {
        synchronized(lock) {
            sleepJob?.cancel()
            generation += 1L
            val token = generation
            sleepJob = scope.launch(mainDispatcher) {
                delay(delayMillis)
                val shouldStop = synchronized(lock) {
                    isActive && token == generation
                }
                if (shouldStop) {
                    onStopRequested()
                }
            }
        }
    }

    fun cancel() {
        synchronized(lock) {
            generation += 1L
            sleepJob?.cancel()
            sleepJob = null
        }
    }
}

@Singleton
class ExoPlayerAyahPlaylistPlayer internal constructor(
    private val context: Context?,
    private val downloadRepository: QuranAudioDownloadRepository?,
    private val appScope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher,
    private val stopControllerAction: (() -> Unit)?,
    private val stopServiceAction: (() -> Unit)?,
    autoConnect: Boolean,
) {

    @Inject
    constructor(
        @ApplicationContext appContext: Context,
        downloadRepository: QuranAudioDownloadRepository,
        @ApplicationScope appScope: CoroutineScope,
    ) : this(
        context = appContext,
        downloadRepository = downloadRepository,
        appScope = appScope,
        mainDispatcher = Dispatchers.Main.immediate,
        stopControllerAction = null,
        stopServiceAction = null,
        autoConnect = true,
    )

    private val _state = MutableStateFlow(AyahPlaybackState())
    val state: StateFlow<AyahPlaybackState> = _state.asStateFlow()

    private var sleepMode: QuranSleepMode = QuranSleepMode.NONE
    private var sleepMinutes: Int = 0
    private var repeatRange: QuranRepeatRange? = null

    private var currentEditionId: String? = null
    private var currentSurah: Int = 0
    private var currentTotal: Int = 0

    private var controller: MediaController? = null

    private var pendingPlay: Triple<QuranReciterEdition, Int, Int>? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? =
        null

    private val sleepTimerCoordinator = SleepTimerCoordinator(
        scope = appScope,
        mainDispatcher = mainDispatcher,
        onStopRequested = { stopOnMain() },
    )

    private val controllerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val c = controller ?: return
            val index = c.currentMediaItemIndex
            when (QuranPlaybackPolicy.onTransition(index, repeatRange, sleepMode)) {
                is QuranPlaybackPolicy.TransitionAction.Stop -> stopOnMain()
                is QuranPlaybackPolicy.TransitionAction.WrapToRangeStart -> {
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
                    stopOnMain()
                } else {
                    _state.value = _state.value.copy(isPlaying = false, isPaused = false)
                    persistProgress()
                }
            }
        }
    }

    init {
        if (autoConnect) {
            connect()
            appScope.launch { restoreSnapshot() }
        }
    }

    private fun connect() {
        val ctx = context ?: return
        val token = SessionToken(
            ctx,
            ComponentName(ctx, QuranPlaybackService::class.java),
        )
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(controllerListener)
                applyRepeatMode()
                syncFromController()
                pendingPlay?.let { (edition, surah, startAyah) ->
                    pendingPlay = null
                    play(edition, surah, startAyah)
                }
            } ?: run {
                appScope.launch(mainDispatcher) {
                    delay(2_000)
                    if (controller == null) connect()
                }
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

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
        if (AdhanPlaybackService.isAdhanActive) {
            _state.value = _state.value.copy(blockedByAdhan = true)
            return
        }
        _state.value = _state.value.copy(blockedByAdhan = false)

        if (controller == null) {
            pendingPlay = Triple(edition, surah, startAyahInSurah)
            return
        }
        val c = controller ?: return
        val total = QuranAudioCatalog.ayahCount(surah)
        val firstGlobal = QuranAudioCatalog.firstGlobalAyah(surah)
        val items = (1..total).map { ayahInSurah ->
            val local: File? = downloadRepository?.localAyahFile(
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
        appScope.launch(mainDispatcher) {
            stopOnMain()
        }
    }

    internal fun stopOnMain() {
        clearTimer()
        persistProgress()
        pendingPlay = null
        val customStop = stopControllerAction
        if (customStop != null) {
            runCatching { customStop.invoke() }
        } else {
            controller?.run {
                runCatching { stop() }
                runCatching { clearMediaItems() }
            }
        }
        val customServiceStop = stopServiceAction
        if (customServiceStop != null) {
            runCatching { customServiceStop.invoke() }
        } else {
            context?.let { ctx ->
                runCatching {
                    ctx.stopService(Intent(ctx, QuranPlaybackService::class.java))
                }
            }
        }
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

    fun setRepeatRange(range: QuranRepeatRange?) {
        repeatRange = range
        _state.value = _state.value.copy(repeatRange = range)
    }

    fun setSleepTimer(mode: QuranSleepMode, minutes: Int = 0) {
        setSleepTimerMillis(mode, minutes, minutes * 60_000L)
    }

    internal fun setSleepTimerMillis(mode: QuranSleepMode, minutes: Int, delayMillis: Long) {
        clearTimer()
        sleepMode = mode
        sleepMinutes = minutes
        _state.value = _state.value.copy(sleepMode = mode, sleepMinutes = minutes)
        if (mode == QuranSleepMode.MINUTES && minutes > 0 && delayMillis > 0L) {
            sleepTimerCoordinator.schedule(delayMillis)
        }
    }

    internal fun setStateForTest(state: AyahPlaybackState) {
        _state.value = state
    }

    private fun clearTimer() {
        sleepTimerCoordinator.cancel()
        sleepMode = QuranSleepMode.NONE
        sleepMinutes = 0
        _state.value = _state.value.copy(
            sleepMode = QuranSleepMode.NONE,
            sleepMinutes = 0,
        )
    }

    private fun applyRepeatMode() {
        controller?.repeatMode = QuranPlaybackPolicy.playerRepeatMode(
            _state.value.repeatAyah,
        )
    }

    private fun persistProgress() {
        val ctx = context ?: return
        val index = _state.value.ayahIndex
        if (currentEditionId == null || currentSurah == 0 || index < 0) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EDITION, currentEditionId)
            .putInt(KEY_SURAH, currentSurah)
            .putInt(KEY_AYAH_INDEX, index)
            .putLong(KEY_POSITION_MS, 0L)
            .apply()
        publishRestorable()
    }

    private fun publishRestorable() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editionId = prefs.getString(KEY_EDITION, null) ?: return
        val surah = prefs.getInt(KEY_SURAH, 0)
        val ayahIndex = prefs.getInt(KEY_AYAH_INDEX, -1)
        if (surah !in 1..114 || ayahIndex < 0) return
        val edition = QuranAudioCatalog.byEdition(editionId)
        _state.value = _state.value.copy(
            restorable = SavedPlayback(edition.editionId, surah, ayahIndex, 0L),
        )
    }

    fun clearRestore() {
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.apply()
        _state.value = _state.value.copy(restorable = null)
    }

    fun resumeLast() {
        val saved = _state.value.restorable ?: return
        val edition = QuranAudioCatalog.byEdition(saved.editionId)
        play(edition, saved.surah, saved.ayahInSurah)
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    fun durationMs(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

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
