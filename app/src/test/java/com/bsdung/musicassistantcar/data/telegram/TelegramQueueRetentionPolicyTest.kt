package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramQueueRetentionPolicyTest {
    @Test fun preservePlayingSourceThroughEmptyScan() {
        val previous = listOf(10L, 20L, 30L)
        assertEquals(previous,
            TelegramQueueRetentionPolicy.forSelected(emptyList<Long>(), previous, 20L) { it })
    }
    @Test fun visibleSourceReplacesPreviousWhenUserSelectsNewTrack() {
        assertEquals(listOf(40L, 50L),
            TelegramQueueRetentionPolicy.forSelected(
                listOf(40L, 50L), listOf(10L, 20L), 40L) { it })
    }
    @Test fun oldQueueDoesNotLeakIntoUnrelatedTrack() {
        assertEquals(emptyList<Long>(),
            TelegramQueueRetentionPolicy.forSelected(
                listOf(1L), listOf(2L), 99L) { it })
    }
}
