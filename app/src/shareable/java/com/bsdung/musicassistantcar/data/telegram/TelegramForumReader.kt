package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

/** Bounded WORKER-thread reads over the app's existing owner. The caller cancels by invalidating isCurrent. */
internal class TelegramForumReader(
    private val dao: TelegramLibraryDao,
    private val request: (JSONObject, () -> Boolean) -> Reply,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed class Reply {
        data class Ok(val json: JSONObject) : Reply()
        data class Error(val code: Int, val retryAfterSeconds: Int = 0, val accessDenied: Boolean = false) : Reply()
        object Timeout : Reply()
        object Cancelled : Reply()
    }
    enum class Error { NETWORK, ACCESS, RATE_LIMIT, WAIT, SCHEMA, STALLED, STORAGE, CANCELLED }
    class ReadFailure(val reason: Error, val retryAtMs: Long = 0L) : RuntimeException()
    data class Catalogue(val topics: List<IndexedTelegramForumTopic>, val cursor: TelegramForumContract.ListCursor,
        val complete: Boolean, val stalled: Boolean)
    data class History(val state: TelegramForumHistoryState, val pages: Int, val error: Error? = null) {
        val hasMore: Boolean get() = !state.olderComplete || state.refreshPending
    }

    private fun call(json: JSONObject, isCurrent: () -> Boolean): JSONObject {
        if (!isCurrent()) throw ReadFailure(Error.CANCELLED)
        val response = request(json, isCurrent)
        if (!isCurrent()) throw ReadFailure(Error.CANCELLED)
        return when (response) {
            is Reply.Ok -> response.json
            is Reply.Error -> when {
                response.code == 429 -> throw ReadFailure(Error.RATE_LIMIT, clock() + maxOf(1, response.retryAfterSeconds) * 1000L)
                response.code == 401 || response.code == 403 || response.code == 404 || response.accessDenied -> throw ReadFailure(Error.ACCESS)
                response.code == 400 -> throw ReadFailure(Error.SCHEMA, clock() + 3_000L)
                else -> throw ReadFailure(Error.NETWORK, clock() + 3_000L)
            }
            Reply.Timeout -> throw ReadFailure(Error.NETWORK, clock() + 3_000L)
            Reply.Cancelled -> throw ReadFailure(Error.CANCELLED)
        }
    }

    fun discover(accountId: Long, chatId: Long, title: String, isCurrent: () -> Boolean): TelegramForumContract.ForumState {
        val chat = call(JSONObject().put("@type", "getChat").put("chat_id", chatId), isCurrent)
        if (chat.optString("@type") != "chat" || chat.optLong("id") != chatId) throw ReadFailure(Error.SCHEMA)
        val type = chat.optJSONObject("type") ?: throw ReadFailure(Error.SCHEMA)
        val state = if (type.optString("@type") != "chatTypeSupergroup") TelegramForumContract.ForumState.REGULAR else {
            val groupId = type.optLong("supergroup_id")
            if (groupId <= 0L) throw ReadFailure(Error.SCHEMA)
            val group = call(JSONObject().put("@type", "getSupergroup").put("supergroup_id", groupId), isCurrent)
            try {
                if (group.optLong("id") != groupId) throw ReadFailure(Error.SCHEMA)
                TelegramForumContract.forumState(group)
            } catch (_: org.json.JSONException) { throw ReadFailure(Error.SCHEMA) }
            catch (_: IllegalArgumentException) { throw ReadFailure(Error.SCHEMA) }
        }
        if (!isCurrent()) throw ReadFailure(Error.CANCELLED)
        val old = dao.getSources(accountId).firstOrNull { it.chatId == chatId }
        if (!isCurrent()) throw ReadFailure(Error.CANCELLED)
        dao.upsertSources(listOf((old ?: IndexedTelegramSource(accountId, chatId, title)).copy(
            isForum = state == TelegramForumContract.ForumState.FORUM)))
        return state
    }

    fun catalogueCursor(accountId: Long, chatId: Long): TelegramForumContract.ListCursor =
        dao.getSyncState(accountId, TelegramLibraryDao.forumCatalogueScope(chatId))?.nextOffset?.takeIf { it.isNotBlank() }?.let {
            runCatching { val json = JSONObject(it); TelegramForumContract.ListCursor(json.getInt("date"), json.getLong("message"), json.getInt("topic")) }.getOrNull()
        } ?: TelegramForumContract.ListCursor()

    /** Direct metadata read proves rename/hidden/closed state without scanning other topic histories. */
    fun topic(accountId: Long, chatId: Long, topicId: Int, isCurrent: () -> Boolean): IndexedTelegramForumTopic {
        val response = call(JSONObject().put("@type", "getForumTopic").put("chat_id", chatId).put("forum_topic_id", topicId), isCurrent)
        val parsed = try {
            TelegramForumContract.topicPage(JSONObject().put("@type", "forumTopics").put("topics", org.json.JSONArray().put(response))
                .put("next_offset_date", 0).put("next_offset_message_id", 0).put("next_offset_forum_topic_id", 0), chatId).topics.single()
        } catch (_: Exception) { throw ReadFailure(Error.SCHEMA) }
        if (parsed.id != topicId) throw ReadFailure(Error.SCHEMA)
        val topic = IndexedTelegramForumTopic(accountId, chatId, topicId, parsed.name, parsed.isGeneral, parsed.isHidden,
            parsed.isClosed, parsed.isPinned, parsed.order, clock())
        if (!dao.commitForumTopics(accountId, chatId, listOf(topic), isCurrent = isCurrent)) throw ReadFailure(Error.CANCELLED)
        return topic
    }

    /** One catalogue page. Search results stay separate from the persistent catalogue and its checkpoint. */
    fun catalogue(accountId: Long, chatId: Long, cursor: TelegramForumContract.ListCursor = TelegramForumContract.ListCursor(),
        query: String = "", isCurrent: () -> Boolean): Catalogue {
        val response = call(TelegramForumContract.topicsRequest(chatId, cursor, query), isCurrent)
        val page = try { TelegramForumContract.topicPage(response, chatId, cursor) }
        catch (_: Exception) { throw ReadFailure(Error.SCHEMA) }
        val topics = page.topics.map { IndexedTelegramForumTopic(accountId, chatId, it.id, it.name,
            it.isGeneral, it.isHidden, it.isClosed, it.isPinned, it.order, clock()) }
        if (query.isBlank()) {
            // Hidden General may be absent from the list: verify a previously visible General directly.
            if (cursor.isEnd) dao.getForumTopics(accountId, chatId, limit = Int.MAX_VALUE).firstOrNull { it.isGeneral }
                ?.takeIf { cached -> page.topics.none { it.id == cached.forumTopicId } }
                ?.let { topic(accountId, chatId, it.forumTopicId, isCurrent) }
            val scope = TelegramLibraryDao.forumCatalogueScope(chatId)
            val old = dao.getSyncState(accountId, scope)
            val offset = JSONObject().put("date", page.next.date).put("message", page.next.messageId).put("topic", page.next.topicId).toString()
            val checkpoint = TelegramSyncState(accountId, scope, "forum_catalogue", offset,
                pagesSynced = if (cursor.isEnd) 1 else (old?.pagesSynced ?: 0) + 1,
                status = if (page.complete) TelegramSyncState.STATUS_COMPLETE else TelegramSyncState.STATUS_PARTIAL)
            if (!dao.commitForumTopics(accountId, chatId, topics, checkpoint, isCurrent)) throw ReadFailure(Error.CANCELLED)
        }
        if (!isCurrent()) throw ReadFailure(Error.CANCELLED)
        return Catalogue(topics, page.next, page.complete, page.stalled)
    }

    fun history(accountId: Long, chatId: Long, topicId: Int, sourceTitle: String?, refresh: Boolean,
        budget: Int = 3, isCurrent: () -> Boolean, onPage: (TelegramForumHistoryState) -> Unit = {}): History {
        var state = dao.getForumHistoryState(accountId, chatId, topicId) ?: TelegramForumHistoryState(accountId, chatId, topicId)
        if (state.retryAfterMs > clock()) return History(state, 0, Error.WAIT)
        if (refresh && state.newestMessageId > 0L && !state.refreshPending) state = state.copy(
            refreshPending = true, refreshCursor = 0L, refreshUntilMessageId = state.newestMessageId)
        var pages = 0
        try {
            while (pages < budget.coerceIn(1, 3) && (state.refreshPending || !state.olderComplete)) {
                val head = state.refreshPending
                val from = if (head) state.refreshCursor else state.olderCursor
                val response = call(TelegramForumContract.historyRequest(chatId, topicId, from), isCurrent)
                val page = try { TelegramForumContract.historyPage(response, chatId, topicId, from) }
                catch (_: Exception) { throw ReadFailure(Error.SCHEMA) }
                if (page.rejected > 0) throw ReadFailure(Error.SCHEMA)
                if (page.stalled) throw ReadFailure(Error.STALLED)
                val messages = response.getJSONArray("messages")
                var newest = state.newestMessageId
                var reached = page.complete
                for (i in 0 until messages.length()) {
                    val raw = messages.getJSONObject(i).getLong("id")
                    newest = maxOf(newest, raw)
                    if (head && raw <= state.refreshUntilMessageId) reached = true
                }
                val songs = page.messages.mapNotNull { TelegramSongMessageParser.parse(it, accountId, accountId,
                    chatId, sourceTitle, topicId, clock()) }
                val next = if (head) state.copy(newestMessageId = newest, refreshPending = !reached,
                    refreshCursor = if (reached) 0L else page.nextMessageId,
                    refreshUntilMessageId = if (reached) 0L else state.refreshUntilMessageId, retryAfterMs = 0L)
                else state.copy(olderCursor = page.nextMessageId, olderComplete = page.complete,
                    newestMessageId = newest, retryAfterMs = 0L)
                if (!dao.commitForumHistoryPage(next, songs, isCurrent)) throw ReadFailure(Error.CANCELLED)
                state = next
                pages++
                if (isCurrent()) onPage(state)
            }
            return History(state, pages)
        } catch (failure: ReadFailure) {
            if (failure.reason != Error.CANCELLED && isCurrent()) {
                val retryAt = if (failure.retryAtMs > 0L) failure.retryAtMs else clock() + 3_000L
                val paused = state.copy(retryAfterMs = retryAt)
                if (dao.commitForumHistoryPage(paused, emptyList(), isCurrent)) state = paused
            }
            return History(state, pages, failure.reason)
        }
    }
}
