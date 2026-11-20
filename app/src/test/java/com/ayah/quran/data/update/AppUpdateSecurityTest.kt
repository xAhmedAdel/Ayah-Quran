package com.ayah.quran.data.update

import com.ayah.quran.domain.update.AppUpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AppUpdateSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val trustedDigest = AppUpdateSecurityValidator.TRUSTED_RELEASE_CERT_SHA256

    private fun buildClient(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun fakeResponse(
        request: okhttp3.Request,
        code: Int,
        bodyBytes: ByteArray = ByteArray(0),
        location: String? = null,
        declaredContentLength: Long? = null,
    ): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(bodyBytes.toResponseBody("application/vnd.android.package-archive".toMediaType()))
        if (location != null) {
            builder.header("Location", location)
        }
        if (declaredContentLength != null) {
            builder.header("Content-Length", declaredContentLength.toString())
        }
        return builder.build()
    }

    @Test(expected = SecurityException::class)
    fun rejectsNonHttpsUrl() {
        AppUpdateSecurityValidator.validateUrl(
            "http://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.12/app.apk",
        )
    }

    @Test(expected = SecurityException::class)
    fun rejectsUnapprovedHost() {
        AppUpdateSecurityValidator.validateUrl(
            "https://evil.example.com/Ayah-v1.0.0-beta.12.apk",
        )
    }

    @Test(expected = SecurityException::class)
    fun rejectsHttpsToHttpDowngradeRedirect() {
        val client = buildClient { request ->
            fakeResponse(
                request = request,
                code = 302,
                location = "http://objects.githubusercontent.com/file.apk",
            )
        }
        AppUpdateSecurityValidator.executeWithValidatedRedirects(
            client = client,
            initialUrl = "https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.12/app.apk",
        )
    }

    @Test(expected = SecurityException::class)
    fun rejectsRedirectToUnapprovedHost() {
        val client = buildClient { request ->
            fakeResponse(
                request = request,
                code = 302,
                location = "https://attacker.cdn.invalid/app.apk",
            )
        }
        AppUpdateSecurityValidator.executeWithValidatedRedirects(
            client = client,
            initialUrl = "https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.12/app.apk",
        )
    }

    @Test
    fun allowsValidatedRedirectToApprovedGitHubAssetHost() {
        val visited = mutableListOf<String>()
        val client = buildClient { request ->
            visited += request.url.host
            if (request.url.host == "github.com") {
                fakeResponse(
                    request = request,
                    code = 302,
                    location = "https://objects.githubusercontent.com/release/app.apk",
                )
            } else {
                fakeResponse(request = request, code = 200, bodyBytes = byteArrayOf(1, 2, 3, 4))
            }
        }
        AppUpdateSecurityValidator.executeWithValidatedRedirects(
            client = client,
            initialUrl = "https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.12/app.apk",
        ).use { response ->
            assertEquals(200, response.code)
            assertEquals(listOf("github.com", "objects.githubusercontent.com"), visited)
        }
    }

    @Test(expected = IOException::class)
    fun rejectsExcessiveRedirectChain() {
        val client = buildClient { request ->
            fakeResponse(
                request = request,
                code = 302,
                location = "https://objects.githubusercontent.com/loop",
            )
        }
        AppUpdateSecurityValidator.executeWithValidatedRedirects(
            client = client,
            initialUrl = "https://github.com/loop",
            maxRedirects = 2,
        )
    }

    @Test
    fun rejectsWrongPackageName() {
        val apk = tempFolder.newFile("rogue.apk").apply { writeBytes(ByteArray(128) { 1 }) }
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.evil.other", 13L, setOf(trustedDigest)) },
        )
        assertTrue(result is ApkValidationResult.Invalid)
        assertTrue((result as ApkValidationResult.Invalid).reason.contains("package name"))
    }

    @Test
    fun rejectsWrongSigningCertificate() {
        val apk = tempFolder.newFile("wrong-cert.apk").apply { writeBytes(ByteArray(128) { 1 }) }
        val wrongDigest = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(wrongDigest)) },
        )
        assertTrue(result is ApkValidationResult.Invalid)
        assertTrue((result as ApkValidationResult.Invalid).reason.contains("not trusted"))
    }

    @Test
    fun rejectsIncompatibleInstalledSigner() {
        val apk = tempFolder.newFile("mismatch-installed.apk").apply { writeBytes(ByteArray(128) { 1 }) }
        val debugInstalledDigest = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(debugInstalledDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(trustedDigest)) },
        )
        assertTrue(result is ApkValidationResult.Invalid)
        assertTrue((result as ApkValidationResult.Invalid).reason.contains("installed application"))
    }

    @Test
    fun rejectsSameOrOlderVersionCode() {
        val apk = tempFolder.newFile("same-version.apk").apply { writeBytes(ByteArray(128) { 1 }) }
        val same = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 12L, setOf(trustedDigest)) },
        )
        val older = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 11L, setOf(trustedDigest)) },
        )
        assertTrue(same is ApkValidationResult.Invalid)
        assertTrue(older is ApkValidationResult.Invalid)
    }

    @Test
    fun rejectsEmptyApk() {
        val empty = tempFolder.newFile("empty.apk")
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = empty,
            expectedByteCount = null,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(trustedDigest)) },
        )
        assertTrue(result is ApkValidationResult.Invalid)
        assertTrue((result as ApkValidationResult.Invalid).reason.contains("empty"))
    }

    @Test
    fun rejectsTruncatedApk() {
        val truncated = tempFolder.newFile("trunc.apk").apply { writeBytes(ByteArray(64) { 1 }) }
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = truncated,
            expectedByteCount = 128L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(trustedDigest)) },
        )
        assertTrue(result is ApkValidationResult.Invalid)
        assertTrue((result as ApkValidationResult.Invalid).reason.contains("size mismatch"))
    }

    @Test
    fun acceptsValidSignedNewerApk() {
        val apk = tempFolder.newFile("valid.apk").apply { writeBytes(ByteArray(256) { 7 }) }
        val result = AppUpdateSecurityValidator.validateApk(
            apkFile = apk,
            expectedByteCount = 256L,
            installedVersionCode = 12L,
            installedSignerSha256Digests = setOf(trustedDigest),
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(trustedDigest)) },
        )
        assertTrue(result is ApkValidationResult.Valid)
        assertEquals(13L, (result as ApkValidationResult.Valid).metadata.versionCode)
    }

    @Test
    fun repositoryDownloadValidatesAndStoresInInternalDirectoryAndCleansFailures() {
        val internalUpdates = File(tempFolder.root, "internal_updates")
        val legacyExternal = File(tempFolder.root, "legacy_external").apply {
            mkdirs()
            File(this, "stale.apk").writeBytes(ByteArray(16) { 9 })
        }
        val payload = ByteArray(512) { 3 }
        val client = buildClient { request ->
            fakeResponse(request = request, code = 200, bodyBytes = payload)
        }
        val repo = AppUpdateRepository(
            updateDirProvider = { internalUpdates },
            legacyExternalDirProvider = { legacyExternal },
            client = client,
            metadataReader = { ApkArchiveMetadata("com.ayah.quran", 13L, setOf(trustedDigest)) },
            installedVersionCodeProvider = { 12L },
            installedSignersProvider = { setOf(trustedDigest) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            releasesUrl = "https://api.github.com/repos/xAhmedAdel/Ayah-Quran/releases?per_page=10",
        )
        val info = AppUpdateInfo(
            tagName = "v1.0.0-beta.12",
            releaseName = "Beta 12",
            apkUrl = "https://github.com/xAhmedAdel/Ayah-Quran/releases/download/v1.0.0-beta.12/app.apk",
            apkSizeBytes = payload.size.toLong(),
            releaseNotesUrl = "https://github.com/xAhmedAdel/Ayah-Quran/releases/tag/v1.0.0-beta.12",
        )

        repo.downloadUpdateInternal(info)

        val state = repo.state.value
        assertTrue(state is AppUpdateState.Downloaded)
        val saved = (state as AppUpdateState.Downloaded).apkFile
        assertEquals(internalUpdates, saved.parentFile)
        assertFalse(File(legacyExternal, "stale.apk").exists())
        assertEquals(saved, repo.pendingUpdateFile())

        val badInfo = info.copy(apkSizeBytes = 9999L)
        repo.downloadUpdateInternal(badInfo)
        assertTrue(repo.state.value is AppUpdateState.Failed)
        assertFalse(File(internalUpdates, "ayah-update.apk").exists())
        assertFalse(File(internalUpdates, "ayah-update.apk.part").exists())
        assertNull(repo.pendingUpdateFile())
    }
}
