package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramContiguousQueuePolicyTest {
    @Test fun neverSkipsMissingFileToDistantCache() {
        val order = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(0, 1),
            TelegramContiguousQueuePolicy.indices(order, 1) { it in setOf(1L, 2L, 4L) })
    }
    @Test fun keepsContiguousPreviousAndNext() {
        val order = listOf(10L, 20L, 30L, 40L, 50L)
        assertEquals(listOf(1, 2, 3),
            TelegramContiguousQueuePolicy.indices(order, 30) {
                it in setOf(20L, 30L, 40L)
            })
    }
    @Test fun boundedLargeChannel() {
        val order = (1..600).map(Int::toLong)
        val indices = TelegramContiguousQueuePolicy.indices(order, 300) { true }
        assertEquals(17, indices.size)
        assertEquals(291, indices.first())
        assertEquals(307, indices.last())
    }
    @Test fun missingSelectedReturnsEmpty() {
        assertEquals(emptyList<Int>(),
            TelegramContiguousQueuePolicy.indices(listOf(1L, 2L), 99L) { true })
    }
}
