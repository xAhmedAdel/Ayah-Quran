package com.ayah.quran.data.prayer

import android.content.Context
import android.util.Log
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

/**
 * Downloads adhan audio using OkHttp (direct HTTPS, reliable on ALL devices
 * including HONOR/MagicOS). Saves to app-private storage for offline playback.
 */
@Singleton
class AdhanDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    private val audioDir: File
        get() = File(context.getExternalFilesDir(null), "adhan").also { it.mkdirs() }

    fun isDownloaded(recording: AdhanRecording): Boolean =
        fileOf(recording).let { it.exists() && it.length() > 1000 }

    private fun fileOf(recording: AdhanRecording): File =
        File(audioDir, "${recording.id}.${recording.fileExtension}")

    fun downloadedFile(recordingId: String): File? =
        audioDir.listFiles()?.firstOrNull {
            it.name.startsWith("$recordingId.") && it.length() > 1000
        }

    /**
     * Downloads the adhan audio using OkHttp with browser-like User-Agent.
     * OkHttp handles redirects, SSL, timeouts properly on all devices.
     */
    fun download(recording: AdhanRecording) {
        scope.launch {
            Log.i(TAG, "download: start id=${recording.id} url=${recording.downloadUrl}")
            _states.value = _states.value + (
                recording.id to AdhanDownloadState.Downloading(0f)
                )
            val partFile = File(audioDir, "${recording.id}.part")
            try {
                val request = Request.Builder()
                    .url(recording.downloadUrl ?: throw IOException("no download URL"))
                    .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; HONOR) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "download: HTTP ${response.code} id=${recording.id}")
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} from ${recording.downloadUrl}")
                    }
                    val body = response.body ?: throw IOException("empty body")
                    val contentLength = body.contentLength()
                    var total = 0L
                    var lastEmit = 0f
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buf = ByteArray(64_000)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                total += read
                                if (contentLength > 0) {
                                    val progress = total.toFloat() / contentLength
                                    if (progress - lastEmit >= 0.1f || progress >= 1f) {
                                        lastEmit = progress
                                        _states.value = _states.value + (
                                            recording.id to
                                                AdhanDownloadState.Downloading(progress)
                                            )
                                    }
                                }
                            }
                        }
                    }
                    Log.i(TAG, "download: bytes=$total id=${recording.id}")
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
                Log.e(TAG, "download: FAILED id=${recording.id}", e)
                partFile.delete()
                _states.value = _states.value + (
                    recording.id to AdhanDownloadState.Failed(e.message ?: "unknown error")
                    )
            }
        }
    }

    private companion object {
        const val TAG = "AdhanDownload"
    }

    fun cancelDownload(recordingId: String) {
        _states.value = _states.value - recordingId
        File(audioDir, "${recordingId}.part").delete()
    }

    fun deleteDownload(recordingId: String) {
        audioDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$recordingId.")) file.delete()
        }
        _states.value = _states.value - recordingId
    }
}
