package com.bsdung.musicassistantcar.data.telegram

import java.security.MessageDigest

/**
 * Stable account-local identifier for a message within its Telegram chat.
 * Older Saved Messages playlists keep their original message IDs unchanged.
 * IDs from other chats are domain-separated before entering playlists/cache.
 */
internal object TelegramSongKey {
    private const val EXTERNAL_MARKER = 1L shl 62
    private const val MASK = EXTERNAL_MARKER - 1L

    fun forMessage(chatId: Long, messageId: Long, savedChatId: Long): Long {
        require(chatId != 0L && messageId > 0L)
        if (chatId == savedChatId) return messageId
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("melovault:chat-message:v1:$chatId:$messageId".toByteArray(Charsets.UTF_8))
        var hash = 0L
        for (i in 0 until 8) {
            hash = (hash shl 8) or (digest[i].toLong() and 255L)
        }
        return EXTERNAL_MARKER or (hash and MASK)
    }
}
