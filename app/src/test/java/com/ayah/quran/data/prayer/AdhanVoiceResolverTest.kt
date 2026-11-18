package com.ayah.quran.data.prayer

import com.ayah.quran.domain.model.prayer.AdhanVoiceDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Voice resolution rules: stored id → recording, unknown/missing id →
 * DEFAULT recording (never silence), user-file pseudo-id → handled by the
 * caller (null here).
 */
class AdhanVoiceResolverTest {

    private val catalog = BundledAdhanCatalog()
    private val resolver = AdhanVoiceResolver(catalog)

    @Test
    fun `null voice resolves to the default recording`() {
        val recording = resolver.recordingFor(null)
        assertEquals(AdhanVoiceDefaults.DEFAULT_RECORDING_ID, recording?.id)
    }

    @Test
    fun `unknown voice id falls back to the default recording`() {
        val recording = resolver.recordingFor("recording_deleted_in_update")
        assertEquals(AdhanVoiceDefaults.DEFAULT_RECORDING_ID, recording?.id)
    }

    @Test
    fun `every catalog id resolves to itself`() {
        catalog.recordings.forEach { recording ->
            assertEquals(recording, resolver.recordingFor(recording.id))
        }
    }

    @Test
    fun `user-file pseudo-id is deferred to the caller`() {
        assertNull(
            resolver.recordingFor(AdhanVoiceDefaults.USER_FILE_ID),
        )
    }

    @Test
    fun `default detection covers null and the default id`() {
        assertTrue(resolver.isDefault(null))
        assertTrue(resolver.isDefault(AdhanVoiceDefaults.DEFAULT_RECORDING_ID))
        assertFalse(resolver.isDefault("adhan_pd_sabah_fakhri"))
    }

    @Test
    fun `resolveAll covers all five prayers`() {
        val all = resolver.resolveAll { "adhan_pd_sabah_fakhri" }
        assertEquals(5, all.size)
        all.values.forEach { recording ->
            assertEquals("adhan_pd_sabah_fakhri", recording?.id)
        }
        assertTrue(all.values.all { it != null })
    }
}
