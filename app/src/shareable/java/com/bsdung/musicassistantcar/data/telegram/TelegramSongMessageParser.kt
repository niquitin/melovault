package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

/** Shared recognition for global scans and topic history; inspecting metadata never downloads a file. */
internal object TelegramSongMessageParser {
    private val extensions = listOf(".flac", ".mp3", ".m4a", ".wav", ".ogg", ".opus", ".aac")

    fun file(message: JSONObject): JSONObject? {
        val content = message.optJSONObject("content") ?: return null
        val field = when (content.optString("@type")) { "messageAudio" -> "audio"; "messageDocument" -> "document"; else -> return null }
        return content.optJSONObject(field)?.optJSONObject(field)
    }

    fun parse(message: JSONObject, accountId: Long, selfChatId: Long, fallbackChatId: Long = 0L,
        sourceTitle: String? = null, requestedTopicId: Int? = null, nowMs: Long = System.currentTimeMillis()): IndexedTelegramSong? {
        if (accountId <= 0L) return null
        val content = message.optJSONObject("content") ?: return null
        val field = when (content.optString("@type")) { "messageAudio" -> "audio"; "messageDocument" -> "document"; else -> return null }
        val attachment = content.optJSONObject(field) ?: return null
        val file = attachment.optJSONObject(field) ?: return null
        val filename = attachment.optString("file_name", "audio")
        if (field == "document" && extensions.none { filename.endsWith(it, ignoreCase = true) }) return null
        val chatId = message.optLong("chat_id").takeIf { it != 0L } ?: fallbackChatId
        val messageId = message.optLong("id")
        val fileId = file.optInt("id")
        if (chatId == 0L || messageId <= 0L || fileId <= 0) return null
        if (requestedTopicId != null && chatId != fallbackChatId) return null
        val relation = TelegramForumContract.relation(message, chatId, requestedTopicId)
        if (requestedTopicId != null && relation !is TelegramForumContract.Relation.Known) return null
        return IndexedTelegramSong(accountId, chatId, messageId, TelegramSongKey.forMessage(chatId, messageId, selfChatId),
            fileId, attachment.optString("title").ifBlank { filename }, filename,
            attachment.optString("performer").takeIf { it.isNotBlank() }, attachment.optInt("duration"),
            file.optLong("size").takeIf { it > 0 } ?: file.optLong("expected_size").takeIf { it > 0 } ?: -1L,
            attachment.optString("mime_type").takeIf { it.isNotBlank() }, field, message.optLong("date"), nowMs, sourceTitle,
            forumTopicId = (relation as? TelegramForumContract.Relation.Known)?.id)
    }
}
