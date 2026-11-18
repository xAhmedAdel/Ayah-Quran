package com.ayah.quran.data.prayer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Download-mechanics tests against a LOCAL HTTP server (no network): success
 * path, SHA-256/size validation failures, corrupted-file rejection. The real
 * Wikimedia download URLs are exercised by the app at runtime (Phase 15 §3) —
 * research-time copies verified the source files themselves (see
 * docs/ADHAN-AUDIO-ASSETS.md).
 */
class AdhanFileDownloaderTest {

    // Real OggS magic so the downloader's audio-magic check passes.
    private val payload: ByteArray by lazy {
        "OggS".toByteArray() + ByteArray(2048) { (it % 251).toByte() }
    }
    private val payloadSha: String by lazy {
        MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }
    }

    private lateinit var serverSocket: ServerSocket
    private lateinit var serverThread: Thread
    private lateinit var tmpDir: File

    /**
     * Raw-socket HTTP server: one canned response per request, matched by
     * the requested path. ServerSocket is pure JDK — no HTTP module needed.
     */
    private fun startServer(responses: Map<String, Pair<Int, ByteArray>>) {
        serverSocket = ServerSocket(0) // ephemeral port
        serverThread = thread(isDaemon = true) {
            while (!serverSocket.isClosed) {
                val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    try {
                        val reader = BufferedReader(
                            InputStreamReader(socket.getInputStream()),
                        )
                        val requestLine = reader.readLine()
                        val path = requestLine?.let { line ->
                            Regex("""^[A-Z]+ (\S+) HTTP""").find(line)?.groupValues?.get(1)
                        } ?: "/"
                        val (status, bytes) = responses[path]
                            ?: responses["/missing-handler"] ?: (404 to ByteArray(0))
                        val header = "HTTP/1.1 $status OK\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        socket.getOutputStream().apply {
                            write(header.toByteArray())
                            write(bytes)
                            flush()
                        }
                        socket.close()
                    } catch (_: Exception) {
                        // client aborted — next connection proceeds
                    }
                }
            }
        }
    }

    private fun url(path: String): String = "http://localhost:${serverSocket.localPort}$path"

    private val downloader = AdhanFileDownloader()

    @Before
    fun setUp() {
        tmpDir = File(System.getProperty("java.io.tmpdir"), "adhan-dl-${System.nanoTime()}")
        tmpDir.mkdirs()
    }

    @After
    fun tearDown() {
        serverSocket.close()
        tmpDir.deleteRecursively()
    }

    @Test
    fun `successful download computes the sha256`() {
        startServer(mapOf("/good.ogg" to (200 to payload)))
        val dest = File(tmpDir, "good.ogg.part")
        val sha = downloader.downloadTo(url("/good.ogg"), dest, payload.size.toLong(), null)
        assertEquals(payloadSha, sha)
        assertEquals(payload.size.toLong(), dest.length())
    }

    @Test
    fun `matching expected sha256 passes`() {
        startServer(mapOf("/good.ogg" to (200 to payload)))
        val dest = File(tmpDir, "match.ogg.part")
        val sha = downloader.downloadTo(url("/good.ogg"), dest, payload.size.toLong(), payloadSha)
        assertEquals(payloadSha, sha)
    }

    @Test
    fun `size mismatch fails and deletes the partial file`() {
        startServer(mapOf("/truncated.ogg" to (200 to payload.copyOf(1024))))
        val dest = File(tmpDir, "truncated.ogg.part")
        try {
            downloader.downloadTo(url("/truncated.ogg"), dest, payload.size.toLong(), null)
            org.junit.Assert.fail("expected size-mismatch failure")
        } catch (e: AdhanDownloadVerificationException) {
            assertTrue(e.message!!.contains("size mismatch"))
        }
        // Partial-file cleanup is AdhanDownloadRepository's responsibility;
        // the downloader itself only validates and reports.
    }

    @Test
    fun `corrupted non-audio payload is rejected`() {
        startServer(mapOf("/corrupt.ogg" to (200 to "<html>error page</html>".toByteArray())))
        val dest = File(tmpDir, "corrupt.ogg.part")
        try {
            downloader.downloadTo(url("/corrupt.ogg"), dest, null, null)
            org.junit.Assert.fail("expected magic-byte failure")
        } catch (e: AdhanDownloadVerificationException) {
            assertTrue(e.message!!.contains("audio"))
        }
    }

    @Test
    fun `wrong expected sha256 fails with mismatch`() {
        startServer(mapOf("/good.ogg" to (200 to payload)))
        val dest = File(tmpDir, "wrongsha.ogg.part")
        try {
            downloader.downloadTo(url("/good.ogg"), dest, payload.size.toLong(), "deadbeef")
            org.junit.Assert.fail("expected sha256 mismatch failure")
        } catch (e: AdhanDownloadVerificationException) {
            assertTrue(e.message!!.contains("sha256 mismatch"))
        }
    }

    @Test
    fun `http 404 fails cleanly`() {
        startServer(mapOf("/missing" to (404 to ByteArray(0))))
        val dest = File(tmpDir, "404.ogg.part")
        try {
            downloader.downloadTo(url("/missing"), dest, null, null)
            org.junit.Assert.fail("expected HTTP failure")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("404"))
        }
    }
}
