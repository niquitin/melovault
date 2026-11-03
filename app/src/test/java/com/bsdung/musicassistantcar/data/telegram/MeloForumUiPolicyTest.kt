package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class MeloForumUiPolicyTest {
    private fun topic(chat: Long = -1001, id: Int = 71, downloaded: Boolean = false) =
        MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.FORUM_TOPIC, chat, downloaded, id)

    @Test fun forumCatalogueAndSongScopesCannotShareRowsOrAccountIdentity() {
        val catalogue = MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.FORUM_TOPICS, -1001)
        val a = MeloBrowseUiPolicy.Request(11, 1, topic())
        for (scope in listOf(catalogue, topic(id = 72), topic(chat = -1002), topic(downloaded = true))) {
            assertFalse(MeloBrowseUiPolicy.canKeepRows(a, MeloBrowseUiPolicy.Request(11, 2, scope)))
        }
        assertNotEquals(MeloBrowseState.Key(11, topic()), MeloBrowseState.Key(12, topic()))
        assertFalse(MeloBrowseUiPolicy.accepts(a, 12, 1, true))
        assertFalse(MeloBrowseUiPolicy.accepts(a, 11, 2, true))
    }

    @Test fun scopeFactoryKeepsTopicWhileDownloadedFilterChanges() {
        assertEquals(topic(downloaded = true), MeloBrowseUiPolicy.scope(false, false, true, true, -1001, 71))
        assertEquals(topic(), MeloBrowseUiPolicy.scope(false, false, false, true, -1001, 71))
        assertEquals(MeloBrowseUiPolicy.Kind.FORUM_TOPICS,
            MeloBrowseUiPolicy.scope(false, false, false, true, -1001, forumTopics = true).kind)
        assertThrows(IllegalArgumentException::class.java) { MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.FORUM_TOPIC, -1001) }
        assertThrows(IllegalArgumentException::class.java) { topic(id = 0) }
    }

    @Test fun savedForumContextKeepsParentSourceTopicAndBothFilterQueries() {
        val state = MeloBrowseState()
        val source = MeloBrowseState.Key(11, MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.SOURCES))
        val parent = MeloBrowseState.Key(11, MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.FORUM_TOPICS, -1001))
        val all = MeloBrowseState.Key(11, topic())
        val downloaded = MeloBrowseState.Key(11, topic(downloaded = true))
        state.activate(source, SongSort.NEWEST); state.sourcePosition(source, -1001, -32)
        state.activate(parent, SongSort.NEWEST); state.query(parent, "nhac"); state.topicPosition(parent, 72, -18)
        state.activate(all, SongSort.NAME); state.query(all, "audio"); state.position(all, 991, -12, 0)
        state.activate(downloaded, SongSort.NEWEST); state.query(downloaded, "offline")
        state.topicsReturn = parent
        val saved = state.save()!!
        val restored = MeloBrowseState()
        assertTrue(restored.restore(saved, 11))
        assertEquals("audio", restored.get(all)?.query)
        assertEquals("offline", restored.get(downloaded)?.query)
        assertEquals(72, restored.get(parent)?.topicAnchorId)
        assertEquals(-18, restored.get(parent)?.topicOffsetPx)
        assertEquals(-1001L, restored.get(source)?.sourceAnchorChatId)
        assertFalse(MeloBrowseState().restore(saved, 12))
    }

    @Test fun changingCatalogueSearchResetsOnlyItsTopicScrollAnchor() {
        val state = MeloBrowseState()
        val key = MeloBrowseState.Key(11, MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.FORUM_TOPICS, -1001))
        state.activate(key, SongSort.NEWEST); state.topicPosition(key, 71, -9)
        state.query(key, "new")
        assertNull(state.get(key)?.topicAnchorId)
        assertEquals(0, state.get(key)?.topicOffsetPx)
    }

    @Test fun oldOrIncompleteStateFallsBackToGroupWithoutGuessingGeneral() {
        assertEquals(MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.CHANNEL, -1001),
            MeloBrowseState.decodeKey(11, "CHANNEL", -1001, false)?.scope)
        assertEquals(MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.CHANNEL, -1001),
            MeloBrowseState.decodeKey(11, "FORUM_TOPIC", -1001, true)?.scope)
        assertEquals(topic(downloaded = true), MeloBrowseState.decodeKey(11, "FORUM_TOPIC", -1001, true, 71)?.scope)
        assertNull(MeloBrowseState.decodeKey(11, "LIBRARY", null, true))
    }

    @Test fun countLabelsNeverCallPartialOrUnknownHistoryEmpty() {
        val partial = TelegramForumHistoryState(11, -1001, 71, olderCursor = 500)
        assertEquals("Chưa lập chỉ mục", MeloForumUiPolicy.countLabel(0, null))
        assertEquals("Đã tìm thấy 0 bài", MeloForumUiPolicy.countLabel(0, partial))
        assertEquals("Đã tìm thấy 3 bài", MeloForumUiPolicy.countLabel(3, null))
        assertEquals("Chưa có nhạc", MeloForumUiPolicy.countLabel(0, partial.copy(olderComplete = true)))
        assertEquals("Đã tìm thấy 3 bài", MeloForumUiPolicy.countLabel(3, partial.copy(olderComplete = true, refreshPending = true)))
    }

    @Test fun queueKeepsAllIndexedPagesAndSameNamedMessagesButRejectsForeignScope() {
        fun song(message: Long, account: Long = 11, chat: Long = -1001, id: Int? = 71) = IndexedTelegramSong(
            account, chat, message, TelegramSongKey.forMessage(chat, message, account), 81, "Same", "same.wav", forumTopicId = id)
        val a = (1L..503L).map { song(it) }
        val b = (10001L..10503L).map { song(it, id = 72) }
        val rows = a + b + listOf(song(99991, account = 12), song(99992, chat = -1002), song(99993, id = null), song(99994).copy(isDeleted = true))
        val queue = MeloForumUiPolicy.queue(rows, 11, -1001, 71)
        assertEquals(503, queue.size)
        assertEquals(a.map { it.songKey }, queue.map { it.songKey })
        assertTrue(queue.any { it.messageId > 200 })
        assertEquals(503, queue.map { it.songKey }.distinct().size)
    }

    @Test fun sourceBadgeRequiresConfirmedForumMetadata() {
        val row = MeloScreenHost.SourceRow(-1001, "Nhóm", 3)
        assertFalse(MeloLibraryPolicy.sourcePresentation(row, null).detail.contains("Có chủ đề"))
        assertTrue(MeloLibraryPolicy.sourcePresentation(row.copy(hasTopics = true), null).detail.contains("Có chủ đề"))
    }
}
