package com.ayah.quran.data.quran

import com.ayah.quran.core.database.dao.ReadingProgressDao
import com.ayah.quran.core.database.entity.ReadingProgressEntity
import com.ayah.quran.domain.model.ReadingPosition
import com.ayah.quran.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao,
) : ReadingProgressRepository {

    override fun observeReadingPosition(): Flow<ReadingPosition?> =
        readingProgressDao.observe().map { entity -> entity?.toDomain() }

    override suspend fun saveReadingPosition(surahId: Int, ayahNumber: Int) {
        readingProgressDao.upsert(
            ReadingProgressEntity(
                surahId = surahId,
                ayahNumber = ayahNumber,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun ReadingProgressEntity.toDomain() = ReadingPosition(
        surahId = surahId,
        ayahNumber = ayahNumber,
        updatedAtMillis = updatedAt,
    )
}
