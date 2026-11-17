package com.ayah.quran.domain.model

/**
 * The reader's presentation mode.
 *
 * [MUSHAF] renders the authentic 604-page Madinah mushaf layout: page
 * boundaries, surah starts and bismillah placement come from the verified
 * page mapping — but lines are rendered as justified Uthmani text, NOT the
 * printed line breaks. [CONTINUOUS] is the adjustable surah reader.
 */
enum class ReadingMode {
    MUSHAF,
    CONTINUOUS,
    ;

    companion object {
        fun fromStorage(value: String?): ReadingMode =
            entries.firstOrNull { it.name == value } ?: MUSHAF
    }
}
