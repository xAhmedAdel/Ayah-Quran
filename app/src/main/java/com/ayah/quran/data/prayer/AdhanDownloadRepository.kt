package com.ayah.quran.data.prayer

import android.content.Context
import android.util.Log
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.di.IoDispatcher
import com.ayah.quran.domain.model.prayer.AdhanRecording
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

@Singleton
class AdhanDownloadRepository internal constructor(
    private val audioDirProvider: () -> File,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val minValidBytes: Long,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        baseClient: OkHttpClient,
        @ApplicationScope appScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        audioDirProvider = { File(context.getExternalFilesDir(null), "adhan").also { it.mkdirs() } },
        client = baseClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
        scope = appScope,
        ioDispatcher = ioDispatcher,
        minValidBytes = DEFAULT_MIN_VALID_BYTES,
    )

    private class ActiveDownload {
        val job = AtomicReference<Job?>(null)
        val call = AtomicReference<Call?>(null)
        val cancelled = AtomicBoolean(false)

        fun cancel() {
            cancelled.set(true)
            call.get()?.cancel()
            job.get()?.cancel()
        }
    }

    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    private val audioDir: File
        get() = audioDirProvider().also { it.mkdirs() }

    fun isDownloaded(recording: AdhanRecording): Boolean =
        fileOf(recording).let { it.exists() && it.length() > 1000 }

    private fun fileOf(recording: AdhanRecording): File =
        File(audioDir, "${recording.id}.${recording.fileExtension}")

    fun downloadedFile(recordingId: String): File? =
        audioDir.listFiles()?.firstOrNull {
            it.name.startsWith("$recordingId.") && !it.name.endsWith(".part") && it.length() > 1000
        }

    fun download(recording: AdhanRecording) {
        val active = ActiveDownload()
        if (activeDownloads.putIfAbsent(recording.id, active) != null) {
            return
        }
        _states.update { it + (recording.id to AdhanDownloadState.Downloading(0f)) }

        val job = scope.launch(ioDispatcher) {
            val partFile = File(audioDir, "${recording.id}.part")
            try {
                Log.i(TAG, "download: start id=${recording.id}")
                val downloadUrl = recording.downloadUrl ?: throw IOException("no download URL")
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; HONOR) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                    )
                    .build()

                val call = client.newCall(request)
                active.call.set(call)
                if (active.cancelled.get()) {
                    call.cancel()
                    throw CancellationException("Download cancelled")
                }

                call.execute().use { response ->
                    currentCoroutineContext().ensureActive()
                    if (active.cancelled.get()) throw CancellationException("Download cancelled")
                    Log.i(TAG, "download: HTTP ${response.code} id=${recording.id}")
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} downloading adhan")
                    }
                    val body = response.body ?: throw IOException("empty body")
                    val contentLength = body.contentLength()
                    var total = 0L
                    var lastEmit = 0f
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buf = ByteArray(64_000)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (active.cancelled.get()) {
                                    throw CancellationException("Download cancelled")
                                }
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                total += read
                                if (contentLength > 0) {
                                    val progress = (total.toFloat() / contentLength).coerceIn(0f, 1f)
                                    if (progress - lastEmit >= 0.1f || progress >= 1f) {
                                        lastEmit = progress
                                        if (!active.cancelled.get()) {
                                            _states.update {
                                                it + (recording.id to AdhanDownloadState.Downloading(progress))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Log.i(TAG, "download: bytes=$total id=${recording.id}")
                }

                currentCoroutineContext().ensureActive()
                if (active.cancelled.get()) {
                    throw CancellationException("Download cancelled")
                }

                if (partFile.length() < minValidBytes) {
                    throw IOException("downloaded file too small (${partFile.length()} bytes)")
                }

                val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
                if (active.cancelled.get()) {
                    throw CancellationException("Download cancelled")
                }
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                if (active.cancelled.get()) {
                    finalFile.delete()
                    throw CancellationException("Download cancelled")
                }

                _states.update { it + (recording.id to AdhanDownloadState.Downloaded) }
            } catch (ce: CancellationException) {
                partFile.delete()
                _states.update { current ->
                    if (activeDownloads[recording.id] === active || current[recording.id] is AdhanDownloadState.Downloading) {
                        current - recording.id
                    } else {
                        current
                    }
                }
                throw ce
            } catch (e: Exception) {
                partFile.delete()
                if (active.cancelled.get()) {
                    _states.update { it - recording.id }
                } else {
                    Log.e(TAG, "download: FAILED id=${recording.id}", e)
                    _states.update {
                        it + (recording.id to AdhanDownloadState.Failed(e.message ?: "unknown error"))
                    }
                }
            } finally {
                activeDownloads.remove(recording.id, active)
            }
        }
        active.job.set(job)
        if (active.cancelled.get()) {
            active.cancel()
        }
    }

    fun cancelDownload(recordingId: String) {
        val active = activeDownloads.remove(recordingId)
        active?.cancel()
        _states.update { it - recordingId }
        File(audioDir, "$recordingId.part").delete()
    }

    fun deleteDownload(recordingId: String) {
        cancelDownload(recordingId)
        audioDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("$recordingId.")) file.delete()
        }
        _states.update { it - recordingId }
    }

    internal fun isDownloadActive(recordingId: String): Boolean =
        activeDownloads.containsKey(recordingId)

    private companion object {
        const val TAG = "AdhanDownload"
        const val DEFAULT_MIN_VALID_BYTES = 100_000L
    }
}
