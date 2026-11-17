package com.ayah.quran.domain.repository

import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.Surah
import kotlinx.coroutines.flow.Flow

/** Read access to the Quran corpus and its import lifecycle. */
interface QuranRepository {

    /** Emits the current import state and every subsequent change. */
    val importState: Flow<QuranImportState>

    /**
     * Runs the dataset import if the database is not yet populated.
     * Idempotent and safe to call concurrently; completion is reflected in
     * [importState].
     */
    suspend fun importIfNeeded()

    /** All surahs in canonical mushaf order. Emits empty until import completes. */
    fun observeSurahs(): Flow<List<Surah>>

    /** Surahs whose Arabic, transliterated or English name matches [query]. */
    fun searchSurahs(query: String): Flow<List<Surah>>

    suspend fun getSurah(surahId: Int): Surah?

    /** Ayahs of a surah in canonical order. */
    fun observeAyahs(surahId: Int): Flow<List<Ayah>>

    suspend fun getAyah(surahId: Int, ayahNumber: Int): Ayah?
}
