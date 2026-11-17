package com.ayah.quran.data.quran

import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.SurahDao
import com.ayah.quran.core.database.entity.AyahEntity
import com.ayah.quran.core.database.entity.SurahEntity
import com.ayah.quran.domain.model.Ayah
import com.ayah.quran.domain.model.QuranImportState
import com.ayah.quran.domain.model.RevelationType
import com.ayah.quran.domain.model.Surah
import com.ayah.quran.domain.repository.QuranRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranRepositoryImpl @Inject constructor(
    private val surahDao: SurahDao,
    private val ayahDao: AyahDao,
    private val importer: QuranImporter,
) : QuranRepository {

    override val importState: Flow<QuranImportState> = importer.importState

    override suspend fun importIfNeeded() = importer.importIfNeeded()

    override fun observeSurahs(): Flow<List<Surah>> =
        surahDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun searchSurahs(query: String): Flow<List<Surah>> {
        // LIKE ignores Unicode case for Latin; Arabic has no case. Trim so
        // stray whitespace doesn't defeat the match.
        val sanitized = query.trim()
        if (sanitized.isEmpty()) return observeSurahs()
        return surahDao.searchByName(sanitized).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSurah(surahId: Int): Surah? = surahDao.getSurah(surahId)?.toDomain()

    override fun observeAyahs(surahId: Int): Flow<List<Ayah>> =
        ayahDao.observeSurahAyahs(surahId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAyah(surahId: Int, ayahNumber: Int): Ayah? =
        ayahDao.getAyah(surahId, ayahNumber)?.toDomain()

    private fun SurahEntity.toDomain() = Surah(
        id = id,
        nameArabic = nameArabic,
        nameTransliterated = nameTransliterated,
        nameEnglish = nameEnglish,
        revelationType = when (revelationType) {
            "makkan" -> RevelationType.MECCAN
            "madinan" -> RevelationType.MEDINAN
            else -> throw IllegalStateException("unknown revelation type: $revelationType")
        },
        ayahCount = ayahCount,
    )

    private fun AyahEntity.toDomain() = Ayah(
        id = id,
        surahId = surahId,
        ayahNumber = ayahNumber,
        textUthmani = textUthmani,
        juz = juz,
        page = page,
    )
}
