package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistPolicy.ToggleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaylistPolicyTest {
    private fun ids(n: Int): List<Long> = (1..n).map { it.toLong() }

    @Test fun addAndRemovePreservesOrder() {
        val first = TelegramPlaylistPolicy.toggle(emptyList(), 10)
        assertEquals(ToggleResult.Added(listOf(10L)), first)
        val second = TelegramPlaylistPolicy.toggle((first as ToggleResult.Added).ids, 20)
        assertEquals(ToggleResult.Added(listOf(10L, 20L)), second)
        assertEquals(
            ToggleResult.Removed(listOf(20L)),
            TelegramPlaylistPolicy.toggle((second as ToggleResult.Added).ids, 10)
        )
    }

    @Test fun deduplicatesAndIgnoresInvalidExistingEntries() {
        assertEquals(
            ToggleResult.Added(listOf(2L, 3L)),
            TelegramPlaylistPolicy.toggle(listOf(-1L, 2L, 2L), 3L)
        )
    }

    @Test fun at299OneMoreSongIsAcceptedAndNothingIsLost() {
        val result = TelegramPlaylistPolicy.toggle(ids(299), 1000L)
        assertTrue(result is ToggleResult.Added)
        val next = (result as ToggleResult.Added).ids
        assertEquals(300, next.size)
        assertEquals(ids(299), next.take(299))
        assertEquals(1000L, next.last())
    }

    @Test fun at300ANewSongIsRefusedAndNoOldSongIsDropped() {
        val full = ids(300)
        assertEquals(ToggleResult.Full(300), TelegramPlaylistPolicy.toggle(full, 301L))
    }

    @Test fun at300RemovingStillWorksAndKeepsTheOrderOfTheRest() {
        val result = TelegramPlaylistPolicy.toggle(ids(300), 150L)
        assertEquals(ToggleResult.Removed(ids(300).filterNot { it == 150L }), result)
    }

    @Test fun at300RemoveThenAddAgainWorks() {
        val removed = (TelegramPlaylistPolicy.toggle(ids(300), 1L) as ToggleResult.Removed).ids
        assertEquals(299, removed.size)
        val readded = TelegramPlaylistPolicy.toggle(removed, 1L)
        assertTrue(readded is ToggleResult.Added)
        val next = (readded as ToggleResult.Added).ids
        assertEquals(300, next.size)
        assertEquals(1L, next.last())
        assertEquals(removed, next.dropLast(1))
    }

    @Test fun at301ListIsNeverCutByAnyToggle() {
        // A list longer than the limit cannot be produced by this app any more, but must never be shortened silently.
        val over = ids(301)
        assertEquals(ToggleResult.Full(301), TelegramPlaylistPolicy.toggle(over, 9999L))
        val removed = (TelegramPlaylistPolicy.toggle(over, 5L) as ToggleResult.Removed).ids
        assertEquals(300, removed.size)
        assertEquals(over.filterNot { it == 5L }, removed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidTargetId() {
        TelegramPlaylistPolicy.toggle(emptyList(), 0L)
    }
}
