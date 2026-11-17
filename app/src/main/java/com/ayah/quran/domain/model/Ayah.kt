package com.ayah.quran.domain.model

/**
 * A single verse of the Quran.
 *
 * [id] is the global verse number (1..6236). [surahId] + [ayahNumber] form the
 * canonical reference; [textUthmani] must always be preserved exactly as
 * delivered by the verified dataset (see docs/QURAN_DATA.md).
 *
 * [juz] and [page] refer to the Madani mushaf layout (30 juz, 604 pages).
 */
data class Ayah(
    val id: Int,
    val surahId: Int,
    val ayahNumber: Int,
    val textUthmani: String,
    val juz: Int,
    val page: Int,
)
