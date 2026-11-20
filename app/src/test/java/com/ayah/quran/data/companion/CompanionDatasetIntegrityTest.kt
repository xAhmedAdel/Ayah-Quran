package com.ayah.quran.data.companion

import com.ayah.quran.core.text.ArabicTextNormalizer
import com.ayah.quran.domain.model.companion.DhikrItemType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompanionDatasetIntegrityTest {

    private val parser = CompanionContentParser()

    private fun assetFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/assets/$relativePath"),
            File("app/src/main/assets/$relativePath"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Asset not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private fun loadQuranVerseKeys(): Set<Pair<Int, Int>> {
        val lines = assetFile("quran/quran-uthmani.txt").readLines(Charsets.UTF_8)
        val keys = mutableSetOf<Pair<Int, Int>>()
        for (raw in lines) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split("|", limit = 3)
            if (parts.size == 3) {
                val surah = parts[0].toIntOrNull()
                val ayah = parts[1].toIntOrNull()
                if (surah != null && ayah != null) {
                    keys.add(surah to ayah)
                }
            }
        }
        return keys
    }

    @Test
    fun adhkarDatasetPassesFullIntegrityAndNonTruncationChecks() {
        val raw = assetFile("companion/adhkar_hisn.json").readText(Charsets.UTF_8)
        val manifest = JSONObject(raw).getJSONObject("manifest")
        assertTrue(manifest.getString("datasetId").isNotBlank())
        assertTrue(manifest.getString("version").isNotBlank())
        assertTrue(manifest.getString("contentSha256").length == 64)
        assertTrue(manifest.getJSONArray("sources").length() >= 2)

        val parsed = parser.parseAdhkar(raw)
        assertTrue(parsed.categories.size >= 16)
        assertTrue(parsed.items.size >= 100)
        assertEquals(parsed.items.size, parsed.items.map { it.id }.toSet().size)

        val morningIstighfar = parsed.items.firstOrNull {
            it.categoryId == "morning" &&
                ArabicTextNormalizer.normalize(it.textAr).contains("اللهم انت ربي لا اله الا انت")
        }
        val eveningIstighfar = parsed.items.firstOrNull {
            it.categoryId == "evening" &&
                ArabicTextNormalizer.normalize(it.textAr).contains("اللهم انت ربي لا اله الا انت")
        }
        assertNotNull("Morning Sayyid al-Istighfar missing", morningIstighfar)
        assertNotNull("Evening Sayyid al-Istighfar missing", eveningIstighfar)
        assertTrue(
            "Morning Sayyid al-Istighfar appears truncated",
            ArabicTextNormalizer.normalize(morningIstighfar!!.textAr).contains("لا يغفر الذنوب الا انت"),
        )
        assertTrue(
            "Evening Sayyid al-Istighfar appears truncated",
            ArabicTextNormalizer.normalize(eveningIstighfar!!.textAr).contains("لا يغفر الذنوب الا انت"),
        )

        val sunnahLinks = parsed.items.filter { it.itemType == DhikrItemType.SUNNAH_SURAH_LINK.name }
        assertEquals(setOf(18, 67, 32), sunnahLinks.mapNotNull { it.surahId }.toSet())

        parsed.items.forEach { item ->
            assertTrue("Blank Arabic text for ${item.id}", item.textAr.isNotBlank())
            assertTrue("Blank source for ${item.id}", item.sourceAr.isNotBlank())
            assertTrue("Invalid repeat count for ${item.id}", item.repeatCount >= 1)
            assertTrue("Blank normalized search text for ${item.id}", item.searchTextNormalized.isNotBlank())
            assertFalse("Unexpected ellipsis truncation in ${item.id}", item.textAr.endsWith("..."))
        }
    }

    @Test
    fun quranicDuasPointersAllResolveAgainstVerifiedTanzilUthmaniText() {
        val quranKeys = loadQuranVerseKeys()
        assertEquals(6236, quranKeys.size)

        val raw = assetFile("companion/quranic_duas.json").readText(Charsets.UTF_8)
        val manifest = JSONObject(raw).getJSONObject("manifest")
        assertTrue(manifest.getString("license").contains("Tanzil"))
        assertTrue(manifest.getString("contentSha256").length == 64)

        val parsed = parser.parseQuranicDuas(raw)
        assertEquals(40, parsed.items.size)
        assertEquals(parsed.items.size, parsed.items.map { it.id }.toSet().size)

        parsed.items.forEach { dua ->
            for (ayahNum in dua.startAyah..dua.endAyah) {
                assertTrue(
                    "Invalid Quranic Dua pointer ${dua.id} -> ${dua.surahId}:$ayahNum",
                    (dua.surahId to ayahNum) in quranKeys,
                )
            }
        }
    }

    @Test
    fun hadithDatasetPassesAttributionVersionAndCompletenessChecks() {
        val raw = assetFile("companion/hadith_starter.json").readText(Charsets.UTF_8)
        val manifest = JSONObject(raw).getJSONObject("manifest")
        assertTrue(manifest.getString("datasetId").isNotBlank())
        assertTrue(manifest.getString("contentSha256").length == 64)
        assertTrue(manifest.getJSONArray("sources").length() >= 2)

        val parsed = parser.parseHadith(raw)
        assertEquals(6, parsed.collections.size)
        assertTrue(parsed.items.size >= 200)
        assertEquals(parsed.items.size, parsed.items.map { it.id }.toSet().size)

        val nawawi = parsed.items.filter { it.collectionId == "nawawi40" }
        assertEquals(42, nawawi.size)

        val henc = parsed.items.filter { it.collectionId.startsWith("hadeethenc_") }
        assertTrue("Expected >= 150 HadeethEnc entries, got ${henc.size}", henc.size >= 150)
        henc.forEach { item ->
            assertEquals("HadeethEnc.com", item.graderAttribution)
            assertTrue("Missing HadeethEnc version on ${item.id}", item.sourceVersion.startsWith("hadeethenc-"))
            assertNotNull("Missing verified English translation on ${item.id}", item.textEn)
            assertTrue("Blank English translation on ${item.id}", item.textEn!!.isNotBlank())
            assertTrue("Blank Arabic text on ${item.id}", item.textAr.isNotBlank())
            assertTrue("Blank reference on ${item.id}", item.referenceAr.isNotBlank())
        }

        parsed.collections.forEach { col ->
            val actualCount = parsed.items.count { it.collectionId == col.id }
            assertEquals("Collection ${col.id} hadithCount mismatch", col.hadithCount, actualCount)
            assertTrue("Blank sourceAttribution on ${col.id}", col.sourceAttribution.isNotBlank())
            assertTrue("Blank datasetVersion on ${col.id}", col.datasetVersion.isNotBlank())
        }
    }

    @Test
    fun hisnAlMuslimChaptersNeverShipUnverifiedEnglishTranslations() {
        val raw = assetFile("companion/adhkar_hisn.json").readText(Charsets.UTF_8)
        val parsed = parser.parseAdhkar(raw)
        val nonMorningEvening = parsed.items.filter {
            it.categoryId !in setOf("morning", "evening") &&
                it.itemType != DhikrItemType.SUNNAH_SURAH_LINK.name
        }
        assertTrue(nonMorningEvening.isNotEmpty())
        nonMorningEvening.forEach { item ->
            assertTrue(
                "Unexpected unverified English translation on Hisn chapter item ${item.id}",
                item.textEn.isNullOrBlank(),
            )
        }
    }

    @Test
    fun inAppLicensesAndProguardRulesIncludeAllCompanionAttributionsAndProjections() {
        val enStrings = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).first { it.exists() }.readText(Charsets.UTF_8)
        val arStrings = listOf(
            File("src/main/res/values-ar/strings.xml"),
            File("app/src/main/res/values-ar/strings.xml"),
        ).first { it.exists() }.readText(Charsets.UTF_8)
        val proguard = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro"),
        ).first { it.exists() }.readText(Charsets.UTF_8)

        val requiredTokens = listOf(
            "Tanzil",
            "GeoNames",
            "Seen-Arabic/Morning-And-Evening-Adhkar-DB",
            "asellam/HisnElMuslim",
            "fawazahmed0/hadith-api",
            "HadeethEnc.com",
            "hadeethenc-api-v1-2026-11-20",
        )
        requiredTokens.forEach { token ->
            assertTrue("Missing $token in EN strings.xml", enStrings.contains(token))
        }
        listOf(
            "tanzil.net",
            "GeoNames",
            "Seen-Arabic/Morning-And-Evening-Adhkar-DB",
            "asellam/HisnElMuslim",
            "fawazahmed0/hadith-api",
            "HadeethEnc.com",
            "hadeethenc-api-v1-2026-11-20",
        ).forEach { token ->
            assertTrue("Missing $token in AR strings.xml", arStrings.contains(token))
        }
        assertTrue(proguard.contains("-keep class com.ayah.quran.core.database.entity.** { *; }"))
        assertTrue(proguard.contains("-keep class com.ayah.quran.core.database.dao.companion.HadithWithUserState { *; }"))
    }
}
