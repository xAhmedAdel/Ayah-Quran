package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanCategory
import com.ayah.quran.domain.model.prayer.AdhanRecording
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.junit.Assert.assertArrayEquals
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AdhanDownloadCancellationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val recording = makeRecording("test_adhan")
    private val otherRecording = makeRecording("other_adhan")

    private fun makeRecording(id: String) = AdhanRecording(
        id = id,
        displayNameAr = "أذان تجريبي",
        displayNameEn = "Test Adhan $id",
        descriptionAr = "وصف",
        descriptionEn = "Description",
        durationSeconds = 60,
        license = "CC0",
        attribution = "Test",
        sourceUrl = "https://example.org",
        category = AdhanCategory.MAKKAH,
        isBundled = false,
        downloadUrl = "https://upload.wikimedia.org/wikipedia/commons/$id.ogg",
        fileExtension = "ogg",
    )

    private fun blockingBody(
        started: CountDownLatch,
        release: CountDownLatch,
        call: okhttp3.Call,
        firstChunkBytes: Int = 4096,
    ): ResponseBody = object : ResponseBody() {
        override fun contentType() = "audio/ogg".toMediaType()
        override fun contentLength() = 20_000L
        override fun source(): BufferedSource = object : Source {
            private var sent = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (call.isCanceled()) throw IOException("Canceled")
                if (!sent) {
                    sent = true
                    sink.write(ByteArray(firstChunkBytes) { 1 })
                    return firstChunkBytes.toLong()
                }
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                if (call.isCanceled()) throw IOException("Canceled")
                return -1L
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }.buffer()
    }

    private fun okResponse(chain: Interceptor.Chain, payload: ByteArray): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(payload.toResponseBody("audio/ogg".toMediaType()))
            .build()

    private fun errorResponse(chain: Interceptor.Chain, code: Int): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
            .build()

    private fun hasAnyPartFiles(dir: File): Boolean =
        dir.listFiles()?.any { it.name.endsWith(".part") } == true

    @Test
    fun cancelDownloadCancelsActiveCallCleansPartFileAndNeverReportsFailedOrDownloaded() = runBlocking {
        val started = CountDownLatch(1)
        val allowFinish = CountDownLatch(1)
        val networkCalls = AtomicInteger(0)
        val observedStates = Collections.synchronizedList(mutableListOf<AdhanDownloadState?>())

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkCalls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(blockingBody(started, allowFinish, chain.call()))
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_cancel")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )
            val collector = scope.launch {
                repo.states.collect { observedStates += it[recording.id] }
            }

            repo.download(recording)
            assertTrue(started.await(5, TimeUnit.SECONDS))

            repo.download(recording)
            assertEquals(1, networkCalls.get())

            repo.cancelDownload(recording.id)
            allowFinish.countDown()
            repo.awaitPendingOperation(recording.id)

            assertFalse(repo.isDownloadActive(recording.id))
            assertNull(repo.states.value[recording.id])
            assertTrue(observedStates.none { it is AdhanDownloadState.Failed || it is AdhanDownloadState.Downloaded })
            assertFalse(hasAnyPartFiles(audioDir))
            assertFalse(File(audioDir, "${recording.id}.${recording.fileExtension}").exists())
            collector.cancel()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun retryAfterCancellationDownloadsCleanlyToFinalFile() = runBlocking {
        val attempt = AtomicInteger(0)
        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val secondPayload = ByteArray(2048) { 5 }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val n = attempt.incrementAndGet()
                if (n == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(blockingBody(firstStarted, firstRelease, chain.call()))
                        .build()
                } else {
                    okResponse(chain, secondPayload)
                }
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_retry")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )

            repo.download(recording)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            repo.cancelDownload(recording.id)
            repo.download(recording)
            firstRelease.countDown()
            repo.awaitPendingOperation(recording.id)

            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            assertArrayEquals(secondPayload, repo.downloadedFile(recording.id)!!.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun downloadDeleteDownloadHidesOldFileThroughoutAndCompletesWithNewPayload() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_dl_del_dl").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 11 }
        val payloadV2 = ByteArray(2048) { 22 }
        val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
        finalFile.writeBytes(payloadV1)

        val attempt = AtomicInteger(0)
        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondRelease = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val n = attempt.incrementAndGet()
                if (n == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(blockingBody(firstStarted, firstRelease, chain.call()))
                        .build()
                } else {
                    secondStarted.countDown()
                    secondRelease.await(5, TimeUnit.SECONDS)
                    okResponse(chain, payloadV2)
                }
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )

            assertTrue(repo.isDownloaded(recording))
            repo.download(recording)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            repo.deleteDownload(recording.id)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertEquals(DeleteLifecycleStatus.PENDING, repo.deleteStatusForTest(recording.id))

            repo.download(recording)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertEquals(DeleteLifecycleStatus.PENDING, repo.deleteStatusForTest(recording.id))
            assertTrue(finalFile.exists())

            firstRelease.countDown()
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertFalse(finalFile.exists())
            assertNull(repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            secondRelease.countDown()
            repo.awaitPendingOperation(recording.id)

            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            assertArrayEquals(payloadV2, repo.downloadedFile(recording.id)!!.readBytes())
            assertNull(repo.deleteStatusForTest(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteDownloadCancelDownloadChainPreservesDeleteProtectionAndCompletesLatest() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_del_dl_cancel_dl").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 31 }
        val payloadV3 = ByteArray(2048) { 33 }
        File(audioDir, "${recording.id}.${recording.fileExtension}").writeBytes(payloadV1)

        val deleteReached = CompletableDeferred<Unit>()
        val allowDelete = CompletableDeferred<Unit>()
        val networkCalls = AtomicInteger(0)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkCalls.incrementAndGet()
                okResponse(chain, payloadV3)
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                onBeforeDeleteFile = {
                    deleteReached.complete(Unit)
                    allowDelete.await()
                },
            )

            repo.deleteDownload(recording.id)
            deleteReached.await()
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            repo.download(recording)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            repo.cancelDownload(recording.id)
            assertNull(repo.states.value[recording.id])
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            repo.download(recording)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            allowDelete.complete(Unit)
            repo.awaitPendingOperation(recording.id)

            assertEquals(1, networkCalls.get())
            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            assertArrayEquals(payloadV3, repo.downloadedFile(recording.id)!!.readBytes())
            assertNull(repo.deleteStatusForTest(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun downloadCancelDeleteDownloadChainCleansDiskAndCompletesLatest() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_dl_cancel_del_dl").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 41 }
        val payloadV2 = ByteArray(2048) { 42 }
        File(audioDir, "${recording.id}.${recording.fileExtension}").writeBytes(payloadV1)

        val attempt = AtomicInteger(0)
        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val n = attempt.incrementAndGet()
                if (n == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(blockingBody(firstStarted, firstRelease, chain.call()))
                        .build()
                } else {
                    okResponse(chain, payloadV2)
                }
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )

            repo.download(recording)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            repo.cancelDownload(recording.id)
            repo.deleteDownload(recording.id)
            repo.download(recording)

            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            firstRelease.countDown()
            repo.awaitPendingOperation(recording.id)

            assertEquals(2, attempt.get())
            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            assertArrayEquals(payloadV2, repo.downloadedFile(recording.id)!!.readBytes())
            assertNull(repo.deleteStatusForTest(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun olderDeleteCompletionDoesNotClearNewerDeleteTrackerOwnership() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_double_delete").apply { mkdirs() }
        val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
        finalFile.writeBytes(ByteArray(2048) { 51 })

        val delete1Entered = CompletableDeferred<Unit>()
        val allowDelete1 = CompletableDeferred<Unit>()
        val statusRightAfterDelete1 = CompletableDeferred<DeleteLifecycleStatus?>()
        val deleteCalls = AtomicInteger(0)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient.Builder().addInterceptor { okResponse(it, ByteArray(2048) { 52 }) }.build(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                onBeforeDeleteFile = { file ->
                    if (deleteCalls.incrementAndGet() == 1) {
                        delete1Entered.complete(Unit)
                        allowDelete1.await()
                    }
                    assertTrue(file.name.startsWith("${recording.id}."))
                },
            )

            repo.deleteDownload(recording.id)
            delete1Entered.await()
            val delete1Job = repo.currentOperationJobForTest(recording.id)!!
            delete1Job.invokeOnCompletion {
                statusRightAfterDelete1.complete(repo.deleteStatusForTest(recording.id))
            }

            repo.download(recording)
            repo.deleteDownload(recording.id)
            assertEquals(DeleteLifecycleStatus.PENDING, repo.deleteStatusForTest(recording.id))

            allowDelete1.complete(Unit)
            assertEquals(DeleteLifecycleStatus.PENDING, statusRightAfterDelete1.await())

            repo.awaitPendingOperation(recording.id)

            assertNull(repo.deleteStatusForTest(recording.id))
            assertNull(repo.states.value[recording.id])
            assertFalse(repo.isDownloaded(recording))
            assertFalse(finalFile.exists())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteCancelledBeforeEnteringBodyRetainsCancelledTrackerAndHidesOldFile() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_del_cancel_prebody").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 61 }
        val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
        finalFile.writeBytes(payloadV1)

        val firstStarted = CountDownLatch(1)
        val firstRelease = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(blockingBody(firstStarted, firstRelease, chain.call()))
                    .build()
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )

            repo.download(recording)
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

            repo.deleteDownload(recording.id)
            val deleteJob = repo.currentOperationJobForTest(recording.id)!!
            deleteJob.cancel(CancellationException("cancelled before delete body"))
            firstRelease.countDown()
            deleteJob.join()

            assertEquals(DeleteLifecycleStatus.CANCELLED, repo.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repo.states.value[recording.id])
            assertTrue(finalFile.exists())
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteCancelledDuringCleanupRetainsCancelledTrackerAndHidesRemainingFile() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_del_cancel_mid").apply { mkdirs() }
        val partFile = File(audioDir, "${recording.id}.1.part").apply { writeBytes(ByteArray(2048) { 71 }) }
        val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}").apply {
            writeBytes(ByteArray(2048) { 72 })
        }

        val secondFileReached = CompletableDeferred<Unit>()
        val allowSecondFile = CompletableDeferred<Unit>()
        val seenCount = AtomicInteger(0)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                onBeforeDeleteFile = {
                    if (seenCount.incrementAndGet() == 2) {
                        secondFileReached.complete(Unit)
                        allowSecondFile.await()
                    }
                },
            )

            repo.deleteDownload(recording.id)
            secondFileReached.await()
            val deleteJob = repo.currentOperationJobForTest(recording.id)!!
            deleteJob.cancel(CancellationException("cancelled mid cleanup"))
            allowSecondFile.complete(Unit)
            deleteJob.join()

            assertEquals(DeleteLifecycleStatus.CANCELLED, repo.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repo.states.value[recording.id])
            val remainingCount = listOf(partFile, finalFile).count { it.exists() }
            assertEquals(1, remainingCount)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun fileDeleteReturningFalseAndPartialDeletionRetainFailedTrackerAndHideFiles() = runBlocking {
        val audioDirA = File(tempFolder.root, "adhan_del_false").apply { mkdirs() }
        val finalFileA = File(audioDirA, "${recording.id}.${recording.fileExtension}")
        finalFileA.writeBytes(ByteArray(2048) { 81 })

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repoA = AdhanDownloadRepository(
                audioDirProvider = { audioDirA },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                deleteFile = { false },
            )

            repoA.deleteDownload(recording.id)
            repoA.awaitPendingOperation(recording.id)

            assertEquals(DeleteLifecycleStatus.FAILED, repoA.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repoA.states.value[recording.id])
            assertTrue(finalFileA.exists())
            assertFalse(repoA.isDownloaded(recording))
            assertNull(repoA.downloadedFile(recording.id))

            val audioDirB = File(tempFolder.root, "adhan_del_partial").apply { mkdirs() }
            val partFileB = File(audioDirB, "${recording.id}.9.part").apply { writeBytes(ByteArray(2048) { 82 }) }
            val finalFileB = File(audioDirB, "${recording.id}.${recording.fileExtension}").apply {
                writeBytes(ByteArray(2048) { 83 })
            }

            val repoB = AdhanDownloadRepository(
                audioDirProvider = { audioDirB },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                deleteFile = { file ->
                    if (file.name == finalFileB.name) {
                        throw SecurityException("simulated permission failure")
                    } else {
                        file.delete()
                    }
                },
            )

            repoB.deleteDownload(recording.id)
            repoB.awaitPendingOperation(recording.id)

            assertFalse(partFileB.exists())
            assertTrue(finalFileB.exists())
            assertEquals(DeleteLifecycleStatus.FAILED, repoB.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repoB.states.value[recording.id])
            assertFalse(repoB.isDownloaded(recording))
            assertNull(repoB.downloadedFile(recording.id))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun subsequentDownloadRecoversOnlyAfterVerifiedReplacementAndKeepsOldFileHiddenOnFailure() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_recover").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 91 }
        val payloadV2 = ByteArray(2048) { 92 }
        val finalFile = File(audioDir, "${recording.id}.${recording.fileExtension}")
        finalFile.writeBytes(payloadV1)

        val failDelete = AtomicBoolean(true)
        val failHttp = AtomicBoolean(true)
        val recoveryStarted = CountDownLatch(1)
        val allowRecoveryFinish = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                if (failHttp.get()) {
                    errorResponse(chain, 500)
                } else {
                    recoveryStarted.countDown()
                    allowRecoveryFinish.await(5, TimeUnit.SECONDS)
                    okResponse(chain, payloadV2)
                }
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                deleteFile = { file ->
                    if (failDelete.get() && file.name == finalFile.name) {
                        false
                    } else {
                        file.delete()
                    }
                },
            )

            repo.deleteDownload(recording.id)
            repo.awaitPendingOperation(recording.id)
            assertEquals(DeleteLifecycleStatus.FAILED, repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)
            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertEquals(DeleteLifecycleStatus.FAILED, repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            failHttp.set(false)
            failDelete.set(false)
            repo.download(recording)
            assertTrue(recoveryStarted.await(5, TimeUnit.SECONDS))
            assertEquals(DeleteLifecycleStatus.FAILED, repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            allowRecoveryFinish.countDown()
            repo.awaitPendingOperation(recording.id)

            assertNull(repo.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            assertArrayEquals(payloadV2, repo.downloadedFile(recording.id)!!.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun deleteTargetFileCancellationExceptionPropagatesRetainsCancelledMarkerAndRecoversSafely() = runBlocking {
        val audioDirA = File(tempFolder.root, "adhan_del_ce_delete").apply { mkdirs() }
        val payloadV1 = ByteArray(2048) { 101 }
        val payloadV2 = ByteArray(2048) { 102 }
        val finalFileA = File(audioDirA, "${recording.id}.${recording.fileExtension}")
        finalFileA.writeBytes(payloadV1)

        val throwCancelOnDelete = AtomicBoolean(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repoA = AdhanDownloadRepository(
                audioDirProvider = { audioDirA },
                client = OkHttpClient.Builder().addInterceptor { okResponse(it, payloadV2) }.build(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                deleteFile = { file ->
                    if (throwCancelOnDelete.get() && file.name == finalFileA.name) {
                        throw CancellationException("deleteFile seam cancelled")
                    }
                    file.delete()
                },
            )

            repoA.deleteDownload(recording.id)
            val deleteJobA = repoA.currentOperationJobForTest(recording.id)!!
            deleteJobA.join()

            assertTrue(deleteJobA.isCancelled)
            assertEquals(DeleteLifecycleStatus.CANCELLED, repoA.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repoA.states.value[recording.id])
            assertTrue(finalFileA.exists())
            assertFalse(repoA.isDownloaded(recording))
            assertNull(repoA.downloadedFile(recording.id))

            throwCancelOnDelete.set(false)
            repoA.download(recording)
            repoA.awaitPendingOperation(recording.id)

            assertNull(repoA.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.Downloaded, repoA.states.value[recording.id])
            assertTrue(repoA.isDownloaded(recording))
            assertArrayEquals(payloadV2, repoA.downloadedFile(recording.id)!!.readBytes())

            val audioDirB = File(tempFolder.root, "adhan_del_ce_exists").apply { mkdirs() }
            val finalFileB = File(audioDirB, "${recording.id}.${recording.fileExtension}")
            finalFileB.writeBytes(payloadV1)
            val throwCancelOnExists = AtomicBoolean(true)

            val repoB = AdhanDownloadRepository(
                audioDirProvider = { audioDirB },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
                fileExists = { file ->
                    if (throwCancelOnExists.get() && file.name == finalFileB.name) {
                        throw CancellationException("fileExists seam cancelled")
                    }
                    file.exists()
                },
            )

            repoB.deleteDownload(recording.id)
            val deleteJobB = repoB.currentOperationJobForTest(recording.id)!!
            deleteJobB.join()

            assertTrue(deleteJobB.isCancelled)
            assertEquals(DeleteLifecycleStatus.CANCELLED, repoB.deleteStatusForTest(recording.id))
            assertEquals(AdhanDownloadState.NotDownloaded, repoB.states.value[recording.id])
            assertTrue(finalFileB.exists())
            assertFalse(repoB.isDownloaded(recording))
            assertNull(repoB.downloadedFile(recording.id))

            throwCancelOnExists.set(false)
            repoB.deleteDownload(recording.id)
            repoB.awaitPendingOperation(recording.id)

            assertNull(repoB.deleteStatusForTest(recording.id))
            assertNull(repoB.states.value[recording.id])
            assertFalse(finalFileB.exists())
            assertFalse(repoB.isDownloaded(recording))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun independentRecordingsReadOnlyLookupsAndNormalFailureRetryWorkCleanly() = runBlocking {
        val nonExistentDir = File(tempFolder.root, "adhan_nonexistent")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val readOnlyRepo = AdhanDownloadRepository(
                audioDirProvider = { nonExistentDir },
                client = OkHttpClient(),
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )
            assertFalse(readOnlyRepo.isDownloaded(recording))
            assertNull(readOnlyRepo.downloadedFile(recording.id))
            assertFalse(nonExistentDir.exists())

            val audioDir = File(tempFolder.root, "adhan_independent").apply { mkdirs() }
            File(audioDir, "${recording.id}.77.part").writeBytes(ByteArray(4096) { 1 })
            assertFalse(
                AdhanDownloadRepository(
                    audioDirProvider = { audioDir },
                    client = OkHttpClient(),
                    scope = scope,
                    ioDispatcher = Dispatchers.IO,
                    minValidBytes = 1024L,
                ).downloadedFile(recording.id) != null,
            )

            val blockedA = CountDownLatch(1)
            val releaseA = CountDownLatch(1)
            val payloadA = ByteArray(2048) { 111 }
            val payloadB = ByteArray(2048) { 112 }
            val aAttempts = AtomicInteger(0)

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.toString()
                    if (url.contains(recording.id)) {
                        when (aAttempts.incrementAndGet()) {
                            1 -> Response.Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(blockingBody(blockedA, releaseA, chain.call()))
                                .build()

                            2 -> errorResponse(chain, 503)
                            else -> okResponse(chain, payloadA)
                        }
                    } else {
                        okResponse(chain, payloadB)
                    }
                }
                .build()

            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1024L,
            )

            repo.download(recording)
            assertTrue(blockedA.await(5, TimeUnit.SECONDS))

            repo.download(otherRecording)
            repo.awaitPendingOperation(otherRecording.id)
            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[otherRecording.id])
            assertTrue(repo.isDownloaded(otherRecording))
            assertArrayEquals(payloadB, repo.downloadedFile(otherRecording.id)!!.readBytes())
            assertTrue(repo.isDownloadActive(recording.id))

            repo.cancelDownload(recording.id)
            releaseA.countDown()
            repo.awaitPendingOperation(recording.id)

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)
            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertFalse(hasAnyPartFiles(audioDir))

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)
            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertNotNull(repo.downloadedFile(recording.id))
            assertArrayEquals(payloadA, repo.downloadedFile(recording.id)!!.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    private fun customAdhanBody(
        declaredLength: Long,
        payload: ByteArray,
        readInvoked: AtomicBoolean? = null,
    ): ResponseBody = object : ResponseBody() {
        override fun contentType() = "audio/ogg".toMediaType()
        override fun contentLength() = declaredLength
        override fun source(): BufferedSource = object : Source {
            private var offset = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                readInvoked?.set(true)
                if (offset >= payload.size) return -1L
                val count = minOf(byteCount, (payload.size - offset).toLong()).toInt()
                sink.write(payload, offset, count)
                offset += count
                return count.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }.buffer()
    }

    @Test
    fun truncatedAdhanAboveMinValidBytesIsRejectedAndRetrySucceedsWithoutOverwritingValidFile() = runBlocking {
        val mode = AtomicInteger(0)
        val fullPayload = ByteArray(4_096) { 0x31 }
        val truncatedPayload = ByteArray(2_048) { 0x19 }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = when (mode.get()) {
                    0 -> customAdhanBody(declaredLength = 4_096L, payload = truncatedPayload)
                    1 -> customAdhanBody(declaredLength = 4_096L, payload = fullPayload)
                    else -> customAdhanBody(declaredLength = 8_192L, payload = truncatedPayload)
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_truncated_retry")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(File(audioDir, "${recording.id}.ogg").exists())
            assertFalse(hasAnyPartFiles(audioDir))

            mode.set(1)
            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            val downloaded = repo.downloadedFile(recording.id)
            assertNotNull(downloaded)
            assertEquals(4_096L, downloaded!!.length())
            assertArrayEquals(fullPayload, downloaded.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))

            mode.set(2)
            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            val preserved = File(audioDir, "${recording.id}.ogg")
            assertTrue(preserved.isFile)
            assertArrayEquals(fullPayload, preserved.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun overReadBeyondDeclaredContentLengthIsRejectedAndCleansPart() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_over_read")
        val readCalls = AtomicInteger(0)
        val partBytesAtClose = AtomicLong(-1L)
        val chunks = listOf(
            ByteArray(2_048) { 0x41 },
            ByteArray(1_024) { 0x42 },
            ByteArray(1_024) { 0x43 },
            ByteArray(1_024) { 0x44 },
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = object : ResponseBody() {
                    private val cachedSource = object : Source {
                        private var index = 0
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            readCalls.incrementAndGet()
                            if (index >= chunks.size) return -1L
                            val chunk = chunks[index++]
                            sink.write(chunk)
                            return chunk.size.toLong()
                        }

                        override fun timeout(): Timeout = Timeout.NONE
                        override fun close() {
                            val part = audioDir.listFiles()?.singleOrNull { it.name.endsWith(".part") }
                            partBytesAtClose.compareAndSet(-1L, part?.length() ?: -1L)
                        }
                    }.buffer()

                    override fun contentType() = "audio/ogg".toMediaType()
                    override fun contentLength() = 2_048L
                    override fun source(): BufferedSource = cachedSource
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertEquals(2, readCalls.get())
            assertEquals(2_048L, partBytesAtClose.get())
            assertFalse(File(audioDir, "${recording.id}.ogg").exists())
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun explicitZeroContentLengthIsRejectedAndCleansPart() = runBlocking {
        val readInvoked = AtomicBoolean(false)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        customAdhanBody(
                            declaredLength = 0L,
                            payload = ByteArray(2_048) { 0x4E },
                            readInvoked = readInvoked,
                        ),
                    )
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_zero_len")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertFalse(readInvoked.get())
            assertFalse(File(audioDir, "${recording.id}.ogg").exists())
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun declaredContentLengthBelowMinValidBytesFailsFastWithoutReadingStream() = runBlocking {
        val readInvoked = AtomicBoolean(false)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        customAdhanBody(
                            declaredLength = 512L,
                            payload = ByteArray(512) { 0x51 },
                            readInvoked = readInvoked,
                        ),
                    )
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_fail_fast_small")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertFalse(readInvoked.get())
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unknownContentLengthAboveMinValidBytesSucceeds() = runBlocking {
        val payload = ByteArray(2_048) { 0x61 }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(customAdhanBody(declaredLength = -1L, payload = payload))
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_unknown_len_ok")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertTrue(repo.isDownloaded(recording))
            val file = repo.downloadedFile(recording.id)
            assertNotNull(file)
            assertEquals(2_048L, file!!.length())
            assertArrayEquals(payload, file.readBytes())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unknownContentLengthBelowMinValidBytesIsRejected() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(customAdhanBody(declaredLength = -1L, payload = ByteArray(512) { 0x71 }))
                    .build()
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_unknown_len_small")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun truncatedRecoveryDownloadAfterFailedDeletePreservesDeleteTrackerAndHidesOldFile() = runBlocking {
        val audioDir = File(tempFolder.root, "adhan_truncated_after_failed_delete").apply { mkdirs() }
        val oldPayload = ByteArray(2_048) { 0x0A }
        val existingFile = File(audioDir, "${recording.id}.ogg").apply { writeBytes(oldPayload) }
        val allowDelete = AtomicBoolean(false)
        val returnFull = AtomicBoolean(false)
        val fullPayload = ByteArray(4_096) { 0x5B }
        val truncatedPayload = ByteArray(2_048) { 0x2C }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = if (returnFull.get()) {
                    customAdhanBody(declaredLength = 4_096L, payload = fullPayload)
                } else {
                    customAdhanBody(declaredLength = 4_096L, payload = truncatedPayload)
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
                deleteFile = { file ->
                    if (file.name == existingFile.name && !allowDelete.get()) {
                        false
                    } else {
                        file.delete()
                    }
                },
            )

            repo.deleteDownload(recording.id)
            repo.awaitPendingOperation(recording.id)

            assertEquals(DeleteLifecycleStatus.FAILED, repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))

            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertTrue(repo.states.value[recording.id] is AdhanDownloadState.Failed)
            assertEquals(DeleteLifecycleStatus.FAILED, repo.deleteStatusForTest(recording.id))
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertArrayEquals(oldPayload, existingFile.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))

            allowDelete.set(true)
            returnFull.set(true)
            repo.download(recording)
            repo.awaitPendingOperation(recording.id)

            assertEquals(AdhanDownloadState.Downloaded, repo.states.value[recording.id])
            assertNull(repo.deleteStatusForTest(recording.id))
            assertTrue(repo.isDownloaded(recording))
            val recovered = repo.downloadedFile(recording.id)
            assertNotNull(recovered)
            assertArrayEquals(fullPayload, recovered!!.readBytes())
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelProducingEarlyEofClearsStateAndNeverReportsTruncationFailure() = runBlocking {
        val firstChunkWritten = CountDownLatch(1)
        val allowEof = CountDownLatch(1)
        val observedStates = Collections.synchronizedList(mutableListOf<AdhanDownloadState?>())

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = object : ResponseBody() {
                    override fun contentType() = "audio/ogg".toMediaType()
                    override fun contentLength() = 20_000L
                    override fun source(): BufferedSource = object : Source {
                        private var sent = false
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            if (!sent) {
                                sent = true
                                sink.write(ByteArray(2_048) { 0x33 })
                                return 2_048L
                            }
                            firstChunkWritten.countDown()
                            allowEof.await(5, TimeUnit.SECONDS)
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
            }
            .build()

        val audioDir = File(tempFolder.root, "adhan_cancel_early_eof")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AdhanDownloadRepository(
                audioDirProvider = { audioDir },
                client = client,
                scope = scope,
                ioDispatcher = Dispatchers.IO,
                minValidBytes = 1_024L,
            )

            val collector = scope.launch {
                repo.states.collect { map -> observedStates.add(map[recording.id]) }
            }

            repo.download(recording)
            assertTrue(firstChunkWritten.await(5, TimeUnit.SECONDS))

            repo.cancelDownload(recording.id)
            allowEof.countDown()
            repo.awaitPendingOperation(recording.id)
            collector.cancel()

            assertNull(repo.states.value[recording.id])
            assertTrue(observedStates.none { it is AdhanDownloadState.Failed })
            assertFalse(repo.isDownloaded(recording))
            assertNull(repo.downloadedFile(recording.id))
            assertFalse(hasAnyPartFiles(audioDir))
        } finally {
            scope.cancel()
        }
    }
}
