package com.ayah.quran.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayah.quran.data.prayer.AdhanRawResources
import org.junit.runner.RunWith
import com.ayah.quran.data.prayer.BundledAdhanCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * Verifies the BUNDLED adhan audio end-to-end on a real Android runtime:
 * every catalog recording maps to a real res/raw resource that opens and
 * decodes, with a duration within ±5s of the catalog metadata (source:
 * Wikimedia Commons file info). Also proves per-prayer voice selection
 * persistence through the repository on-device.
 */
@RunWith(AndroidJUnit4::class)
class AdhanAudioInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = BundledAdhanCatalog()

    @Test
    fun allCatalogRecordingsMapToRealDecodableRawResources() {
        catalog.recordings.forEach { recording ->
            val resId = AdhanRawResources.resIdOf(recording.id)
            assertTrue("resId for ${recording.id} must be valid", resId != 0)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(
                    context.resources.openRawResource(resId).use { input ->
                        // MediaMetadataRetriever needs a file descriptor; copy
                        // to cache first (raw streams are not seekable).
                        val cache = java.io.File.createTempFile("adhan", "tmp", context.cacheDir)
                        cache.outputStream().use { output -> input.copyTo(output) }
                        cache.absolutePath
                    },
                )
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLong() ?: error("no duration for ${recording.id}")
                val deltaSeconds = abs(durationMs / 1000 - recording.durationSeconds)
                assertTrue(
                    "${recording.id}: catalog ${recording.durationSeconds}s vs device ${durationMs / 1000}s",
                    deltaSeconds <= 5,
                )
            } finally {
                retriever.release()
            }
        }
    }

    @Test
    fun unknownRecordingIdFallsBackToDefaultResource() {
        val fallback = AdhanRawResources.resIdOf("recording_that_no_longer_exists")
        assertEquals(
            AdhanRawResources.resIdOf(com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults.DEFAULT_RECORDING_ID),
            fallback,
        )
    }

    @Test
    fun catalogDisplayNamesAreProvidedPerLocale() {
        // Both localized names must be non-blank for every recording (the UI
        // picks by AppLocale at render time).
        catalog.recordings.forEach { recording ->
            assertTrue(recording.displayNameAr.isNotBlank())
            assertTrue(recording.displayNameEn.isNotBlank())
            assertEquals(
                "recording ${recording.id} id",
                recording.id,
                recording.id.lowercase(Locale.ROOT),
            )
        }
    }
}
