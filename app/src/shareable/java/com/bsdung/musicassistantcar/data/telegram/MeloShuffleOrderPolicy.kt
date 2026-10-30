package com.bsdung.musicassistantcar.data.telegram

import java.util.Random

/**
 * Pure "play order" contract for the Shuffle (Đảo) toggle. Media3 never shuffles by itself:
 * its flag only means on/off, and [ShareablePlaybackService] keeps the base order, the seed and
 * the resulting play order. No Android API, no logging, no I/O; generic so Telegram (Long message
 * ids) and local music (String media ids) share it.
 */
internal object MeloShuffleOrderPolicy {
    /**
     * [anchor] first (when it is in [base]), the rest permuted once by Fisher-Yates over
     * `java.util.Random(seed)`. Duplicates are dropped, first occurrence wins.
     */
    fun <T> shuffled(base: List<T>, anchor: T?, seed: Long): List<T> {
        val unique = base.distinct()
        val head = anchor != null && unique.contains(anchor)
        val rest = (if (head) unique.filter { it != anchor } else unique).toMutableList()
        val random = Random(seed)
        for (i in rest.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val swap = rest[i]
            rest[i] = rest[j]
            rest[j] = swap
        }
        return if (head) listOf(anchor as T) + rest else rest
    }

    /**
     * Play order after the base queue was configured again.
     * - [enabled] false: [newBase].
     * - [enabled] true and [anchor] is in [oldPlay] and the new base is the same source
     *   (`oldBase` is not empty and at least half of it is still in [newBase]): keep the part of
     *   [oldPlay] that is still in [newBase], then append the songs of [newBase] that [oldPlay]
     *   did not have, shuffled with [seed]. An identical base therefore keeps [oldPlay] as is.
     * - otherwise: `shuffled(newBase, anchor, seed)`.
     * No other heuristic.
     */
    fun <T> rebase(
        oldBase: List<T>, oldPlay: List<T>, newBase: List<T>,
        anchor: T?, enabled: Boolean, seed: Long
    ): List<T> {
        if (!enabled) return newBase
        val newSet = newBase.toHashSet()
        val sameSource = oldBase.isNotEmpty() && oldBase.count { it in newSet } * 2 >= oldBase.size
        if (anchor == null || !oldPlay.contains(anchor) || !sameSource) {
            return shuffled(newBase, anchor, seed)
        }
        val kept = oldPlay.distinct().filter { it in newSet }
        val keptSet = kept.toHashSet()
        val added = newBase.distinct().filter { it !in keptSet }
        return kept + shuffled(added, null, seed)
    }

    /** Shuffle switched: on = `shuffled(base, current, seed)`, off = [base]. */
    fun <T> toggled(base: List<T>, current: T?, enabled: Boolean, seed: Long): List<T> =
        if (enabled) shuffled(base, current, seed) else base

    /**
     * Drop [id] then put it right after [current] ("Phát tiếp theo" wins over Shuffle). [current]
     * missing: [id] goes last. [id] equal to [current] changes nothing. Used for base and play.
     */
    fun <T> insertNext(order: List<T>, current: T?, id: T): List<T> {
        if (current != null && current == id) return order
        val rest = order.filter { it != id }
        val at = if (current == null) -1 else rest.indexOf(current)
        return if (at < 0) rest + id else rest.subList(0, at + 1) + id + rest.subList(at + 1, rest.size)
    }

    /**
     * Base order of the ids still [present]; present ids that are not in [base] follow, in the
     * order of [present].
     */
    fun <T> restore(base: List<T>, present: List<T>): List<T> {
        val presentSet = present.toHashSet()
        val baseSet = base.toHashSet()
        return base.distinct().filter { it in presentSet } + present.distinct().filter { it !in baseSet }
    }
}
