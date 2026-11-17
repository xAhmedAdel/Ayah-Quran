package com.ayah.quran.core.wallpaper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ayah.quran.data.quran.QuranImporter
import com.ayah.quran.core.database.AyahDatabase
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders REAL dataset verses on-device and validates dimensions, layout
 * fit, Arabic text presence, and the shrink-fit contract across aspect
 * ratios and all five templates.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperRendererInstrumentedTest {

    private lateinit var database: AyahDatabase
    private lateinit var renderer: WallpaperRenderer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AyahDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        QuranImporter(
            context = context,
            database = database,
            parser = com.ayah.quran.data.quran.QuranTextParser(),
            appScope = CoroutineScope(Dispatchers.IO),
        ).let { importer ->
            runBlocking { importer.importIfNeeded() }
        }
        renderer = WallpaperRenderer(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun ayahText(surahId: Int, ayahNumber: Int): String =
        runBlocking { database.ayahDao().getAyah(surahId, ayahNumber)?.textUthmani }
            ?: error("verse $surahId:$ayahNumber missing after import")

    private val shortVerse get() = ayahText(1, 1) // Al-Fatiha 1:1
    private val longestVerse get() = ayahText(2, 282) // Al-Baqarah 2:282 (the longest)

    @Test
    fun rendersAtRequestedDimensions() {
        val result = renderer.render(
            renderParams(shortVerse, width = 1080, height = 2400, template = WallpaperTemplate.ALL.first()),
        )
        assertEquals(1080, result.bitmap.width)
        assertEquals(2400, result.bitmap.height)
        result.bitmap.recycle()
    }

    @Test
    fun rendersOnAllFiveTemplates() {
        WallpaperTemplate.ALL.forEach { template ->
            val result = renderer.render(renderParams(shortVerse, 1080, 2400, template))
            assertTrue("no text lines rendered for ${template.id}", result.textLineCount >= 1)
            result.bitmap.recycle()
        }
    }

    @Test
    fun longVerseIsShrunkToFitWithoutOverflow() {
        val template = WallpaperTemplate.ALL.first()
        val baseSize = (2400 * template.baseTextSizeFraction).toInt()
        val floor = (2400 * 0.012f).toInt()
        val result = renderer.render(renderParams(longestVerse, 1080, 2400, template))
        // The shrink pass must converge inside the floor..base range and wrap
        // the longest verse over multiple lines.
        assertTrue(
            "used text size ${result.usedTextSizePx}px outside [$floor, $baseSize]",
            result.usedTextSizePx in floor..baseSize,
        )
        assertTrue("the longest verse must wrap over multiple lines", result.textLineCount > 1)
    }

    @Test
    fun shortVerseUsesLargerTextThanLongVerse() {
        val template = WallpaperTemplate.ALL.first()
        val shortResult = renderer.render(renderParams(shortVerse, 1080, 2400, template))
        val longResult = renderer.render(renderParams(longestVerse, 1080, 2400, template))
        android.util.Log.d(
            "RenderDiag",
            "test: short=${shortResult.usedTextSizePx}/${shortResult.textLineCount} " +
                "long=${longResult.usedTextSizePx}/${longResult.textLineCount}",
        )
        assertTrue(
            "shrink pass must reduce size for the longest verse " +
                "(short=${shortResult.usedTextSizePx}px/${shortResult.textLineCount} lines, " +
                "long=${longResult.usedTextSizePx}px/${longResult.textLineCount} lines)",
            longResult.usedTextSizePx < shortResult.usedTextSizePx,
        )
    }

    @Test
    fun arabicTextPixelsArePresentInsideContentArea() {
        val template = WallpaperTemplate.byId(WallpaperTemplate.MINIMAL_CREAM_ID) // dark text on light background
        val result = renderer.render(renderParams(shortVerse, 1080, 2400, template))
        val bitmap = result.bitmap
        val bounds = result.contentBounds
        // Sample the content strip and count dark (text) pixels.
        var darkPixels = 0
        val samples = 20_000
        val random = kotlin.random.Random(7)
        repeat(samples) {
            val x = (bounds.left + random.nextFloat() * bounds.width()).toInt()
                .coerceIn(0, bitmap.width - 1)
            val y = (bounds.top + random.nextFloat() * bounds.height()).toInt()
                .coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(x, y)
            val luminance = ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
            if (luminance < 96) darkPixels++
        }
        // Verse text occupies a meaningful share of the sampled strip.
        assertTrue("expected visible Arabic text, only $darkPixels/$samples dark pixels", darkPixels > samples / 400)
        bitmap.recycle()
    }

    @Test
    fun adaptsToDifferentAspectRatios() {
        val template = WallpaperTemplate.ALL.first()
        listOf(
            1080 to 1920,
            1080 to 2400,
            1440 to 3120,
            768 to 1024,
        ).forEach { (width, height) ->
            val result = renderer.render(renderParams(longestVerse, width, height, template))
            assertEquals(width, result.bitmap.width)
            assertEquals(height, result.bitmap.height)
            assertTrue(result.textLineCount >= 1)
            result.bitmap.recycle()
        }
    }

    @Test
    fun rejectsInvalidDimensions() {
        try {
            renderer.render(renderParams(shortVerse, 0, 2400, WallpaperTemplate.ALL.first()))
            throw AssertionError("width 0 must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    private fun renderParams(
        text: String,
        width: Int,
        height: Int,
        template: WallpaperTemplate,
    ) = RenderParams(
        ayahText = text,
        referenceLabel = "Al-Baqara · 2:282",
        template = template,
        widthPx = width,
        heightPx = height,
        fontScale = 1.0f,
        verticalPosition = com.ayah.quran.domain.model.TextVerticalPosition.CENTER,
    )
}
