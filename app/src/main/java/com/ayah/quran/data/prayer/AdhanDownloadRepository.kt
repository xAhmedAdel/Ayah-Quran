package com.ayah.quran.data.prayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Download lifecycle for one adhan recording. */
sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

/**
 * Downloads adhan recordings using the SYSTEM DownloadManager.
 * Works reliably on ALL devices including HONOR/MagicOS — the system
 * handles redirects, SSL, retries, and network changes natively.
 */
@Singleton
class AdhanDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PrayerPreferencesRepository,
) {

    private val systemDm =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    /** System download IDs keyed by recording id (in-flight only). */
    private val systemIds = mutableMapOf<String, Long>()

    init {
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                    val sysId = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1L,
                    )
                    onSystemDownloadComplete(sysId)
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun audioDir(): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "adhan")
            .also { it.mkdirs() }

    fun isDownloaded(recordingId: String): Boolean {
        val ext = extFor(recordingId) ?: return false
        return File(audioDir(), "$recordingId.$ext").exists()
    }

    fun downloadedFile(recordingId: String): File? {
        val ext = extFor(recordingId) ?: return null
        val file = File(audioDir(), "$recordingId.$ext")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Enqueues a download via the SYSTEM DownloadManager. Works on ALL
     * devices — the system handles redirects, SSL, retries natively.
     */
    fun download(recording: AdhanRecording) {
        val url = recording.downloadUrl ?: return
        if (isDownloaded(recording.id)) return

        val fileName = "${recording.id}.${recording.fileExtension}"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(recording.displayNameAr.ifBlank { recording.displayNameEn })
            .setDescription(recording.license)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, fileName,
            )
            .setAllowedOverMetered(true)

        val systemId = systemDm.enqueue(request)
        systemIds[recording.id] = systemId
        _states.value = _states.value + (recording.id to AdhanDownloadState.Downloading(0f))
    }

    fun cancelDownload(recordingId: String) {
        val systemId = systemIds.remove(recordingId) ?: return
        systemDm.remove(systemId)
        _states.value = _states.value - recordingId
    }

    fun deleteDownload(recordingId: String) {
        cancelDownload(recordingId)
        scope.launch {
            preferencesRepository.setAdhanDownloaded(recordingId, false)
            downloadedFile(recordingId)?.delete()
            _states.value = _states.value - recordingId
        }
    }

    /**
     * Called by the DownloadManager completion receiver. Checks the system
     * status and marks the recording as downloaded (or failed).
     */
    fun onSystemDownloadComplete(systemId: Long) {
        scope.launch {
            val recordingId = systemIds.entries
                .firstOrNull { it.value == systemId }?.key
            if (recordingId == null) return@launch

            val query = DownloadManager.Query().setFilterById(systemId)
            systemDm.query(query).use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIdx)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        preferencesRepository.setAdhanDownloaded(recordingId, true)
                        _states.value = _states.value + (
                            recordingId to AdhanDownloadState.Downloaded
                            )
                    } else {
                        _states.value = _states.value + (
                            recordingId to AdhanDownloadState.Failed("status=$status")
                            )
                    }
                }
            }
        }
    }

    private fun extFor(recordingId: String): String? = when (recordingId) {
        "adhan_cc0_beautiful" -> "ogg"
        "adhan_pd_sabah_fakhri" -> "mp3"
        "adhan_by_sa_azan" -> "ogg"
        "adhan_by_sa_call_to_worship" -> "oga"
        "adhan_madinah_prophet_mosque" -> "ogg"
        "adhan_casablanca_hassan2" -> "wav"
        else -> null
    }
}
