package com.ayah.quran.domain.update

/**
 * Parses and compares the app's version tags (GitHub release tag_name vs
 * BuildConfig.VERSION_NAME), e.g. "v1.0.0-beta.2".
 *
 * Comparison rules (matching the project's release workflow):
 * - Numeric core: 1.2.0 < 1.10.0 (per-component numeric, not lexical).
 * - A release (no prerelease suffix) is NEWER than any prerelease of the
 *   same core (1.0.0 > 1.0.0-beta.9).
 * - Same prerelease channel compares numerically: beta.2 > beta.1.
 * - Channel order: alpha < beta < rc.
 */
object AppUpdateVersion {

    /** Channel ranks; a release (null channel) outranks all of them. */
    private val channelRank = mapOf("alpha" to 0, "beta" to 1, "rc" to 2)

    data class Parsed(
        val core: List<Int>,
        /** null for a full release; e.g. ("beta", 2) for v1.0.0-beta.2. */
        val channel: String?,
        val channelNumber: Int,
    ) : Comparable<Parsed> {
        override fun compareTo(other: Parsed): Int {
            // Pad cores to equal length so 1.0 == 1.0.0.
            val n = maxOf(core.size, other.core.size)
            for (i in 0 until n) {
                val a = core.getOrNull(i) ?: 0
                val b = other.core.getOrNull(i) ?: 0
                if (a != b) return a.compareTo(b)
            }
            val aRank = channel?.let { channelRank[it] } ?: Int.MAX_VALUE
            val bRank = other.channel?.let { channelRank[it] } ?: Int.MAX_VALUE
            if (aRank != bRank) return aRank.compareTo(bRank)
            return channelNumber.compareTo(other.channelNumber)
        }
    }

    /** Accepts "1.0.0", "v1.0.0", "1.0.0-beta.2", "v1.0.0-beta.10". */
    fun parse(raw: String?): Parsed? {
        if (raw.isNullOrBlank()) return null
        val tag = raw.trim().removePrefix("v").removePrefix("V")
        val parts = tag.split("-")
        val coreParts = parts[0].split(".")
        if (coreParts.isEmpty() || coreParts.any { it.toIntOrNull() == null }) return null
        val core = coreParts.map { it.toInt() }
        if (parts.size == 1) return Parsed(core, channel = null, channelNumber = 0)
        // Prerelease = "beta.2" / "rc.1" / "beta2".
        val pre = parts.drop(1).joinToString("-")
        val match = Regex("^(alpha|beta|rc)\\.?(\\d+)$").find(pre) ?: return null
        val (channel, num) = match.destructured
        return Parsed(core, channel, num.toInt())
    }

    /** True when [candidate] (the GitHub tag) is strictly newer than [current] (VERSION_NAME). */
    fun isNewer(candidate: String?, current: String?): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return true
        return a > b
    }
}
