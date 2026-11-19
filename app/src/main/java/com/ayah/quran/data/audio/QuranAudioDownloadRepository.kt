package com.ayah.quran.data.audio

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/** Offline state of one surah for one reciter edition. */
sealed interface QuranAudioState {
    data object NotDownloaded : QuranAudioState
    data class Downloading(val downloadedAyahs: Int, val totalAyahs: Int) : QuranAudioState
    data object Downloaded : QuranAudioState
    data class Failed(val reason: String?) : QuranAudioState
}

/**
 * Per-ayah Quran audio downloads (Phase 17) — OkHttp direct HTTPS, the
 * transport proven across Phase 15/16 (works on all devices including
 * HONOR/MagicOS).
 *
 * Storage layout (app-scoped external files):
 *   quran-audio/{editionId}/{surah}/{ayahInSurah}.mp3
 *
 * "Download surah" = fetch every ayah file of the surah with per-ayah
 * progress (n/total); already-present files are skipped, so an interrupted
 * download RESUMES instead of restarting.
 */
@Singleton
class QuranAudioDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // per-ayah files are small
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _states =
        MutableStateFlow<Map<String, QuranAudioState>>(emptyMap())
    val states: StateFlow<Map<String, QuranAudioState>> = _states.asStateFlow()

    /** Active surah downloads, for cancellation. */
    private val jobs = mutableMapOf<String, Job>()

    private val audioDir: File
        get() = File(context.getExternalFilesDir(null), "quran-audio").also { it.mkdirs() }

    private fun surahDir(editionId: String, surah: Int): File =
        File(File(audioDir, editionId), surah.toString()).also { it.mkdirs() }

    private fun key(editionId: String, surah: Int) = "$editionId:$surah"

    fun surahDirFor(editionId: String, surah: Int): File = surahDir(editionId, surah)

    fun downloadedAyahCount(editionId: String, surah: Int, totalAyahs: Int): Int =
        surahDir(editionId, surah).listFiles()
            ?.count { it.name.endsWith(".mp3") && it.length() > 1_000 }
            ?.coerceAtMost(totalAyahs) ?: 0

    fun isDownloaded(editionId: String, surah: Int, totalAyahs: Int): Boolean =
        downloadedAyahCount(editionId, surah, totalAyahs) >= totalAyahs

    fun localAyahFile(editionId: String, surah: Int, ayahInSurah: Int): File? =
        File(surahDir(editionId, surah), "$ayahInSurah.mp3")
            .takeIf { it.exists() && it.length() > 1_000 }

    fun downloadSurah(
        edition: QuranReciterEdition,
        surah: Int,
        onDone: () -> Unit = {},
    ) {
        val id = key(edition.editionId, surah)
        if (jobs.containsKey(id)) return
        val total = QuranAudioCatalog.ayahCount(surah)
        val firstGlobal = QuranAudioCatalog.firstGlobalAyah(surah)
        val dir = surahDir(edition.editionId, surah)

        jobs[id] = scope.launch {
            Log.i(TAG, "downloadSurah: start ${edition.editionId} s=$surah total=$total")
            var done = downloadedAyahCount(edition.editionId, surah, total)
            _states.value = _states.value + (
                id to QuranAudioState.Downloading(done, total)
                )
            try {
                for (ayahInSurah in 1..total) {
                    if (!isActiveJob(id)) break
                    val local = File(dir, "$ayahInSurah.mp3")
                    if (local.exists() && local.length() > 1_000) continue

                    val global = firstGlobal + ayahInSurah - 1
                    val url = QuranAudioCatalog.ayahUrl(edition, global)
                    fetchTo(url, File(dir, "$ayahInSurah.mp3.part"))
                    val part = File(dir, "$ayahInSurah.mp3.part")
                    if (part.length() < 1_000) {
                        throw IOException("ayah file too small (${part.length()}B) $url")
                    }
                    if (!part.renameTo(local)) {
                        part.copyTo(local, overwrite = true)
                        part.delete()
                    }
                    done++
                    _states.value = _states.value + (
                        id to QuranAudioState.Downloading(done, total)
                        )
                }
                if (done >= total) {
                    Log.i(TAG, "downloadSurah: complete ${edition.editionId} s=$surah")
                    _states.value = _states.value + (id to QuranAudioState.Downloaded)
                    onDone()
                } else {
                    // Cancelled mid-way: keep partial files, drop the busy state.
                    _states.value = _states.value - id
                }
            } catch (e: Exception) {
                Log.e(TAG, "downloadSurah: FAILED ${edition.editionId} s=$surah", e)
                _states.value = _states.value + (
                    id to QuranAudioState.Failed(e.message ?: "network error")
                    )
            } finally {
                jobs.remove(id)
            }
        }
    }

    fun cancelDownload(editionId: String, surah: Int) {
        jobs.remove(key(editionId, surah))?.cancel()
        _states.value = _states.value - key(editionId, surah)
    }

    fun deleteSurah(editionId: String, surah: Int) {
        jobs.remove(key(editionId, surah))?.cancel()
        surahDir(editionId, surah).deleteRecursively()
        _states.value = _states.value - key(editionId, surah)
    }

    private fun isActiveJob(id: String): Boolean = jobs.containsKey(id)

    private fun fetchTo(url: String, target: File) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} $url")
            val body = response.body ?: throw IOException("empty body $url")
            target.outputStream().use { output ->
                body.byteStream().copyTo(output, bufferSize = 64_000)
            }
        }
    }

    private companion object {
        const val TAG = "QuranAudio"
        const val UA = "Mozilla/5.0 (Linux; Android) Ayah-Quran-App"
    }
}
