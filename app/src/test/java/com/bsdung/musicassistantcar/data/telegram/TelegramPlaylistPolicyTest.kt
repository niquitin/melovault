package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramPlaylistPolicyTest {
    @Test fun addAndRemovePreservesOrder() {
        val first = TelegramPlaylistPolicy.toggle(emptyList(), 10)
        assertEquals(listOf(10L), first)
        val second = TelegramPlaylistPolicy.toggle(first, 20)
        assertEquals(listOf(10L, 20L), second)
        assertEquals(listOf(20L), TelegramPlaylistPolicy.toggle(second, 10))
    }

    @Test fun deduplicatesAndIgnoresInvalidExistingEntries() {
        assertEquals(listOf(2L, 3L),
            TelegramPlaylistPolicy.toggle(listOf(-1L, 2L, 2L), 3L))
    }

    @Test fun keepsBoundedPlaylist() {
        val original = (1..300).map { it.toLong() }
        val result = TelegramPlaylistPolicy.toggle(original, 301L)
        assertEquals(300, result.size)
        assertEquals(2L, result.first())
        assertEquals(301L, result.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTargetId() {
        TelegramPlaylistPolicy.toggle(emptyList(), 0L)
    }
}
