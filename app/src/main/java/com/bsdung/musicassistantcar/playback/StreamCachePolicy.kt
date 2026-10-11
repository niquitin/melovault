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
    const val MIN_SD_USABLE_BYTES = 256L * 1024 * 1024 // 256 MiB
    const val MIN_STREAM_CACHE_BYTES = 64L * 1024 * 1024 // 64 MiB

    fun recommendedCapGiB(
        totalBytes: Long,
        usableBytes: Long,
        existingBytes: Long = 0L,
    ): Int {
        val targetDefault = if (totalBytes >= 200L * GIB) DEFAULT_LARGE_GIB else DEFAULT_SMALL_GIB
        val reserve = minOf(RESERVE_GIB * GIB, totalBytes / 4)
        val availableAboveReserve = (usableBytes + existingBytes - reserve).coerceAtLeast(0L)
        return when {
            targetDefault == DEFAULT_LARGE_GIB && availableAboveReserve >= DEFAULT_LARGE_GIB * GIB -> DEFAULT_LARGE_GIB
            availableAboveReserve >= DEFAULT_SMALL_GIB * GIB -> DEFAULT_SMALL_GIB
            availableAboveReserve >= 5L * GIB -> 5
            else -> DEFAULT_SMALL_GIB
        }
    }

    @JvmOverloads
    fun defaultCapGiB(
        totalBytes: Long,
        usableBytes: Long = totalBytes,
        existingBytes: Long = 0L,
    ): Int = recommendedCapGiB(totalBytes, usableBytes, existingBytes)

    fun safeCapBytes(desired: Long, free: Long, existing: Long, total: Long): Long {
        val reserve = minOf(RESERVE_GIB * GIB, total / 4)
        return minOf(desired, (free + existing - reserve).coerceAtLeast(0L))
    }

    fun hasSufficientSpace(free: Long, total: Long): Boolean {
        val reserve = minOf(RESERVE_GIB * GIB, total / 4)
        return free > reserve
    }

    fun startupIndices(index: Int, count: Int): List<Int> =
        if (count <= 0 || index !in 0 until count) emptyList()
        else listOf(index)

    fun nextIndices(index: Int, count: Int): List<Int> =
        if (index !in 0 until count - 1) emptyList()
        else ((index + 1) until minOf(count, index + PREFETCH_NEXT_COUNT + 1)).toList()
}
