package com.bsdung.musicassistantcar.playback

object StreamCachePolicy {
    const val GIB = 1024L * 1024 * 1024
    const val RESERVE_GIB = 20
    const val DEFAULT_SMALL_GIB = 10
    const val DEFAULT_LARGE_GIB = 30
    val allowedCapsGiB = listOf(5, 10, 20, 30, 50)
    const val PREFETCH_NEXT_COUNT = 2
    const val PREFETCH_UNMETERED_BYTES = 3L * 1024 * 1024
    const val PREFETCH_METERED_BYTES = 512L * 1024

    fun defaultCapGiB(totalBytes: Long): Int =
        if (totalBytes >= 200L * GIB) DEFAULT_LARGE_GIB else DEFAULT_SMALL_GIB

    fun safeCapBytes(desired: Long, free: Long, existing: Long, total: Long): Long {
        val reserve = minOf(RESERVE_GIB * GIB, total / 4)
        return minOf(desired, (free + existing - reserve).coerceAtLeast(0L))
    }

    fun nextIndices(index: Int, count: Int): List<Int> =
        if (index !in 0 until count - 1) emptyList()
        else ((index + 1) until minOf(count, index + PREFETCH_NEXT_COUNT + 1)).toList()
}
