package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class QueuePageCursorTest {
    private fun cursor(total: Int = 75, pageSize: Int = 30): QueuePageCursor =
        QueuePageCursor(
            QueueBrowseSeed("drive-folder:0:Jazz", "", "", 1, pageSize),
            (0 until minOf(total, pageSize)).map { "track:$it" },
        )

    @Test fun firstPageIsPrefetchedEvenWhenPlayingFirstTrack() {
        val c = cursor()
        assertEquals(1, c.beginFetch(0))
        assertNull(c.beginFetch(0))
        assertTrue(c.acceptPage(1, 30, (30 until 60).map { "track:$it" }))
        assertEquals(60, c.queueSize)
        assertFalse(c.shouldFetch(0))
        assertFalse(c.shouldFetch(47))
        assertTrue(c.shouldFetch(48))
        assertEquals(2, c.beginFetch(48))
        assertTrue(c.acceptPage(2, 15, (60 until 75).map { "track:$it" }))
        assertEquals(75, c.queueSize)
        assertTrue(c.endReached)
        assertFalse(c.shouldFetch(74))
    }

    @Test fun exactBoundariesDoNotSkipOrDuplicate() {
        for (total in listOf(30, 31, 60, 61, 75, 90, 91, 136, 151)) {
            val c = cursor(total)
            var page = 1
            var loops = 0
            while (!c.endReached && loops++ < 12) {
                val current = c.queueSize - 1
                assertEquals(page, c.beginFetch(current))
                val from = page * 30
                val to = minOf(total, from + 30)
                val ids = (from until to).map { "track:$it" }
                assertTrue(c.acceptPage(page, ids.size, ids))
                page++
            }
            assertTrue("total=$total", c.endReached)
            assertEquals("total=$total", total, c.queueSize)
            assertEquals(total, c.queueSize)
        }
    }

    @Test fun largeCatalogLoadsOnlyOnePageAtATime() {
        val total = 11001
        val c = cursor(total)
        assertEquals(30, c.queueSize)
        assertEquals(1, c.beginFetch(0))
        assertTrue(c.acceptPage(1, 30, (30 until 60).map { "track:$it" }))
        assertEquals(60, c.queueSize)
        assertFalse(c.shouldFetch(0)) // no eager 11k materialization
        assertNull(c.beginFetch(0))
        var page = 2
        while (!c.endReached) {
            val from = page * 30
            val ids = (from until minOf(total, from + 30)).map { "track:$it" }
            assertEquals(page, c.beginFetch(c.queueSize - 1))
            assertTrue(c.acceptPage(page, ids.size, ids))
            page++
        }
        assertEquals(total, c.queueSize)
    }

    @Test fun genuineDuplicatePlaylistOccurrencesPreserved() {
        val ids = listOf("track:1", "track:1", "track:2")
        val c = QueuePageCursor(QueueBrowseSeed("playlist:3", "", "", 1, 3), ids)
        assertEquals(1, c.beginFetch(0))
        assertTrue(c.acceptPage(1, 2, listOf("track:2", "track:2")))
        assertEquals(listOf("track:1", "track:1", "track:2", "track:2", "track:2"),
            listOf("track:1", "track:1", "track:2", "track:2", "track:2").also {
                assertTrue(c.isUnchanged(it))
            })
        assertTrue(c.endReached)
    }

    @Test fun failedPageNeverTriggersAutomaticRetry() {
        val c = cursor()
        assertEquals(1, c.beginFetch(0))
        c.fail(1)
        assertTrue(c.pausedOnError)
        assertNull(c.beginFetch(29))
        assertEquals(30, c.queueSize)
        assertFalse(c.acceptPage(1, 30, (30 until 60).map { "track:$it" }))
    }

    @Test fun staleResultAndUserEditedQueueAreRejected() {
        val c = cursor()
        assertEquals(1, c.beginFetch(0))
        assertFalse(c.acceptPage(2, 30, (30 until 60).map { "track:$it" }))
        val changed = (0 until 29).map { "track:$it" }
        assertFalse(c.isUnchanged(changed))
        c.cancel()
        assertFalse(c.shouldFetch(29))
    }

    @Test fun folderOnlyPageCountsAsSourcePageNotPlayableQueue() {
        val c = cursor()
        assertEquals(1, c.beginFetch(0))
        assertTrue(c.acceptPage(1, 30, emptyList()))
        assertEquals(30, c.queueSize)
        assertFalse(c.endReached)
        assertEquals(2, c.beginFetch(0)) // keep scanning folder-only pages
        assertTrue(c.acceptPage(2, 10, (30 until 40).map { "track:$it" }))
        assertEquals(40, c.queueSize)
        assertTrue(c.endReached)
    }

    @Test fun supportedContextsExcludeDeviceFilesAndUnknownFolders() {
        assertTrue(QueueBrowseSeed.supports("drive-folder:0:Jazz"))
        assertTrue(QueueBrowseSeed.supports("source:telegram"))
        assertTrue(QueueBrowseSeed.supports("playlist:12"))
        assertTrue(QueueBrowseSeed.supports("artist:Jazz"))
        assertTrue(QueueBrowseSeed.supports("search", "Chill"))
        assertFalse(QueueBrowseSeed.supports("search", ""))
        assertFalse(QueueBrowseSeed.supports("device_folder:primary:A"))
    }
}
