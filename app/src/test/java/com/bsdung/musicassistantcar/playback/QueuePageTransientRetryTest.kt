package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class QueuePageTransientRetryTest {
    private fun cursor() = QueuePageCursor(
        QueueBrowseSeed("drive-folder:0:Jazz", "", "", 1, 30),
        (0 until 30).map { "drive-file:$it" },
    )

    @Test fun timeoutGetsExactlyOneDelayedRetryAndPreservesPosition() {
        val cursor = cursor()
        assertEquals(1, cursor.beginFetch(20, 1_000))
        assertTrue(cursor.failWithOneAutoRetry(1, 2_000, 5_000))
        assertFalse(cursor.pausedOnError)
        assertNull(cursor.beginFetch(29, 6_999))
        assertEquals(1, cursor.beginFetch(29, 7_000))
        assertTrue(cursor.acceptPage(1, 30, (30 until 60).map { "drive-file:$it" }))
        assertEquals(60, cursor.queueSize)
        assertTrue(cursor.isUnchanged((0 until 60).map { "drive-file:$it" }))
    }

    @Test fun secondTimeoutPausesPermanentlyWithoutRetryStorm() {
        val cursor = cursor()
        assertEquals(1, cursor.beginFetch(28, 500))
        assertTrue(cursor.failWithOneAutoRetry(1, 1_000))
        assertEquals(1, cursor.beginFetch(29, 6_000))
        assertFalse(cursor.failWithOneAutoRetry(1, 7_000))
        assertTrue(cursor.pausedOnError)
        assertNull(cursor.beginFetch(29, 100_000))
    }

    @Test fun oneRetryBudgetAppliesAcrossAllPagesAndUserCancelWins() {
        val cursor = cursor()
        assertEquals(1, cursor.beginFetch(0, 1000))
        assertTrue(cursor.failWithOneAutoRetry(1, 1001, 5000))
        assertEquals(1, cursor.beginFetch(0, 6001))
        assertTrue(cursor.acceptPage(1, 30, (30 until 60).map { "drive-file:$it" }))
        assertEquals(2, cursor.beginFetch(49, 7000))
        assertFalse(cursor.failWithOneAutoRetry(2, 8000))
        cursor.cancel()
        assertNull(cursor.beginFetch(59, 9000))
        assertTrue(cursor.endReached)
    }
}
