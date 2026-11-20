package com.ayah.quran.domain.model.companion

enum class DhikrSectionType {
    ADHKAR_SESSION,
    ADHKAR_GENERAL,
    DUA_QURANIC,
    DUA_PROPHETIC,
    DUA_SITUATION,
    SUNNAH_SURAH,
}

enum class DhikrItemType {
    DHIKR,
    QURANIC_DUA,
    PROPHETIC_DUA,
    SUNNAH_SURAH_LINK,
}

data class DhikrCategory(
    val id: String,
    val sectionType: DhikrSectionType,
    val titleAr: String,
    val titleEn: String,
    val sortOrder: Int,
    val recommendedWindow: String?,
)

data class DhikrItem(
    val id: String,
    val categoryId: String,
    val itemType: DhikrItemType,
    val sortOrder: Int,
    val titleAr: String,
    val titleEn: String,
    val textAr: String,
    val textEn: String?,
    val virtueAr: String?,
    val virtueEn: String?,
    val sourceAr: String,
    val sourceEn: String,
    val repeatCount: Int,
    val surahId: Int?,
    val startAyah: Int?,
    val endAyah: Int?,
    val datasetVersion: String,
)

data class DhikrDailyProgress(
    val dayKey: String,
    val itemId: String,
    val categoryId: String,
    val currentCount: Int,
    val targetCount: Int,
    val completed: Boolean,
    val updatedAtMillis: Long,
)

data class QuranicDuaPointer(
    val id: String,
    val categoryId: String,
    val surahId: Int,
    val startAyah: Int,
    val endAyah: Int,
    val titleAr: String,
    val titleEn: String,
    val sortOrder: Int,
)

data class ResolvedQuranicDua(
    val pointer: QuranicDuaPointer,
    val textUthmani: String,
    val surahNameArabic: String,
    val surahNameEnglish: String,
)

data class TasbihCounter(
    val id: Long,
    val stableKey: String?,
    val titleAr: String,
    val titleEn: String,
    val dhikrTextAr: String,
    val targetCount: Int,
    val currentCount: Int,
    val completedRounds: Int,
    val lifetimeCount: Long,
    val isCustom: Boolean,
    val sortOrder: Int,
    val updatedAtMillis: Long,
)

data class HadithCollection(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val sourceAttribution: String,
    val datasetVersion: String,
    val isBundled: Boolean,
    val isDownloaded: Boolean,
    val hadithCount: Int,
    val sortOrder: Int,
)

data class HadithItem(
    val id: String,
    val collectionId: String,
    val chapterTitleAr: String,
    val chapterTitleEn: String,
    val hadithNumber: String,
    val titleAr: String,
    val titleEn: String,
    val textAr: String,
    val textEn: String?,
    val gradeAr: String,
    val gradeEn: String,
    val graderAttribution: String,
    val referenceAr: String,
    val referenceEn: String,
    val explanationAr: String?,
    val explanationEn: String?,
    val sourceVersion: String,
    val sortOrder: Int,
    val isFavorite: Boolean = false,
    val lastReadAtMillis: Long? = null,
)

sealed interface CompanionImportState {
    data object NotStarted : CompanionImportState
    data object Importing : CompanionImportState
    data object Completed : CompanionImportState
    data class Failed(val reason: String) : CompanionImportState
}
