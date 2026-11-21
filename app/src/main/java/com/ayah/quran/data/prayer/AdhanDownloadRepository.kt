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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

internal enum class DeleteLifecycleStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    CANCELLED,
}

internal data class DeleteTrackerEntry(
    val opId: Long,
    val status: DeleteLifecycleStatus,
)

@Singleton
class AdhanDownloadRepository internal constructor(
    private val audioDirProvider: () -> File,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val minValidBytes: Long,
    private val deleteFile: (File) -> Boolean = { it.delete() },
    private val fileExists: (File) -> Boolean = { it.exists() },
    private val onBeforeDeleteFile: (suspend (File) -> Unit)? = null,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        baseClient: OkHttpClient,
        @ApplicationScope appScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        audioDirProvider = { File(context.getExternalFilesDir(null), "adhan") },
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

    private class ActiveDownload(val opId: Long) {
        val job = AtomicReference<Job?>(null)
        val call = AtomicReference<Call?>(null)
        val cancelled = AtomicBoolean(false)

        fun markCancelled() {
            cancelled.set(true)
        }

        fun cancelResources() {
            call.get()?.cancel()
            job.get()?.cancel()
        }
    }

    private val nextOperationId = AtomicLong(0L)
    private val downloadLock = Any()
    private val latestGeneration = HashMap<String, Long>()
    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()
    private val operationJobs = HashMap<String, LinkedHashSet<Job>>()
    private val deleteTrackers = HashMap<String, DeleteTrackerEntry>()
    private val recordingMutexes = ConcurrentHashMap<String, Mutex>()

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    private fun mutexFor(recordingId: String): Mutex =
        recordingMutexes.computeIfAbsent(recordingId) { Mutex() }

    private fun isOwner(recordingId: String, opId: Long): Boolean =
        synchronized(downloadLock) { latestGeneration[recordingId] == opId }

    private fun isBlockedByDelete(recordingId: String): Boolean =
        synchronized(downloadLock) { deleteTrackers.containsKey(recordingId) }

    private fun existingAudioDir(): File? =
        audioDirProvider().takeIf { fileExists(it) && it.isDirectory }

    private fun writableAudioDir(): File =
        audioDirProvider().also { it.mkdirs() }

    private fun fileOf(dir: File, recording: AdhanRecording): File =
        File(dir, "${recording.id}.${recording.fileExtension}")

    fun isDownloaded(recording: AdhanRecording): Boolean {
        if (isBlockedByDelete(recording.id)) return false
        val dir = existingAudioDir() ?: return false
        val file = fileOf(dir, recording)
        return fileExists(file) && file.isFile && file.length() > MIN_EXISTING_BYTES
    }

    fun downloadedFile(recordingId: String): File? {
        if (isBlockedByDelete(recordingId)) return null
        return existingAudioDir()?.listFiles()?.firstOrNull {
            it.isFile &&
                fileExists(it) &&
                it.name.startsWith("$recordingId.") &&
                !it.name.endsWith(".part") &&
                it.length() > MIN_EXISTING_BYTES
        }
    }

    fun download(recording: AdhanRecording) {
        val recordingId = recording.id
        val jobToStart: Job

        synchronized(downloadLock) {
            if (activeDownloads.containsKey(recordingId)) {
                return
            }
            val opId = nextOperationId.incrementAndGet()
            val active = ActiveDownload(opId)
            val previousJobs = snapshotPriorJobsLocked(recordingId)

            latestGeneration[recordingId] = opId
            activeDownloads[recordingId] = active
            _states.update { it + (recordingId to AdhanDownloadState.Downloading(0f)) }

            lateinit var downloadJob: Job
            downloadJob = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                var partFile: File? = null
                try {
                    for (prev in previousJobs) {
                        prev.join()
                    }
                    currentCoroutineContext().ensureActive()
                    if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                        throw CancellationException("Download cancelled")
                    }

                    mutexFor(recordingId).withLock {
                        currentCoroutineContext().ensureActive()
                        if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                            throw CancellationException("Download cancelled")
                        }

                        Log.i(TAG, "download: start id=$recordingId op=$opId")
                        val dir = writableAudioDir()
                        cleanupOrphanedPartFiles(recordingId)

                        val targetPart = File(dir, "$recordingId.$opId.part")
                        partFile = targetPart
                        deleteTargetFile(targetPart)

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
                        if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                            call.cancel()
                            throw CancellationException("Download cancelled")
                        }

                        var contentLength = -1L
                        var total = 0L
                        try {
                            call.execute().use { response ->
                                currentCoroutineContext().ensureActive()
                                if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                                    throw CancellationException("Download cancelled")
                                }
                                Log.i(TAG, "download: HTTP ${response.code} id=$recordingId op=$opId")
                                if (!response.isSuccessful) {
                                    throw IOException("HTTP ${response.code} downloading adhan")
                                }
                                val body = response.body ?: throw IOException("empty body")
                                contentLength = body.contentLength()
                                if (contentLength == 0L) {
                                    throw IOException("empty adhan body (Content-Length: 0)")
                                }
                                if (contentLength > 0L && contentLength < minValidBytes) {
                                    throw IOException("declared adhan file too small ($contentLength bytes)")
                                }
                                var lastEmit = 0f
                                body.byteStream().use { input ->
                                    targetPart.outputStream().use { output ->
                                        val buf = ByteArray(64_000)
                                        var consecutiveZeroReads = 0
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                                                throw CancellationException("Download cancelled")
                                            }
                                            val read = input.read(buf)
                                            if (read < 0) break
                                            if (read == 0) {
                                                consecutiveZeroReads++
                                                if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                                                    throw IOException("stalled adhan download stream")
                                                }
                                                continue
                                            }
                                            consecutiveZeroReads = 0
                                            total += read
                                            if (contentLength > 0L && total > contentLength) {
                                                throw IOException("adhan download exceeded Content-Length (expected $contentLength bytes, received >$contentLength)")
                                            }
                                            output.write(buf, 0, read)
                                            if (contentLength > 0L) {
                                                val progress = (total.toFloat() / contentLength).coerceIn(0f, 1f)
                                                if (progress - lastEmit >= 0.1f || progress >= 1f) {
                                                    lastEmit = progress
                                                    if (!active.cancelled.get()) {
                                                        updateOwnedState(
                                                            recordingId,
                                                            opId,
                                                            AdhanDownloadState.Downloading(progress),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Log.i(TAG, "download: bytes=$total id=$recordingId op=$opId")
                            }
                        } finally {
                            active.call.compareAndSet(call, null)
                        }

                        currentCoroutineContext().ensureActive()
                        if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                            throw CancellationException("Download cancelled")
                        }

                        if (total <= 0L) {
                            throw IOException("downloaded adhan file is empty")
                        }
                        if (contentLength > 0L && total != contentLength) {
                            throw IOException("truncated adhan download (expected $contentLength bytes, received $total)")
                        }
                        val writtenPartBytes = targetPart.length()
                        if (!fileExists(targetPart) || writtenPartBytes != total) {
                            throw IOException("written adhan part size mismatch (expected $total bytes, on disk $writtenPartBytes)")
                        }
                        if (writtenPartBytes < minValidBytes) {
                            throw IOException("downloaded file too small ($writtenPartBytes bytes)")
                        }

                        val finalFile = fileOf(dir, recording)
                        val staleSiblings = dir.listFiles()
                            ?.filter {
                                it.isFile &&
                                    it.name.startsWith("$recordingId.") &&
                                    it.name != finalFile.name &&
                                    it.name != targetPart.name
                            }
                            .orEmpty()
                        for (sibling in staleSiblings) {
                            if (!deleteTargetFile(sibling)) {
                                throw IOException("failed removing stale adhan file ${sibling.name}")
                            }
                        }

                        currentCoroutineContext().ensureActive()
                        if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                            throw CancellationException("Download cancelled")
                        }

                        if (fileExists(finalFile)) {
                            deleteTargetFile(finalFile)
                        }
                        if (!targetPart.renameTo(finalFile)) {
                            try {
                                targetPart.copyTo(finalFile, overwrite = true)
                            } catch (t: Throwable) {
                                cleanupPartQuietly(finalFile, t)
                                throw t
                            }
                            cleanupPartQuietly(targetPart)
                        }
                        partFile = null

                        val finalBytes = finalFile.length()
                        if (!fileExists(finalFile) || finalBytes != total || finalBytes < minValidBytes) {
                            cleanupPartQuietly(finalFile)
                            throw IOException("replaced adhan file invalid (expected $total bytes, on disk $finalBytes)")
                        }

                        val coroutineActive = currentCoroutineContext().isActive
                        var committed = false
                        synchronized(downloadLock) {
                            if (coroutineActive && latestGeneration[recordingId] == opId && !active.cancelled.get()) {
                                val existingDelete = deleteTrackers[recordingId]
                                if (existingDelete != null && existingDelete.opId < opId) {
                                    deleteTrackers.remove(recordingId)
                                }
                                _states.update { it + (recordingId to AdhanDownloadState.Downloaded) }
                                committed = true
                            }
                        }
                        if (!committed) {
                            deleteTargetFile(finalFile)
                            throw CancellationException("Download cancelled")
                        }
                    }
                } catch (ce: CancellationException) {
                    cleanupPartQuietly(partFile, ce)
                    clearOwnedState(recordingId, opId)
                    throw ce
                } catch (e: Error) {
                    cleanupPartQuietly(partFile, e)
                    clearOwnedState(recordingId, opId)
                    throw e
                } catch (e: Exception) {
                    cleanupPartQuietly(partFile, e)
                    if (active.cancelled.get() || !isOwner(recordingId, opId)) {
                        clearOwnedState(recordingId, opId)
                    } else {
                        Log.e(TAG, "download: FAILED id=$recordingId op=$opId", e)
                        updateOwnedState(
                            recordingId,
                            opId,
                            AdhanDownloadState.Failed(e.message ?: "unknown error"),
                        )
                    }
                } finally {
                    active.call.set(null)
                    synchronized(downloadLock) {
                        activeDownloads.remove(recordingId, active)
                        unregisterJobLocked(recordingId, downloadJob)
                    }
                }
            }

            downloadJob.invokeOnCompletion { cause ->
                synchronized(downloadLock) {
                    if (cause != null && latestGeneration[recordingId] == opId) {
                        if (_states.value[recordingId] is AdhanDownloadState.Downloading) {
                            _states.update { it - recordingId }
                        }
                    }
                    activeDownloads.remove(recordingId, active)
                    unregisterJobLocked(recordingId, downloadJob)
                }
            }

            active.job.set(downloadJob)
            registerJobLocked(recordingId, downloadJob)
            jobToStart = downloadJob
        }

        jobToStart.start()
    }

    fun cancelDownload(recordingId: String) {
        val activeToCancel: ActiveDownload?
        val jobToStart: Job

        synchronized(downloadLock) {
            val opId = nextOperationId.incrementAndGet()
            val active = activeDownloads.remove(recordingId)
            active?.markCancelled()
            val previousJobs = snapshotPriorJobsLocked(recordingId)
            latestGeneration[recordingId] = opId
            _states.update { it - recordingId }

            lateinit var cancelJob: Job
            cancelJob = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                for (prev in previousJobs) {
                    prev.join()
                }
                currentCoroutineContext().ensureActive()
                mutexFor(recordingId).withLock {
                    currentCoroutineContext().ensureActive()
                    if (isOwner(recordingId, opId)) {
                        cleanupOrphanedPartFiles(recordingId)
                    }
                }
            }

            cancelJob.invokeOnCompletion {
                synchronized(downloadLock) {
                    unregisterJobLocked(recordingId, cancelJob)
                }
            }

            registerJobLocked(recordingId, cancelJob)
            activeToCancel = active
            jobToStart = cancelJob
        }

        activeToCancel?.cancelResources()
        jobToStart.start()
    }

    fun deleteDownload(recordingId: String) {
        val activeToCancel: ActiveDownload?
        val jobToStart: Job

        synchronized(downloadLock) {
            val opId = nextOperationId.incrementAndGet()
            val active = activeDownloads.remove(recordingId)
            active?.markCancelled()
            val previousJobs = snapshotPriorJobsLocked(recordingId)
            latestGeneration[recordingId] = opId
            deleteTrackers[recordingId] = DeleteTrackerEntry(opId, DeleteLifecycleStatus.PENDING)
            _states.update { it + (recordingId to AdhanDownloadState.NotDownloaded) }

            lateinit var deleteJob: Job
            deleteJob = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                try {
                    for (prev in previousJobs) {
                        prev.join()
                    }
                    currentCoroutineContext().ensureActive()
                    mutexFor(recordingId).withLock {
                        synchronized(downloadLock) {
                            if (deleteTrackers[recordingId]?.opId == opId) {
                                deleteTrackers[recordingId] = DeleteTrackerEntry(
                                    opId,
                                    DeleteLifecycleStatus.IN_PROGRESS,
                                )
                            }
                        }
                        currentCoroutineContext().ensureActive()

                        var allDeleted = true
                        val targets = try {
                            existingAudioDir()
                                ?.listFiles()
                                ?.filter { it.name.startsWith("$recordingId.") }
                                .orEmpty()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Error) {
                            throw e
                        } catch (t: Throwable) {
                            Log.e(TAG, "deleteDownload: failed listing files for $recordingId", t)
                            allDeleted = false
                            emptyList()
                        }

                        for (file in targets) {
                            currentCoroutineContext().ensureActive()
                            onBeforeDeleteFile?.invoke(file)
                            currentCoroutineContext().ensureActive()
                            if (!deleteTargetFile(file)) {
                                allDeleted = false
                            }
                        }

                        if (allDeleted) {
                            val remaining = try {
                                existingAudioDir()
                                    ?.listFiles()
                                    ?.filter { it.name.startsWith("$recordingId.") && fileExists(it) }
                                    .orEmpty()
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (e: Error) {
                                throw e
                            } catch (t: Throwable) {
                                Log.e(TAG, "deleteDownload: failed verifying files for $recordingId", t)
                                allDeleted = false
                                emptyList()
                            }
                            if (remaining.isNotEmpty()) {
                                allDeleted = false
                            }
                        }

                        if (allDeleted) {
                            currentCoroutineContext().ensureActive()
                            synchronized(downloadLock) {
                                if (deleteTrackers[recordingId]?.opId == opId) {
                                    deleteTrackers.remove(recordingId)
                                }
                                if (latestGeneration[recordingId] == opId) {
                                    _states.update { it - recordingId }
                                }
                            }
                        } else {
                            synchronized(downloadLock) {
                                if (deleteTrackers[recordingId]?.opId == opId) {
                                    deleteTrackers[recordingId] = DeleteTrackerEntry(
                                        opId,
                                        DeleteLifecycleStatus.FAILED,
                                    )
                                }
                                if (latestGeneration[recordingId] == opId) {
                                    _states.update { it + (recordingId to AdhanDownloadState.NotDownloaded) }
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    synchronized(downloadLock) {
                        if (deleteTrackers[recordingId]?.opId == opId) {
                            deleteTrackers[recordingId] = DeleteTrackerEntry(
                                opId,
                                DeleteLifecycleStatus.CANCELLED,
                            )
                        }
                        if (latestGeneration[recordingId] == opId) {
                            _states.update { it + (recordingId to AdhanDownloadState.NotDownloaded) }
                        }
                    }
                    throw ce
                } catch (e: Error) {
                    synchronized(downloadLock) {
                        if (deleteTrackers[recordingId]?.opId == opId) {
                            deleteTrackers[recordingId] = DeleteTrackerEntry(
                                opId,
                                DeleteLifecycleStatus.FAILED,
                            )
                        }
                        if (latestGeneration[recordingId] == opId) {
                            _states.update { it + (recordingId to AdhanDownloadState.NotDownloaded) }
                        }
                    }
                    throw e
                }
            }

            deleteJob.invokeOnCompletion { cause ->
                synchronized(downloadLock) {
                    val entry = deleteTrackers[recordingId]
                    if (entry != null && entry.opId == opId) {
                        if (entry.status == DeleteLifecycleStatus.PENDING ||
                            entry.status == DeleteLifecycleStatus.IN_PROGRESS
                        ) {
                            val terminalStatus = if (cause is CancellationException) {
                                DeleteLifecycleStatus.CANCELLED
                            } else {
                                DeleteLifecycleStatus.FAILED
                            }
                            deleteTrackers[recordingId] = entry.copy(status = terminalStatus)
                            if (latestGeneration[recordingId] == opId) {
                                _states.update { it + (recordingId to AdhanDownloadState.NotDownloaded) }
                            }
                        }
                    }
                    unregisterJobLocked(recordingId, deleteJob)
                }
            }

            registerJobLocked(recordingId, deleteJob)
            activeToCancel = active
            jobToStart = deleteJob
        }

        activeToCancel?.cancelResources()
        jobToStart.start()
    }

    internal fun isDownloadActive(recordingId: String): Boolean =
        activeDownloads.containsKey(recordingId)

    internal fun hasPendingOperation(recordingId: String): Boolean =
        synchronized(downloadLock) {
            activeDownloads.containsKey(recordingId) ||
                operationJobs[recordingId]?.any { it.isActive } == true
        }

    internal fun deleteStatusForTest(recordingId: String): DeleteLifecycleStatus? =
        synchronized(downloadLock) { deleteTrackers[recordingId]?.status }

    internal fun currentOperationJobForTest(recordingId: String): Job? =
        synchronized(downloadLock) { operationJobs[recordingId]?.lastOrNull() }

    internal suspend fun awaitPendingOperation(recordingId: String) {
        while (true) {
            val pending = synchronized(downloadLock) {
                operationJobs[recordingId]?.toList().orEmpty()
            }
            if (pending.isEmpty()) return
            for (job in pending) {
                job.join()
            }
            val remaining = synchronized(downloadLock) {
                operationJobs[recordingId]?.toList().orEmpty()
            }
            if (remaining.isEmpty() || remaining == pending) return
        }
    }

    private fun snapshotPriorJobsLocked(recordingId: String): List<Job> =
        operationJobs[recordingId]?.toList().orEmpty()

    private fun registerJobLocked(recordingId: String, job: Job) {
        operationJobs.getOrPut(recordingId) { LinkedHashSet() }.add(job)
    }

    private fun unregisterJobLocked(recordingId: String, job: Job) {
        val jobs = operationJobs[recordingId] ?: return
        jobs.remove(job)
        if (jobs.isEmpty()) {
            operationJobs.remove(recordingId)
        }
    }

    private fun updateOwnedState(
        recordingId: String,
        opId: Long,
        state: AdhanDownloadState,
    ): Boolean = synchronized(downloadLock) {
        if (latestGeneration[recordingId] != opId) return false
        _states.update { it + (recordingId to state) }
        true
    }

    private fun clearOwnedState(recordingId: String, opId: Long) {
        synchronized(downloadLock) {
            if (latestGeneration[recordingId] == opId) {
                _states.update { it - recordingId }
            }
        }
    }

    private fun deleteTargetFile(file: File): Boolean = try {
        if (!fileExists(file)) {
            true
        } else {
            deleteFile(file) && !fileExists(file)
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Error) {
        throw e
    } catch (t: Throwable) {
        Log.e(TAG, "deleteDownload: failed deleting ${file.name}", t)
        false
    }

    private fun cleanupPartQuietly(partFile: File?, primary: Throwable? = null) {
        if (partFile == null) return
        try {
            if (partFile.exists()) {
                partFile.delete()
            }
        } catch (t: Throwable) {
            if (primary != null) {
                primary.addSuppressed(t)
                Log.e(TAG, "download: failed cleaning part file ${partFile.name}", t)
            } else if (t is Error) {
                throw t
            } else {
                Log.e(TAG, "download: failed cleaning part file ${partFile.name}", t)
            }
        }
    }

    private fun cleanupOrphanedPartFiles(recordingId: String) {
        val parts = try {
            existingAudioDir()
                ?.listFiles()
                ?.filter {
                    it.isFile &&
                        (it.name == "$recordingId.part" ||
                            (it.name.startsWith("$recordingId.") && it.name.endsWith(".part")))
                }
                .orEmpty()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Error) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "cleanupOrphanedPartFiles: failed listing for $recordingId", t)
            emptyList()
        }
        parts.forEach { deleteTargetFile(it) }
    }

    private companion object {
        const val TAG = "AdhanDownload"
        const val DEFAULT_MIN_VALID_BYTES = 100_000L
        const val MIN_EXISTING_BYTES = 1_000L
        const val MAX_CONSECUTIVE_ZERO_READS = 5
    }
}
