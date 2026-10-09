package com.bsdung.musicassistantcar.playback

/** Source-index mapping prevents an unavailable track's seek position reaching another song. */
internal data class QueueRestoreTarget(val index: Int, val positionMs: Long)

/** Pure safety gates for asynchronous persisted-queue restoration. */
internal object QueueStartupRestorePolicy {
    fun shouldApply(serviceShuttingDown: Boolean, hasSession: Boolean, currentQueueSize: Int): Boolean =
        !serviceShuttingDown && hasSession && currentQueueSize == 0

    /**
     * Legacy identity-only selector. Distinguish repeated occurrences in a playlist.
     * Production restore uses [selectTarget] and original source indices instead.
     */
    fun targetIndex(savedIds: List<String>, savedIndex: Int, resolvedIds: List<String>): Int {
        if (resolvedIds.isEmpty()) return 0
        val target = savedIds.getOrNull(savedIndex) ?: return 0
        val occurrence = savedIds.take(savedIndex + 1).count { it == target }
        var seen = 0
        resolvedIds.forEachIndexed { index, id ->
            if (id == target && ++seen == occurrence) return index
        }
        return resolvedIds.indexOf(target).takeIf { it >= 0 } ?: 0
    }

    /**
     * Every index refers to the original saved queue (in restored playback order).
     * Only the exact saved occurrence may inherit its seek position.
     * If it is inaccessible, prefer the next available song, starting at zero.
     */
    fun selectTarget(
        savedIndex: Int,
        savedPositionMs: Long,
        restoredSourceIndices: List<Int>,
    ): QueueRestoreTarget {
        if (restoredSourceIndices.isEmpty()) return QueueRestoreTarget(0, 0L)
        val matchedIndex = restoredSourceIndices.indexOf(savedIndex)
        if (matchedIndex >= 0) {
            return QueueRestoreTarget(matchedIndex, savedPositionMs.coerceAtLeast(0L))
        }
        val next = restoredSourceIndices.indexOfFirst { it > savedIndex }
        val fallback = if (next >= 0) next else restoredSourceIndices.lastIndex
        return QueueRestoreTarget(fallback, 0L)
    }
}
