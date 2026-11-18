package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog invariants for the BUILT-IN adhan recordings: stable unique ids,
 * a default that exists, complete recordings (not samples), licensing and
 * attribution present, and res/raw-name-safe ids. The licensing decision per
 * recording is documented in docs/ADHAN-AUDIO-LICENSES.md.
 */
class BundledAdhanCatalogTest {

    private val catalog = BundledAdhanCatalog()

    @Test
    fun `ships at least four distinct recordings`() {
        assertTrue(catalog.recordings.size >= 4)
    }

    @Test
    fun `recording ids are unique and res-raw safe`() {
        val ids = catalog.recordings.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { id ->
            assertTrue(
                "id must be res/raw safe: $id",
                id.matches(Regex("[a-z0-9_]+")),
            )
        }
    }

    @Test
    fun `default recording exists and is the CC0 public-dedication one`() {
        val default = catalog.defaultRecording
        assertEquals(AdhanVoiceDefaults.DEFAULT_RECORDING_ID, default.id)
        assertEquals("CC0 1.0 Universal", default.license)
    }

    @Test
    fun `every recording is a COMPLETE adhan with licensing and attribution`() {
        catalog.recordings.forEach { recording ->
            // The complete standard adhan runs ~2.5–3.5 minutes; anything
            // under 2 minutes would be a sample, which Phase 13 forbids.
            assertTrue(
                "${recording.id} looks too short (${recording.durationSeconds}s)",
                recording.durationSeconds >= 120,
            )
            assertTrue(recording.license.isNotBlank())
            assertTrue(recording.attribution.isNotBlank())
            assertTrue(recording.sourceUrl.startsWith("https://"))
            assertTrue(recording.displayNameAr.isNotBlank())
            assertTrue(recording.displayNameEn.isNotBlank())
        }
    }

    @Test
    fun `byId resolves exactly the catalog entries`() {
        catalog.recordings.forEach { recording ->
            assertEquals(recording, catalog.byId(recording.id))
        }
        assertEquals(null, catalog.byId("missing_recording"))
    }

    @Test
    fun `recordings are distinct audio sources`() {
        val urls = catalog.recordings.map { it.sourceUrl }
        assertEquals(urls.size, urls.toSet().size)
    }
}
