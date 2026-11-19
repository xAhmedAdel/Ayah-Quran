package com.ayah.quran.domain.update

import org.json.JSONArray
import org.json.JSONObject

/** The newest published release as seen by the updater. */
data class AppUpdateInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotesUrl: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

/**
 * Pure GitHub-Releases JSON → update decision logic (no Android deps) so
 * JVM tests can pin the behavior against real API response shapes:
 * - drafts are skipped (they are invisible/unfinished),
 * - tags not strictly newer than the installed version are skipped,
 * - the newest surviving release with an .apk asset wins,
 * - arm64/universal APK assets are preferred when several exist.
 */
object AppUpdateReleaseParser {

    fun newestRelease(
        releases: JSONArray,
        currentVersionName: String,
    ): AppUpdateInfo? {
        var best: AppUpdateInfo? = null
        var bestVersion: AppUpdateVersion.Parsed? = null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            val tag = release.optString("tag_name")
            if (!AppUpdateVersion.isNewer(tag, currentVersionName)) continue
            val parsed = AppUpdateVersion.parse(tag) ?: continue
            if (bestVersion != null && parsed <= bestVersion) continue
            val asset = pickApkAsset(release) ?: continue
            best = AppUpdateInfo(
                tagName = tag,
                releaseName = release.optString("name").ifBlank { tag },
                releaseNotesUrl = release.optString("html_url"),
                apkUrl = asset.optString("browser_download_url"),
                apkSizeBytes = asset.optLong("size", 0L),
            )
            bestVersion = parsed
        }
        return best
    }

    /** Prefers arm64 (smallest, our target device) over universal over any APK. */
    fun pickApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        var universal: JSONObject? = null
        var fallback: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            if (name.contains("arm64", ignoreCase = true)) return asset
            if (universal == null && name.contains("universal", ignoreCase = true)) {
                universal = asset
            }
            if (fallback == null) fallback = asset
        }
        return universal ?: fallback
    }
}
