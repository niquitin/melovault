package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class MeloShuffleOrderPolicyTest {
    private fun ids(n: Int): List<Long> = (1L..n.toLong()).toList()

    @Test fun shuffledIsAPermutationWithoutLossOrDuplicates() {
        val base = ids(50)
        val out = MeloShuffleOrderPolicy.shuffled(base, null, 7L)
        assertEquals(base.size, out.size)
        assertEquals(base.toSet(), out.toSet())
        assertNotEquals(base, out)
    }

    @Test fun anchorStaysFirstAndRestIsPermuted() {
        val base = ids(30)
        val out = MeloShuffleOrderPolicy.shuffled(base, 17L, 99L)
        assertEquals(17L, out.first())
        assertEquals(base.toSet(), out.toSet())
        assertEquals(base.size, out.size)
    }

    @Test fun anchorMissingFromBaseIsIgnored() {
        val base = ids(10)
        val out = MeloShuffleOrderPolicy.shuffled(base, 777L, 3L)
        assertEquals(base.toSet(), out.toSet())
        assertFalse(out.contains(777L))
    }

    @Test fun sameSeedGivesSameOrderAndAnotherSeedDiffers() {
        val base = ids(50)
        val a = MeloShuffleOrderPolicy.shuffled(base, 5L, 1234L)
        assertEquals(a, MeloShuffleOrderPolicy.shuffled(base, 5L, 1234L))
        assertNotEquals(a, MeloShuffleOrderPolicy.shuffled(base, 5L, 4321L))
    }

    @Test fun emptyAndSingleBase() {
        assertTrue(MeloShuffleOrderPolicy.shuffled(emptyList<Long>(), null, 1L).isEmpty())
        assertTrue(MeloShuffleOrderPolicy.shuffled(emptyList(), 4L, 1L).isEmpty())
        assertEquals(listOf(9L), MeloShuffleOrderPolicy.shuffled(listOf(9L), 9L, 1L))
        assertEquals(listOf(9L), MeloShuffleOrderPolicy.shuffled(listOf(9L), null, 1L))
    }

    @Test fun duplicatesAreDroppedFirstOccurrenceWins() {
        val out = MeloShuffleOrderPolicy.shuffled(listOf(1L, 2L, 2L, 3L, 1L), 2L, 8L)
        assertEquals(3, out.size)
        assertEquals(setOf(1L, 2L, 3L), out.toSet())
        assertEquals(2L, out.first())
    }

    @Test fun toggledOffReturnsTheBaseOrder() {
        val base = ids(12)
        assertEquals(base, MeloShuffleOrderPolicy.toggled(base, 4L, false, 5L))
        val on = MeloShuffleOrderPolicy.toggled(base, 4L, true, 5L)
        assertEquals(4L, on.first())
        assertEquals(base.toSet(), on.toSet())
    }

    @Test fun rebaseOffIsTheNewBase() {
        val newBase = listOf(3L, 1L, 2L)
        assertEquals(newBase, MeloShuffleOrderPolicy.rebase(ids(3), listOf(2L, 1L, 3L), newBase, 2L, false, 1L))
    }

    @Test fun rebaseSameBaseKeepsThePlayOrderAndDoesNotReshuffle() {
        val base = ids(20)
        val play = MeloShuffleOrderPolicy.shuffled(base, 6L, 11L)
        val again = MeloShuffleOrderPolicy.rebase(base, play, base, 6L, true, 999L)
        assertEquals(play, again)
        // Tapping another row of the same queue (anchor = that row, already in play) keeps the order.
        val tapped = play[5]
        assertEquals(play, MeloShuffleOrderPolicy.rebase(base, play, base, tapped, true, 123L))
    }

    @Test fun rebaseSameSourceKeepsDecidedPartAndAppendsNewSongsShuffled() {
        val base = ids(20)
        val play = MeloShuffleOrderPolicy.shuffled(base, 6L, 11L)
        val grown = base + (21L..30L).toList()
        val out = MeloShuffleOrderPolicy.rebase(base, play, grown, 6L, true, 42L)
        assertEquals(play, out.take(20))
        assertEquals((21L..30L).toSet(), out.drop(20).toSet())
        assertEquals(grown.toSet(), out.toSet())
        assertEquals(30, out.size)
        assertEquals(out, MeloShuffleOrderPolicy.rebase(base, play, grown, 6L, true, 42L))
    }

    @Test fun rebaseDropsSongsThatLeftTheBase() {
        val base = ids(10)
        val play = MeloShuffleOrderPolicy.shuffled(base, 1L, 2L)
        val shrunk = base.filter { it != 5L && it != 8L }
        val out = MeloShuffleOrderPolicy.rebase(base, play, shrunk, 1L, true, 3L)
        assertEquals(play.filter { it != 5L && it != 8L }, out)
    }

    @Test fun rebaseReshufflesWhenTheSourceChangedOrTheAnchorIsNew() {
        val oldBase = ids(10)
        val oldPlay = MeloShuffleOrderPolicy.shuffled(oldBase, 1L, 2L)
        val otherSource = (101L..120L).toList()
        val out = MeloShuffleOrderPolicy.rebase(oldBase, oldPlay, otherSource, 105L, true, 77L)
        assertEquals(105L, out.first())
        assertEquals(otherSource.toSet(), out.toSet())
        assertEquals(MeloShuffleOrderPolicy.shuffled(otherSource, 105L, 77L), out)
        // anchor not in the old play order: new shuffle with that anchor even for the same base
        val fresh = MeloShuffleOrderPolicy.rebase(oldBase, emptyList(), oldBase, 4L, true, 5L)
        assertEquals(MeloShuffleOrderPolicy.shuffled(oldBase, 4L, 5L), fresh)
        // empty old base counts as a new source
        assertEquals(MeloShuffleOrderPolicy.shuffled(oldBase, 4L, 5L),
            MeloShuffleOrderPolicy.rebase(emptyList(), emptyList(), oldBase, 4L, true, 5L))
    }

    @Test fun rebaseBoundaryOfHalfTheOldBaseIsSameSource() {
        val oldBase = ids(10)
        val play = MeloShuffleOrderPolicy.shuffled(oldBase, 1L, 9L)
        val half = (1L..5L).toList() + (50L..59L).toList()
        val kept = MeloShuffleOrderPolicy.rebase(oldBase, play, half, 1L, true, 4L)
        assertEquals(play.filter { it <= 5L }, kept.take(5))
        val belowHalf = (1L..4L).toList() + (50L..59L).toList()
        val reshuffled = MeloShuffleOrderPolicy.rebase(oldBase, play, belowHalf, 1L, true, 4L)
        assertEquals(MeloShuffleOrderPolicy.shuffled(belowHalf, 1L, 4L), reshuffled)
    }

    @Test fun insertNextPutsTheSongRightAfterTheCurrentOne() {
        val order = listOf(1L, 2L, 3L, 4L, 5L)
        assertEquals(listOf(1L, 2L, 9L, 3L, 4L, 5L), MeloShuffleOrderPolicy.insertNext(order, 2L, 9L))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 9L), MeloShuffleOrderPolicy.insertNext(order, 5L, 9L))
    }

    @Test fun insertNextMovesAnExistingSongAndHandlesMissingCurrent() {
        val order = listOf(1L, 2L, 3L, 4L, 5L)
        assertEquals(listOf(1L, 4L, 2L, 3L, 5L), MeloShuffleOrderPolicy.insertNext(order, 1L, 4L))
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), MeloShuffleOrderPolicy.insertNext(order, null, 4L))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 9L), MeloShuffleOrderPolicy.insertNext(order, 77L, 9L))
        assertEquals(order, MeloShuffleOrderPolicy.insertNext(order, 3L, 3L))
        assertEquals(listOf(9L), MeloShuffleOrderPolicy.insertNext(emptyList(), null, 9L))
    }

    @Test fun restoreReturnsBaseOrderOfPresentIdsAndAppendsUnknownOnes() {
        val base = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("b", "d", "e"), MeloShuffleOrderPolicy.restore(base, listOf("e", "b", "d")))
        assertEquals(listOf("a", "c", "y", "x"), MeloShuffleOrderPolicy.restore(base, listOf("y", "c", "x", "a")))
        assertEquals(listOf("x"), MeloShuffleOrderPolicy.restore(emptyList(), listOf("x")))
        assertTrue(MeloShuffleOrderPolicy.restore(base, emptyList()).isEmpty())
    }

    @Test fun worksForStringMediaIdsToo() {
        val base = (1..40).map { "local:$it" }
        val out = MeloShuffleOrderPolicy.toggled(base, "local:7", true, 1L)
        assertEquals("local:7", out.first())
        assertEquals(base.toSet(), out.toSet())
        assertEquals(base, MeloShuffleOrderPolicy.restore(base, out))
    }

    @Test fun largeQueueIsFast() {
        val base = ids(2000)
        MeloShuffleOrderPolicy.shuffled(base, 1L, 1L) // warm up
        val start = System.nanoTime()
        val play = MeloShuffleOrderPolicy.shuffled(base, 1000L, 5L)
        MeloShuffleOrderPolicy.rebase(base, play, base + 2001L, 1000L, true, 6L)
        MeloShuffleOrderPolicy.insertNext(play, 1000L, 1500L)
        MeloShuffleOrderPolicy.restore(base, play)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(2000, play.size)
        assertTrue("took ${ms}ms", ms < 50)
    }
}
