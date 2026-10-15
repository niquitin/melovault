package com.bsdung.musicassistantcar.data.telegram

/**
 * TDLib-independent selection for one-step-ahead prefetch.
 *
 * The caller must still verify the authenticated account, TDLib file ID
 * and canonical ownership of any downloaded file. This policy deliberately
 * contains no path, credentials, logging or persistence.
 */
internal object TelegramPrefetchOrderPolicy {
    fun immediateSuccessor(order: List<Long>, current: Long): Long? {
        if (current <= 0L) return null
        val position = order.indexOf(current)
        if (position < 0 || position + 1 >= order.size) return null
        return order[position + 1].takeIf { it > 0L && it != current }
    }

    fun isStillNext(order: List<Long>, current: Long, candidate: Long): Boolean =
        immediateSuccessor(order, current) == candidate && candidate > 0L
}
