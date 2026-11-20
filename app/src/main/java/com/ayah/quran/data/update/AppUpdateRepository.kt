package com.ayah.quran.data.update

import android.content.Context
import android.util.Log
import com.ayah.quran.BuildConfig
import com.ayah.quran.domain.update.AppUpdateInfo
import com.ayah.quran.domain.update.AppUpdateReleaseParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val currentVersion: String) : AppUpdateState
    data object NoReleases : AppUpdateState
    data class Available(val info: AppUpdateInfo) : AppUpdateState
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : AppUpdateState
    data class Downloaded(val info: AppUpdateInfo, val apkFile: File) : AppUpdateState
    data class Failed(val reason: String?) : AppUpdateState
}

@Singleton
class AppUpdateRepository internal constructor(
    private val updateDirProvider: () -> File,
    private val legacyExternalDirProvider: () -> File?,
    private val client: OkHttpClient,
    private val metadataReader: ApkMetadataReader,
    private val installedVersionCodeProvider: () -> Long,
    private val installedSignersProvider: () -> Set<String>,
    private val scope: CoroutineScope,
    private val releasesUrl: String,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        updateDirProvider = { File(context.filesDir, "updates").also { it.mkdirs() } },
        legacyExternalDirProvider = { context.getExternalFilesDir(null)?.let { File(it, "updates") } },
        client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        metadataReader = AppUpdateSecurityValidator.androidApkMetadataReader(context),
        installedVersionCodeProvider = { BuildConfig.VERSION_CODE.toLong() },
        installedSignersProvider = { AppUpdateSecurityValidator.readInstalledSignerDigests(context) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        releasesUrl = RELEASES_URL,
    )

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val updateDir: File
        get() = updateDirProvider().also { it.mkdirs() }

    fun pendingUpdateFile(): File? {
        cleanupLegacyExternalUpdates()
        val candidate = File(updateDir, APK_FILE)
        if (!candidate.exists()) return null
        return when (validateApkFile(candidate, expectedByteCount = null)) {
            is ApkValidationResult.Valid -> candidate
            is ApkValidationResult.Invalid -> {
                candidate.delete()
                null
            }
        }
    }

    fun validateApkFile(apkFile: File, expectedByteCount: Long?): ApkValidationResult =
        AppUpdateSecurityValidator.validateApk(
            apkFile = apkFile,
            expectedByteCount = expectedByteCount,
            installedVersionCode = installedVersionCodeProvider(),
            installedSignerSha256Digests = installedSignersProvider(),
            metadataReader = metadataReader,
        )

    fun reportInstallValidationFailure(reason: String) {
        File(updateDir, APK_FILE).delete()
        _state.value = AppUpdateState.Failed(reason)
    }

    fun checkForUpdate(currentVersionName: String) {
        scope.launch {
            checkForUpdateInternal(currentVersionName)
        }
    }

    internal fun checkForUpdateInternal(currentVersionName: String) {
        cleanupLegacyExternalUpdates()
        _state.value = AppUpdateState.Checking
        try {
            AppUpdateSecurityValidator.executeWithValidatedRedirects(
                client = client,
                initialUrl = releasesUrl,
                headers = mapOf(
                    "Accept" to "application/vnd.github+json",
                    "User-Agent" to UA,
                ),
            ).use { response ->
                when {
                    response.code == 404 ->
                        _state.value = AppUpdateState.NoReleases

                    response.code == 403 ->
                        throw IOException("GitHub rate limit — try again later")

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
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "checkForUpdate failed", e)
            _state.value = AppUpdateState.Failed(e.message ?: "network error")
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        scope.launch {
            downloadUpdateInternal(info)
        }
    }

    internal fun downloadUpdateInternal(info: AppUpdateInfo) {
        cleanupLegacyExternalUpdates()
        val partFile = File(updateDir, "$APK_FILE.part")
        val finalFile = File(updateDir, APK_FILE)
        _state.value = AppUpdateState.Downloading(info, 0f)
        try {
            partFile.delete()
            AppUpdateSecurityValidator.executeWithValidatedRedirects(
                client = client,
                initialUrl = info.apkUrl,
                headers = mapOf("User-Agent" to UA),
            ).use { response ->
                Log.i(TAG, "downloadUpdate: HTTP ${response.code} for ${info.tagName}")
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} downloading update")
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
                                        info,
                                        (percent / 100f).coerceIn(0f, 1f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (total <= 0L) {
                    throw IOException("Downloaded update APK is empty")
                }
                if (contentLength > 0L && total != contentLength) {
                    throw IOException("Truncated download: expected $contentLength bytes, received $total")
                }
            }

            val expectedSize = info.apkSizeBytes.takeIf { it > 0L }
            val validation = validateApkFile(partFile, expectedByteCount = expectedSize)
            if (validation is ApkValidationResult.Invalid) {
                throw SecurityException(validation.reason)
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            Log.i(TAG, "downloadUpdate: verified and saved ${finalFile.length()} bytes")
            _state.value = AppUpdateState.Downloaded(info, finalFile)
        } catch (ce: CancellationException) {
            partFile.delete()
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "downloadUpdate FAILED", e)
            partFile.delete()
            finalFile.delete()
            _state.value = AppUpdateState.Failed(e.message ?: "download error")
        }
    }

    private fun cleanupLegacyExternalUpdates() {
        runCatching {
            val legacy = legacyExternalDirProvider() ?: return
            if (legacy.exists()) {
                legacy.listFiles()?.forEach { it.delete() }
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
