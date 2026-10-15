package com.bsdung.musicassistantcar.data.telegram

/** Song-list display never duplicates Telegram's title and its filename. */
internal object TelegramSongDisplayPolicy {
    fun label(title: String, filename: String): String {
        val t = title.trim()
        val f = filename.trim()
        if (t.isNotBlank() && t != "audio") return t
        return f.ifBlank { "Bài hát không tên" }
    }
}
