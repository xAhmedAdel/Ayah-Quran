package com.ayah.quran.data.companion

import com.ayah.quran.core.database.entity.companion.DhikrCategoryEntity
import com.ayah.quran.core.database.entity.companion.DhikrItemEntity
import com.ayah.quran.core.database.entity.companion.HadithCollectionEntity
import com.ayah.quran.core.database.entity.companion.HadithEntity
import com.ayah.quran.core.database.entity.companion.QuranicDuaEntity
import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.DhikrItemType
import com.ayah.quran.domain.model.companion.DhikrSectionType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

class CompanionParseException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class ParsedAdhkarDataset(
    val version: String,
    val categories: List<DhikrCategoryEntity>,
    val items: List<DhikrItemEntity>,
)

data class ParsedQuranicDuasDataset(
    val version: String,
    val items: List<QuranicDuaEntity>,
)

data class ParsedHadithDataset(
    val version: String,
    val collections: List<HadithCollectionEntity>,
    val items: List<HadithEntity>,
)

@Singleton
class CompanionContentParser @Inject constructor() {

    fun parseAdhkar(rawJson: String): ParsedAdhkarDataset {
        val root = parseObject(rawJson, "adhkar")
        val manifest = root.requireObject("manifest", "adhkar")
        val version = manifest.requireNonBlankString("version", "adhkar.manifest")
        val expectedCats = manifest.requirePositiveInt("categoryCount", "adhkar.manifest")
        val expectedItems = manifest.requirePositiveInt("recordCount", "adhkar.manifest")

        val categoriesArray = root.requireArray("categories", "adhkar")
        val categoryIds = mutableSetOf<String>()
        val categories = buildList(categoriesArray.length()) {
            for (i in 0 until categoriesArray.length()) {
                val obj = categoriesArray.requireObjectAt(i, "adhkar.categories")
                val id = obj.requireNonBlankString("id", "adhkar.categories[$i]")
                if (!categoryIds.add(id)) {
                    throw CompanionParseException("Duplicate adhkar category id: $id")
                }
                val sectionType = obj.requireNonBlankString("sectionType", "adhkar.categories[$i]")
                if (DhikrSectionType.entries.none { it.name == sectionType }) {
                    throw CompanionParseException("Invalid DhikrSectionType '$sectionType' for $id")
                }
                add(
                    DhikrCategoryEntity(
                        id = id,
                        sectionType = sectionType,
                        titleAr = obj.requireNonBlankString("titleAr", "adhkar.categories[$i]"),
                        titleEn = obj.requireNonBlankString("titleEn", "adhkar.categories[$i]"),
                        sortOrder = obj.requirePositiveInt("sortOrder", "adhkar.categories[$i]"),
                        recommendedWindow = obj.optNullableString("recommendedWindow"),
                    ),
                )
            }
        }
        if (categories.size != expectedCats) {
            throw CompanionParseException(
                "adhkar categoryCount mismatch: manifest=$expectedCats, actual=${categories.size}",
            )
        }

        val itemsArray = root.requireArray("items", "adhkar")
        val itemIds = mutableSetOf<String>()
        val items = buildList(itemsArray.length()) {
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.requireObjectAt(i, "adhkar.items")
                val id = obj.requireNonBlankString("id", "adhkar.items[$i]")
                if (!itemIds.add(id)) {
                    throw CompanionParseException("Duplicate adhkar item id: $id")
                }
                val categoryId = obj.requireNonBlankString("categoryId", "adhkar.items[$i]")
                if (categoryId !in categoryIds) {
                    throw CompanionParseException("Unknown adhkar categoryId '$categoryId' on item $id")
                }
                val itemType = obj.requireNonBlankString("itemType", "adhkar.items[$i]")
                if (DhikrItemType.entries.none { it.name == itemType }) {
                    throw CompanionParseException("Invalid DhikrItemType '$itemType' on item $id")
                }
                val titleAr = obj.requireNonBlankString("titleAr", "adhkar.items[$i]")
                val titleEn = obj.requireNonBlankString("titleEn", "adhkar.items[$i]")
                val textAr = obj.requireNonBlankString("textAr", "adhkar.items[$i]")
                val textEn = obj.optNullableString("textEn")
                val virtueAr = obj.optNullableString("virtueAr")
                val virtueEn = obj.optNullableString("virtueEn")
                val sourceAr = obj.requireNonBlankString("sourceAr", "adhkar.items[$i]")
                val sourceEn = obj.requireNonBlankString("sourceEn", "adhkar.items[$i]")
                val repeatCount = obj.requirePositiveInt("repeatCount", "adhkar.items[$i]")
                val surahId = obj.optNullableInt("surahId")
                val startAyah = obj.optNullableInt("startAyah")
                val endAyah = obj.optNullableInt("endAyah")
                if (itemType == DhikrItemType.SUNNAH_SURAH_LINK.name && (surahId == null || surahId !in 1..114)) {
                    throw CompanionParseException("Invalid surahId '$surahId' for SUNNAH_SURAH_LINK $id")
                }
                val searchBlob = ArabicTextNormalizer.normalize(
                    listOfNotNull(titleAr, titleEn, textAr, textEn, sourceAr, sourceEn).joinToString(" "),
                )
                add(
                    DhikrItemEntity(
                        id = id,
                        categoryId = categoryId,
                        itemType = itemType,
                        sortOrder = obj.requirePositiveInt("sortOrder", "adhkar.items[$i]"),
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
                        searchTextNormalized = searchBlob,
                        datasetVersion = obj.requireNonBlankString("datasetVersion", "adhkar.items[$i]"),
                    ),
                )
            }
        }
        if (items.size != expectedItems) {
            throw CompanionParseException(
                "adhkar recordCount mismatch: manifest=$expectedItems, actual=${items.size}",
            )
        }
        return ParsedAdhkarDataset(version = version, categories = categories, items = items)
    }

    fun parseQuranicDuas(rawJson: String): ParsedQuranicDuasDataset {
        val root = parseObject(rawJson, "quranic_duas")
        val manifest = root.requireObject("manifest", "quranic_duas")
        val version = manifest.requireNonBlankString("version", "quranic_duas.manifest")
        val expected = manifest.requirePositiveInt("recordCount", "quranic_duas.manifest")
        val arr = root.requireArray("items", "quranic_duas")
        val seenIds = mutableSetOf<String>()
        val items = buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.requireObjectAt(i, "quranic_duas.items")
                val id = obj.requireNonBlankString("id", "quranic_duas.items[$i]")
                if (!seenIds.add(id)) {
                    throw CompanionParseException("Duplicate quranic dua id: $id")
                }
                val surahId = obj.requirePositiveInt("surahId", "quranic_duas.items[$i]")
                val startAyah = obj.requirePositiveInt("startAyah", "quranic_duas.items[$i]")
                val endAyah = obj.requirePositiveInt("endAyah", "quranic_duas.items[$i]")
                if (surahId !in 1..114 || endAyah < startAyah) {
                    throw CompanionParseException("Invalid Quran pointer $surahId:$startAyah-$endAyah on $id")
                }
                val titleAr = obj.requireNonBlankString("titleAr", "quranic_duas.items[$i]")
                val titleEn = obj.requireNonBlankString("titleEn", "quranic_duas.items[$i]")
                add(
                    QuranicDuaEntity(
                        id = id,
                        categoryId = obj.requireNonBlankString("categoryId", "quranic_duas.items[$i]"),
                        surahId = surahId,
                        startAyah = startAyah,
                        endAyah = endAyah,
                        titleAr = titleAr,
                        titleEn = titleEn,
                        sortOrder = obj.requirePositiveInt("sortOrder", "quranic_duas.items[$i]"),
                        searchTextNormalized = ArabicTextNormalizer.normalize("$titleAr $titleEn $surahId:$startAyah"),
                    ),
                )
            }
        }
        if (items.size != expected) {
            throw CompanionParseException(
                "quranic_duas recordCount mismatch: manifest=$expected, actual=${items.size}",
            )
        }
        return ParsedQuranicDuasDataset(version = version, items = items)
    }

    fun parseHadith(rawJson: String): ParsedHadithDataset {
        val root = parseObject(rawJson, "hadith")
        val manifest = root.requireObject("manifest", "hadith")
        val version = manifest.requireNonBlankString("version", "hadith.manifest")
        val expectedCols = manifest.requirePositiveInt("collectionCount", "hadith.manifest")
        val expectedItems = manifest.requirePositiveInt("recordCount", "hadith.manifest")

        val colsArr = root.requireArray("collections", "hadith")
        val colIds = mutableSetOf<String>()
        val collections = buildList(colsArr.length()) {
            for (i in 0 until colsArr.length()) {
                val obj = colsArr.requireObjectAt(i, "hadith.collections")
                val id = obj.requireNonBlankString("id", "hadith.collections[$i]")
                if (!colIds.add(id)) {
                    throw CompanionParseException("Duplicate hadith collection id: $id")
                }
                add(
                    HadithCollectionEntity(
                        id = id,
                        titleAr = obj.requireNonBlankString("titleAr", "hadith.collections[$i]"),
                        titleEn = obj.requireNonBlankString("titleEn", "hadith.collections[$i]"),
                        descriptionAr = obj.requireNonBlankString("descriptionAr", "hadith.collections[$i]"),
                        descriptionEn = obj.requireNonBlankString("descriptionEn", "hadith.collections[$i]"),
                        sourceAttribution = obj.requireNonBlankString("sourceAttribution", "hadith.collections[$i]"),
                        datasetVersion = obj.requireNonBlankString("datasetVersion", "hadith.collections[$i]"),
                        isBundled = obj.optBoolean("isBundled", true),
                        isDownloaded = obj.optBoolean("isDownloaded", true),
                        hadithCount = obj.requirePositiveInt("hadithCount", "hadith.collections[$i]"),
                        sortOrder = obj.requirePositiveInt("sortOrder", "hadith.collections[$i]"),
                    ),
                )
            }
        }
        if (collections.size != expectedCols) {
            throw CompanionParseException(
                "hadith collectionCount mismatch: manifest=$expectedCols, actual=${collections.size}",
            )
        }

        val itemsArr = root.requireArray("items", "hadith")
        val seenIds = mutableSetOf<String>()
        val items = buildList(itemsArr.length()) {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.requireObjectAt(i, "hadith.items")
                val id = obj.requireNonBlankString("id", "hadith.items[$i]")
                if (!seenIds.add(id)) {
                    throw CompanionParseException("Duplicate hadith id: $id")
                }
                val collectionId = obj.requireNonBlankString("collectionId", "hadith.items[$i]")
                if (collectionId !in colIds) {
                    throw CompanionParseException("Unknown hadith collectionId '$collectionId' on $id")
                }
                val chapterAr = obj.requireNonBlankString("chapterTitleAr", "hadith.items[$i]")
                val chapterEn = obj.requireNonBlankString("chapterTitleEn", "hadith.items[$i]")
                val hadithNumber = obj.requireNonBlankString("hadithNumber", "hadith.items[$i]")
                val titleAr = obj.requireNonBlankString("titleAr", "hadith.items[$i]")
                val titleEn = obj.requireNonBlankString("titleEn", "hadith.items[$i]")
                val textAr = obj.requireNonBlankString("textAr", "hadith.items[$i]")
                val textEn = obj.optNullableString("textEn")
                val gradeAr = obj.requireNonBlankString("gradeAr", "hadith.items[$i]")
                val gradeEn = obj.requireNonBlankString("gradeEn", "hadith.items[$i]")
                val graderAttribution = obj.requireNonBlankString("graderAttribution", "hadith.items[$i]")
                val referenceAr = obj.requireNonBlankString("referenceAr", "hadith.items[$i]")
                val referenceEn = obj.requireNonBlankString("referenceEn", "hadith.items[$i]")
                val explanationAr = obj.optNullableString("explanationAr")
                val explanationEn = obj.optNullableString("explanationEn")
                val sourceVersion = obj.requireNonBlankString("sourceVersion", "hadith.items[$i]")
                val searchBlob = ArabicTextNormalizer.normalize(
                    listOfNotNull(
                        titleAr,
                        titleEn,
                        chapterAr,
                        chapterEn,
                        textAr,
                        textEn,
                        referenceAr,
                        referenceEn,
                        hadithNumber,
                    ).joinToString(" "),
                )
                add(
                    HadithEntity(
                        id = id,
                        collectionId = collectionId,
                        chapterTitleAr = chapterAr,
                        chapterTitleEn = chapterEn,
                        hadithNumber = hadithNumber,
                        titleAr = titleAr,
                        titleEn = titleEn,
                        textAr = textAr,
                        textEn = textEn,
                        gradeAr = gradeAr,
                        gradeEn = gradeEn,
                        graderAttribution = graderAttribution,
                        referenceAr = referenceAr,
                        referenceEn = referenceEn,
                        explanationAr = explanationAr,
                        explanationEn = explanationEn,
                        sourceVersion = sourceVersion,
                        searchTextNormalized = searchBlob,
                        sortOrder = obj.requirePositiveInt("sortOrder", "hadith.items[$i]"),
                    ),
                )
            }
        }
        if (items.size != expectedItems) {
            throw CompanionParseException(
                "hadith recordCount mismatch: manifest=$expectedItems, actual=${items.size}",
            )
        }
        return ParsedHadithDataset(version = version, collections = collections, items = items)
    }

    private fun parseObject(raw: String, label: String): JSONObject =
        try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw CompanionParseException("Malformed JSON in $label", e)
        }

    private fun JSONObject.requireObject(key: String, path: String): JSONObject =
        optJSONObject(key) ?: throw CompanionParseException("Missing object '$key' at $path")

    private fun JSONObject.requireArray(key: String, path: String): JSONArray =
        optJSONArray(key) ?: throw CompanionParseException("Missing array '$key' at $path")

    private fun JSONArray.requireObjectAt(index: Int, path: String): JSONObject =
        optJSONObject(index) ?: throw CompanionParseException("Expected object at $path[$index]")

    private fun JSONObject.requireNonBlankString(key: String, path: String): String {
        if (!has(key) || isNull(key)) {
            throw CompanionParseException("Missing required string '$key' at $path")
        }
        val value = optString(key, "").trim()
        if (value.isEmpty()) {
            throw CompanionParseException("Blank required string '$key' at $path")
        }
        return value
    }

    private fun JSONObject.requirePositiveInt(key: String, path: String): Int {
        if (!has(key) || isNull(key)) {
            throw CompanionParseException("Missing required int '$key' at $path")
        }
        val value = optInt(key, -1)
        if (value < 1) {
            throw CompanionParseException("Expected positive int '$key' at $path, got $value")
        }
        return value
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }
}
