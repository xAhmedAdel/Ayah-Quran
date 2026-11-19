package com.ayah.quran.domain.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateReleaseParserTest {

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<Pair<String, Long>>,
    ): String {
        val assetJson = assets.joinToString(",") { (name, size) ->
            """{"name":"$name","size":$size,
               "browser_download_url":"https://github.com/x/x/releases/download/$tag/$name"}"""
        }
        return """
        {"tag_name":"$tag","name":"Ayah $tag","draft":$draft,"prerelease":$prerelease,
         "html_url":"https://github.com/xAhmedAdel/Ayah-Quran/releases/tag/$tag",
         "assets":[$assetJson]}
        """.trimIndent()
    }

    @Test
    fun picksNewestNewerReleaseWithApk() {
        val releases = JSONArray(
            "[" + listOf(
                release("v1.0.0-beta.1", assets = listOf("Ayah-v1.0-beta.apk" to 12_000_000L)),
                release("v1.0.0-beta.2", assets = listOf("Ayah-v1.0.0-beta.2.apk" to 12_500_000L)),
                release("v1.0.0-beta.3", draft = true, assets = listOf("x.apk" to 1L)),
            ).joinToString(",") + "]",
        )
        val newest = AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.1")
        assertNotNull(newest)
        assertEquals("v1.0.0-beta.2", newest!!.tagName)
        assertEquals(12_500_000L, newest.apkSizeBytes)
    }

    @Test
    fun emptyReleasesYieldsNull() {
        assertNull(AppUpdateReleaseParser.newestRelease(JSONArray("[]"), "1.0.0-beta.1"))
    }

    @Test
    fun noNewerReleaseYieldsNull() {
        val releases = JSONArray(
            "[" + listOf(
                release("v1.0.0-beta.1", assets = listOf("Ayah.apk" to 12_000_000L)),
            ).joinToString(",") + "]",
        )
        assertNull(AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.2"))
        assertNull(AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.1"))
    }

    @Test
    fun releaseWithoutApkAssetIsSkipped() {
        val releases = JSONArray(
            "[" + listOf(
                release("v1.0.0-beta.2", assets = listOf("notes.md" to 100L)),
                release("v1.0.0-beta.1", assets = listOf("Ayah.apk" to 12_000_000L)),
            ).joinToString(",") + "]",
        )
        // beta.2 is newer but has no APK → falls back to beta.1.
        val newest = AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.1")
        assertNull(newest) // beta.1 is not NEWER than the installed beta.1
    }

    @Test
    fun arm64AssetPreferredOverGenericApk() {
        val releases = JSONArray(
            "[" + listOf(
                release(
                    "v1.0.0-beta.2",
                    assets = listOf(
                        "Ayah-universal.apk" to 20_000_000L,
                        "Ayah-arm64-v8a.apk" to 12_000_000L,
                        "Ayah-armeabi.apk" to 11_000_000L,
                    ),
                ),
            ).joinToString(",") + "]",
        )
        val newest = AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.1")
        assertNotNull(newest)
        assertTrue(newest!!.apkUrl.contains("Ayah-arm64-v8a.apk"))
    }

    @Test
    fun prereleaseTagsAreHandledLikeTheWorkflow() {
        // Real-world GitHub prerelease list — /releases returns them fine
        // while /releases/latest would 404. The updater must still see them.
        val releases = JSONArray(
            "[" + listOf(
                release(
                    "v1.0.0-beta.2",
                    prerelease = true,
                    assets = listOf("Ayah-v1.0.0-beta.2-arm64.apk" to 12_300_000L),
                ),
            ).joinToString(",") + "]",
        )
        val newest = AppUpdateReleaseParser.newestRelease(releases, "1.0.0-beta.1")
        assertNotNull(newest)
        assertEquals("v1.0.0-beta.2", newest!!.tagName)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
