package com.ayah.quran.prayer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ayah.quran.R
import com.ayah.quran.data.prayer.AdhanPlaybackSource
import com.ayah.quran.data.prayer.AdhanRawResources
import com.ayah.quran.data.prayer.AdhanVoiceResolver
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import com.ayah.quran.domain.model.prayer.Prayer
import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import com.ayah.quran.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Foreground service that plays the selected adhan audio when a prayer time
 * arrives — including with the app in the background.
 *
 * Honest limitations (also surfaced in the Advanced settings section):
 * - Audio comes ONLY from a user-selected local file (verified bundled
 *   recordings are not yet available; the catalog shows a truthful empty
 *   state). With no file selected the service falls back to the short
 *   notification and exits — it never fakes playback.
 * - OEM battery managers can delay or kill foreground services; we document
 *   this, we cannot promise playback under every configuration.
 *
 * Audio focus: gains ALARM focus; transient-loss pauses, permanent loss or
 * completion stops the service cleanly. Device volume is respected — the
 * service never changes system volume.
 */
@AndroidEntryPoint
class AdhanPlaybackService : android.app.Service() {

    @Inject lateinit var preferencesRepository: PrayerPreferencesRepository
    @Inject lateinit var notifications: PrayerNotifications
    @Inject lateinit var voiceResolver: AdhanVoiceResolver
    @Inject lateinit var sourceResolver: com.ayah.quran.data.prayer.AdhanPlaybackSourceResolver
    @Inject lateinit var catalog: BundledAdhanCatalog

    private var player: ExoPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cleanup()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val isTest = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER) ?: Prayer.FAJR.name
        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrDefault(Prayer.FAJR)
        val prefs = runBlocking { preferencesRepository.preferences.first() }

        // Phase 13/15: resolve THIS prayer's selected voice. Legacy user-file
        // selection still wins when chosen; otherwise the catalog resolution
        // (bundled resource, downloaded file, or DEFAULT fallback when a
        // remote voice is not downloaded — never silence).
        val audioUri: String? = when {
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

        startInForeground(prayer, isTest)

        if (audioUri == null) {
            // No resolvable audio: honest fallback to a notification, no
            // fake playback.
            notifications.post(prayer, com.ayah.quran.domain.model.prayer.AdhanMode.SILENT)
            stopSelf()
            return START_NOT_STICKY
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!requestFocus()) {
            // Incoming call / other focus holder: stop cleanly, notify only.
            notifications.post(prayer, com.ayah.quran.domain.model.prayer.AdhanMode.SILENT)
            stopSelf()
            return START_NOT_STICKY
        }

        val player = ExoPlayer.Builder(this).build().also {
            this.player = it
            it.setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_ALARM)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ false, // focus handled above, explicitly
            )
            it.setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            it.setMediaItem(MediaItem.fromUri(audioUri))
            it.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        cleanup()
                        stopSelf()
                    }
                }
            })
            it.prepare()
            it.play()
        }
        return START_NOT_STICKY
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
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> {
                        // Interruption (call, navigation, other alarm): stop
                        // cleanly rather than fight for focus.
                        cleanup()
                        stopSelf()
                    }
                }
            }
            .build()
        focusRequest = request
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
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

    private fun cleanup() {
        player?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        player = null
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_TEST = "test"
        const val ACTION_STOP = "com.ayah.quran.prayer.STOP_ADHAN"
    }
}
