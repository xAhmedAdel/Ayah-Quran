package com.ayah.quran.data.audio

import androidx.media3.common.MediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class ExoPlayerPlaylistPreparationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val edition = QuranAudioCatalog.defaultReciter

    @Test
    fun playResolvesLocalFilesOnIoDispatcherAndStartsControllerOnMainDispatcherInOrder() {
        val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "test-main") }
        val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "test-io") }
        val mainDispatcher = mainExecutor.asCoroutineDispatcher()
        val ioDispatcher = ioExecutor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + mainDispatcher)

        val audioDir = File(tempFolder.root, "playlist_audio")
        val surahDir = File(File(audioDir, edition.editionId), "108").apply { mkdirs() }
        File(surahDir, "1.mp3").writeBytes(ByteArray(2048) { 1 })
        File(surahDir, "3.mp3").writeBytes(ByteArray(2048) { 3 })

        val repo = QuranAudioDownloadRepository(
            audioDirProvider = { audioDir },
            client = OkHttpClient(),
            scope = scope,
            ioDispatcher = ioDispatcher,
            minAyahBytes = 1_000L,
            autoScanOnInit = false,
        )

        val resolveThread = AtomicReference<String>()
        val startThread = AtomicReference<String>()
        val startedItems = AtomicReference<List<MediaItem>>()
        val startedIndex = AtomicReference<Int>()
        val startedLatch = CountDownLatch(1)

        try {
            val player = ExoPlayerAyahPlaylistPlayer(
                context = null,
                downloadRepository = repo,
                appScope = scope,
                mainDispatcher = mainDispatcher,
                ioDispatcher = ioDispatcher,
                stopControllerAction = {},
                stopServiceAction = {},
                playlistResolverOverride = { ed, surah ->
                    resolveThread.set(Thread.currentThread().name)
                    val total = QuranAudioCatalog.ayahCount(surah)
                    (1..total).map { ayah ->
                        val local = repo.localAyahFile(ed.editionId, surah, ayah)
                        val tag = if (local != null) "local:$ayah" else "remote:$ayah"
                        MediaItem.Builder().setMediaId(tag).build()
                    }
                },
                startControllerAction = { items, startIdx ->
                    startThread.set(Thread.currentThread().name)
                    startedItems.set(items)
                    startedIndex.set(startIdx)
                    startedLatch.countDown()
                },
                autoConnect = false,
            )

            player.play(edition, surah = 108, startAyahInSurah = 2)
            assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

            val stateDeadline = System.currentTimeMillis() + 2_000L
            while (!player.state.value.isPlaying && System.currentTimeMillis() < stateDeadline) {
                Thread.sleep(10)
            }

            assertTrue(resolveThread.get().startsWith("test-io"))
            assertTrue(startThread.get().startsWith("test-main"))
            assertEquals(1, startedIndex.get())
            assertEquals(
                listOf("local:1", "remote:2", "local:3"),
                startedItems.get()?.map { it.mediaId },
            )
            assertEquals(108, player.state.value.surah)
            assertEquals(1, player.state.value.ayahIndex)
            assertEquals(3, player.state.value.totalAyahs)
            assertTrue(player.state.value.isPlaying)
        } finally {
            scope.cancel()
            mainExecutor.shutdownNow()
            ioExecutor.shutdownNow()
        }
    }

    @Test
    fun concurrentPlayCancelsStalePreparationAndAppliesOnlyLatestSurah() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val surah2Gate = CompletableDeferred<Unit>()
        val startedSurahs = CopyOnWriteArrayList<Int>()

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            stopControllerAction = {},
            stopServiceAction = {},
            playlistResolverOverride = { _, surah ->
                if (surah == 2) {
                    surah2Gate.await()
                }
                val total = QuranAudioCatalog.ayahCount(surah)
                (1..total).map { ayah ->
                    MediaItem.Builder().setMediaId("$surah:$ayah").build()
                }
            },
            startControllerAction = { items, _ ->
                val surah = items.first().mediaId.substringBefore(":").toInt()
                startedSurahs.add(surah)
            },
            autoConnect = false,
        )

        player.play(edition, surah = 2, startAyahInSurah = 1)
        runCurrent()

        player.play(edition, surah = 114, startAyahInSurah = 3)
        surah2Gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(114), startedSurahs)
        assertEquals(114, player.state.value.surah)
        assertEquals(2, player.state.value.ayahIndex)
        assertEquals(6, player.state.value.totalAyahs)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun stopDuringPendingPreparationPreventsStalePlaybackStart() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val gate = CompletableDeferred<Unit>()
        val startedCalls = CopyOnWriteArrayList<Int>()

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            stopControllerAction = {},
            stopServiceAction = {},
            playlistResolverOverride = { _, surah ->
                gate.await()
                listOf(MediaItem.Builder().setMediaId("$surah:1").build())
            },
            startControllerAction = { _, _ ->
                startedCalls.add(1)
            },
            autoConnect = false,
        )

        player.play(edition, surah = 1, startAyahInSurah = 1)
        runCurrent()

        player.stop()
        gate.complete(Unit)
        runCurrent()

        assertTrue(startedCalls.isEmpty())
        assertTrue(player.state.value.isIdle)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun rapidStopFollowedByPlayPreventsDelayedStopFromCancellingNewerPlay() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val startedSurahs = CopyOnWriteArrayList<Int>()
        var stopControllerCalls = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            stopControllerAction = { stopControllerCalls++ },
            stopServiceAction = {},
            playlistResolverOverride = { _, surah ->
                val total = QuranAudioCatalog.ayahCount(surah)
                (1..total).map { ayah ->
                    MediaItem.Builder().setMediaId("$surah:$ayah").build()
                }
            },
            startControllerAction = { items, _ ->
                startedSurahs.add(items.first().mediaId.substringBefore(":").toInt())
            },
            autoConnect = false,
        )

        player.stop()
        player.play(edition, surah = 114, startAyahInSurah = 2)
        runCurrent()

        assertEquals(0, stopControllerCalls)
        assertEquals(listOf(114), startedSurahs)
        assertEquals(114, player.state.value.surah)
        assertEquals(1, player.state.value.ayahIndex)
        assertEquals(6, player.state.value.totalAyahs)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun stopClearsPendingPlaySynchronouslyBeforeControllerConnects() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            stopControllerAction = {},
            stopServiceAction = {},
            playlistResolverOverride = null,
            startControllerAction = null,
            autoConnect = false,
        )

        player.play(edition, surah = 1, startAyahInSurah = 1)
        assertTrue(player.hasPendingPlay())

        player.stop()
        assertFalse(player.hasPendingPlay())
        assertEquals(null, player.consumePendingPlay())

        runCurrent()
        assertTrue(player.state.value.isIdle)
        assertFalse(player.state.value.isPlaying)
    }

    @Test
    fun rapidStopPlayStopEndsIdleWithNoStalePlayback() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val startedSurahs = CopyOnWriteArrayList<Int>()
        var stopControllerCalls = 0

        val player = ExoPlayerAyahPlaylistPlayer(
            context = null,
            downloadRepository = null,
            appScope = backgroundScope,
            mainDispatcher = mainDispatcher,
            ioDispatcher = ioDispatcher,
            stopControllerAction = { stopControllerCalls++ },
            stopServiceAction = {},
            playlistResolverOverride = { _, surah ->
                listOf(MediaItem.Builder().setMediaId("$surah:1").build())
            },
            startControllerAction = { items, _ ->
                startedSurahs.add(items.first().mediaId.substringBefore(":").toInt())
            },
            autoConnect = false,
        )

        player.stop()
        player.play(edition, surah = 1, startAyahInSurah = 1)
        player.stop()
        runCurrent()

        assertEquals(1, stopControllerCalls)
        assertTrue(startedSurahs.isEmpty())
        assertTrue(player.state.value.isIdle)
        assertFalse(player.state.value.isPlaying)
    }
}
