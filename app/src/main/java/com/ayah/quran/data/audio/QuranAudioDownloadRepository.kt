package com.ayah.quran.data.audio

import android.content.Context
import android.util.Log
import com.ayah.quran.di.ApplicationScope
import com.ayah.quran.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

sealed interface QuranAudioState {
    data object NotDownloaded : QuranAudioState
    data class Downloading(val downloadedAyahs: Int, val totalAyahs: Int) : QuranAudioState
    data object Downloaded : QuranAudioState
    data class Failed(val reason: String?) : QuranAudioState
}

@Singleton
class QuranAudioDownloadRepository internal constructor(
    private val audioDirProvider: () -> File,
    internal val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val minAyahBytes: Long,
    autoScanOnInit: Boolean,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        baseClient: OkHttpClient,
        @ApplicationScope appScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        audioDirProvider = { File(context.getExternalFilesDir(null), "quran-audio") },
        client = baseClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
        scope = appScope,
        ioDispatcher = ioDispatcher,
        minAyahBytes = DEFAULT_MIN_AYAH_BYTES,
        autoScanOnInit = true,
    )

    private class ActiveSurahDownload(val opId: Long) {
        val job = AtomicReference<Job?>(null)
        val call = AtomicReference<Call?>(null)
        val cancelled = AtomicBoolean(false)

        fun cancel() {
            cancelled.set(true)
            call.get()?.cancel()
            job.get()?.cancel()
        }
    }

    private val nextOperationId = AtomicLong(0L)
    private val surahLock = Any()
    private val latestGeneration = HashMap<String, Long>()
    private val activeDownloads = ConcurrentHashMap<String, ActiveSurahDownload>()
    private val lastOperationJob = HashMap<String, Job>()
    private val surahMutexes = ConcurrentHashMap<String, Mutex>()

    private val _states = MutableStateFlow<Map<String, QuranAudioState>>(emptyMap())
    val states: StateFlow<Map<String, QuranAudioState>> = _states.asStateFlow()

    private val _downloadedCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadedCounts: StateFlow<Map<String, Int>> = _downloadedCounts.asStateFlow()

    init {
        if (autoScanOnInit) {
            scope.launch(ioDispatcher) {
                refreshDownloadedCounts()
            }
        }
    }

    private fun key(editionId: String, surah: Int) = "$editionId:$surah"

    private fun mutexFor(id: String): Mutex = surahMutexes.computeIfAbsent(id) { Mutex() }

    private fun isOwner(id: String, opId: Long): Boolean =
        synchronized(surahLock) { latestGeneration[id] == opId }

    private fun existingAudioDir(): File? =
        audioDirProvider().takeIf { it.exists() && it.isDirectory }

    private fun existingSurahDir(editionId: String, surah: Int): File? =
        existingAudioDir()
            ?.let { File(File(it, editionId), surah.toString()) }
            ?.takeIf { it.exists() && it.isDirectory }

    private fun writableSurahDir(editionId: String, surah: Int): File =
        File(File(audioDirProvider(), editionId), surah.toString()).also { it.mkdirs() }

    suspend fun refreshDownloadedCounts() = refreshDownloadedCountsInternal(onSurahScanned = null)

    internal suspend fun refreshDownloadedCountsInternal(
        onSurahScanned: (suspend (String) -> Unit)? = null,
    ) = withContext(ioDispatcher) {
        val scanStartGenerations = synchronized(surahLock) { latestGeneration.toMap() }
        val root = existingAudioDir() ?: run {
            synchronized(surahLock) {
                if (activeDownloads.isEmpty() &&
                    lastOperationJob.isEmpty() &&
                    latestGeneration == scanStartGenerations
                ) {
                    _downloadedCounts.value = emptyMap()
                }
            }
            return@withContext
        }
        val scanned = mutableMapOf<String, Int>()
        val scannedGenerations = mutableMapOf<String, Long>()
        val busyKeys = mutableSetOf<String>()
        root.listFiles()?.filter { it.isDirectory }?.forEach { editionDir ->
            val editionId = editionDir.name
            editionDir.listFiles()?.filter { it.isDirectory }?.forEach { surahDir ->
                val surah = surahDir.name.toIntOrNull() ?: return@forEach
                if (surah !in 1..114) return@forEach
                val id = key(editionId, surah)
                val mutex = mutexFor(id)
                if (!mutex.tryLock()) {
                    busyKeys += id
                    return@forEach
                }
                var scannedThisSurah = false
                try {
                    val (hasPendingOp, capturedGeneration) = synchronized(surahLock) {
                        val pending = activeDownloads.containsKey(id) || lastOperationJob[id]?.isActive == true
                        pending to (latestGeneration[id] ?: 0L)
                    }
                    if (hasPendingOp) {
                        busyKeys += id
                        return@forEach
                    }
                    scannedGenerations[id] = capturedGeneration
                    val totalAyahs = QuranAudioCatalog.ayahCount(surah)
                    var validCount = 0
                    surahDir.listFiles()?.forEach { file ->
                        if (!file.isFile) return@forEach
                        if (file.name.endsWith(".part")) {
                            file.delete()
                        } else if (isCompletedAyahFile(file)) {
                            validCount++
                        }
                    }
                    if (validCount > 0) {
                        scanned[id] = validCount.coerceAtMost(totalAyahs)
                    }
                    scannedThisSurah = true
                } finally {
                    mutex.unlock()
                }
                if (scannedThisSurah) {
                    onSurahScanned?.invoke(id)
                }
            }
        }
        synchronized(surahLock) {
            _downloadedCounts.update { current ->
                val merged = mutableMapOf<String, Int>()
                scanned.forEach { (id, count) ->
                    val currentGen = latestGeneration[id] ?: 0L
                    if (currentGen == scannedGenerations[id]) {
                        merged[id] = count
                    }
                }
                val protectedKeys = mutableSetOf<String>()
                protectedKeys += busyKeys
                protectedKeys += activeDownloads.keys
                protectedKeys += lastOperationJob.keys
                latestGeneration.forEach { (id, currentGen) ->
                    val baselineGen = scannedGenerations[id] ?: (scanStartGenerations[id] ?: 0L)
                    if (currentGen != baselineGen) {
                        protectedKeys += id
                    }
                }
                protectedKeys.forEach { protectedKey ->
                    current[protectedKey]?.let { live ->
                        merged[protectedKey] = live
                    } ?: merged.remove(protectedKey)
                }
                merged
            }
        }
    }

    private fun scanSurahOnDisk(editionId: String, surah: Int, totalAyahs: Int): Int =
        existingSurahDir(editionId, surah)
            ?.listFiles()
            ?.count { it.isFile && isCompletedAyahFile(it) }
            ?.coerceAtMost(totalAyahs)
            ?: 0

    private fun isCompletedAyahFile(file: File): Boolean =
        file.name.endsWith(".mp3") && !file.name.endsWith(".part") && file.length() > minAyahBytes

    fun downloadedAyahCount(editionId: String, surah: Int, totalAyahs: Int): Int =
        (_downloadedCounts.value[key(editionId, surah)] ?: 0).coerceAtMost(totalAyahs)

    fun isDownloaded(editionId: String, surah: Int, totalAyahs: Int): Boolean =
        downloadedAyahCount(editionId, surah, totalAyahs) >= totalAyahs

    fun localAyahFile(editionId: String, surah: Int, ayahInSurah: Int): File? =
        existingSurahDir(editionId, surah)
            ?.let { File(it, "$ayahInSurah.mp3") }
            ?.takeIf { it.isFile && it.length() > minAyahBytes }

    fun downloadSurah(
        edition: QuranReciterEdition,
        surah: Int,
        onDone: () -> Unit = {},
    ) {
        val id = key(edition.editionId, surah)
        val total = QuranAudioCatalog.ayahCount(surah)
        val firstGlobal = QuranAudioCatalog.firstGlobalAyah(surah)
        val jobToStart: Job

        synchronized(surahLock) {
            if (activeDownloads.containsKey(id)) {
                return
            }
            val opId = nextOperationId.incrementAndGet()
            val active = ActiveSurahDownload(opId)
            val previousJob = lastOperationJob[id]
            val initialKnown = downloadedAyahCount(edition.editionId, surah, total)

            latestGeneration[id] = opId
            activeDownloads[id] = active
            _states.update { it + (id to QuranAudioState.Downloading(initialKnown, total)) }

            val job = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                var currentPart: File? = null
                var completed = false
                try {
                    previousJob?.join()
                    currentCoroutineContext().ensureActive()
                    if (active.cancelled.get() || !isOwner(id, opId)) {
                        throw CancellationException("Download cancelled")
                    }

                    mutexFor(id).withLock {
                        currentCoroutineContext().ensureActive()
                        if (active.cancelled.get() || !isOwner(id, opId)) {
                            throw CancellationException("Download cancelled")
                        }

                        Log.i(TAG, "downloadSurah: start ${edition.editionId} s=$surah total=$total op=$opId")
                        val dir = writableSurahDir(edition.editionId, surah)
                        cleanupOrphanedPartFiles(edition.editionId, surah)
                        var done = scanSurahOnDisk(edition.editionId, surah, total)
                        updateOwnedProgress(id, opId, done, QuranAudioState.Downloading(done, total))

                        for (ayahInSurah in 1..total) {
                            currentCoroutineContext().ensureActive()
                            if (active.cancelled.get() || !isOwner(id, opId)) {
                                throw CancellationException("Download cancelled")
                            }
                            val local = File(dir, "$ayahInSurah.mp3")
                            if (local.isFile && local.length() > minAyahBytes) continue

                            val part = File(dir, "$ayahInSurah.$opId.mp3.part")
                            currentPart = part
                            part.delete()

                            val global = firstGlobal + ayahInSurah - 1
                            val url = QuranAudioCatalog.ayahUrl(edition, global)
                            val expectedBytes = fetchTo(url, part, active, id, opId)

                            currentCoroutineContext().ensureActive()
                            if (active.cancelled.get() || !isOwner(id, opId)) {
                                cleanupFileQuietly(part)
                                throw CancellationException("Download cancelled")
                            }

                            val partLength = part.length()
                            if (!part.isFile || partLength != expectedBytes || partLength <= minAyahBytes) {
                                cleanupFileQuietly(part)
                                throw IOException("ayah file invalid (expected ${expectedBytes}B, on disk ${partLength}B) $url")
                            }
                            if (!part.renameTo(local)) {
                                try {
                                    part.copyTo(local, overwrite = true)
                                } catch (t: Throwable) {
                                    cleanupFileQuietly(local, t)
                                    throw t
                                }
                                cleanupFileQuietly(part)
                            }
                            currentPart = null

                            val promotedLength = local.length()
                            if (!local.isFile || promotedLength != expectedBytes || promotedLength <= minAyahBytes) {
                                cleanupFileQuietly(local)
                                throw IOException("promoted ayah file invalid (expected ${expectedBytes}B, on disk ${promotedLength}B) $url")
                            }

                            done++
                            updateOwnedProgress(
                                id = id,
                                opId = opId,
                                count = done,
                                state = if (!active.cancelled.get()) {
                                    QuranAudioState.Downloading(done, total)
                                } else {
                                    null
                                },
                            )
                        }

                        currentCoroutineContext().ensureActive()
                        if (active.cancelled.get() || !isOwner(id, opId)) {
                            throw CancellationException("Download cancelled")
                        }

                        if (done >= total) {
                            Log.i(TAG, "downloadSurah: complete ${edition.editionId} s=$surah op=$opId")
                            completed = updateOwnedProgress(id, opId, total, QuranAudioState.Downloaded)
                        } else {
                            clearOwnedState(id, opId)
                        }
                    }
                } catch (ce: CancellationException) {
                    cleanupFileQuietly(currentPart, ce)
                    clearOwnedState(id, opId)
                    throw ce
                } catch (e: Error) {
                    cleanupFileQuietly(currentPart, e)
                    clearOwnedState(id, opId)
                    throw e
                } catch (e: Exception) {
                    cleanupFileQuietly(currentPart, e)
                    if (active.cancelled.get() || !isOwner(id, opId)) {
                        clearOwnedState(id, opId)
                    } else {
                        Log.e(TAG, "downloadSurah: FAILED ${edition.editionId} s=$surah op=$opId", e)
                        synchronized(surahLock) {
                            if (latestGeneration[id] == opId) {
                                _states.update { it + (id to QuranAudioState.Failed(e.message ?: "network error")) }
                            }
                        }
                    }
                } finally {
                    active.call.set(null)
                    synchronized(surahLock) {
                        activeDownloads.remove(id, active)
                        if (lastOperationJob[id] === active.job.get()) {
                            lastOperationJob.remove(id)
                        }
                    }
                }
                if (completed) {
                    onDone()
                }
            }

            active.job.set(job)
            lastOperationJob[id] = job
            jobToStart = job
        }

        jobToStart.start()
    }

    fun cancelDownload(editionId: String, surah: Int) {
        val id = key(editionId, surah)
        val jobToStart: Job

        synchronized(surahLock) {
            val opId = nextOperationId.incrementAndGet()
            val active = activeDownloads.remove(id)
            val previousJob = active?.job?.get() ?: lastOperationJob[id]
            latestGeneration[id] = opId
            active?.cancel()
            _states.update { it - id }

            lateinit var cancelJob: Job
            cancelJob = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                try {
                    previousJob?.join()
                    mutexFor(id).withLock {
                        if (isOwner(id, opId)) {
                            cleanupOrphanedPartFiles(editionId, surah)
                        }
                    }
                } finally {
                    synchronized(surahLock) {
                        if (lastOperationJob[id] === cancelJob) {
                            lastOperationJob.remove(id)
                        }
                    }
                }
            }
            lastOperationJob[id] = cancelJob
            jobToStart = cancelJob
        }

        jobToStart.start()
    }

    fun deleteSurah(editionId: String, surah: Int) {
        val id = key(editionId, surah)
        val jobToStart: Job

        synchronized(surahLock) {
            val opId = nextOperationId.incrementAndGet()
            val active = activeDownloads.remove(id)
            val previousJob = active?.job?.get() ?: lastOperationJob[id]
            latestGeneration[id] = opId
            active?.cancel()
            _states.update { it - id }
            _downloadedCounts.update { it - id }

            lateinit var deleteJob: Job
            deleteJob = scope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
                try {
                    previousJob?.join()
                    mutexFor(id).withLock {
                        existingSurahDir(editionId, surah)?.deleteRecursively()
                        synchronized(surahLock) {
                            if (latestGeneration[id] == opId) {
                                _states.update { it - id }
                                _downloadedCounts.update { it - id }
                            }
                        }
                    }
                } finally {
                    synchronized(surahLock) {
                        if (lastOperationJob[id] === deleteJob) {
                            lastOperationJob.remove(id)
                        }
                    }
                }
            }
            lastOperationJob[id] = deleteJob
            jobToStart = deleteJob
        }

        jobToStart.start()
    }

    internal fun isDownloadActive(editionId: String, surah: Int): Boolean =
        activeDownloads.containsKey(key(editionId, surah))

    internal fun hasPendingOperation(editionId: String, surah: Int): Boolean =
        synchronized(surahLock) {
            val id = key(editionId, surah)
            activeDownloads.containsKey(id) || lastOperationJob[id]?.isActive == true
        }

    internal suspend fun awaitPendingOperation(editionId: String, surah: Int) {
        val job = synchronized(surahLock) {
            lastOperationJob[key(editionId, surah)]
        }
        job?.join()
    }

    private fun updateOwnedProgress(
        id: String,
        opId: Long,
        count: Int,
        state: QuranAudioState?,
    ): Boolean = synchronized(surahLock) {
        if (latestGeneration[id] != opId) return false
        _downloadedCounts.update { map ->
            if (count > 0) map + (id to count) else map - id
        }
        if (state != null) {
            _states.update { it + (id to state) }
        }
        true
    }

    private fun clearOwnedState(id: String, opId: Long) {
        synchronized(surahLock) {
            if (latestGeneration[id] == opId) {
                _states.update { it - id }
            }
        }
    }

    private fun cleanupOrphanedPartFiles(editionId: String, surah: Int) {
        existingSurahDir(editionId, surah)
            ?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { it.delete() }
    }

    private fun cleanupFileQuietly(file: File?, primary: Throwable? = null) {
        if (file == null) return
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (t: Throwable) {
            if (primary != null) {
                primary.addSuppressed(t)
                Log.e(TAG, "downloadSurah: failed cleaning file ${file.name}", t)
            } else if (t is Error) {
                throw t
            } else {
                Log.e(TAG, "downloadSurah: failed cleaning file ${file.name}", t)
            }
        }
    }

    private suspend fun fetchTo(
        url: String,
        target: File,
        active: ActiveSurahDownload,
        id: String,
        opId: Long,
    ): Long {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        val call = client.newCall(request)
        active.call.set(call)
        if (active.cancelled.get() || !isOwner(id, opId)) {
            call.cancel()
            throw CancellationException("Download cancelled")
        }
        var contentLength = -1L
        var totalBytesRead = 0L
        try {
            call.execute().use { response ->
                currentCoroutineContext().ensureActive()
                if (active.cancelled.get() || !isOwner(id, opId)) {
                    throw CancellationException("Download cancelled")
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} $url")
                val body = response.body ?: throw IOException("empty body $url")
                contentLength = body.contentLength()
                if (contentLength == 0L) {
                    throw IOException("empty ayah body (Content-Length: 0) $url")
                }
                if (contentLength > 0L && contentLength <= minAyahBytes) {
                    throw IOException("declared ayah file too small (${contentLength}B) $url")
                }
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64_000)
                        var consecutiveZeroReads = 0
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (active.cancelled.get() || !isOwner(id, opId)) {
                                throw CancellationException("Download cancelled")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) {
                                consecutiveZeroReads++
                                if (consecutiveZeroReads >= MAX_CONSECUTIVE_ZERO_READS) {
                                    throw IOException("stalled ayah download stream $url")
                                }
                                continue
                            }
                            consecutiveZeroReads = 0
                            totalBytesRead += read
                            if (contentLength > 0L && totalBytesRead > contentLength) {
                                throw IOException("ayah download exceeded Content-Length (expected ${contentLength}B, received >${contentLength}B) $url")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            if (active.cancelled.get() || !isOwner(id, opId)) {
                throw CancellationException("Download cancelled")
            }
            if (totalBytesRead <= 0L) {
                throw IOException("downloaded ayah file is empty $url")
            }
            if (contentLength > 0L && totalBytesRead != contentLength) {
                throw IOException("truncated ayah download (expected ${contentLength}B, received ${totalBytesRead}B) $url")
            }
            val writtenBytes = target.length()
            if (!target.isFile || writtenBytes != totalBytesRead) {
                throw IOException("written ayah file size mismatch (expected ${totalBytesRead}B, on disk ${writtenBytes}B) $url")
            }
            if (writtenBytes <= minAyahBytes) {
                throw IOException("ayah file too small (${writtenBytes}B) $url")
            }
            return totalBytesRead
        } catch (t: Throwable) {
            cleanupFileQuietly(target, t)
            throw t
        } finally {
            active.call.compareAndSet(call, null)
        }
    }

    private companion object {
        const val TAG = "QuranAudio"
        const val UA = "Mozilla/5.0 (Linux; Android) Ayah-Quran-App"
        const val DEFAULT_MIN_AYAH_BYTES = 1_000L
        const val MAX_CONSECUTIVE_ZERO_READS = 5
    }
}
