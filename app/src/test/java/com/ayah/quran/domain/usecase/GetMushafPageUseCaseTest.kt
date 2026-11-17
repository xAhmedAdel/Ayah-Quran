package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies mushaf page assembly from the page mapping: segment splitting at
 * surah boundaries, header/bismillah conventions (surahs 1, 9 and the
 * embedded-prefix convention), and verbatim text preservation.
 */
class GetMushafPageUseCaseTest {

    private val repository = mockk<QuranRepository>(relaxed = true)
    private lateinit var useCase: GetMushafPageUseCase

    private val bismillah = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

    private fun surah(id: Int, ayahCount: Int) = Surah(
        id = id,
        nameArabic = "سورة $id",
        nameTransliterated = "Surah $id",
        nameEnglish = "Surah $id",
        revelationType = RevelationType.MECCAN,
        ayahCount = ayahCount,
    )

    private fun ayah(surahId: Int, number: Int, text: String) = Ayah(
        id = 0,
        surahId = surahId,
        ayahNumber = number,
        textUthmani = text,
        juz = 1,
        page = 1,
    )

    @Before
    fun setUp() {
        useCase = GetMushafPageUseCase(repository)
        coEvery { repository.getAyah(1, 1) } returns ayah(1, 1, bismillah)
    }

    @Test
    fun `out-of-range pages are rejected`() = runTest {
        assertNull(useCase(0))
        assertNull(useCase(-3))
        assertNull(useCase(605))
    }

    @Test
    fun `a surah that starts on the page gets a header with lifted bismillah`() = runTest {
        val first = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ"
        coEvery { repository.ayahsOnPage(2) } returns listOf(
            ayah(2, 1, first),
            ayah(2, 2, "تِلْكَ ٱلْكِتَٰبُ"),
        )
        coEvery { repository.getSurah(2) } returns surah(2, 286)

        val page = useCase(2)!!

        assertEquals(1, page.segments.size)
        val segment = page.segments.first()
        assertTrue(segment.surahStartsHere)
        assertEquals(bismillah, segment.bismillah)
        // The bismillah prefix is lifted to the header — never duplicated.
        assertEquals("الٓمٓ", segment.ayahs.first().displayText)
        assertEquals("تِلْكَ ٱلْكِتَٰبُ", segment.ayahs[1].displayText)
    }

    @Test
    fun `surah 1 keeps its bismillah inline with no header bismillah`() = runTest {
        coEvery { repository.ayahsOnPage(1) } returns listOf(
            ayah(1, 1, bismillah),
            ayah(1, 2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"),
        )
        coEvery { repository.getSurah(1) } returns surah(1, 7)

        val page = useCase(1)!!

        assertNull(page.segments.first().bismillah)
        assertEquals(bismillah, page.segments.first().ayahs.first().displayText)
    }

    @Test
    fun `surah 9 shows no bismillah`() = runTest {
        coEvery { repository.ayahsOnPage(9) } returns listOf(
            ayah(9, 1, "بَرَاءَةٌ مِّنَ ٱللَّهِ"),
        )
        coEvery { repository.getSurah(9) } returns surah(9, 129)

        val page = useCase(9)!!

        assertNull(page.segments.first().bismillah)
        assertEquals("بَرَاءَةٌ مِّنَ ٱللَّهِ", page.segments.first().ayahs.first().displayText)
    }

    @Test
    fun `a page continuing mid-surah has no header`() = runTest {
        coEvery { repository.ayahsOnPage(300) } returns listOf(
            ayah(18, 54, "وَلَقَدْ صَرَّفْنَا"),
        )
        coEvery { repository.getSurah(18) } returns surah(18, 110)

        val page = useCase(300)!!

        val segment = page.segments.single()
        assertTrue(!segment.surahStartsHere)
        assertNull(segment.bismillah)
        assertEquals("وَلَقَدْ صَرَّفْنَا", segment.ayahs.single().displayText)
    }

    @Test
    fun `a page spanning two surahs splits into two segments`() = runTest {
        coEvery { repository.ayahsOnPage(604) } returns listOf(
            ayah(112, 1, "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ قُلْ هُوَ ٱللَّهُ أَحَدٌ"),
            ayah(112, 2, "ٱللَّهُ ٱلصَّمَدُ"),
            ayah(113, 1, "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ قُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ"),
        )
        coEvery { repository.getSurah(112) } returns surah(112, 4)
        coEvery { repository.getSurah(113) } returns surah(113, 5)

        val page = useCase(604)!!

        assertEquals(2, page.segments.size)
        assertTrue(page.segments[0].surahStartsHere)
        assertTrue(page.segments[1].surahStartsHere)
        assertEquals(112, page.segments[0].surah.id)
        assertEquals(113, page.segments[1].surah.id)
        assertEquals(2, page.segments[0].ayahs.size)
        assertEquals(1, page.segments[1].ayahs.size)
        // Both surahs are short "start" surahs whose first ayahs embed the
        // bismillah; each header carries the lifted prefix.
        assertEquals(bismillah, page.segments[0].bismillah)
        assertEquals(bismillah, page.segments[1].bismillah)
        assertEquals("قُلْ هُوَ ٱللَّهُ أَحَدٌ", page.segments[0].ayahs.first().displayText)
        assertEquals("قُلْ أَعُوذُ بِرَبِّ ٱلْفَلَقِ", page.segments[1].ayahs.first().displayText)
    }

    @Test
    fun `unmapped page returns null rather than an empty shell`() = runTest {
        coEvery { repository.ayahsOnPage(400) } returns emptyList()
        assertNull(useCase(400))
    }
}
