package com.ayah.quran.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArabicTextNormalizerTest {

    @Test
    fun normalizesAlefVariants() {
        assertEquals("احمد ابراهيم امن اسم", ArabicTextNormalizer.normalize("أحمد إبراهيم آمن ٱسم"))
    }

    @Test
    fun normalizesHamzaCarriersAndAlefMaksuraAndTaaMarbuta() {
        assertEquals("مومن بيس هدي رحمه", ArabicTextNormalizer.normalize("مؤمن بئس هدى رحمة"))
    }

    @Test
    fun stripsDiacriticsAndTatweel() {
        val vocalized = "بِسْمِ اللَّهِ الرَّحْمَـٰنِ الرَّحِيمِ"
        assertEquals("بسم الله الرحمن الرحيم", ArabicTextNormalizer.normalize(vocalized))
        assertEquals("سبحان الله", ArabicTextNormalizer.normalize("سُبْحَـــانَ اللَّهِ"))
    }

    @Test
    fun stripsArabicAndAsciiPunctuationAndCollapsesWhitespace() {
        val raw = "  «إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ»،   رواه البخاري؛ رقم: ١!  "
        assertEquals("انما الاعمال بالنيات رواه البخاري رقم 1", ArabicTextNormalizer.normalize(raw))
    }

    @Test
    fun convertsArabicIndicAndExtendedDigitsToWesternDigits() {
        assertEquals("1234567890 1234567890", ArabicTextNormalizer.normalize("١٢٣٤٥٦٧٨٩٠ ۱۲۳۴۵۶۷۸۹۰"))
    }

    @Test
    fun producesEquivalentSearchKeysForVocalizedAndUnvocalizedQueries() {
        val originalDisplay = "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ"
        val userQuery = "اللهم انت ربي لا اله الا انت"
        assertEquals(
            ArabicTextNormalizer.normalize(originalDisplay),
            ArabicTextNormalizer.normalize(userQuery),
        )
        assertNotEquals(originalDisplay, ArabicTextNormalizer.normalize(originalDisplay))
        assertEquals("اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ", originalDisplay)
    }

    @Test
    fun normalizesMixedArabicAndEnglishForBilingualSearch() {
        assertEquals("sahih al bukhari 6306", ArabicTextNormalizer.normalize("Sahih Al-Bukhari (6306)"))
    }

    @Test
    fun buildsSafePrefixFtsQuery() {
        assertEquals("الاستغفار* الصباح*", ArabicTextNormalizer.buildFtsQuery("  الِاسْتِغْفَارِ، الصَّبَاح  "))
        assertEquals("", ArabicTextNormalizer.buildFtsQuery("   ،،،   "))
    }

    @Test
    fun deterministicAcrossRepeatedInvocations() {
        val sample = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً"
        val first = ArabicTextNormalizer.normalize(sample)
        val second = ArabicTextNormalizer.normalize(sample)
        assertEquals(first, second)
    }
}
