package com.ayah.quran.data.companion

import org.junit.Assert.assertThrows
import org.junit.Test

class CompanionContentParserMalformedInputTest {

    private val parser = CompanionContentParser()

    @Test
    fun rejectsMalformedJson() {
        assertThrows(CompanionParseException::class.java) {
            parser.parseAdhkar("{not valid json")
        }
        assertThrows(CompanionParseException::class.java) {
            parser.parseQuranicDuas("[1,2,3]")
        }
        assertThrows(CompanionParseException::class.java) {
            parser.parseHadith("")
        }
    }

    @Test
    fun rejectsDuplicateAdhkarIds() {
        val json = """
            {
              "manifest": { "version": "1.0", "categoryCount": 1, "recordCount": 2 },
              "categories": [
                { "id": "morning", "sectionType": "ADHKAR_SESSION", "titleAr": "الصباح", "titleEn": "Morning", "sortOrder": 1 }
              ],
              "items": [
                { "id": "dup", "categoryId": "morning", "itemType": "DHIKR", "sortOrder": 1, "titleAr": "أ", "titleEn": "A", "textAr": "سبحان الله", "sourceAr": "مسلم", "sourceEn": "Muslim", "repeatCount": 3, "datasetVersion": "v1" },
                { "id": "dup", "categoryId": "morning", "itemType": "DHIKR", "sortOrder": 2, "titleAr": "ب", "titleEn": "B", "textAr": "الحمد لله", "sourceAr": "مسلم", "sourceEn": "Muslim", "repeatCount": 3, "datasetVersion": "v1" }
              ]
            }
        """.trimIndent()
        assertThrows(CompanionParseException::class.java) { parser.parseAdhkar(json) }
    }

    @Test
    fun rejectsInvalidQuranicDuaPointerRange() {
        val json = """
            {
              "manifest": { "version": "1.0", "recordCount": 1 },
              "items": [
                { "id": "bad_1", "categoryId": "dua_guidance", "surahId": 115, "startAyah": 1, "endAyah": 1, "titleAr": "دعاء", "titleEn": "Dua", "sortOrder": 1 }
              ]
            }
        """.trimIndent()
        assertThrows(CompanionParseException::class.java) { parser.parseQuranicDuas(json) }
    }

    @Test
    fun rejectsHadithWithMissingGraderAttributionOrVersion() {
        val json = """
            {
              "manifest": { "version": "1.0", "collectionCount": 1, "recordCount": 1 },
              "collections": [
                { "id": "c1", "titleAr": "مجموعة", "titleEn": "Col", "descriptionAr": "وصف", "descriptionEn": "Desc", "sourceAttribution": "Src", "datasetVersion": "v1", "hadithCount": 1, "sortOrder": 1 }
              ],
              "items": [
                { "id": "h1", "collectionId": "c1", "chapterTitleAr": "باب", "chapterTitleEn": "Ch", "hadithNumber": "1", "titleAr": "عنوان", "titleEn": "Title", "textAr": "متن", "gradeAr": "صحيح", "gradeEn": "Sahih", "graderAttribution": "", "referenceAr": "البخاري", "referenceEn": "Bukhari", "sourceVersion": "v1", "sortOrder": 1 }
              ]
            }
        """.trimIndent()
        assertThrows(CompanionParseException::class.java) { parser.parseHadith(json) }
    }

    @Test
    fun rejectsManifestCountMismatch() {
        val json = """
            {
              "manifest": { "version": "1.0", "recordCount": 2 },
              "items": [
                { "id": "q1", "categoryId": "dua_guidance", "surahId": 1, "startAyah": 6, "endAyah": 7, "titleAr": "دعاء", "titleEn": "Dua", "sortOrder": 1 }
              ]
            }
        """.trimIndent()
        assertThrows(CompanionParseException::class.java) { parser.parseQuranicDuas(json) }
    }
}
