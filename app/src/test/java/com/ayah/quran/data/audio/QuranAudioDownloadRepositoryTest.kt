package com.ayah.quran.data.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class QuranAudioDownloadRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val edition = QuranAudioCatalog.defaultReciter
    private val validMp3Payload = ByteArray(2048) { 0x2A }

    @Test
    fun midSurahCancelCancelsCallCleansPartPreservesCompletedNeverFailsAndResumesCleanly() {
        val ayah3Started = CountDownLatch(1)
        val allowAyah3Unblock = CountDownLatch(1)
        val ayah3Call = AtomicReference<Call?>()
        val cancelPhase = AtomicBoolean(true)
        val requestCounts = ConcurrentHashMap<Int, AtomicInteger>()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val globalAyah = url.substringAfterLast("/").substringBefore(".").toInt()
                requestCounts.computeIfAbsent(globalAyah) { AtomicInteger(0) }.incrementAndGet()
                val call = chain.call()

                if (globalAyah == 3 && cancelPhase.get()) {
                    ayah3Call.set(call)
                    val body = object : ResponseBody() {
                        override fun contentType() = "audio/mpeg".toMediaType()
                        override fun contentLength() = 16_384L
                        override fun source(): BufferedSource {
                            val slow = object : Source {
                                private var sentFirst = false
                                override fun read(sink: Buffer, byteCount: Long): Long {
                                    if (call.isCanceled()) {
                                        throw IOException("Canceled")
                                    }
                                    if (!sentFirst) {
                                        sentFirst = true
                                        sink.write(ByteArray(512) { 1 })
                                        ayah3Started.countDown()
                                        return 512L
                                    }
                                    allowAyah3Unblock.await(5, TimeUnit.SECONDS)
                                    if (call.isCanceled()) {
                                        throw IOException("Canceled")
                                    }
                                    return -1L
                                }

                                override fun timeout(): Timeout = Timeout.NONE
                                override fun close() = Unit
                            }
                            return slow.buffer()
                        }
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
                        .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                }
            })
            .build()

        val audioDir = File(tempFolder.root, "quran_cancel_resume")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val observedStates = Collections.synchronizedList(mutableListOf<QuranAudioState?>())
        val key = "${edition.editionId}:1"

        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            val collector = scope.launch {
                repo.states.collect { map ->
                    observedStates.add(map[key])
                }
            }

            repo.downloadSurah(edition, surah = 1)
            assertTrue(ayah3Started.await(5, TimeUnit.SECONDS))

            repo.downloadSurah(edition, surah = 1)
            assertEquals(1, requestCounts[1]?.get())
            assertEquals(1, requestCounts[2]?.get())
            assertEquals(1, requestCounts[3]?.get())

            repo.cancelDownload(edition.editionId, surah = 1)
            allowAyah3Unblock.countDown()

            val cancelDeadline = System.currentTimeMillis() + 3_000L
            while (repo.isDownloadActive(edition.editionId, 1) && System.currentTimeMillis() < cancelDeadline) {
                Thread.sleep(20)
            }
            Thread.sleep(100)

            assertFalse(repo.isDownloadActive(edition.editionId, 1))
            assertTrue(ayah3Call.get()?.isCanceled() == true)
            assertNull(repo.states.value[key])
            assertTrue(observedStates.none { it is QuranAudioState.Failed })

            val surahDir = File(File(audioDir, edition.editionId), "1")
            assertTrue(File(surahDir, "1.mp3").isFile)
            assertTrue(File(surahDir, "2.mp3").isFile)
            assertFalse(File(surahDir, "3.mp3").exists())
            assertTrue(surahDir.listFiles()?.none { it.name.endsWith(".part") } == true)
            assertEquals(2, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertFalse(repo.isDownloaded(edition.editionId, 1, 7))

            cancelPhase.set(false)
            val completed = CountDownLatch(1)
            repo.downloadSurah(edition, surah = 1) {
                completed.countDown()
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS))
            val doneDeadline = System.currentTimeMillis() + 2_000L
            while (repo.states.value[key] != QuranAudioState.Downloaded && System.currentTimeMillis() < doneDeadline) {
                Thread.sleep(20)
            }

            assertEquals(QuranAudioState.Downloaded, repo.states.value[key])
            assertEquals(7, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertTrue(repo.isDownloaded(edition.editionId, 1, 7))
            assertEquals(1, requestCounts[1]?.get())
            assertEquals(1, requestCounts[2]?.get())
            assertEquals(2, requestCounts[3]?.get())
            for (ayah in 4..7) {
                assertEquals(1, requestCounts[ayah]?.get())
            }
            assertTrue(surahDir.listFiles()?.none { it.name.endsWith(".part") } == true)
            collector.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun readOnlyLookupsNeverCreateDirectories() {
        val audioDir = File(tempFolder.root, "non_existent_quran_audio")
        assertFalse(audioDir.exists())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            assertEquals(0, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertFalse(repo.isDownloaded(edition.editionId, 1, 7))
            assertNull(repo.localAyahFile(edition.editionId, 1, 1))
            assertFalse(audioDir.exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun refreshDownloadedCountsScansValidFilesIgnoresSmallAndCleansStaleParts() = runBlocking {
        val audioDir = File(tempFolder.root, "scan_audio")
        val surah1Dir = File(File(audioDir, edition.editionId), "1").apply { mkdirs() }
        File(surah1Dir, "1.mp3").writeBytes(validMp3Payload)
        File(surah1Dir, "2.mp3").writeBytes(validMp3Payload)
        File(surah1Dir, "3.mp3").writeBytes(ByteArray(500) { 1 })
        File(surah1Dir, "4.mp3.part").writeBytes(validMp3Payload)
        File(surah1Dir, "notes.txt").writeText("ignored")

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            repo.refreshDownloadedCounts()

            val key = "${edition.editionId}:1"
            assertEquals(2, repo.downloadedCounts.value[key])
            assertEquals(2, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertFalse(repo.isDownloaded(edition.editionId, 1, 7))
            assertNotNull(repo.localAyahFile(edition.editionId, 1, 1))
            assertNull(repo.localAyahFile(edition.editionId, 1, 3))
            assertFalse(File(surah1Dir, "4.mp3.part").exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteSurahClearsCountsStatesAndDiskFiles() {
        val audioDir = File(tempFolder.root, "delete_audio")
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                    .build()
            })
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:108"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            val done = CountDownLatch(1)
            repo.downloadSurah(edition, surah = 108) {
                done.countDown()
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(3, repo.downloadedAyahCount(edition.editionId, 108, 3))
            assertTrue(repo.isDownloaded(edition.editionId, 108, 3))

            repo.deleteSurah(edition.editionId, 108)
            val surahDir = File(File(audioDir, edition.editionId), "108")
            val deleteDeadline = System.currentTimeMillis() + 3_000L
            while (surahDir.exists() && System.currentTimeMillis() < deleteDeadline) {
                Thread.sleep(20)
            }

            assertFalse(surahDir.exists())
            assertNull(repo.states.value[key])
            assertNull(repo.downloadedCounts.value[key])
            assertEquals(0, repo.downloadedAyahCount(edition.editionId, 108, 3))
            assertFalse(repo.isDownloaded(edition.editionId, 108, 3))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun httpFailureCleansPartFileAndEmitsFailedState() {
        val audioDir = File(tempFolder.root, "fail_audio")
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body(ByteArray(0).toResponseBody("audio/mpeg".toMediaType()))
                    .build()
            })
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:108"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            repo.downloadSurah(edition, surah = 108)
            val deadline = System.currentTimeMillis() + 3_000L
            while (repo.states.value[key] !is QuranAudioState.Failed && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            assertTrue(repo.states.value[key] is QuranAudioState.Failed)
            val surahDir = File(File(audioDir, edition.editionId), "108")
            assertTrue(surahDir.listFiles()?.none { it.name.endsWith(".part") } ?: true)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelAndImmediateRestartDoesNotDeleteNewPartFilesOrClobberDownloadingState() {
        val d1Ayah3Blocked = CountDownLatch(1)
        val allowD1Exit = CountDownLatch(1)
        val d2Ayah3Started = CountDownLatch(1)
        val allowD2Finish = CountDownLatch(1)
        val ayah3Attempt = AtomicInteger(0)
        val requestCounts = ConcurrentHashMap<Int, AtomicInteger>()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val globalAyah = url.substringAfterLast("/").substringBefore(".").toInt()
                requestCounts.computeIfAbsent(globalAyah) { AtomicInteger(0) }.incrementAndGet()
                val call = chain.call()

                if (globalAyah == 3) {
                    val attempt = ayah3Attempt.incrementAndGet()
                    if (attempt == 1) {
                        val body = object : ResponseBody() {
                            override fun contentType() = "audio/mpeg".toMediaType()
                            override fun contentLength() = 16_384L
                            override fun source(): BufferedSource = object : Source {
                                private var first = true
                                override fun read(sink: Buffer, byteCount: Long): Long {
                                    if (first) {
                                        first = false
                                        sink.write(ByteArray(512) { 1 })
                                        d1Ayah3Blocked.countDown()
                                        return 512L
                                    }
                                    allowD1Exit.await(5, TimeUnit.SECONDS)
                                    throw IOException("Canceled")
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
                        val body = object : ResponseBody() {
                            override fun contentType() = "audio/mpeg".toMediaType()
                            override fun contentLength() = validMp3Payload.size.toLong()
                            override fun source(): BufferedSource = object : Source {
                                private var step = 0
                                override fun read(sink: Buffer, byteCount: Long): Long {
                                    if (call.isCanceled()) throw IOException("Canceled")
                                    return when (step++) {
                                        0 -> {
                                            sink.write(validMp3Payload, 0, 1024)
                                            d2Ayah3Started.countDown()
                                            1024L
                                        }
                                        1 -> {
                                            allowD2Finish.await(5, TimeUnit.SECONDS)
                                            sink.write(validMp3Payload, 1024, validMp3Payload.size - 1024)
                                            (validMp3Payload.size - 1024).toLong()
                                        }
                                        else -> -1L
                                    }
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
                    }
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                }
            })
            .build()

        val audioDir = File(tempFolder.root, "cancel_immediate_restart")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:1"
        val statesAfterRestart = Collections.synchronizedList(mutableListOf<QuranAudioState?>())

        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            repo.downloadSurah(edition, surah = 1)
            assertTrue(d1Ayah3Blocked.await(5, TimeUnit.SECONDS))

            repo.cancelDownload(edition.editionId, surah = 1)
            val d2Done = CountDownLatch(1)
            repo.downloadSurah(edition, surah = 1) {
                d2Done.countDown()
            }

            assertTrue(repo.states.value[key] is QuranAudioState.Downloading)
            val collector = scope.launch {
                repo.states.collect { map ->
                    statesAfterRestart.add(map[key])
                }
            }

            allowD1Exit.countDown()
            assertTrue(d2Ayah3Started.await(5, TimeUnit.SECONDS))

            val surahDir = File(File(audioDir, edition.editionId), "1")
            val activeParts = surahDir.listFiles()?.filter { it.name.endsWith(".mp3.part") }.orEmpty()
            assertEquals(1, activeParts.size)
            assertTrue(repo.states.value[key] is QuranAudioState.Downloading)

            allowD2Finish.countDown()
            assertTrue(d2Done.await(5, TimeUnit.SECONDS))

            assertEquals(QuranAudioState.Downloaded, repo.states.value[key])
            assertTrue(statesAfterRestart.none { it == null || it is QuranAudioState.Failed })
            assertEquals(7, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertEquals(1, requestCounts[1]?.get())
            assertEquals(1, requestCounts[2]?.get())
            assertEquals(2, requestCounts[3]?.get())
            for (ayah in 1..7) {
                assertTrue(File(surahDir, "$ayah.mp3").isFile)
            }
            assertTrue(surahDir.listFiles()?.none { it.name.endsWith(".part") } == true)
            collector.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun concurrentCancelAndDeleteWaitsForActiveDownloadAndLeavesCleanDisk() {
        val blocked = CountDownLatch(1)
        val allowExit = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val globalAyah = url.substringAfterLast("/").substringBefore(".").toInt()
                if (globalAyah == 2) {
                    val body = object : ResponseBody() {
                        override fun contentType() = "audio/mpeg".toMediaType()
                        override fun contentLength() = 8192L
                        override fun source(): BufferedSource = object : Source {
                            private var first = true
                            override fun read(sink: Buffer, byteCount: Long): Long {
                                if (first) {
                                    first = false
                                    sink.write(ByteArray(512) { 2 })
                                    blocked.countDown()
                                    return 512L
                                }
                                allowExit.await(5, TimeUnit.SECONDS)
                                throw IOException("Canceled")
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
                        .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                }
            })
            .build()

        val audioDir = File(tempFolder.root, "cancel_and_delete")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:1"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            repo.downloadSurah(edition, surah = 1)
            assertTrue(blocked.await(5, TimeUnit.SECONDS))

            repo.cancelDownload(edition.editionId, surah = 1)
            repo.deleteSurah(edition.editionId, surah = 1)
            allowExit.countDown()

            val deadline = System.currentTimeMillis() + 3_000L
            while (repo.hasPendingOperation(edition.editionId, 1) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            val surahDir = File(File(audioDir, edition.editionId), "1")
            assertFalse(repo.hasPendingOperation(edition.editionId, 1))
            assertFalse(surahDir.exists())
            assertNull(repo.states.value[key])
            assertNull(repo.downloadedCounts.value[key])
            assertEquals(0, repo.downloadedAyahCount(edition.editionId, 1, 7))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteFollowedImmediatelyByDownloadCompletesDeleteFirstThenDownloadsCleanly() {
        val requestCount = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestCount.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                    .build()
            })
            .build()

        val audioDir = File(tempFolder.root, "delete_then_download")
        val surahDir = File(File(audioDir, edition.editionId), "108").apply { mkdirs() }
        for (ayah in 1..3) {
            File(surahDir, "$ayah.mp3").writeBytes(ByteArray(2048) { 0x09 })
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:108"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )
            runBlocking { repo.refreshDownloadedCounts() }
            assertEquals(3, repo.downloadedAyahCount(edition.editionId, 108, 3))

            val done = CountDownLatch(1)
            repo.deleteSurah(edition.editionId, 108)
            repo.downloadSurah(edition, surah = 108) {
                done.countDown()
            }

            assertTrue(done.await(5, TimeUnit.SECONDS))
            val deadline = System.currentTimeMillis() + 2_000L
            while (repo.hasPendingOperation(edition.editionId, 108) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            assertEquals(3, requestCount.get())
            assertEquals(QuranAudioState.Downloaded, repo.states.value[key])
            assertEquals(3, repo.downloadedAyahCount(edition.editionId, 108, 3))
            assertTrue(repo.isDownloaded(edition.editionId, 108, 3))
            for (ayah in 1..3) {
                val file = File(surahDir, "$ayah.mp3")
                assertTrue(file.isFile)
                assertEquals(0x2A.toByte(), file.readBytes().first())
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun midSurahHttpFailurePreservesCompletedAyahFilesAndAllowsResume() {
        val failAyah3 = AtomicBoolean(true)
        val requestCounts = ConcurrentHashMap<Int, AtomicInteger>()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val globalAyah = url.substringAfterLast("/").substringBefore(".").toInt()
                requestCounts.computeIfAbsent(globalAyah) { AtomicInteger(0) }.incrementAndGet()

                if (globalAyah == 3 && failAyah3.get()) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body(ByteArray(0).toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validMp3Payload.toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                }
            })
            .build()

        val audioDir = File(tempFolder.root, "mid_surah_fail")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:1"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            repo.downloadSurah(edition, surah = 1)
            val failDeadline = System.currentTimeMillis() + 3_000L
            while (repo.states.value[key] !is QuranAudioState.Failed && System.currentTimeMillis() < failDeadline) {
                Thread.sleep(20)
            }

            assertTrue(repo.states.value[key] is QuranAudioState.Failed)
            val surahDir = File(File(audioDir, edition.editionId), "1")
            assertTrue(File(surahDir, "1.mp3").isFile)
            assertTrue(File(surahDir, "2.mp3").isFile)
            assertFalse(File(surahDir, "3.mp3").exists())
            assertTrue(surahDir.listFiles()?.none { it.name.endsWith(".part") } == true)
            assertEquals(2, repo.downloadedAyahCount(edition.editionId, 1, 7))

            failAyah3.set(false)
            val done = CountDownLatch(1)
            repo.downloadSurah(edition, surah = 1) {
                done.countDown()
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))

            assertEquals(QuranAudioState.Downloaded, repo.states.value[key])
            assertEquals(7, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertEquals(1, requestCounts[1]?.get())
            assertEquals(1, requestCounts[2]?.get())
            assertEquals(2, requestCounts[3]?.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoScanOnInitRecoversDownloadedCountsAndCleansStalePartsOnStartup() {
        val audioDir = File(tempFolder.root, "auto_scan_restart")
        val surah1Dir = File(File(audioDir, edition.editionId), "1").apply { mkdirs() }
        File(surah1Dir, "1.mp3").writeBytes(validMp3Payload)
        File(surah1Dir, "2.mp3").writeBytes(validMp3Payload)
        File(surah1Dir, "3.mp3").writeBytes(ByteArray(500) { 1 })
        File(surah1Dir, "4.99.mp3.part").writeBytes(validMp3Payload)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val key = "${edition.editionId}:1"
        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = true,
            )

            val deadline = System.currentTimeMillis() + 3_000L
            while (repo.downloadedCounts.value[key] != 2 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }

            assertEquals(2, repo.downloadedCounts.value[key])
            assertEquals(2, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertFalse(File(surah1Dir, "4.99.mp3.part").exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupScanDoesNotRestoreDeletedSurahCountWhenDeleteCompletesBeforeMerge() = runBlocking {
        val audioDir = File(tempFolder.root, "scan_delete_race")
        val surah1Dir = File(File(audioDir, edition.editionId), "1").apply { mkdirs() }
        for (ayah in 1..7) {
            File(surah1Dir, "$ayah.mp3").writeBytes(validMp3Payload)
        }
        val surah108Dir = File(File(audioDir, edition.editionId), "108").apply { mkdirs() }
        for (ayah in 1..3) {
            File(surah108Dir, "$ayah.mp3").writeBytes(validMp3Payload)
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val surah1Key = "${edition.editionId}:1"
        val surah108Key = "${edition.editionId}:108"
        val surah1Scanned = CompletableDeferred<Unit>()
        val allowScanMerge = CompletableDeferred<Unit>()

        try {
            val repo = QuranAudioDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minAyahBytes = 1_000L,
                autoScanOnInit = false,
            )

            val scanJob = scope.launch {
                repo.refreshDownloadedCountsInternal { scannedId ->
                    if (scannedId == surah1Key) {
                        surah1Scanned.complete(Unit)
                        allowScanMerge.await()
                    }
                }
            }

            surah1Scanned.await()

            repo.deleteSurah(edition.editionId, 1)
            repo.awaitPendingOperation(edition.editionId, 1)

            assertFalse(surah1Dir.exists())
            assertFalse(repo.hasPendingOperation(edition.editionId, 1))
            assertNull(repo.downloadedCounts.value[surah1Key])

            allowScanMerge.complete(Unit)
            scanJob.join()

            assertNull(repo.downloadedCounts.value[surah1Key])
            assertEquals(0, repo.downloadedAyahCount(edition.editionId, 1, 7))
            assertFalse(repo.isDownloaded(edition.editionId, 1, 7))
            assertEquals(3, repo.downloadedCounts.value[surah108Key])
            assertEquals(3, repo.downloadedAyahCount(edition.editionId, 108, 3))
            assertTrue(repo.isDownloaded(edition.editionId, 108, 3))
        } finally {
            scope.cancel()
        }
    }
}
