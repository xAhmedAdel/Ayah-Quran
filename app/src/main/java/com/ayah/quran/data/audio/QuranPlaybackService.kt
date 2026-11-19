package com.ayah.quran.data.audio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ayah.quran.prayer.AdhanPlaybackService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Computes what the playback engine should do when the playhead moves —
 * pure and JVM-testable so sleep-timer and repeat-range behavior is pinned.
 */
object QuranPlaybackPolicy {

    /** Result of a media-item transition while a range/sleep is active. */
    sealed interface TransitionAction {
        data object Continue : TransitionAction
        data object Stop : TransitionAction
        data class WrapToRangeStart(val ayahIndex: Int) : TransitionAction
    }

    /**
     * @param newIndex the NEW 0-based ayah index after the transition.
     */
    fun onTransition(
        newIndex: Int,
        repeatRange: QuranRepeatRange?,
        sleepMode: QuranSleepMode,
    ): TransitionAction = when {
        // Sleep "after current ayah": a transition LEAVES an ayah — stop.
        // (Sleep takes precedence over a repeat-range wrap, by policy.)
        sleepMode == QuranSleepMode.AFTER_AYAH -> TransitionAction.Stop
        sleepMode == QuranSleepMode.AFTER_SURA -> TransitionAction.Continue

        repeatRange != null && (newIndex < repeatRange.fromAyah - 1 ||
            newIndex > repeatRange.toAyah - 1) ->
            TransitionAction.WrapToRangeStart(repeatRange.fromAyah - 1)

        else -> TransitionAction.Continue
    }

    /** Repeat mode for the ExoPlayer playlist given the active settings. */
    fun playerRepeatMode(repeatAyah: Boolean): Int = when {
        repeatAyah -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
}

/** A closed repeat range inside the surah (1-based ayah-in-surah bounds). */
data class QuranRepeatRange(val fromAyah: Int, val toAyah: Int) {
    init {
        require(fromAyah >= 1 && toAyah >= fromAyah) {
            "invalid repeat range: $fromAyah..$toAyah"
        }
    }
}

/** What stops playback when the sleep timer fires. */
enum class QuranSleepMode { NONE, AFTER_AYAH, AFTER_SURA, MINUTES }

/**
 * Phase 17 slice 2: background Quran recitation playback.
 *
 * Deliberately MINIMAL: it owns the ExoPlayer + MediaSession, and Media3
 * does the heavy lifting —
 * - the system media notification (play/pause, prev/next, seekbar) and
 *   lock-screen controls come from the session automatically;
 * - playback continues with the screen off / app backgrounded (foreground
 *   service with mediaPlayback type);
 * - audio focus with handleAudioFocus = true: phone calls and navigation
 *   pause+resume, permanent loss stops;
 * - dismissing the notification while NOT playing stops the service.
 *
 * The playlist, sleep timer, repeat-range logic and progress persistence
 * live in [ExoPlayerAyahPlaylistPlayer] (an in-process MediaController
 * client) which stays alive in the background alongside this foreground
 * service — keeping them out of the binder boundary keeps everything
 * testable and avoids custom-command plumbing.
 *
 * Conflict policy: ADHAN WINS. If the adhan is active this service refuses
 * to run; AdhanPlaybackService stops this service when the adhan starts.
 */
@AndroidEntryPoint
class QuranPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val ACTION_STOP_QURAN = "com.ayah.quran.audio.STOP_QURAN"
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            /* handleAudioFocus = */ true, // calls/interruptions handled by Media3
        )
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.action == ACTION_STOP_QURAN -> {
                stopPlaybackAndSelf()
                return START_NOT_STICKY
            }
            // Conflict policy: the adhan owns the moment.
            AdhanPlaybackService.isAdhanActive -> {
                stopPlaybackAndSelf()
                return START_NOT_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val sessionPlayer = mediaSession?.player ?: return
        // While reciting the notification is the stop gesture; when idle,
        // dismissal (or swiping the app away) must tear the service down.
        if (!sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            stopPlaybackAndSelf()
        }
    }

    private fun stopPlaybackAndSelf() {
        mediaSession?.player?.run {
            pause()
            clearMediaItems()
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
