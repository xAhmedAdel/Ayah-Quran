package com.ayah.quran.data.prayer

import android.content.Context
import com.ayah.quran.domain.model.prayer.AdhanRecording
import com.ayah.quran.domain.repository.prayer.PrayerPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Download lifecycle for one catalog recording. */
sealed interface AdhanDownloadState {
    data object NotDownloaded : AdhanDownloadState
    data class Downloading(val progress: Float) : AdhanDownloadState
    data class Failed(val reason: String?) : AdhanDownloadState
    data object Downloaded : AdhanDownloadState
}

/**
 * Download manager for REMOTE adhan recordings (Phase 15).
 *
 * Guarantees:
 * - HTTPS only; explicit Ayah User-Agent (Wikimedia robot policy).
 * - One download at a time (mutex); a second request for the SAME recording
 *   is a no-op (duplicate prevention).
 * - Streams to a `.part` file, verifies the SIZE against catalog metadata
 *   and audio magic bytes, computes SHA-256 and PERSISTS it; when the
 *   catalog carries an expected SHA-256 it is verified against the download.
 * - User cancellation → partial file deleted, state back to NotDownloaded.
 * - All files live in app-private storage (context.filesDir/adhan).
 * - Never mutates prayer selections: deleting a downloaded recording leaves
 *   the stored voice id in place; playback resolution falls back to the
 *   default recording and the UI explains it (spec §4).
 */
@Singleton
class AdhanDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: BundledAdhanCatalog,
    private val preferencesRepository: PrayerPreferencesRepository,
    private val fileDownloader: AdhanFileDownloader,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _states = MutableStateFlow<Map<String, AdhanDownloadState>>(emptyMap())
    val states: StateFlow<Map<String, AdhanDownloadState>> = _states.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val cancelled = mutableSetOf<String>()

    private val audioDir: File get() = File(context.filesDir, "adhan").also { it.mkdirs() }

    /** Persisted downloaded ids (source of truth across restarts). */
    val downloadedIds: Flow<Set<String>> = preferencesRepository.downloadedAdhanIds

    fun isDownloaded(id: String): Boolean =
        File(audioDir, "$id.${catalog.byId(id)?.fileExtension ?: "ogg"}").exists()

    fun downloadedFile(id: String): File? {
        val ext = catalog.byId(id)?.fileExtension ?: "ogg"
        return File(audioDir, "$id.$ext").takeIf { it.exists() }
    }

    fun download(recording: AdhanRecording) {
        val url = recording.downloadUrl ?: return
        synchronized(activeJobs) {
            if (activeJobs.containsKey(recording.id)) return // duplicate prevention
            activeJobs[recording.id] = scope.launch { runDownload(recording, url) }
        }
    }

    fun cancel(recordingId: String) {
        cancelled.add(recordingId)
        activeJobs.remove(recordingId)?.cancel()
    }

    fun delete(recordingId: String) {
        cancel(recordingId)
        scope.launch {
            preferencesRepository.setAdhanDownloaded(recordingId, false)
            downloadedFile(recordingId)?.delete()
            _states.value = _states.value - recordingId
        }
    }

    /** Drops orphaned .part files left by cancelled/died processes. */
    fun cleanupPartialFiles() {
        scope.launch {
            audioDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    private suspend fun runDownload(recording: AdhanRecording, url: String) {
        mutex.withLock {
            val id = recording.id
            _states.value = _states.value + (id to AdhanDownloadState.Downloading(0f))
            val partFile = File(audioDir, "$id.part")
            try {
                val computedSha = fileDownloader.downloadTo(
                    url = url,
                    destination = partFile,
                    expectedSizeBytes = recording.expectedSizeBytes,
                    expectedSha256 = recording.expectedSha256,
                )
                persistSha(recording.id, computedSha)

                val finalFile = File(audioDir, "$id.${recording.fileExtension}")
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                preferencesRepository.setAdhanDownloaded(id, true)
                _states.value = _states.value + (id to AdhanDownloadState.Downloaded)
            } catch (e: UserCancelled) {
                partFile.delete()
                _states.value = _states.value + (id to AdhanDownloadState.NotDownloaded)
            } catch (e: IOException) {
                partFile.delete()
                _states.value = _states.value + (id to AdhanDownloadState.Failed(e.message))
            } catch (e: VerificationException) {
                partFile.delete()
                _states.value = _states.value + (id to AdhanDownloadState.Failed(e.message))
            } finally {
                activeJobs.remove(id)
            }
        }
    }

    private fun persistSha(id: String, sha256: String) {
        val hashesFile = File(audioDir, "hashes.properties")
        val lines = if (hashesFile.exists()) {
            hashesFile.readLines().filterNot { it.startsWith("$id=") }
        } else {
            emptyList()
        }
        hashesFile.writeText((lines + "$id=$sha256").joinToString(separator = "\n"))
    }

    private class UserCancelled : IOException("cancelled by user")

    private class VerificationException(message: String) : IOException(message)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
