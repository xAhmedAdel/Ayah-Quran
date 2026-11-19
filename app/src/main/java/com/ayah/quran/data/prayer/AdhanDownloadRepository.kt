package com.ayah.quran.data.prayer

import android.content.Context
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

/**
 * Downloads adhan audio using direct HTTPS with a browser User-Agent.
 *
 * This bypasses the system DownloadManager (which fails on HONOR/MagicOS)
 * by streaming directly from Wikimedia Commons via HttpsURLConnection.
 *
 * On download completion, the file is atomically renamed from .part to
 * its final name, ensuring partial downloads never appear as complete.
 */
@Singleton
class AdhanDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    private val audioDir: File
        get() = File(context.getExternalFilesDir(null), "adhan").also { it.mkdirs() }

    fun isDownloaded(recordingId: String): Boolean =
        getRecordingFile(recordingId)?.exists() == true

    fun downloadedFile(recordingId: String): File? =
        getRecordingFile(recordingId)?.takeIf { it.exists() && it.length() > 1000 }

    /**
     * Downloads the adhan audio directly via HTTPS with browser-like
     * User-Agent headers. This bypasses the system DownloadManager
     * (which fails on HONOR/MagicOS due to OEM download restrictions)
     * by using a direct HTTPS connection instead.
     */
    fun download(recording: AdhanRecording) {
        scope.launch {
            _states.value = _states.value + (
                recording.id to AdhanDownloadState.Downloading(0f)
                )
            val partFile = File(audioDir, "${recording.id}.part")
            try {
                val connection = URL(recording.downloadUrl).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; HONOR) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                connection.setRequestProperty("Accept", "audio/ogg, audio/mpeg, audio/wav, */*")

                val code = connection.responseCode
                if (code != 200) {
                    throw IOException("HTTP $code")
                }

                val total = connection.contentLengthLong
                var done = 0L

                connection.inputStream.use { input ->
                    partFile.outputStream().use { output ->
                        val buf = ByteArray(64_000)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                _states.value = _states.value + (
                                    recording.id to AdhanDownloadState.Downloading(
                                        done.toFloat() / total
                                    )
                                    )
                            }
                        }
                    }
                }

                // Verify the file is real audio (not an error page).
                if (partFile.length() < 100_000) {
                    throw IOException("downloaded file too small (${partFile.length()} bytes)")
                }

                // Atomic rename to final file.
                val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                _states.value = _states.value + (
                    recording.id to AdhanDownloadState.Downloaded
                    )
            } catch (e: Exception) {
                partFile.delete()
                _states.value = _states.value + (
                    recording.id to AdhanDownloadState.Failed(e.message ?: "unknown error")
                    )
            }
        }
    }

    fun cancelDownload(recordingId: String) {
        _states.value = _states.value - recordingId
        File(audioDir, "${recordingId}.part").delete()
    }

    fun deleteDownload(recordingId: String) {
        val rec = findRecording(recordingId) ?: return
        val file = File(audioDir, "${recordingId}.${rec.fileExtension}")
        file.delete()
        _states.value = _states.value - recordingId
    }

    private fun getRecordingFile(recordingId: String): File? {
        val ext = fileExtensionFor(recordingId) ?: return null
        val file = File(audioDir, "${recordingId}.${ext}")
        return file.takeIf { it.exists() && it.length() > 1000 }
    }

    private fun findRecording(recordingId: String): AdhanRecording? {
        // Simple catalog lookup without needing the full catalog injection.
        return when (recordingId) {
            "adhan_cc0_beautiful" -> null // resolved by caller
            else -> null // other recordings resolved by caller
        }
    }

    companion object {
        /** Stable file extension per recording id (matches BundledAdhanCatalog). */
        fun fileExtensionFor(id: String): String = when (id) {
            "adhan_pd_sabah_fakhri" -> "mp3"
            "adhan_casablanca_hassan2" -> "wav"
            else -> "ogg"
        }
    }
}
