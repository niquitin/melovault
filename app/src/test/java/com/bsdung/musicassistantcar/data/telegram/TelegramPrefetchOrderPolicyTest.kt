package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPrefetchOrderPolicyTest {
    @Test fun choosesOnlyImmediateNeighbor() {
        assertEquals(20L,
            TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(10, 20, 30), 10L))
    }

    @Test fun doesNotPrefetchWhenQueueEndsOrCurrentIsUnknown() {
        assertNull(TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(10, 20), 20L))
        assertNull(TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(10, 20), 99L))
        assertNull(TelegramPrefetchOrderPolicy.immediateSuccessor(emptyList(), 10L))
    }

    @Test fun forbidsInvalidOrRepeatedNeighbors() {
        assertNull(TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(10, 10), 10L))
        assertNull(TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(10, -1), 10L))
    }

    @Test fun discardsStaleDownloadAfterPlaybackChanged() {
        val order = listOf(10L, 20L, 30L)
        assertTrue(TelegramPrefetchOrderPolicy.isStillNext(order, 10L, 20L))
        assertFalse(TelegramPrefetchOrderPolicy.isStillNext(order, 20L, 20L))
        assertFalse(TelegramPrefetchOrderPolicy.isStillNext(order, 30L, 20L))
    }

    @Test fun respectsPlaylistOrderRatherThanNumericIds() {
        assertEquals(3L,
            TelegramPrefetchOrderPolicy.immediateSuccessor(listOf(100L, 3L, 40L), 100L))
        assertTrue(TelegramPrefetchOrderPolicy.isStillNext(listOf(100L, 3L), 100L, 3L))
    }
}
