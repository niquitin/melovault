package com.bsdung.musicassistantcar.playback

/** Pure safety gates for asynchronous persisted-queue restoration. */
internal object QueueStartupRestorePolicy {
    fun shouldApply(serviceShuttingDown: Boolean, hasSession: Boolean, currentQueueSize: Int): Boolean =
        !serviceShuttingDown && hasSession && currentQueueSize == 0

    fun targetIndex(savedIds: List<String>, savedIndex: Int, resolvedIds: List<String>): Int {
        if (resolvedIds.isEmpty()) return 0
        val target = savedIds.getOrNull(savedIndex)
        return resolvedIds.indexOfFirst { it == target }.takeIf { it >= 0 } ?: 0
    }
}
