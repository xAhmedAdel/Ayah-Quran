package com.ayah.quran.data.update

import android.content.Context
import android.util.Log
import com.ayah.quran.domain.update.AppUpdateReleaseParser
import com.ayah.quran.domain.update.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState

    /** No release on GitHub is newer than the installed build. */
    data class UpToDate(val currentVersion: String) : AppUpdateState

    /** GitHub has releases but none newer (e.g. only older/prerelease builds). */
    data class Available(val info: AppUpdateInfo) : AppUpdateState
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : AppUpdateState
    data class Downloaded(val info: AppUpdateInfo, val apkFile: File) : AppUpdateState
    data class Failed(val reason: String?) : AppUpdateState

    /** The repo has no published releases yet (first-run reality). */
    data object NoReleases : AppUpdateState
}

/**
 * In-app updater backed by GitHub Releases:
 * 1. GET /releases (list — works with prereleases; [releases/latest] skips
 *    them and 404s when the repo has no full release).
 * 2. Compare tag_name against the installed VERSION_NAME.
 * 3. Download the newest APK asset with OkHttp (browser-like UA — the same
 *    transport proven in AdhanDownloadRepository).
 *
 * The download lands in app-scoped external storage; the install itself is
 * launched via FileProvider + the system package installer (Android never
 * allows silent installs — the user confirms in the installer UI).
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // APK can be tens of MB
        .followRedirects(true) // release assets redirect to the CDN host
        .followSslRedirects(true)
        .build()

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val updateDir: File
        get() = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }

    /** Previously downloaded APK (survives app restarts) — if still valid. */
    fun pendingUpdateFile(): File? =
        File(updateDir, APK_FILE).takeIf { it.exists() && it.length() > 1_000_000 }

    fun checkForUpdate(currentVersionName: String) {
        scope.launch {
            _state.value = AppUpdateState.Checking
            try {
                val request = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", UA)
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 ->
                            // No releases at all (or repo renamed) — honest state.
                            _state.value = AppUpdateState.NoReleases

                        response.code == 403 -> {
                            // GitHub API rate limit (60/h per IP, unauthenticated).
                            throw IOException("GitHub rate limit — try again later")
                        }

                        !response.isSuccessful ->
                            throw IOException("GitHub HTTP ${response.code}")

                        else -> {
                            val body = response.body ?: throw IOException("empty body")
                            val releases = JSONArray(body.string())
                            val newest = AppUpdateReleaseParser.newestRelease(
                                releases,
                                currentVersionName,
                            )
                            _state.value = when {
                                newest != null -> AppUpdateState.Available(newest)
                                releases.length() == 0 -> AppUpdateState.NoReleases
                                else -> AppUpdateState.UpToDate(currentVersionName)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkForUpdate failed", e)
                _state.value = AppUpdateState.Failed(e.message ?: "network error")
            }
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        scope.launch {
            val partFile = File(updateDir, "$APK_FILE.part")
            _state.value = AppUpdateState.Downloading(info, 0f)
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", UA)
                    .build()

                client.newCall(request).execute().use { response ->
                    Log.i(TAG, "downloadUpdate: HTTP ${response.code} for ${info.tagName}")
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} downloading ${info.apkUrl}")
                    }
                    val body = response.body ?: throw IOException("empty body")
                    val contentLength = body.contentLength()
                    var total = 0L
                    var lastEmit = -1
                    body.byteStream().use { input ->
                        partFile.outputStream().use { output ->
                            val buf = ByteArray(256_000)
                            while (true) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                output.write(buf, 0, read)
                                total += read
                                if (contentLength > 0) {
                                    val percent = (total * 100 / contentLength).toInt()
                                    if (percent != lastEmit) {
                                        lastEmit = percent
                                        _state.value = AppUpdateState.Downloading(
                                            info, percent / 100f,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val finalFile = File(updateDir, APK_FILE)
                if (finalFile.exists()) finalFile.delete()
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                Log.i(TAG, "downloadUpdate: saved ${finalFile.length()} bytes")
                _state.value = AppUpdateState.Downloaded(info, finalFile)
            } catch (e: Exception) {
                Log.e(TAG, "downloadUpdate FAILED", e)
                partFile.delete()
                _state.value = AppUpdateState.Failed(e.message ?: "download error")
            }
        }
    }

    private companion object {
        const val TAG = "AppUpdate"
        const val UA = "Mozilla/5.0 (Linux; Android) Ayah-Quran-App"
        const val APK_FILE = "ayah-update.apk"
        const val RELEASES_URL =
            "https://api.github.com/repos/xAhmedAdel/Ayah-Quran/releases?per_page=10"
    }
}
