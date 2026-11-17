package com.ayah.quran.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The parser must refuse ANY structurally broken dataset instead of
 * importing a partial or corrupted corpus. A miniature configuration is
 * used so line-level validation is reachable without generating a full
 * 6,236-line dataset; production uses the canonical counts.
 */
class QuranTextParserMalformedInputTest {

    private val parser = QuranTextParser(
        expectedSurahs = 1,
        expectedAyahs = 3,
        expectedJuz = 1,
        expectedPages = 1,
    )

    // One real-ish minimal VALID pair to mutate. Content strings are short
    // dataset-format samples, used only to exercise structure validation.
    private val surahMetadata = listOf(
        "<quran><suras>",
        "<sura index=\"1\" ayas=\"3\" start=\"0\" name=\"الفاتحة\" tname=\"Al-Faatiha\" ename=\"The Opening\" type=\"Makkan\" rukus=\"1\" />",
        "<juz index=\"1\" sura=\"1\" aya=\"1\" />",
        "</suras></quran>",
    )

    @Test
    fun `truncated ayah line throws`() {
        val text = listOf("1|1|سورة", "1|2") // second line missing the text field
        assertThrows(QuranParseException::class.java) {
            parser.parse(text, surahMetadata, listOf("1|1|1|1"))
        }
    }

    @Test
    fun `empty ayah text throws`() {
        val text = listOf("1|1|سورة", "1|2|")
        assertThrows(QuranParseException::class.java) {
            parser.parse(text, surahMetadata, listOf("1|1|1|1", "1|2|1|1"))
        }
    }

    @Test
    fun `sequence break throws`() {
        val text = listOf("1|1|سورة", "1|3|سورة") // skips ayah 2
        assertThrows(QuranParseException::class.java) {
            parser.parse(text, surahMetadata, listOf("1|1|1|1", "1|3|1|1"))
        }
    }

    @Test
    fun `missing metric reference throws`() {
        val text = listOf("1|1|سورة", "1|2|سورة", "1|3|سورة")
        // Only two metric lines for three ayahs.
        assertThrows(QuranParseException::class.java) {
            parser.parse(text, surahMetadata, listOf("1|1|1|1", "1|2|1|1"))
        }
    }

    @Test
    fun `copyright comment lines are ignored`() {
        val text = listOf(
            "# PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK",
            "1|1|سورة",
            "# trailing note",
            "1|2|سورة",
            "1|3|سورة",
        )
        val parsed = parser.parse(text, surahMetadata, listOf("1|1|1|1", "1|2|1|1", "1|3|1|1"))
        assertEquals(3, parsed.ayahs.size)
        assertEquals("سورة", parsed.ayahs[1].textUthmani)
    }
}
