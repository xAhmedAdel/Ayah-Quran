package com.ayah.quran.core.quran

import javax.inject.Inject
import kotlin.random.Random

/**
 * Pure ayah-selection algorithms for the wallpaper engine.
 *
 * The pool always comes from the imported verified dataset (global verse ids
 * 1..6236, or the subset of one surah). Selection can never produce a
 * reference outside the pool — fabricated or missing content is impossible
 * by construction.
 */
class AyahSelector @Inject constructor() {

    /**
     * Random selection avoiding recently used verses. When every candidate
     * is in [recent], the avoidance window resets (full pool) so selection
     * can never deadlock.
     */
    fun selectRandom(pool: List<Long>, recent: Set<Long>, random: Random = Random.Default): Long {
        require(pool.isNotEmpty()) { "selection pool must not be empty" }
        val candidates = pool.filterNot { it in recent }
        val effective = candidates.ifEmpty { pool }
        return effective[random.nextInt(effective.size)]
    }

    /**
     * Sequential walk over the pool. [cursor] is the persistent position
     * (number of previously issued selections). Returns the selected global
     * id and the updated cursor.
     */
    fun selectSequential(cursor: Long, poolSize: Int): Pair<Long, Long> {
        require(poolSize > 0) { "selection pool must not be empty" }
        val normalized = if (cursor < 0) 0 else cursor
        val index = (normalized % poolSize).toInt()
        return index + 1L to normalized + 1
    }

    /**
     * Deterministic daily selection: stable for a given [epochDay], varies
     * across days, and cycles through the pool.
     */
    fun selectDaily(epochDay: Long, poolSize: Int): Long {
        require(poolSize > 0) { "selection pool must not be empty" }
        val day = if (epochDay < 0) 0 else epochDay
        return (day % poolSize).toInt() + 1L
    }
}
