package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.AdhanRecording
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AdhanDownloadCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val recording = AdhanRecording(
        id = "test_adhan",
        displayNameAr = "أذان تجريبي",
        displayNameEn = "Test Adhan",
        descriptionAr = "وصف",
        descriptionEn = "Description",
        durationSeconds = 60,
        license = "CC0",
        attribution = "Test",
        sourceUrl = "https://example.org",
        category = AdhanCategory.MAKKAH,
        isBundled = false,
        downloadUrl = "https://upload.wikimedia.org/wikipedia/commons/test.ogg",
        fileExtension = "ogg",
    )

    @Test
    fun cancelDownloadCancelsActiveCallCleansPartFileAndNeverReportsFailedOrDownloaded() {
        val started = CountDownLatch(1)
        val allowFinish = CountDownLatch(1)
        val networkCalls = AtomicInteger(0)

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                networkCalls.incrementAndGet()
                val call = chain.call()
                val body = object : ResponseBody() {
                    override fun contentType() = "audio/ogg".toMediaType()
                    override fun contentLength() = 20_000L
                    override fun source(): BufferedSource {
                        val slowSource = object : Source {
                            private var chunks = 0
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                if (call.isCanceled()) throw IOException("Canceled")
                                if (chunks == 0) {
                                    chunks++
                                    sink.write(ByteArray(4096) { 1 })
                                    started.countDown()
                                    return 4096L
                                }
                                allowFinish.await(5, TimeUnit.SECONDS)
                                if (call.isCanceled()) throw IOException("Canceled")
                                return -1L
                            }

                            override fun timeout(): Timeout = Timeout.NONE
                            override fun close() = Unit
                        }
                        return slowSource.buffer()
                    }
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val audioDir = File(tempFolder.root, "adhan")
        val repo = AdhanDownloadRepository(
            audioDirProvider = { audioDir },
            client = client,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            minValidBytes = 1024L,
        )

        repo.download(recording)
        assertTrue(started.await(5, TimeUnit.SECONDS))

        repo.download(recording)
        assertEquals(1, networkCalls.get())

        repo.cancelDownload(recording.id)
        allowFinish.countDown()

        val deadline = System.currentTimeMillis() + 3_000L
        while (repo.isDownloadActive(recording.id) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }

        assertFalse(repo.isDownloadActive(recording.id))
        assertNull(repo.states.value[recording.id])
        assertFalse(File(audioDir, "${recording.id}.part").exists())
        assertFalse(File(audioDir, "${recording.id}.${recording.fileExtension}").exists())
    }

    @Test
    fun retryAfterCancellationDownloadsCleanlyToFinalFile() {
        val attempt = AtomicInteger(0)
        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val n = attempt.incrementAndGet()
                val call = chain.call()
                if (n == 1) {
                    val body = object : ResponseBody() {
                        override fun contentType() = "audio/ogg".toMediaType()
                        override fun contentLength() = 4096L
                        override fun source(): BufferedSource = object : Source {
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                firstStarted.countDown()
                                firstRelease.await(5, TimeUnit.SECONDS)
                                if (call.isCanceled()) throw IOException("Canceled")
                                return -1L
                            }
                            override fun timeout(): Timeout = Timeout.NONE
                            override fun close() = Unit
                        }.buffer()
                    }
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ByteArray(2048) { 5 }.toResponseBody("audio/ogg".toMediaType()))
                        .build()
                }
            })
            .build()

        val audioDir = File(tempFolder.root, "adhan_retry")
        val repo = AdhanDownloadRepository(
            audioDirProvider = { audioDir },
            client = client,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            minValidBytes = 1024L,
        )

        repo.download(recording)
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
        repo.cancelDownload(recording.id)
        firstRelease.countDown()

        val waitCancel = System.currentTimeMillis() + 3_000L
        while (repo.isDownloadActive(recording.id) && System.currentTimeMillis() < waitCancel) {
            Thread.sleep(20)
        }

        repo.download(recording)
        val waitDone = System.currentTimeMillis() + 3_000L
        while (repo.states.value[recording.id] != AdhanDownloadState.Downloaded && System.currentTimeMillis() < waitDone) {
            Thread.sleep(20)
        }

        assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
        assertTrue(repo.isDownloaded(recording))
        assertFalse(File(audioDir, "${recording.id}.part").exists())
    }
}
