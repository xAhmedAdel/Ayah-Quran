package com.ayah.quran.data.quran

import com.ayah.quran.domain.model.RubElHizb

class RubElHizbParser(
    private val expectedRubCount: Int = EXPECTED_RUB_COUNT,
) {

    fun parse(lines: List<String>): List<RubElHizb> {
        val dataLines = lines.filter { it.isNotBlank() && !it.startsWith("#") }
        if (dataLines.size != expectedRubCount) {
            fail("expected $expectedRubCount rub entries, found ${dataLines.size}")
        }

        val result = ArrayList<RubElHizb>(expectedRubCount)
        var previousSurah = 0
        var previousAyah = 0

        for ((index, rawLine) in dataLines.withIndex()) {
            val parts = rawLine.trim().split('|')
            if (parts.size != 3) {
                fail("rub line ${index + 1} malformed: ${rawLine.take(60)}")
            }
            val rubNumber = parts[0].toIntOrNull()
                ?: fail("invalid rub number at line ${index + 1}")
            val surahId = parts[1].toIntOrNull()
                ?: fail("invalid surah id at line ${index + 1}")
            val ayahNumber = parts[2].toIntOrNull()
                ?: fail("invalid ayah number at line ${index + 1}")

            val expectedNumber = index + 1
            if (rubNumber != expectedNumber) {
                fail("rub index out of sequence at line $expectedNumber: got $rubNumber")
            }
            if (surahId !in MIN_SURAH_ID..MAX_SURAH_ID) {
                fail("surah id out of range at rub $rubNumber: $surahId")
            }
            if (ayahNumber < 1) {
                fail("ayah number must be >= 1 at rub $rubNumber: $ayahNumber")
            }
            if (surahId < previousSurah || (surahId == previousSurah && ayahNumber <= previousAyah)) {
                fail(
                    "rub boundaries must be strictly ascending: rub $rubNumber ($surahId:$ayahNumber) " +
                        "follows ($previousSurah:$previousAyah)",
                )
            }

            val hizbNumber = ((rubNumber - 1) / QUARTERS_PER_HIZB) + 1
            val quarterInHizb = ((rubNumber - 1) % QUARTERS_PER_HIZB) + 1
            val juzNumber = ((rubNumber - 1) / QUARTERS_PER_JUZ) + 1

            result += RubElHizb(
                rubNumber = rubNumber,
                hizbNumber = hizbNumber,
                quarterInHizb = quarterInHizb,
                juzNumber = juzNumber,
                startSurahId = surahId,
                startAyahNumber = ayahNumber,
            )
            previousSurah = surahId
            previousAyah = ayahNumber
        }

        return result
    }

    private fun fail(message: String): Nothing = throw QuranParseException(message)

    companion object {
        const val EXPECTED_RUB_COUNT = 240
        const val EXPECTED_HIZB_COUNT = 60
        const val QUARTERS_PER_HIZB = 4
        const val QUARTERS_PER_JUZ = 8
        const val MIN_SURAH_ID = 1
        const val MAX_SURAH_ID = 114
    }
}
