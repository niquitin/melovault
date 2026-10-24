package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P5B3 REWORK-01: how many outbox OPERATIONS one run may hand to Telegram. Pure arithmetic, no TDLib.
 *
 * The budget is counted in individual operations (not songs) and is always an explicit number the caller passes:
 *  - there is no "unlimited": an absent, zero or negative budget means NOTHING is sent (read and acknowledge only);
 *  - production callers never get more than [PRODUCTION_MAX] per run ([clamp]); only an isolated unit fake may pass more
 *    to the coordinator directly.
 */
internal object SyncSendBudget {
    const val PRODUCTION_MAX = 3

    /** Anything that is not a number between 1 and [max] is no permission at all. */
    fun clamp(requested: Int?, max: Int = PRODUCTION_MAX): Int = when {
        requested == null || requested <= 0 -> 0
        else -> minOf(requested, max.coerceAtLeast(0))
    }
}

internal sealed class TransmitPlan {
    /** [ops] is a contiguous prefix of the waiting operations, in outbox order; never empty. */
    data class Planned(val ops: List<PlaylistOp>) : TransmitPlan()

    /** The budget is spent (or was never given): operations still wait, untouched. */
    object LimitReached : TransmitPlan()

    /** At least one operation was handed over before and nobody knows whether it arrived: nothing at all is sent. */
    object UnconfirmedBlocked : TransmitPlan()

    /** A removal would be sent without the addition it observed (which is not among the chosen operations). */
    object DependencyBlocked : TransmitPlan()
}

/**
 * Chooses what a run may send. Rules (all enforced here, none by the caller):
 *  - the choice is always the FIRST k waiting operations in outbox order, k = min(budget, waiting): nothing is skipped,
 *    reordered or merged;
 *  - while ANY waiting operation is RETRY_REQUIRED (delivery unknown) nothing is planned - not even the pending ones
 *    behind it. The only way out is to find that operation in the channel history (an acknowledgement);
 *  - a REMOVE may only observe tags of this device's additions that are acknowledged, or that sit EARLIER in the chosen
 *    prefix; otherwise it would reach the channel before the addition it refers to.
 */
internal object TransmitPlanner {
    fun plan(waiting: List<OutboxEntry>, budget: Int, ownDevice: String): TransmitPlan {
        if (waiting.any { it.status == OutboxStatus.RETRY_REQUIRED }) return TransmitPlan.UnconfirmedBlocked
        val k = minOf(budget.coerceAtLeast(0), waiting.size)
        if (k == 0) return TransmitPlan.LimitReached
        val chosen = waiting.take(k).map { it.op }
        val addIndex = HashMap<Dot, Int>()
        waiting.forEachIndexed { i, entry ->
            if (entry.op.type == OpType.ADD) entry.op.dots.forEach { addIndex[it] = i }
        }
        chosen.forEachIndexed { i, op ->
            if (op.type != OpType.REMOVE) return@forEachIndexed
            for (dot in op.dots) {
                if (dot.device != ownDevice) continue
                val at = addIndex[dot] ?: continue // acknowledged earlier: fine
                if (at >= i) return TransmitPlan.DependencyBlocked
            }
        }
        return TransmitPlan.Planned(chosen)
    }
}
