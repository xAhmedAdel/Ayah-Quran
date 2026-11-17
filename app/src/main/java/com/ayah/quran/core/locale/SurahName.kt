package com.ayah.quran.core.locale

import androidx.annotation.StringRes
import com.ayah.quran.R
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah

/**
 * Presentation-only surah display name: the Arabic name in the Arabic UI,
 * the transliterated name otherwise. Never touches ayah text — the Quran
 * dataset stays Uthmani Arabic in every locale.
 */
fun Surah.localizedDisplayName(): String =
    if (AppLocale.isArabic) nameArabic else nameTransliterated

/** Same rule for raw DAO results before domain mapping. */
fun SurahEntity.localizedDisplayName(): String =
    if (AppLocale.isArabic) nameArabic else nameTransliterated

/** Localized revelation-type label ("Meccan"/"مكية"). */
@StringRes
fun revelationLabel(type: RevelationType): Int =
    if (type == RevelationType.MECCAN) {
        R.string.surah_revelation_meccan
    } else {
        R.string.surah_revelation_medinan
    }
