package com.bsdung.musicassistantcar.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueHelperTest {

    private fun item(id: String, title: String = id): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()

    // Case 1: Play Next inserts after current index.
    @Test
    fun testPlayNextInsertsAfterCurrentIndex() {
        val q = listOf(item("t1"), item("t2"), item("t3"))
        val nextItem = item("t_new")
        val action = QueueHelper.planPlayNext(q, currentIndex = 0, newItem = nextItem)
        assertTrue(action is PlayNextAction.Insert)
        assertEquals(1, (action as PlayNextAction.Insert).targetIndex)

        val applied = QueueHelper.applyPlayNext(q, currentIndex = 0, newItem = nextItem)
        assertEquals(listOf("t1", "t_new", "t2", "t3"), applied.map { it.mediaId })
    }

    // Case 2: Existing duplicate is moved instead of duplicated.
    @Test
    fun testExistingDuplicateIsMovedInsteadOfDuplicated() {
        val q = listOf(item("t1"), item("t2"), item("t3"), item("t4"))
        // t3 is at index 2 (after currentIndex 0)
        val action = QueueHelper.planPlayNext(q, currentIndex = 0, newItem = item("t3"))
        assertTrue(action is PlayNextAction.Move)
        val move = action as PlayNextAction.Move
        assertEquals(2, move.fromIndex)
        assertEquals(1, move.toIndex)

        val applied = QueueHelper.applyPlayNext(q, currentIndex = 0, newItem = item("t3"))
        assertEquals(listOf("t1", "t3", "t2", "t4"), applied.map { it.mediaId })
        // Confirm no duplicate t3
        assertEquals(1, applied.count { it.mediaId == "t3" })
    }

    // Case 3: Add to Queue appends.
    @Test
    fun testAddToQueueAppends() {
        val q = listOf(item("t1"), item("t2"))
        val newItem = item("t3")
        val action = QueueHelper.planAddToQueue(q, newItem)
        assertTrue(action is AddToQueueAction.Append)

        val applied = QueueHelper.applyAddToQueue(q, newItem)
        assertEquals(listOf("t1", "t2", "t3"), applied.map { it.mediaId })
    }

    // Case 4: Add to Queue de-duplicates by mediaId.
    @Test
    fun testAddToQueueDeduplicatesByMediaId() {
        val q = listOf(item("t1"), item("t2"), item("t3"))
        val action = QueueHelper.planAddToQueue(q, item("t2"))
        assertEquals(AddToQueueAction.AlreadyExists, action)

        val applied = QueueHelper.applyAddToQueue(q, item("t2"))
        assertEquals(listOf("t1", "t2", "t3"), applied.map { it.mediaId })
    }

    // Case 5: Remove upcoming preserves current index.
    @Test
    fun testRemoveUpcomingPreservesCurrentIndex() {
        val q = listOf(item("t1"), item("t2"), item("t3"), item("t4"))
        val currentIndex = 1 // current is t2
        assertTrue(QueueHelper.planRemoveUpcoming(q.size, currentIndex, targetIndex = 2))
        assertFalse(QueueHelper.planRemoveUpcoming(q.size, currentIndex, targetIndex = 1)) // cannot remove current
        assertFalse(QueueHelper.planRemoveUpcoming(q.size, currentIndex, targetIndex = 0)) // cannot remove past

        val applied = QueueHelper.applyRemoveUpcoming(q, currentIndex, targetIndex = 2)
        assertEquals(listOf("t1", "t2", "t4"), applied.map { it.mediaId })
        assertEquals("t2", applied[currentIndex].mediaId) // current index still holds current track!
    }

    // Case 6: Clear upcoming preserves current track.
    @Test
    fun testClearUpcomingPreservesCurrentTrack() {
        val q = listOf(item("t1"), item("t2"), item("t3"), item("t4"))
        val currentIndex = 1 // current is t2
        val range = QueueHelper.planClearUpcoming(q.size, currentIndex)
        assertEquals(2 until 4, range)

        val applied = QueueHelper.applyClearUpcoming(q, currentIndex)
        assertEquals(listOf("t1", "t2"), applied.map { it.mediaId })
        assertEquals("t2", applied[currentIndex].mediaId) // current track preserved!
    }

    // Case 7: Quality rebuild preserves order.
    @Test
    fun testQualityRebuildPreservesOrder() {
        val q = listOf(item("t1"), item("t2"), item("t3"))
        val rebuilt = QueueHelper.applyQualityRebuild(q) { original ->
            MediaItem.Builder()
                .setMediaId(original.mediaId)
                .setMediaMetadata(original.mediaMetadata)
                .build()
        }
        assertEquals(listOf("t1", "t2", "t3"), rebuilt.map { it.mediaId })
        assertEquals(q.size, rebuilt.size)
    }

    // Case 8: Queue current-row detection correct.
    @Test
    fun testQueueCurrentRowDetectionCorrect() {
        val q = listOf(item("t1"), item("t2"), item("t3"))
        val listPlaying = QueueHelper.buildQueueList(q, currentIndex = 1, isPlaying = true)

        val tracks = listPlaying.filterIsInstance<QueueListItem.Track>()
        val currentTrack = tracks.firstOrNull { it.isCurrent }
        assertTrue(currentTrack != null)
        assertEquals("t2", currentTrack?.mediaItem?.mediaId)
        assertTrue(currentTrack?.isPlaying == true)
        assertFalse(currentTrack?.isUpcoming == true)

        val upcomingTrack = tracks.firstOrNull { it.isUpcoming }
        assertTrue(upcomingTrack != null)
        assertEquals("t3", upcomingTrack?.mediaItem?.mediaId)
        assertFalse(upcomingTrack?.isCurrent == true)

        val listPaused = QueueHelper.buildQueueList(q, currentIndex = 1, isPlaying = false)
        val currentPaused = listPaused.filterIsInstance<QueueListItem.Track>().first { it.isCurrent }
        assertFalse(currentPaused.isPlaying)
    }

    // Case 9: Empty queue state safe.
    @Test
    fun testEmptyQueueStateSafe() {
        val emptyQueue = emptyList<MediaItem>()
        val actionNext = QueueHelper.planPlayNext(emptyQueue, currentIndex = 0, newItem = item("t1"))
        assertTrue(actionNext is PlayNextAction.StartNewQueue)

        val actionAdd = QueueHelper.planAddToQueue(emptyQueue, newItem = item("t1"))
        assertTrue(actionAdd is AddToQueueAction.StartNewQueue)

        assertFalse(QueueHelper.planRemoveUpcoming(0, 0, 0))
        assertNull(QueueHelper.planClearUpcoming(0, 0))

        val list = QueueHelper.buildQueueList(emptyQueue, currentIndex = -1, isPlaying = false)
        assertEquals(1, list.size)
        assertTrue(list.first() is QueueListItem.Empty)
        assertEquals("Chưa có bài trong hàng đợi", (list.first() as QueueListItem.Empty).message)
    }

    // Case 10: Single-item queue safe.
    @Test
    fun testSingleItemQueueSafe() {
        val singleQueue = listOf(item("t1"))
        val list = QueueHelper.buildQueueList(singleQueue, currentIndex = 0, isPlaying = true)

        val emptyRow = list.filterIsInstance<QueueListItem.Empty>().firstOrNull()
        assertTrue(emptyRow != null)
        assertEquals("Không còn bài tiếp theo", emptyRow?.message)

        assertNull(QueueHelper.planClearUpcoming(1, 0))
        assertFalse(QueueHelper.planRemoveUpcoming(1, 0, 1))

        val actionSame = QueueHelper.planPlayNext(singleQueue, currentIndex = 0, newItem = item("t1"))
        assertEquals(PlayNextAction.AlreadyCurrent, actionSame)
    }

    // Case 11: Upcoming item can move upward within upcoming area
    @Test
    fun testUpcomingItemCanMoveUpwardWithinUpcomingArea() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"), item("t4"))
        val currentIndex = 1 // current is t1, upcoming: 2 (t2), 3 (t3), 4 (t4)
        assertTrue(QueueHelper.canMoveUpcoming(currentIndex = 1, fromIndex = 4, toIndex = 2, queueSize = 5))

        val applied = QueueHelper.applyMove(q, currentIndex = 1, fromIndex = 4, toIndex = 2)
        assertEquals(listOf("t0", "t1", "t4", "t2", "t3"), applied.map { it.mediaId })
    }

    // Case 12: Upcoming item can move downward
    @Test
    fun testUpcomingItemCanMoveDownward() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"), item("t4"))
        val currentIndex = 1
        assertTrue(QueueHelper.canMoveUpcoming(currentIndex = 1, fromIndex = 2, toIndex = 4, queueSize = 5))

        val applied = QueueHelper.applyMove(q, currentIndex = 1, fromIndex = 2, toIndex = 4)
        assertEquals(listOf("t0", "t1", "t3", "t4", "t2"), applied.map { it.mediaId })
    }

    // Case 13: Cannot move upcoming before/current track
    @Test
    fun testCannotMoveUpcomingBeforeOrCurrentTrack() {
        val currentIndex = 1
        val queueSize = 5
        // cannot move to current (index 1)
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 2, toIndex = 1, queueSize = queueSize))
        // cannot move before current (index 0)
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 2, toIndex = 0, queueSize = queueSize))
    }

    // Case 14: Cannot move current track
    @Test
    fun testCannotMoveCurrentTrack() {
        val currentIndex = 1
        val queueSize = 5
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 1, toIndex = 2, queueSize = queueSize))
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 0, toIndex = 2, queueSize = queueSize))
    }

    // Case 15: Cannot move out of bounds
    @Test
    fun testCannotMoveOutOfBounds() {
        val currentIndex = 1
        val queueSize = 5
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 2, toIndex = 5, queueSize = queueSize))
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = -1, toIndex = 2, queueSize = queueSize))
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex, fromIndex = 2, toIndex = 2, queueSize = queueSize))
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex = -1, fromIndex = 2, toIndex = 3, queueSize = queueSize))
        assertFalse(QueueHelper.canMoveUpcoming(currentIndex = 5, fromIndex = 2, toIndex = 3, queueSize = queueSize))
    }

    // Case 16: Queue order correct after move
    @Test
    fun testQueueOrderCorrectAfterMove() {
        val q = listOf(item("curr"), item("A"), item("B"), item("C"))
        val applied = QueueHelper.applyMove(q, currentIndex = 0, fromIndex = 3, toIndex = 1)
        assertEquals(listOf("curr", "C", "A", "B"), applied.map { it.mediaId })
    }

    // Case 17: Current index and mediaId logically preserved
    @Test
    fun testCurrentIndexAndMediaIdLogicallyPreservedAfterMove() {
        val q = listOf(item("past"), item("current"), item("A"), item("B"), item("C"))
        val currentIndex = 1
        val applied = QueueHelper.applyMove(q, currentIndex = 1, fromIndex = 4, toIndex = 2)
        assertEquals("current", applied[currentIndex].mediaId)
        assertEquals("past", applied[0].mediaId)
    }

    // Case 18: Queue count equals number of future items
    @Test
    fun testQueueCountEqualsNumberOfFutureItems() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"), item("t4"))
        assertEquals(3, QueueHelper.getUpcomingCount(currentIndex = 1, queueSize = 5))
        assertEquals(4, QueueHelper.getUpcomingCount(currentIndex = 0, queueSize = 5))

        val list = QueueHelper.buildQueueList(q, currentIndex = 1, isPlaying = true)
        val upcomingHeader = list.filterIsInstance<QueueListItem.Header>().first { it.title.startsWith("Tiếp theo") }
        assertEquals("Tiếp theo · 3", upcomingHeader.title)
    }

    // Case 19: Single-item queue count is zero
    @Test
    fun testSingleItemQueueCountIsZero() {
        assertEquals(0, QueueHelper.getUpcomingCount(currentIndex = 0, queueSize = 1))
        val list = QueueHelper.buildQueueList(listOf(item("t0")), currentIndex = 0, isPlaying = true)
        val header = list.filterIsInstance<QueueListItem.Header>().first { it.title.startsWith("Tiếp theo") }
        assertEquals("Tiếp theo · 0", header.title)
    }

    // Case 20: Clear upcoming count is zero
    @Test
    fun testClearUpcomingCountIsZero() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        val cleared = QueueHelper.applyClearUpcoming(q, currentIndex = 1)
        assertEquals(0, QueueHelper.getUpcomingCount(currentIndex = 1, queueSize = cleared.size))

        val list = QueueHelper.buildQueueList(cleared, currentIndex = 1, isPlaying = true)
        val header = list.filterIsInstance<QueueListItem.Header>().first { it.title.startsWith("Tiếp theo") }
        assertEquals("Tiếp theo · 0", header.title)
    }

    // Case 21: Reorder then quality transform preserves order
    @Test
    fun testReorderThenQualityTransformPreservesOrder() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        val reordered = QueueHelper.applyMove(q, currentIndex = 0, fromIndex = 3, toIndex = 1)
        assertEquals(listOf("t0", "t3", "t1", "t2"), reordered.map { it.mediaId })

        val transformed = QueueHelper.applyQualityRebuild(reordered) { item(it.mediaId + "_320") }
        assertEquals(listOf("t0_320", "t3_320", "t1_320", "t2_320"), transformed.map { it.mediaId })
    }

    // Case 22: Duplicate mediaIds do not break index-based reorder logic
    @Test
    fun testDuplicateMediaIdsDoNotBreakIndexBasedReorderLogic() {
        val itemA = item("dup", "First Dup")
        val itemB = item("dup", "Second Dup")
        val itemOther = item("other")
        val q = listOf(item("curr"), itemA, itemOther, itemB)

        val applied = QueueHelper.applyMove(q, currentIndex = 0, fromIndex = 3, toIndex = 1)
        assertEquals(listOf("curr", "dup", "dup", "other"), applied.map { it.mediaId })
        assertEquals("Second Dup", applied[1].mediaMetadata.title)
        assertEquals("First Dup", applied[2].mediaMetadata.title)
        assertEquals("other", applied[3].mediaMetadata.title)
    }
}
