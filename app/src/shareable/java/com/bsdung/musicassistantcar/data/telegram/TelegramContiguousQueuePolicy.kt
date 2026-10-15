package com.bsdung.musicassistantcar.data.telegram

/**
 * Cached queue is contiguous around the selected track.
 * A missing neighbor must stop the queue in that direction,
 * never jump over it to a later song from the same channel.
 */
internal object TelegramContiguousQueuePolicy {
    fun indices(
        order: List<Long>, selected: Long,
        beforeLimit: Int = 8, afterLimit: Int = 8,
        cached: (Long) -> Boolean
    ): List<Int> {
        val at = order.indexOf(selected)
        if (at < 0) return emptyList()
        val preceding = mutableListOf<Int>()
        for (idx in at - 1 downTo maxOf(0, at - beforeLimit)) {
            if (!cached(order[idx])) break
            preceding.add(0, idx)
        }
        val result = preceding + at
        val following = mutableListOf<Int>()
        for (idx in at + 1 until minOf(order.size, at + afterLimit + 1)) {
            if (!cached(order[idx])) break
            following.add(idx)
        }
        return result + following
    }
}
