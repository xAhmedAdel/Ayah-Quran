package com.ayah.quran.data.companion

import androidx.room.withTransaction
import com.ayah.quran.core.database.AyahDatabase
import com.ayah.quran.core.database.dao.AyahDao
import com.ayah.quran.core.database.dao.SurahDao
import com.ayah.quran.core.database.dao.companion.AdhkarDao
import com.ayah.quran.core.database.dao.companion.HadithDao
import com.ayah.quran.core.database.dao.companion.HadithWithUserState
import com.ayah.quran.core.database.dao.companion.TasbihDao
import com.ayah.quran.core.database.entity.companion.DhikrCategoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrDailyProgressEntity
import com.ayah.quran.core.database.entity.companion.DhikrFavoriteEntity
import com.ayah.quran.core.database.entity.companion.DhikrItemEntity
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithUserStateEntity
import com.ayah.quran.core.database.entity.companion.TasbihCounterEntity
import com.ayah.quran.core.database.entity.companion.TasbihDailyLogEntity
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.DhikrCategory
import com.ayah.quran.domain.model.companion.DhikrDailyProgress
import com.ayah.quran.domain.model.companion.DhikrItem
import com.ayah.quran.domain.model.companion.DhikrItemType
import com.ayah.quran.domain.model.companion.DhikrSectionType
import com.ayah.quran.domain.model.companion.HadithCollection
import com.ayah.quran.domain.model.companion.HadithItem
import com.ayah.quran.domain.model.companion.QuranicDuaPointer
import com.ayah.quran.domain.model.companion.ResolvedQuranicDua
import com.ayah.quran.domain.model.companion.TasbihCounter
import com.ayah.quran.domain.repository.companion.AdhkarRepository
import com.ayah.quran.domain.repository.companion.HadithRepository
import com.ayah.quran.domain.repository.companion.TasbihRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class AdhkarRepositoryImpl @Inject constructor(
    private val adhkarDao: AdhkarDao,
    private val ayahDao: AyahDao,
    private val surahDao: SurahDao,
) : AdhkarRepository {

    override fun observeCategories(): Flow<List<DhikrCategory>> =
        adhkarDao.observeCategories().map { list -> list.map { it.toDomain() } }

    override fun observeItemsByCategory(categoryId: String): Flow<List<DhikrItem>> =
        adhkarDao.observeItemsByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    override fun observeDailyProgress(dayKey: String): Flow<List<DhikrDailyProgress>> =
        adhkarDao.observeDailyProgress(dayKey).map { list -> list.map { it.toDomain() } }

    override fun observeCategoryProgress(dayKey: String, categoryId: String): Flow<List<DhikrDailyProgress>> =
        adhkarDao.observeCategoryProgress(dayKey, categoryId).map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteIds(): Flow<Set<String>> =
        adhkarDao.observeFavoriteIds().map { it.toSet() }

    override fun observeFavoriteItems(): Flow<List<DhikrItem>> =
        adhkarDao.observeFavoriteItems().map { list -> list.map { it.toDomain() } }

    override fun observeQuranicDuas(): Flow<List<ResolvedQuranicDua>> =
        adhkarDao.observeQuranicDuas().map { entities ->
            entities.mapNotNull { entity ->
                val surah = surahDao.getSurah(entity.surahId) ?: return@mapNotNull null
                val ayahs = (entity.startAyah..entity.endAyah).mapNotNull { num ->
                    ayahDao.getAyah(entity.surahId, num)?.textUthmani
                }
                if (ayahs.isEmpty()) return@mapNotNull null
                ResolvedQuranicDua(
                    pointer = QuranicDuaPointer(
                        id = entity.id,
                        categoryId = entity.categoryId,
                        surahId = entity.surahId,
                        startAyah = entity.startAyah,
                        endAyah = entity.endAyah,
                        titleAr = entity.titleAr,
                        titleEn = entity.titleEn,
                        sortOrder = entity.sortOrder,
                    ),
                    textUthmani = ayahs.joinToString(" "),
                    surahNameArabic = surah.nameArabic,
                    surahNameEnglish = surah.nameEnglish,
                )
            }
        }

    override suspend fun searchItems(query: String): List<DhikrItem> {
        val normalized = ArabicTextNormalizer.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        return adhkarDao.searchItems(normalized).map { it.toDomain() }
    }

    override suspend fun incrementProgress(dayKey: String, itemId: String) {
        val item = adhkarDao.getItemById(itemId) ?: return
        val existing = adhkarDao.getProgress(dayKey, itemId)
        val next = ((existing?.currentCount ?: 0) + 1).coerceAtMost(item.repeatCount)
        adhkarDao.upsertProgress(
            DhikrDailyProgressEntity(
                dayKey = dayKey,
                itemId = itemId,
                categoryId = item.categoryId,
                currentCount = next,
                targetCount = item.repeatCount,
                completed = next >= item.repeatCount,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun decrementProgress(dayKey: String, itemId: String) {
        val item = adhkarDao.getItemById(itemId) ?: return
        val existing = adhkarDao.getProgress(dayKey, itemId) ?: return
        val next = (existing.currentCount - 1).coerceAtLeast(0)
        adhkarDao.upsertProgress(
            existing.copy(
                currentCount = next,
                completed = next >= item.repeatCount,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun resetCategoryProgress(dayKey: String, categoryId: String) {
        adhkarDao.resetCategoryProgress(dayKey, categoryId)
    }

    override suspend fun toggleFavorite(itemId: String) {
        if (adhkarDao.isFavorite(itemId)) {
            adhkarDao.deleteFavorite(itemId)
        } else {
            adhkarDao.getItemById(itemId) ?: return
            adhkarDao.insertFavorite(
                DhikrFavoriteEntity(itemId = itemId, createdAtMillis = System.currentTimeMillis()),
            )
        }
    }
}

@Singleton
class TasbihRepositoryImpl @Inject constructor(
    private val database: AyahDatabase,
    private val tasbihDao: TasbihDao,
) : TasbihRepository {

    override fun observeCounters(): Flow<List<TasbihCounter>> =
        tasbihDao.observeCounters().map { list -> list.map { it.toDomain() } }

    override fun observeDayTotal(dayKey: String): Flow<Int> =
        tasbihDao.observeDayTotal(dayKey)

    override suspend fun increment(counterId: Long, dayKey: String): Boolean =
        database.withTransaction {
            val entity = tasbihDao.getById(counterId) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            val incremented = entity.currentCount + 1
            val reachedTarget = entity.targetCount > 0 && incremented >= entity.targetCount
            val updated = entity.copy(
                currentCount = if (reachedTarget) 0 else incremented,
                completedRounds = if (reachedTarget) entity.completedRounds + 1 else entity.completedRounds,
                lifetimeCount = entity.lifetimeCount + 1,
                updatedAtMillis = now,
            )
            tasbihDao.upsertCounter(updated)
            val existingLog = tasbihDao.getDailyLog(dayKey, counterId)
            tasbihDao.upsertDailyLog(
                TasbihDailyLogEntity(
                    dayKey = dayKey,
                    counterId = counterId,
                    countTotal = (existingLog?.countTotal ?: 0) + 1,
                    updatedAtMillis = now,
                ),
            )
            reachedTarget
        }

    override suspend fun incrementSession(counterId: Long, dayKey: String): Boolean =
        database.withTransaction {
            val entity = tasbihDao.getById(counterId) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            val incremented = entity.currentCount + 1
            val justReachedTarget = entity.targetCount > 0 && incremented == entity.targetCount
            val updated = entity.copy(
                currentCount = incremented,
                completedRounds = if (justReachedTarget) entity.completedRounds + 1 else entity.completedRounds,
                lifetimeCount = entity.lifetimeCount + 1,
                updatedAtMillis = now,
            )
            tasbihDao.upsertCounter(updated)
            val existingLog = tasbihDao.getDailyLog(dayKey, counterId)
            tasbihDao.upsertDailyLog(
                TasbihDailyLogEntity(
                    dayKey = dayKey,
                    counterId = counterId,
                    countTotal = (existingLog?.countTotal ?: 0) + 1,
                    updatedAtMillis = now,
                ),
            )
            justReachedTarget
        }

    override suspend fun undo(counterId: Long, dayKey: String) {
        database.withTransaction {
            val entity = tasbihDao.getById(counterId) ?: return@withTransaction
            if (entity.currentCount <= 0) return@withTransaction
            val now = System.currentTimeMillis()
            val wasExactTarget = entity.targetCount > 0 && entity.currentCount == entity.targetCount
            tasbihDao.upsertCounter(
                entity.copy(
                    currentCount = entity.currentCount - 1,
                    completedRounds = if (wasExactTarget && entity.completedRounds > 0) {
                        entity.completedRounds - 1
                    } else {
                        entity.completedRounds
                    },
                    lifetimeCount = (entity.lifetimeCount - 1).coerceAtLeast(0L),
                    updatedAtMillis = now,
                ),
            )
            val existingLog = tasbihDao.getDailyLog(dayKey, counterId)
            if (existingLog != null && existingLog.countTotal > 0) {
                tasbihDao.upsertDailyLog(
                    existingLog.copy(
                        countTotal = existingLog.countTotal - 1,
                        updatedAtMillis = now,
                    ),
                )
            }
        }
    }

    override suspend fun reset(counterId: Long) {
        val entity = tasbihDao.getById(counterId) ?: return
        tasbihDao.upsertCounter(
            entity.copy(
                currentCount = 0,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun createCustomCounter(
        titleAr: String,
        titleEn: String,
        dhikrTextAr: String,
        targetCount: Int,
    ): Long {
        val cleanText = dhikrTextAr.trim()
        require(cleanText.isNotEmpty()) { "Dhikr text cannot be blank" }
        val clampedTarget = targetCount.coerceIn(0, 10_000)
        val nextSort = tasbihDao.counterCount() + 1
        return tasbihDao.insertCounter(
            TasbihCounterEntity(
                stableKey = null,
                titleAr = titleAr.trim().ifEmpty { cleanText },
                titleEn = titleEn.trim(),
                dhikrTextAr = cleanText,
                targetCount = clampedTarget,
                currentCount = 0,
                completedRounds = 0,
                lifetimeCount = 0,
                isCustom = true,
                sortOrder = nextSort,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun updateCounter(counter: TasbihCounter) {
        val existing = tasbihDao.getById(counter.id) ?: return
        val cleanText = counter.dhikrTextAr.trim()
        if (cleanText.isEmpty()) return
        tasbihDao.upsertCounter(
            existing.copy(
                titleAr = counter.titleAr.trim().ifEmpty { cleanText },
                titleEn = counter.titleEn.trim(),
                dhikrTextAr = cleanText,
                targetCount = counter.targetCount.coerceIn(0, 10_000),
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteCustomCounter(counterId: Long) {
        if (tasbihDao.counterCount() <= 1) return
        tasbihDao.deleteCustomCounter(counterId)
    }
}

@Singleton
class HadithRepositoryImpl @Inject constructor(
    private val hadithDao: HadithDao,
) : HadithRepository {

    override fun observeCollections(): Flow<List<HadithCollection>> =
        hadithDao.observeCollections().map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<HadithItem>> =
        hadithDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByCollection(collectionId: String): Flow<List<HadithItem>> =
        hadithDao.observeByCollection(collectionId).map { list -> list.map { it.toDomain() } }

    override fun observeById(hadithId: String): Flow<HadithItem?> =
        hadithDao.observeById(hadithId).map { it?.toDomain() }

    override fun observeFavorites(): Flow<List<HadithItem>> =
        hadithDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override fun observeRecentlyRead(limit: Int): Flow<List<HadithItem>> =
        hadithDao.observeRecentlyRead(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun search(query: String, limit: Int): List<HadithItem> {
        val normalized = ArabicTextNormalizer.normalize(query)
        if (normalized.isEmpty()) return emptyList()
        val ftsQuery = ArabicTextNormalizer.buildFtsQuery(query)
        val ftsHits = if (ftsQuery.isNotEmpty()) hadithDao.searchFts(ftsQuery, limit) else emptyList()
        if (ftsHits.size >= limit) return ftsHits.map { it.toDomain() }
        val seen = ftsHits.mapTo(mutableSetOf()) { it.hadith.id }
        val fallbackHits = hadithDao.searchNormalized(normalized, limit).filter { seen.add(it.hadith.id) }
        return (ftsHits + fallbackHits).take(limit).map { it.toDomain() }
    }

    override suspend fun toggleFavorite(hadithId: String) {
        val existing = hadithDao.getUserState(hadithId)
        val nextFav = !(existing?.isFavorite ?: false)
        hadithDao.upsertUserState(
            HadithUserStateEntity(
                hadithId = hadithId,
                isFavorite = nextFav,
                favoritedAtMillis = if (nextFav) System.currentTimeMillis() else null,
                lastReadAtMillis = existing?.lastReadAtMillis,
            ),
        )
    }

    override suspend fun recordRead(hadithId: String) {
        val existing = hadithDao.getUserState(hadithId)
        hadithDao.upsertUserState(
            HadithUserStateEntity(
                hadithId = hadithId,
                isFavorite = existing?.isFavorite ?: false,
                favoritedAtMillis = existing?.favoritedAtMillis,
                lastReadAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun dailyHadithId(epochDay: Long): String? {
        val ids = hadithDao.allHadithIds()
        if (ids.isEmpty()) return null
        val index = abs((epochDay * 2654435761L) % ids.size).toInt()
        return ids[index]
    }
}

private fun DhikrCategoryEntity.toDomain() = DhikrCategory(
    id = id,
    sectionType = DhikrSectionType.valueOf(sectionType),
    titleAr = titleAr,
    titleEn = titleEn,
    sortOrder = sortOrder,
    recommendedWindow = recommendedWindow,
)

private fun DhikrItemEntity.toDomain() = DhikrItem(
    id = id,
    categoryId = categoryId,
    itemType = DhikrItemType.valueOf(itemType),
    sortOrder = sortOrder,
    titleAr = titleAr,
    titleEn = titleEn,
    textAr = textAr,
    textEn = textEn,
    virtueAr = virtueAr,
    virtueEn = virtueEn,
    sourceAr = sourceAr,
    sourceEn = sourceEn,
    repeatCount = repeatCount,
    surahId = surahId,
    startAyah = startAyah,
    endAyah = endAyah,
    datasetVersion = datasetVersion,
)

private fun DhikrDailyProgressEntity.toDomain() = DhikrDailyProgress(
    dayKey = dayKey,
    itemId = itemId,
    categoryId = categoryId,
    currentCount = currentCount,
    targetCount = targetCount,
    completed = completed,
    updatedAtMillis = updatedAtMillis,
)

private fun TasbihCounterEntity.toDomain() = TasbihCounter(
    id = id,
    stableKey = stableKey,
    titleAr = titleAr,
    titleEn = titleEn,
    dhikrTextAr = dhikrTextAr,
    targetCount = targetCount,
    currentCount = currentCount,
    completedRounds = completedRounds,
    lifetimeCount = lifetimeCount,
    isCustom = isCustom,
    sortOrder = sortOrder,
    updatedAtMillis = updatedAtMillis,
)

private fun HadithCollectionEntity.toDomain() = HadithCollection(
    id = id,
    titleAr = titleAr,
    titleEn = titleEn,
    descriptionAr = descriptionAr,
    descriptionEn = descriptionEn,
    sourceAttribution = sourceAttribution,
    datasetVersion = datasetVersion,
    isBundled = isBundled,
    isDownloaded = isDownloaded,
    hadithCount = hadithCount,
    sortOrder = sortOrder,
)

private fun HadithWithUserState.toDomain() = HadithItem(
    id = hadith.id,
    collectionId = hadith.collectionId,
    chapterTitleAr = hadith.chapterTitleAr,
    chapterTitleEn = hadith.chapterTitleEn,
    hadithNumber = hadith.hadithNumber,
    titleAr = hadith.titleAr,
    titleEn = hadith.titleEn,
    textAr = hadith.textAr,
    textEn = hadith.textEn,
    gradeAr = hadith.gradeAr,
    gradeEn = hadith.gradeEn,
    graderAttribution = hadith.graderAttribution,
    referenceAr = hadith.referenceAr,
    referenceEn = hadith.referenceEn,
    explanationAr = hadith.explanationAr,
    explanationEn = hadith.explanationEn,
    sourceVersion = hadith.sourceVersion,
    sortOrder = hadith.sortOrder,
    isFavorite = isFavorite == true,
    lastReadAtMillis = lastReadAtMillis,
)
