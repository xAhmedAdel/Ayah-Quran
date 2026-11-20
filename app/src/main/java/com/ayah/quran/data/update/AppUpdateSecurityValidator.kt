package com.ayah.quran.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

data class ApkArchiveMetadata(
    val packageName: String,
    val versionCode: Long,
    val signerSha256Digests: Set<String>,
)

fun interface ApkMetadataReader {
    fun read(apkFile: File): ApkArchiveMetadata?
}

sealed interface ApkValidationResult {
    data class Valid(val metadata: ApkArchiveMetadata) : ApkValidationResult
    data class Invalid(val reason: String) : ApkValidationResult
}

object AppUpdateSecurityValidator {

    const val EXPECTED_PACKAGE_NAME = "com.ayah.quran"
    const val TRUSTED_RELEASE_CERT_SHA256 =
        "90fad949d87c3f75249998378a74e7109cd88a08446e606938263b0de9783e58"
    const val MAX_REDIRECTS = 5

    val ALLOWED_HOSTS: Set<String> = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun validateUrl(url: String): HttpUrl {
        val parsed = url.toHttpUrlOrNull()
            ?: throw SecurityException("Invalid update URL")
        if (!parsed.isHttps) {
            throw SecurityException("Non-HTTPS update URL rejected: ${parsed.scheme}")
        }
        val normalizedHost = parsed.host.lowercase(Locale.ROOT)
        if (normalizedHost !in ALLOWED_HOSTS) {
            throw SecurityException("Unapproved update host rejected: $normalizedHost")
        }
        return parsed
    }

    fun executeWithValidatedRedirects(
        client: OkHttpClient,
        initialUrl: String,
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = MAX_REDIRECTS,
    ): Response {
        var currentUrl = validateUrl(initialUrl)
        var redirectCount = 0
        while (true) {
            val builder = Request.Builder().url(currentUrl)
            headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.newCall(builder.build()).execute()
            if (response.code in REDIRECT_CODES) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect missing Location header")
                }
                redirectCount++
                if (redirectCount > maxRedirects) {
                    throw IOException("Too many redirects ($redirectCount)")
                }
                val resolved = currentUrl.resolve(location)
                    ?: throw SecurityException("Malformed redirect location")
                currentUrl = validateUrl(resolved.toString())
                continue
            }
            return response
        }
    }

    fun validateApk(
        apkFile: File,
        expectedByteCount: Long?,
        installedVersionCode: Long,
        installedSignerSha256Digests: Set<String>,
        metadataReader: ApkMetadataReader,
        expectedPackageName: String = EXPECTED_PACKAGE_NAME,
        trustedCertSha256: String = TRUSTED_RELEASE_CERT_SHA256,
    ): ApkValidationResult {
        if (!apkFile.exists() || !apkFile.isFile) {
            return ApkValidationResult.Invalid("Update APK file not found")
        }
        val fileLength = apkFile.length()
        if (fileLength <= 0L) {
            return ApkValidationResult.Invalid("Update APK file is empty")
        }
        if (expectedByteCount != null && expectedByteCount > 0L && fileLength != expectedByteCount) {
            return ApkValidationResult.Invalid(
                "Update APK size mismatch: expected $expectedByteCount bytes, got $fileLength",
            )
        }

        val metadata = try {
            metadataReader.read(apkFile)
        } catch (t: Throwable) {
            return ApkValidationResult.Invalid("Failed to parse update APK: ${t.message ?: t.javaClass.simpleName}")
        } ?: return ApkValidationResult.Invalid("Unable to parse update APK metadata")

        if (metadata.packageName != expectedPackageName) {
            return ApkValidationResult.Invalid(
                "Unexpected APK package name: ${metadata.packageName}",
            )
        }
        if (metadata.versionCode <= installedVersionCode) {
            return ApkValidationResult.Invalid(
                "Update APK versionCode (${metadata.versionCode}) is not newer than installed ($installedVersionCode)",
            )
        }
        val normalizedApkSigners = metadata.signerSha256Digests
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        if (normalizedApkSigners.isEmpty()) {
            return ApkValidationResult.Invalid("Update APK has no signing certificate")
        }
        val normalizedTrusted = trustedCertSha256.lowercase(Locale.ROOT)
        if (normalizedTrusted !in normalizedApkSigners) {
            return ApkValidationResult.Invalid("Update APK signing certificate is not trusted")
        }
        val normalizedInstalledSigners = installedSignerSha256Digests
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        if (normalizedInstalledSigners.isEmpty() ||
            normalizedApkSigners.none { it in normalizedInstalledSigners }
        ) {
            return ApkValidationResult.Invalid("Update APK signer does not match installed application")
        }

        return ApkValidationResult.Valid(metadata)
    }

    fun readInstalledSignerDigests(context: Context): Set<String> = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        extractSignerSha256Digests(info.signingInfo)
    }.getOrDefault(emptySet())

    fun androidApkMetadataReader(context: Context): ApkMetadataReader = ApkMetadataReader { apkFile ->
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: return@ApkMetadataReader null

        ApkArchiveMetadata(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signerSha256Digests = extractSignerSha256Digests(info.signingInfo),
        )
    }

    private fun extractSignerSha256Digests(signingInfo: android.content.pm.SigningInfo?): Set<String> {
        if (signingInfo == null) return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptySet()
        return signatures.map { sig -> sha256Hex(sig.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
}
