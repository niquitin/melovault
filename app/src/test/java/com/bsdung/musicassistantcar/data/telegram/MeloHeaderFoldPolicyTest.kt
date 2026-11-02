package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.HeaderFold.ScrollPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloHeaderFoldPolicyTest {

    private val threshold = 42 // 16 dp at 2.625

    // ---- scrollDeltaPx -----------------------------------------------------------------------------

    @Test
    fun deltaOnTheSameRowIsTheTopMovement() {
        assertEquals(40, MeloLibraryPolicy.scrollDeltaPx(3, -20, 3, -60, null, 210))
        assertEquals(-15, MeloLibraryPolicy.scrollDeltaPx(3, -60, 3, -45, null, 210))
        assertEquals(0, MeloLibraryPolicy.scrollDeltaPx(3, -60, 3, -60, null, 210))
    }

    @Test
    fun noReferencePointMeansNoDelta() {
        assertEquals(0, MeloLibraryPolicy.scrollDeltaPx(-1, 0, 7, -30, null, 210))
    }

    @Test
    fun crossingARowBoundaryDownwardUsesWhereTheNewFirstRowWas() {
        // Row 3 is 230 px tall where the pitch guess is 210: the old `first * pitch - top` went negative here.
        // Row 3 spans -225..5; the list moves 10 px, row 4 becomes first at -5, and was at 5 before.
        assertEquals(10, MeloLibraryPolicy.scrollDeltaPx(3, -225, 4, -5, null, 210, newFirstTopBefore = 5))
        val oldFormula = (4 * 210 - -5) - (3 * 210 - -225)
        assertTrue("the old formula went the wrong way", oldFormula < 0)
    }

    @Test
    fun crossingARowBoundaryUpwardUsesWhereTheOldFirstRowIsNow() {
        // Row 4 was first at -5; the list moved 10 px back, row 3 is first again and row 4 is at 5.
        assertEquals(-10, MeloLibraryPolicy.scrollDeltaPx(4, -5, 3, -225, prevFirstTopNow = 5, fallbackPitchPx = 210))
    }

    @Test
    fun aJumpOfSeveralRowsFallsBackToThePitch() {
        assertEquals(4 * 210 + (-30 - -10), MeloLibraryPolicy.scrollDeltaPx(2, -30, 6, -10, null, 210))
        assertTrue(MeloLibraryPolicy.scrollDeltaPx(2, -30, 3, -10, null, 210) > 0)
    }

    // ---- the real list: rows of different heights, scrolled down in small steps -----------------------

    private class ListModel(val heights: List<Int>, val divider: Int) {
        fun rowTop(i: Int) = (0 until i).sumOf { heights[it] + divider }
        fun first(y: Int) = heights.indices.first { rowTop(it) + heights[it] > y }
        fun tops(y: Int): IntArray {
            val first = first(y)
            return IntArray(4) { rowTop(first + it) - y }
        }
    }

    @Test
    fun scrollingDownAcrossRowsOfDifferentHeightsNeverLooksLikeScrollingUp() {
        val model = ListModel(listOf(230, 210, 190, 250, 210, 270, 190, 230, 210, 190, 250, 210), divider = 20)
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.FLING)
        var prevFirst = -1
        var prevTops = IntArray(4)
        var changes = 0
        var y = 0
        while (y < 2000) {
            val first = model.first(y)
            val tops = model.tops(y)
            val delta = MeloLibraryPolicy.scrollDeltaPx(
                prevFirst, prevTops[0], first, tops[0],
                prevFirstTopNow = if (first < prevFirst) tops.getOrNull(prevFirst - first) else null,
                fallbackPitchPx = 210,
                newFirstTopBefore = if (first > prevFirst && prevFirst >= 0) prevTops.getOrNull(first - prevFirst) else null
            )
            if (prevFirst >= 0) assertEquals("step at y=$y", 25, delta)
            if (!fold.consumeRebaseline()) fold.onDelta(delta, atTop = first <= 0 && tops[0] >= 0)
            fold.pendingChange()?.let {
                assertTrue("the title row came back at y=$y", it)
                fold.markApplied(it)
                changes++
            }
            prevFirst = first
            prevTops = tops
            y += 25
        }
        assertEquals(1, changes)
        assertTrue(fold.applied)
    }

    // ---- HeaderFold ------------------------------------------------------------------------------------

    @Test
    fun whileAFingerDragsNothingChangesWhateverTheDeltas() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.TOUCH)
        repeat(10) {
            fold.onDelta(200, atTop = false)
            assertNull(fold.pendingChange())
            fold.onDelta(-200, atTop = false)
            assertNull(fold.pendingChange())
        }
        fold.onDelta(200, atTop = false)
        assertNull(fold.pendingChange())
        fold.onPhase(ScrollPhase.IDLE)
        assertEquals(true, fold.pendingChange())
        fold.markApplied(true)
        assertNull(fold.pendingChange())
    }

    @Test
    fun aFingerDragThatEndsTheOtherWayChangesNothing() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.TOUCH)
        fold.onDelta(200, atTop = false)
        fold.onDelta(-200, atTop = false)
        fold.onPhase(ScrollPhase.IDLE)
        assertNull(fold.pendingChange())
    }

    @Test
    fun aSlowDragFoldsWhenTheFingerLiftsOrTheListFlings() {
        val drag = HeaderFold(threshold)
        drag.onPhase(ScrollPhase.TOUCH)
        repeat(5) { drag.onDelta(20, atTop = false) }
        assertNull(drag.pendingChange())
        drag.onPhase(ScrollPhase.IDLE)
        assertEquals(true, drag.pendingChange())

        val fling = HeaderFold(threshold)
        fling.onPhase(ScrollPhase.TOUCH)
        repeat(5) { fling.onDelta(20, atTop = false) }
        fling.onPhase(ScrollPhase.FLING)
        assertEquals(true, fling.pendingChange())
    }

    @Test
    fun aFlingPastTheThresholdChangesOnceAndSmallDeltasBackDoNotUndoIt() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.FLING)
        fold.onDelta(30, atTop = false)
        assertNull(fold.pendingChange())
        fold.onDelta(30, atTop = false)
        assertEquals(true, fold.pendingChange())
        fold.markApplied(true)
        assertTrue(fold.consumeRebaseline())
        assertFalse(fold.consumeRebaseline())
        fold.onDelta(-20, atTop = false)
        assertNull(fold.pendingChange())
        fold.onDelta(10, atTop = false)
        assertNull(fold.pendingChange())
        assertTrue(fold.applied)
    }

    @Test
    fun aRealRunBackUpShowsTheRowAgainOnce() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.FLING)
        fold.onDelta(100, atTop = false)
        fold.markApplied(fold.pendingChange()!!)
        repeat(4) { fold.onDelta(-15, atTop = false) }
        assertEquals(false, fold.pendingChange())
        fold.markApplied(false)
        repeat(4) { fold.onDelta(-15, atTop = false) }
        assertNull(fold.pendingChange())
    }

    @Test
    fun aHugeDeltaDoesNotMakeTheWayBackLong() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.IDLE)
        fold.onDelta(5000, atTop = false)
        fold.markApplied(fold.pendingChange()!!)
        fold.onDelta(-50, atTop = false)
        assertEquals(false, fold.pendingChange())
    }

    @Test
    fun backAtTheTopShowsTheRowAndResetStartsFromNothing() {
        val fold = HeaderFold(threshold)
        fold.onPhase(ScrollPhase.IDLE)
        fold.onDelta(100, atTop = false)
        fold.markApplied(fold.pendingChange()!!)
        fold.onDelta(-5, atTop = true)
        assertEquals(false, fold.pendingChange())
        fold.markApplied(false)

        fold.onDelta(100, atTop = false)
        fold.markApplied(fold.pendingChange()!!)
        fold.reset()
        assertFalse(fold.applied)
        assertNull(fold.pendingChange())
        assertFalse(fold.consumeRebaseline())
    }

    // ---- threshold in dp -------------------------------------------------------------------------------

    @Test
    fun thresholdIsSixteenDp() {
        assertEquals(16, MeloLibraryPolicy.collapseThresholdPx(1.0f))
        assertEquals(42, MeloLibraryPolicy.collapseThresholdPx(2.625f))
        assertEquals(34, MeloLibraryPolicy.collapseThresholdPx(2.125f))
    }

    @Test
    fun theOldPerCallbackRuleStillBehavesAsBefore() {
        assertTrue(MeloLibraryPolicy.headerCollapsed(false, 40, 3))
        assertFalse(MeloLibraryPolicy.headerCollapsed(true, -40, 3))
    }
}
