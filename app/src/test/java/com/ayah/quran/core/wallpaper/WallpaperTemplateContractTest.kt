package com.ayah.quran.core.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract for the five launch templates (structured data, not code). */
class WallpaperTemplateContractTest {

    @Test
    fun `exposes exactly the five premium templates`() {
        assertEquals(
            listOf(
                WallpaperTemplate.EMERALD_LUXURY_ID,
                WallpaperTemplate.MINIMAL_CREAM_ID,
                WallpaperTemplate.MIDNIGHT_GOLD_ID,
                WallpaperTemplate.ISLAMIC_ARCHITECTURE_ID,
                WallpaperTemplate.GEOMETRIC_ART_ID,
            ),
            WallpaperTemplate.ALL.map { it.id },
        )
    }

    @Test
    fun `template ids are unique`() {
        assertEquals(WallpaperTemplate.ALL.size, WallpaperTemplate.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `byId falls back to the first template for unknown ids`() {
        assertEquals(WallpaperTemplate.ALL.first(), WallpaperTemplate.byId("does_not_exist"))
        val midnight = WallpaperTemplate.ALL.first { it.id == WallpaperTemplate.MIDNIGHT_GOLD_ID }
        assertEquals(midnight, WallpaperTemplate.byId(midnight.id))
    }

    @Test
    fun `all templates use fully opaque readable colors`() {
        WallpaperTemplate.ALL.forEach { template ->
            assertEquals("text color must be opaque", 0xFF, template.textColor ushr 24)
            assertEquals("reference color must be opaque", 0xFF, template.referenceColor ushr 24)
            // Text and reference colors must differ enough to be distinguishable.
            assertTrue(
                "text and reference colors identical in ${template.id}",
                template.textColor != template.referenceColor || template.referenceColor == template.accentColor,
            )
        }
    }

    @Test
    fun `safe content area keeps text away from system UI zones`() {
        WallpaperTemplate.ALL.forEach { template ->
            val insets = template.contentInsets
            assertTrue("top inset too small in ${template.id}", insets.topFraction >= 0.10f)
            assertTrue("bottom inset too small in ${template.id}", insets.bottomFraction >= 0.08f)
            assertTrue("horizontal inset too small in ${template.id}", insets.horizontalFraction >= 0.06f)
            assertTrue(insets.topFraction + insets.bottomFraction < 0.5f)
        }
    }

    @Test
    fun `base text size produces readable verses on common screens`() {
        // On a 2400px-tall screen the base size must land in a readable range.
        WallpaperTemplate.ALL.forEach { template ->
            val size = template.baseTextSizeFraction * 2400f
            assertTrue("base text too small in ${template.id}", size >= 100f)
            assertTrue("base text too large in ${template.id}", size <= 180f)
        }
    }
}
