package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E96 P5B3 REWORK-01 (c): which operations a run may send, and how many. Pure, no channel, no TDLib. */
class TransmitPlannerTest {
    private fun add(n: Long, status: OutboxStatus = OutboxStatus.PENDING) =
        OutboxEntry(addOp("dev-a", n, -100123L, n * 4_194_304L), status, 0)

    private fun remove(n: Long, observing: Long, status: OutboxStatus = OutboxStatus.PENDING) =
        OutboxEntry(removeOp("dev-a", n, -100123L, observing * 4_194_304L, setOf(Dot("dev-a", observing))), status, 0)

    private fun planned(plan: TransmitPlan) = (plan as TransmitPlan.Planned).ops.map { it.opId }

    @Test fun t04_fiveWaitingWithABudgetOfThreeChoosesTheFirstThreeInOrder() {
        val waiting = (1L..5L).map { add(it) }
        assertEquals(listOf("dev-a#1", "dev-a#2", "dev-a#3"), planned(TransmitPlanner.plan(waiting, 3, "dev-a")))
        assertEquals(listOf("dev-a#1"), planned(TransmitPlanner.plan(waiting, 1, "dev-a")))
        assertEquals("a big budget never plans more than waits", 5, planned(TransmitPlanner.plan(waiting, 3_000, "dev-a")).size)
    }

    @Test fun t05_noBudgetMeansNothingIsPlanned_zeroNegativeAndMissing() {
        val waiting = (1L..5L).map { add(it) }
        assertEquals(TransmitPlan.LimitReached, TransmitPlanner.plan(waiting, 0, "dev-a"))
        assertEquals(TransmitPlan.LimitReached, TransmitPlanner.plan(waiting, -1, "dev-a"))
        assertEquals(TransmitPlan.LimitReached, TransmitPlanner.plan(waiting, Int.MIN_VALUE, "dev-a"))
        assertEquals(0, SyncSendBudget.clamp(null))
        assertEquals(0, SyncSendBudget.clamp(0))
        assertEquals(0, SyncSendBudget.clamp(-4))
        assertEquals(0, SyncSendBudget.clamp(Int.MIN_VALUE))
    }

    @Test fun theProductionCapIsThreeWhateverIsAsked() {
        assertEquals(1, SyncSendBudget.clamp(1))
        assertEquals(3, SyncSendBudget.clamp(3))
        assertEquals(3, SyncSendBudget.clamp(4))
        assertEquals(3, SyncSendBudget.clamp(Int.MAX_VALUE))
        assertEquals("a unit fake may be given a larger cap on purpose", 50, SyncSendBudget.clamp(500, max = 50))
        assertEquals("a cap that is itself nonsense gives nothing", 0, SyncSendBudget.clamp(5, max = -2))
    }

    @Test fun anUnconfirmedOperationBlocksEverythingBehindItWhateverTheBudget() {
        val head = listOf(add(1, OutboxStatus.RETRY_REQUIRED), add(2), add(3))
        assertEquals(TransmitPlan.UnconfirmedBlocked, TransmitPlanner.plan(head, 3, "dev-a"))
        assertEquals(TransmitPlan.UnconfirmedBlocked, TransmitPlanner.plan(head, 3_000, "dev-a"))
        // also when it is not at the head: nothing is skipped over, nothing is reordered
        val later = listOf(add(1), add(2, OutboxStatus.RETRY_REQUIRED), add(3))
        assertEquals(TransmitPlan.UnconfirmedBlocked, TransmitPlanner.plan(later, 1, "dev-a"))
        // and it wins over a missing budget: the person must be told about the doubt first
        assertEquals(TransmitPlan.UnconfirmedBlocked, TransmitPlanner.plan(head, 0, "dev-a"))
    }

    @Test fun t18_aRemovalIsOnlyChosenTogetherWithOrAfterTheAdditionItObserved() {
        // add#1, add#2, remove(add#1), add#3
        val waiting = listOf(add(1), add(2), remove(3, observing = 1), add(4))
        assertEquals(listOf("dev-a#1", "dev-a#2", "dev-a#3"), planned(TransmitPlanner.plan(waiting, 3, "dev-a")))
        assertEquals(listOf("dev-a#1", "dev-a#2"), planned(TransmitPlanner.plan(waiting, 2, "dev-a")))
    }

    @Test fun t18_aRemovalWhoseAdditionIsNotAheadOfItIsBlocked() {
        // (cannot arise from a real outbox, which is written in order; the planner still refuses it)
        val broken = listOf(remove(1, observing = 2), add(2))
        assertEquals(TransmitPlan.DependencyBlocked, TransmitPlanner.plan(broken, 3, "dev-a"))
        // an addition that is already acknowledged (not in the waiting list) or belongs to another device is fine
        assertEquals(listOf("dev-a#9"), planned(TransmitPlanner.plan(listOf(remove(9, observing = 4)), 1, "dev-a")))
        val foreign = OutboxEntry(removeOp("dev-a", 5, -100123L, 1L, setOf(Dot("dev-b", 3))), OutboxStatus.PENDING, 0)
        assertEquals(listOf("dev-a#5"), planned(TransmitPlanner.plan(listOf(foreign), 1, "dev-a")))
    }

    @Test fun anEmptyQueueIsNeverAPlanWithOperations() {
        assertEquals(TransmitPlan.LimitReached, TransmitPlanner.plan(emptyList(), 3, "dev-a"))
        assertTrue(TransmitPlanner.plan(emptyList(), 0, "dev-a") !is TransmitPlan.Planned)
    }
}
