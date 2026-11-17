package com.ayah.quran.domain.usecase

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.MushafPage
import com.ayah.quran.domain.model.MushafPageAyah
import com.ayah.quran.domain.model.MushafPageSegment
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import javax.inject.Inject

/**
 * Builds one authentic Madinah mushaf page (1..604) from the verified
 * per-ayah page mapping.
 *
 * The page's ayahs are grouped into segments per surah; a surah that begins
 * on this page gets a header and — per the printed mushaf convention — its
 * bismillah is lifted out of the first ayah into the header position
 * (surah 1's bismillah IS its first ayah and surah 9 has none). The Uthmani
 * text itself is never modified; this only repositions the embedded
 * bismillah prefix, exactly like the existing continuous reader.
 */
class GetMushafPageUseCase @Inject constructor(
    private val repository: QuranRepository,
) {
    /** Returns the page, or null for out-of-range page numbers. */
    suspend operator fun invoke(page: Int): MushafPage? {
        if (page !in FIRST_PAGE..LAST_PAGE) return null
        val ayahs = repository.ayahsOnPage(page)
        if (ayahs.isEmpty()) return null

        // Bismillah reference text (surah 1, ayah 1) for prefix stripping.
        val standardBismillah = repository.getAyah(
            BISMILLAH_AS_FIRST_AYAH_SURAH,
            1,
        )?.textUthmani

        val segments = mutableListOf<MushafPageSegment>()
        var index = 0
        while (index < ayahs.size) {
            val surahId = ayahs[index].surahId
            val segmentAyahs = ayahs.drop(index).takeWhile { it.surahId == surahId }
            val surah = repository.getSurah(surahId) ?: return null
            val surahStartsHere = segmentAyahs.first().ayahNumber == 1
            val (bismillah, displayAyahs) = if (surahStartsHere) {
                applyBismillahConvention(surah, segmentAyahs, standardBismillah)
            } else {
                null to segmentAyahs.map { it.toPageAyah(it.textUthmani) }
            }
            segments.add(
                MushafPageSegment(
                    surah = surah,
                    surahStartsHere = surahStartsHere,
                    bismillah = bismillah,
                    ayahs = displayAyahs,
                ),
            )
            index += segmentAyahs.size
        }
        return MushafPage(pageNumber = page, segments = segments)
    }

    private fun applyBismillahConvention(
        surah: Surah,
        ayahs: List<Ayah>,
        standardBismillah: String?,
    ): Pair<String?, List<MushafPageAyah>> {
        // Surah 1: bismillah is its first ayah — keep it inline, no header.
        // Surah 9: no bismillah at all.
        if (surah.id == BISMILLAH_AS_FIRST_AYAH_SURAH || surah.id == NO_BISMILLAH_SURAH) {
            return null to ayahs.map { it.toPageAyah(it.textUthmani) }
        }
        val first = ayahs.first().textUthmani
        val prefix = standardBismillah?.let { "$it " }
        if (prefix != null && first.startsWith(prefix)) {
            return standardBismillah to listOf(
                ayahs.first().toPageAyah(first.removePrefix(prefix)),
            ) + ayahs.drop(1).map { it.toPageAyah(it.textUthmani) }
        }
        // Defensive: no recognizable prefix — render verbatim, no header.
        return null to ayahs.map { it.toPageAyah(it.textUthmani) }
    }

    private fun Ayah.toPageAyah(displayText: String) = MushafPageAyah(
        surahId = surahId,
        ayahNumber = ayahNumber,
        displayText = displayText,
    )

    companion object {
        const val FIRST_PAGE = 1
        const val LAST_PAGE = 604
        const val BISMILLAH_AS_FIRST_AYAH_SURAH = 1
        const val NO_BISMILLAH_SURAH = 9
    }
}
