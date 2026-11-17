package com.ayah.quran.domain.model

/**
 * One page of the authentic Madinah mushaf (604 pages), built from the
 * verified per-ayah page mapping — never from arbitrary splitting.
 *
 * A page usually belongs to one surah, but it may begin in the middle of a
 * surah, and it may contain the end of one surah followed by the beginning
 * of the next. Those transitions appear as multiple [segments].
 *
 * This model carries page boundaries and composition, not printed line
 * breaks: rendering shows each segment as continuous justified Uthmani text.
 */
data class MushafPage(
    val pageNumber: Int,
    val segments: List<MushafPageSegment>,
) {
    val firstAyah: MushafPageAyah?
        get() = segments.firstOrNull()?.ayahs?.firstOrNull()
}

/** One surah's portion of a mushaf page. */
data class MushafPageSegment(
    val surah: Surah,
    /** True when the surah's first ayah lies on this page (header shown). */
    val surahStartsHere: Boolean,
    /**
     * Bismillah to display under the surah header, or null. Non-null only
     * when [surahStartsHere] and the convention applies (stripped from the
     * first ayah; absent for surahs 1 and 9). The underlying ayah text is
     * never modified — this only moves the embedded bismillah into the
     * header position, matching the printed mushaf convention.
     */
    val bismillah: String?,
    val ayahs: List<MushafPageAyah>,
)

/** One ayah as rendered on a mushaf page. */
data class MushafPageAyah(
    val surahId: Int,
    val ayahNumber: Int,
    /** Verbatim Uthmani text (bismillah-stripped per convention). */
    val displayText: String,
)
