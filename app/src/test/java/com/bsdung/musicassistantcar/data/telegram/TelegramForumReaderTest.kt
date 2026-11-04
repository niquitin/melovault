package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TelegramForumReaderTest {
    private val account = 11L
    private val chat = -10001L
    private val topic = 71
    private class Port {
        val calls = mutableListOf<JSONObject>()
        val replies = java.util.ArrayDeque<TelegramForumReader.Reply>()
        var after: () -> Unit = {}
        fun reply(json: JSONObject) { replies.add(TelegramForumReader.Reply.Ok(json)) }
        fun call(json: JSONObject, current: () -> Boolean): TelegramForumReader.Reply {
            assertTrue(current())
            calls.add(JSONObject(json.toString()))
            return replies.removeFirst().also { after() }
        }
    }
    private fun fixture(name: String) = JSONObject(requireNotNull(javaClass.getResource("/telegram-forum/$name.json")).readText())
    private fun message(id: Long, file: String? = null, topicId: Int? = topic, chatId: Long = chat, audio: Boolean = false): JSONObject {
        val content = if (file == null) JSONObject().put("@type", "messageText") else {
            val field = if (audio) "audio" else "document"
            JSONObject().put("@type", if (audio) "messageAudio" else "messageDocument").put(field,
                JSONObject().put("file_name", file).put("performer", "Nghệ sĩ thử").put("duration", 120)
                    .put(field, JSONObject().put("id", 81).put("size", 4096)))
        }
        return JSONObject().put("@type", "message").put("id", id).put("chat_id", chatId).put("date", id)
            .put("content", content).also { if (topicId != null) it.put("topic_id", JSONObject().put("@type", "messageTopicForum").put("forum_topic_id", topicId)) }
    }
    private fun page(vararg messages: JSONObject) = JSONObject().put("@type", "messages").put("messages", JSONArray(messages.toList()))
    private fun reader(dao: TelegramLibraryDao, port: Port, clock: () -> Long = { 1000L }) = TelegramForumReader(dao, port::call, clock)

    @Test fun textOnlyPagesRespectBudgetAndResumeAfterRecreatingReader() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port()
        listOf(900L, 800L, 700L).forEach { port.reply(page(message(it))) }
        val first = reader(dao, port).history(account, chat, topic, null, true, isCurrent = { true })
        assertEquals(3, first.pages); assertTrue(first.hasMore)
        assertEquals(700L, first.state.olderCursor); assertEquals(900L, first.state.newestMessageId)
        assertEquals(0, dao.getSongCount(account))
        port.reply(page(message(600))); port.reply(page())
        val resumed = reader(dao, port).history(account, chat, topic, null, false, isCurrent = { true })
        assertTrue(resumed.state.olderComplete); assertFalse(resumed.hasMore)
        assertEquals(listOf(0L, 900L, 800L, 700L, 600L), port.calls.map { it.getLong("from_message_id") })
    }

    @Test fun headRefreshWhileOlderScanIsIncompleteKeepsIndependentCursorsAndDeduplicates() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port()
        val oldSong = TelegramSongMessageParser.parse(message(900, "same.wav"), account, account)!!
        dao.commitForumHistoryPage(TelegramForumHistoryState(account, chat, topic, olderCursor = 600, newestMessageId = 900), listOf(oldSong))
        port.reply(page(message(1000, "same.wav"), message(900, "same.wav")))
        port.reply(page(message(500, "same.wav"))); port.reply(page())
        val result = reader(dao, port).history(account, chat, topic, "Nguồn", true, isCurrent = { true })
        assertEquals(1000L, result.state.newestMessageId)
        assertEquals(500L, result.state.olderCursor)
        assertFalse(result.state.refreshPending); assertTrue(result.state.olderComplete)
        assertEquals(listOf(0L, 600L, 500L), port.calls.map { it.getLong("from_message_id") })
        assertEquals(3, dao.getSongCount(account, chat, forumTopicId = topic))
        assertEquals(3, dao.getSongs(account, chat, forumTopicId = topic).map { it.songKey }.distinct().size)
    }

    @Test fun interruptedHeadScanResumesBeforeContinuingOlderHistory() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port()
        dao.commitForumHistoryPage(TelegramForumHistoryState(account, chat, topic, olderCursor = 600, newestMessageId = 900), emptyList())
        listOf(1000L, 950L, 920L).forEach { port.reply(page(message(it))) }
        val partial = reader(dao, port).history(account, chat, topic, null, true, isCurrent = { true })
        assertTrue(partial.state.refreshPending)
        assertEquals(920L, partial.state.refreshCursor); assertEquals(900L, partial.state.refreshUntilMessageId)
        assertEquals(600L, partial.state.olderCursor)
        port.reply(page(message(900))); port.reply(page(message(500))); port.reply(page())
        val complete = reader(dao, port).history(account, chat, topic, null, true, isCurrent = { true })
        assertEquals(920L, port.calls[3].getLong("from_message_id"))
        assertTrue(complete.state.olderComplete); assertFalse(complete.state.refreshPending)
    }

    @Test fun sharedParserRecognizesAudioAndMusicDocumentsWithoutClassifyingNonMusic() {
        for ((name, audio) in listOf("01.WAV" to false, "02.flac" to false, "03.mp3" to false, "file" to true)) {
            val parsed = TelegramSongMessageParser.parse(message(100, name, audio = audio), account, account)!!
            assertEquals(topic, parsed.forumTopicId); assertEquals(120, parsed.durationSeconds)
            assertEquals(4096L, parsed.fileSize)
            assertEquals(TelegramSongKey.forMessage(chat, 100, account), parsed.songKey)
        }
        for (name in listOf("cover.jpg", "archive.zip", "book.pdf", "new.dsf")) {
            assertNull(TelegramSongMessageParser.parse(message(100, name), account, account))
        }
        assertNull(TelegramSongMessageParser.parse(message(100), account, account))
        val unknown = message(100, "old.wav", topicId = null)
        assertNull(TelegramSongMessageParser.parse(unknown, account, account)?.forumTopicId)
        assertEquals(topic, TelegramSongMessageParser.parse(unknown, account, account, chat, requestedTopicId = topic)?.forumTopicId)
        assertNull(TelegramSongMessageParser.parse(message(100, "bad.wav", topicId = 72), account, account, chat, requestedTopicId = topic))
    }

    @Test fun lateCancelledResponseCannotWriteSongsCheckpointOrPublish() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port(); var current = true; var publishes = 0
        port.reply(page(message(100, "song.wav"))); port.after = { current = false }
        val result = reader(dao, port).history(account, chat, topic, null, true, isCurrent = { current }, onPage = { publishes++ })
        assertEquals(TelegramForumReader.Error.CANCELLED, result.error)
        assertEquals(0, dao.getSongCount(account)); assertNull(dao.getForumHistoryState(account, chat, topic)); assertEquals(0, publishes)
    }

    @Test fun conflictingTopicOrForeignChatStopsWithoutAdvancingCursor() {
        for (bad in listOf(message(100, "bad.wav", topicId = 72), message(100, "bad.wav", chatId = -10002))) {
            val dao = InMemoryTelegramLibraryDao(); val port = Port()
            val state = TelegramForumHistoryState(account, chat, topic, olderCursor = 200)
            dao.commitForumHistoryPage(state, emptyList()); port.reply(page(bad))
            val result = reader(dao, port).history(account, chat, topic, null, false, isCurrent = { true })
            assertEquals(TelegramForumReader.Error.SCHEMA, result.error)
            assertEquals(200L, dao.getForumHistoryState(account, chat, topic)?.olderCursor)
            assertEquals(0, dao.getSongCount(account))
        }
    }

    @Test fun floodWaitIsPersistedAndReopeningCannotBypassIt() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port(); var now = 1000L
        port.replies.add(TelegramForumReader.Reply.Error(429, 35))
        val first = reader(dao, port) { now }.history(account, chat, topic, null, true, isCurrent = { true })
        assertEquals(TelegramForumReader.Error.RATE_LIMIT, first.error); assertEquals(36000L, first.state.retryAfterMs)
        val waiting = reader(dao, port) { now }.history(account, chat, topic, null, true, isCurrent = { true })
        assertEquals(TelegramForumReader.Error.WAIT, waiting.error); assertEquals(1, port.calls.size)
        now = 36001; port.reply(page())
        assertTrue(reader(dao, port) { now }.history(account, chat, topic, null, false, isCurrent = { true }).state.olderComplete)
    }

    @Test fun catalogueUsesExactCursorAndSearchNeverReplacesPersistentCatalogue() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port(); val r = reader(dao, port)
        port.reply(fixture("topics-first"))
        val first = r.catalogue(account, chat, isCurrent = { true })
        assertEquals(first.cursor, r.catalogueCursor(account, chat))
        assertEquals(listOf(71), dao.getForumTopics(account, chat).map { it.forumTopicId })
        val checkpoint = dao.getSyncState(account, TelegramLibraryDao.forumCatalogueScope(chat))
        port.reply(fixture("topics-end"))
        r.catalogue(account, chat, query = "remote", isCurrent = { true })
        assertEquals(checkpoint, dao.getSyncState(account, checkpoint!!.syncScope))
        assertEquals(1, dao.getForumTopics(account, chat).size)
        port.reply(fixture("topics-first"))
        val stalled = r.catalogue(account, chat, first.cursor, isCurrent = { true })
        assertTrue(stalled.stalled); assertFalse(stalled.complete)
        assertEquals(first.cursor.messageId, port.calls.last().getLong("offset_message_id"))
    }

    @Test fun forumLookupIsOnDemandAndFailedRefreshCannotEraseKnownState() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port(); val r = reader(dao, port)
        assertTrue(port.calls.isEmpty())
        port.reply(JSONObject("""{"@type":"chat","id":-10001,"type":{"@type":"chatTypeSupergroup","supergroup_id":87}}"""))
        port.reply(JSONObject("""{"@type":"supergroup","id":87,"is_forum":true}"""))
        assertEquals(TelegramForumContract.ForumState.FORUM, r.discover(account, chat, "Nhóm", { true }))
        assertEquals(listOf("getChat", "getSupergroup"), port.calls.map { it.getString("@type") })
        port.replies.add(TelegramForumReader.Reply.Timeout)
        assertThrows(TelegramForumReader.ReadFailure::class.java) { r.discover(account, chat, "Nhóm", { true }) }
        assertEquals(true, dao.getSources(account).single().isForum)
        assertTrue(dao.getSources(12).isEmpty())
    }

    @Test fun hiddenGeneralIsVerifiedDirectlyAndRenameKeepsTheSameTopicIdentity() {
        val dao = InMemoryTelegramLibraryDao(); val port = Port(); val r = reader(dao, port)
        dao.commitForumTopics(account, chat, listOf(IndexedTelegramForumTopic(account, chat, 1, "General", isGeneral = true)))
        val hidden = fixture("topics-first").getJSONArray("topics").getJSONObject(0)
        hidden.getJSONObject("info").put("name", "Tên mới")
        port.reply(fixture("topics-end")); port.reply(hidden)
        r.catalogue(account, chat, isCurrent = { true })
        assertTrue(dao.getForumTopics(account, chat).isEmpty())
        assertEquals(listOf("getForumTopics", "getForumTopic"), port.calls.map { it.getString("@type") })
        hidden.getJSONObject("info").put("is_hidden", false)
        port.reply(hidden)
        val renamed = r.topic(account, chat, 1, { true })
        assertEquals("Tên mới", renamed.title); assertEquals(1, renamed.forumTopicId)
        assertEquals(1, dao.getForumTopics(account, chat).size)
    }
}
