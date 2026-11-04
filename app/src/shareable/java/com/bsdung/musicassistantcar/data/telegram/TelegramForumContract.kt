package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

/** Read contract for the vendored TDLib c15d3f5. No client, file download or database access. */
internal object TelegramForumContract {
    enum class ForumState { UNKNOWN, FORUM, REGULAR }

    data class Topic(
        val id: Int, val name: String, val isGeneral: Boolean, val isHidden: Boolean,
        val isClosed: Boolean, val isPinned: Boolean, val order: Long,
    )

    data class ListCursor(val date: Int = 0, val messageId: Long = 0, val topicId: Int = 0) {
        val isEnd: Boolean get() = date == 0 && messageId == 0L && topicId == 0
    }

    data class TopicPage(val topics: List<Topic>, val next: ListCursor, val complete: Boolean, val stalled: Boolean)

    sealed class Relation {
        data class Known(val id: Int) : Relation()
        object Unknown : Relation()
        object Rejected : Relation()
    }

    data class HistoryPage(
        /** Includes non-music messages: paging must advance through text, photos and service messages too. */
        val messages: List<JSONObject>, val nextMessageId: Long, val complete: Boolean,
        val boundaryProbe: Boolean, val rejected: Int, val stalled: Boolean = false,
    )

    private fun int32(json: JSONObject, key: String): Int? = json.opt(key)?.toString()?.toLongOrNull()
        ?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()

    fun forumState(supergroup: JSONObject): ForumState {
        require(supergroup.optString("@type") == "supergroup" && supergroup.has("is_forum"))
        return if (supergroup.getBoolean("is_forum")) ForumState.FORUM else ForumState.REGULAR
    }

    /** Only a forum discriminator proves membership. Never interpret a message thread id as a forum id. */
    fun relation(message: JSONObject, chatId: Long, requestedTopicId: Int? = null): Relation {
        if (message.optString("@type") != "message" || message.optLong("chat_id") != chatId ||
            message.optLong("id") <= 0L) return Relation.Rejected
        if (!message.has("topic_id") || message.isNull("topic_id")) {
            return requestedTopicId?.let { Relation.Known(it) } ?: Relation.Unknown
        }
        val topic = message.optJSONObject("topic_id") ?: return Relation.Rejected
        if (topic.optString("@type") != "messageTopicForum") {
            return if (requestedTopicId == null) Relation.Unknown else Relation.Rejected
        }
        val id = int32(topic, "forum_topic_id") ?: return Relation.Rejected
        return if (requestedTopicId == null || requestedTopicId == id) Relation.Known(id) else Relation.Rejected
    }

    fun topicsRequest(chatId: Long, cursor: ListCursor = ListCursor(), query: String = "", limit: Int = 50): JSONObject =
        JSONObject().put("@type", "getForumTopics").put("chat_id", chatId).put("query", query)
            .put("offset_date", cursor.date).put("offset_message_id", cursor.messageId)
            .put("offset_forum_topic_id", cursor.topicId).put("limit", limit.coerceIn(1, 100))

    fun topicPage(json: JSONObject, chatId: Long, cursor: ListCursor = ListCursor()): TopicPage {
        require(json.getString("@type") == "forumTopics")
        val items = json.getJSONArray("topics")
        val topics = LinkedHashMap<Int, Topic>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            require(item.getString("@type") == "forumTopic")
            val info = item.getJSONObject("info")
            require(info.getString("@type") == "forumTopicInfo" && info.getLong("chat_id") == chatId)
            val id = requireNotNull(int32(info, "forum_topic_id"))
            topics[id] = Topic(id, info.getString("name"), info.getBoolean("is_general"),
                info.getBoolean("is_hidden"), info.getBoolean("is_closed"), item.getBoolean("is_pinned"),
                item.get("order").toString().toLong())
        }
        // All three fields are required: a malformed reply is never mistaken for end-of-list.
        val next = ListCursor(json.getInt("next_offset_date"), json.getLong("next_offset_message_id"),
            json.getInt("next_offset_forum_topic_id"))
        require(next.date >= 0 && next.messageId >= 0L && next.topicId >= 0)
        return TopicPage(topics.values.toList(), next, next.isEnd, !next.isEnd && next == cursor)
    }

    fun historyRequest(chatId: Long, topicId: Int, fromMessageId: Long = 0L, limit: Int = 50): JSONObject =
        JSONObject().put("@type", "getForumTopicHistory").put("chat_id", chatId).put("forum_topic_id", topicId)
            .put("from_message_id", fromMessageId).put("offset", 0).put("limit", limit.coerceIn(1, 100))

    fun historyPage(json: JSONObject, chatId: Long, topicId: Int, fromMessageId: Long): HistoryPage {
        require(json.getString("@type") == "messages")
        val items = json.getJSONArray("messages")
        val accepted = LinkedHashMap<Long, JSONObject>()
        var rejected = 0
        var oldest = Long.MAX_VALUE
        for (i in 0 until items.length()) {
            val message = items.getJSONObject(i)
            // A foreign/malformed message cannot advance the checkpoint and hide legitimate music.
            if (relation(message, chatId, topicId) !is Relation.Known) { rejected++; continue }
            val id = message.getLong("id")
            if (fromMessageId > 0L && id > fromMessageId) { rejected++; continue }
            oldest = minOf(oldest, id)
            if (id != fromMessageId) accepted[id] = message
        }
        if (rejected > 0) return HistoryPage(emptyList(), fromMessageId, false, false, rejected)
        if (items.length() == 0) return HistoryPage(emptyList(), fromMessageId, true, false, 0)
        // A one-message inclusive boundary is not proof of completion. Ask immediately below its RAW id.
        val probe = fromMessageId > 0L && oldest == fromMessageId
        if (probe && oldest <= 1L) return HistoryPage(emptyList(), fromMessageId, false, false, 0, stalled = true)
        val next = if (probe) oldest - 1L else oldest
        return HistoryPage(accepted.values.toList(), next, false, probe, 0)
    }
}
