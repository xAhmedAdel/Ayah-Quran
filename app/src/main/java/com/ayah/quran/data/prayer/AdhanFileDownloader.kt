package com.ayah.quran.data.prayer

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject

/** Validation failure modes of [AdhanFileDownloader.downloadTo]. */
class AdhanDownloadVerificationException(message: String) : IOException(message)

/**
 * Pure HTTPS download routine: streams a URL to a destination file while
 * validating SIZE (against the source catalog metadata), audio magic bytes
 * and computing SHA-256 (verified against an expected value when provided,
 * persisted/passed back otherwise).
 *
 * No Android dependencies — JVM-testable against a local HTTP server.
 */
class AdhanFileDownloader @Inject constructor() {

    private val userAgent =
        "AyahQuranApp/1.0 (https://github.com/xAhmedAdel/Ayah-Quran; ayah-app@example.com)"

    fun downloadTo(
        url: String,
        destination: File,
        expectedSizeBytes: Long?,
        expectedSha256: String?,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw AdhanDownloadVerificationException("HTTP ${connection.responseCode}")
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesDone = 0L
        val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSizeBytes ?: -1L

        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesDone += read
                }
            }
        }

        // Size validation (primary integrity check — from Commons metadata).
        expectedSizeBytes?.let { expected ->
            if (destination.length() != expected) {
                throw AdhanDownloadVerificationException(
                    "size mismatch: expected $expected, got ${destination.length()}",
                )
            }
        }

        // Audio magic bytes (OggS / ID3 / raw-MP3 sync / RIFF).
        val head = destination.inputStream().use { input ->
            val buffer = ByteArray(4)
            val read = input.read(buffer)
            buffer.copyOf(read.coerceAtLeast(0))
        }
        val magicOk = head.decodeToString(0, 4) == "OggS" ||
            head.decodeToString(0, 3) == "ID3" ||
            (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) ||
            head.decodeToString(0, 4) == "RIFF"
        if (!magicOk) throw AdhanDownloadVerificationException("not a recognized audio file")

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        expectedSha256?.let { expected ->
            if (!sha256.equals(expected, ignoreCase = true)) {
                throw AdhanDownloadVerificationException("sha256 mismatch")
            }
        }
        return sha256
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
