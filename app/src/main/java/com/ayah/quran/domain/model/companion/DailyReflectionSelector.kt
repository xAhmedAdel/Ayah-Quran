package com.ayah.quran.domain.model.companion

object DailyReflectionSelector {

    private const val GOLDEN_PRIME_STRIDE = 2654435761L

    fun selectIndex(epochDay: Long, poolSize: Int): Int {
        require(poolSize > 0) { "poolSize must be positive" }
        if (poolSize == 1) return 0
        val size = poolSize.toLong()
        val rawStep = Math.floorMod(GOLDEN_PRIME_STRIDE, size)
        val step = if (rawStep == 0L) 1L else rawStep
        val normalizedDay = Math.floorMod(epochDay, size)
        return Math.floorMod(normalizedDay * step, size).toInt()
    }
}
