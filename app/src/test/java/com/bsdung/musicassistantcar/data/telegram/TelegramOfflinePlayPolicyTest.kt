package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class TelegramOfflinePlayPolicyTest {
    private fun song(id: Long, title: String = "Mix", chat: Long = 10, account: Long = 1, deleted: Boolean = false) =
        IndexedTelegramSong(account, chat, id, chat * 10000 + id, id.toInt(), title, "$id.flac", isDeleted = deleted)

    private fun entry(id: Long, path: String, title: String = "Mix", chat: Long = 10, account: Long = 1, deleted: Boolean = false) =
        TelegramOfflineLibrary.Entry(song(id, title, chat, account, deleted), path)

    private fun group(vararg entries: TelegramOfflineLibrary.Entry) = TelegramOfflineLibrary.Group(entries.toList())
    private fun key(e: TelegramOfflineLibrary.Entry) = e.song.songKey
    private val all: (TelegramOfflineLibrary.Entry) -> Boolean = { true }

    @Test fun singleCopyGroupReturnsThatCopy() {
        val only = entry(1, "/a")
        assertSame(only, TelegramOfflinePlayPolicy.defaultEntry(group(only), 1, null, all))
    }

    @Test fun sameFileFromManySourcesPlaysFirstCopyAndKeepsAllCopies() {
        val g = group(entry(3, "/a", chat = 30), entry(2, "/a", chat = 20), entry(1, "/a", chat = 10))
        val first = TelegramOfflinePlayPolicy.defaultEntry(g, 1, null, all)
        val second = TelegramOfflinePlayPolicy.defaultEntry(g, 1, null, all)
        assertSame(g.entries[0], first)
        assertSame(first, second)
        assertEquals(3, g.entries.size)
        assertEquals(listOf(30L, 20L, 10L), g.entries.map { it.song.chatId })
    }

    @Test fun sameTitleDifferentFileSkipsUnreadableFirstCopy() {
        val g = group(entry(2, "/gone"), entry(1, "/there"))
        val picked = TelegramOfflinePlayPolicy.defaultEntry(g, 1, null) { it.path == "/there" }
        assertSame(g.entries[1], picked)
    }

    @Test fun playingCopyWinsAndFallsBackToNextReadableInGroupOrder() {
        val g = group(entry(4, "/a"), entry(3, "/b"), entry(2, "/c"), entry(1, "/d"))
        val playing = key(g.entries[2])
        assertSame(g.entries[2], TelegramOfflinePlayPolicy.defaultEntry(g, 1, playing, all))
        // Playing copy lost its file: next readable copy in group order, not the copy after it.
        val picked = TelegramOfflinePlayPolicy.defaultEntry(g, 1, playing) { it.path != "/c" && it.path != "/a" }
        assertSame(g.entries[1], picked)
        // A playing key from another group is ignored.
        assertSame(g.entries[0], TelegramOfflinePlayPolicy.defaultEntry(g, 1, 999_999L, all))
    }

    @Test fun nothingReadableReturnsPlayingCopyOrFirstCopy() {
        val g = group(entry(3, "/a"), entry(2, "/b"), entry(1, "/c"))
        val none: (TelegramOfflineLibrary.Entry) -> Boolean = { false }
        assertSame(g.entries[1], TelegramOfflinePlayPolicy.defaultEntry(g, 1, key(g.entries[1]), none))
        assertSame(g.entries[0], TelegramOfflinePlayPolicy.defaultEntry(g, 1, null, none))
        assertSame(g.entries[0], TelegramOfflinePlayPolicy.defaultEntry(g, 1, 999_999L, none))
    }

    @Test fun otherAccountAndDeletedCopiesAreExcluded() {
        val foreign = entry(3, "/a", account = 2)
        val deleted = entry(2, "/a", deleted = true)
        val mine = entry(1, "/a")
        assertSame(mine, TelegramOfflinePlayPolicy.defaultEntry(group(foreign, deleted, mine), 1, key(foreign), all))
        assertNull(TelegramOfflinePlayPolicy.defaultEntry(group(foreign, deleted), 1, null, all))
        assertNull(TelegramOfflinePlayPolicy.defaultEntry(group(foreign), 1, key(foreign), all))
    }

    @Test fun queueHasOneEntryPerGroupInListOrderUsingSelectedCopy() {
        val g1 = group(entry(9, "/s", "S", chat = 10), entry(8, "/s", "S", chat = 20), entry(7, "/s", "S", chat = 30))
        val g2 = group(entry(6, "/x", "X", chat = 10), entry(5, "/y", "X", chat = 20))
        val g3 = group(entry(4, "/z", "Z", chat = 10))
        val groups = listOf(g1, g2, g3)
        val selected = key(g2.entries[1])
        val queue = TelegramOfflinePlayPolicy.queueEntries(groups, selected)
        assertEquals(3, queue.size)
        assertSame(g1.entries[0], queue[0])
        assertSame(g2.entries[1], queue[1])
        assertSame(g3.entries[0], queue[2])
        assertEquals(groups.size, queue.map { it.song.title }.distinct().size)
        // No path from a same-file group appears twice, so Next never replays the same song.
        assertEquals(1, queue.count { it.path == "/s" })
    }

    @Test fun queueWithForeignSelectionUsesFirstCopiesAndOriginalEntries() {
        val g1 = group(entry(9, "/s", "S", chat = 10), entry(8, "/s", "S", chat = 20))
        val g2 = group(entry(6, "/x", "X", chat = 10), entry(5, "/y", "X", chat = 20))
        val queue = TelegramOfflinePlayPolicy.queueEntries(listOf(g1, g2), 123_456_789L)
        assertEquals(2, queue.size)
        assertSame(g1.entries[0], queue[0])
        assertSame(g2.entries[0], queue[1])
        assertEquals(10L * 10000 + 9, queue[0].song.songKey)
        assertEquals(9L, queue[0].song.messageId)
        assertEquals(10L, queue[0].song.chatId)
        assertTrue(TelegramOfflinePlayPolicy.queueEntries(emptyList(), null).isEmpty())
        assertEquals(2, TelegramOfflinePlayPolicy.queueEntries(listOf(g1, g2), null).size)
    }
}
