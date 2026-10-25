package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaylistViewPolicyTest {
    private val account = 1001L

    private fun song(key: Long, chat: Long = -100L, deleted: Boolean = false, acct: Long = account) = IndexedTelegramSong(
        accountId = acct, chatId = chat, messageId = key, songKey = key, fileId = 1,
        title = "t$key", filename = "f$key.mp3", isDeleted = deleted,
    )

    @Test fun savedOrderIsKeptAndUnknownSongsStayAsPlaceholders() {
        val inMemory = mapOf(1L to "one")
        val indexed = mapOf(3L to "three")
        val entries = TelegramPlaylistViewPolicy.resolve(listOf(3L, 2L, 1L), { inMemory[it] }, indexed)
        assertEquals(listOf(3L, 2L, 1L), entries.map { it.key })
        assertEquals(listOf("three", null, "one"), entries.map { it.item })
        assertFalse(entries[0].isPlaceholder)
        assertTrue(entries[1].isPlaceholder)
    }

    @Test fun theLoadedListWinsOverTheIndexForTheSameKey() {
        val entries = TelegramPlaylistViewPolicy.resolve(listOf(7L), { "fresh" }, mapOf(7L to "stale"))
        assertEquals("fresh", entries.single().item)
    }

    @Test fun onlyKeysMissingFromMemoryAreLookedUpInTheDatabase() {
        val inMemory = mapOf(1L to "a", 2L to "b")
        assertEquals(listOf(3L, 4L), TelegramPlaylistViewPolicy.missingFromMemory(listOf(1L, 3L, 2L, 4L)) { inMemory[it] })
    }

    @Test fun nothingIsEverDroppedFromTheSavedList() {
        val keys = (1L..300L).toList()
        val entries = TelegramPlaylistViewPolicy.resolve(keys, { null }, emptyMap<Long, String>())
        assertEquals(300, entries.size)
        assertTrue(entries.all { it.isPlaceholder })
        assertEquals(keys, entries.map { it.key })
    }

    @Test fun aSavedSongOutsideTheNewest200IsStillShownFrom25000Songs() {
        val dao = InMemoryTelegramLibraryDao()
        dao.upsertSongs((1L..25_000L).map { song(it) })
        // The screen only keeps the 200 newest in memory.
        val inMemory = (24_801L..25_000L).associateWith { "mem$it" }
        val saved = listOf(10L, 24_000L, 25_000L, 999_999L /* never indexed */, 4L)
        val missing = TelegramPlaylistViewPolicy.missingFromMemory(saved) { inMemory[it] }
        assertEquals(listOf(10L, 24_000L, 999_999L, 4L), missing)

        val indexed = dao.getSongsByKeys(account, missing).associate { it.songKey to it.title }
        val entries = TelegramPlaylistViewPolicy.resolve(saved, { inMemory[it] }, indexed)

        assertEquals(saved, entries.map { it.key })
        assertEquals("t10", entries[0].item)
        assertEquals("t24000", entries[1].item)
        assertEquals("mem25000", entries[2].item)
        assertTrue("an unknown song is a placeholder, not removed", entries[3].isPlaceholder)
        assertEquals("t4", entries[4].item)
    }

    // ---- getSongsByKeys on the in-memory DAO (the SQL twin is tested against SQLite) ---------

    @Test fun daoReturnsOnlyLiveSongsOfTheAccountForTheKeysAsked() {
        val dao = InMemoryTelegramLibraryDao()
        dao.upsertSongs(listOf(song(1L), song(2L, deleted = true), song(3L), song(4L, acct = 2002L), song(5L)))
        val got = dao.getSongsByKeys(account, listOf(1L, 2L, 4L, 6L)).map { it.songKey }
        assertEquals(listOf(1L), got)
        assertTrue(dao.getSongsByKeys(account, emptyList()).isEmpty())
    }

    @Test fun daoReturnsAtMostOneSongPerKey() {
        val dao = InMemoryTelegramLibraryDao()
        // Same song key in two chats (a 62-bit hash collision is not expected, but must not duplicate rows).
        dao.upsertSongs(
            listOf(
                song(10L, chat = -1L).copy(messageId = 10L),
                song(10L, chat = -2L).copy(messageId = 11L),
            )
        )
        val got = dao.getSongsByKeys(account, listOf(10L, 10L))
        assertEquals(1, got.size)
        assertNotNull(got.single())
    }
}
