package com.bsdung.musicassistantcar.data.telegram

internal object MeloForumUiPolicy {
    fun countLabel(count: Int, history: TelegramForumHistoryState?): String = when {
        history?.olderComplete == true && !history.refreshPending -> if (count == 0) "Chưa có nhạc" else "$count bài"
        history != null || count > 0 -> "Đã tìm thấy $count bài"
        else -> "Chưa lập chỉ mục"
    }

    fun errorText(error: TelegramForumReader.Error): String = when (error) {
        TelegramForumReader.Error.ACCESS -> "Không còn xem được chủ đề này. Dữ liệu đã lưu vẫn được giữ."
        TelegramForumReader.Error.RATE_LIMIT, TelegramForumReader.Error.WAIT -> "Cần chờ trước khi đọc tiếp. Thử lại sau."
        TelegramForumReader.Error.NETWORK -> "Chưa kết nối được Telegram. Thử lại."
        TelegramForumReader.Error.STALLED -> "Chưa đọc tiếp được trang này. Thử lại."
        TelegramForumReader.Error.SCHEMA -> "Chưa xác minh được dữ liệu chủ đề. Thử lại."
        TelegramForumReader.Error.STORAGE -> "Chưa lưu được dữ liệu chủ đề. Thử lại."
        TelegramForumReader.Error.CANCELLED -> ""
    }

    /** Defensive boundary before queue configuration, including an account check absent from the player Song DTO. */
    fun queue(rows: List<IndexedTelegramSong>, accountId: Long, chatId: Long, topicId: Int): List<IndexedTelegramSong> =
        rows.filter { it.accountId == accountId && it.chatId == chatId && it.forumTopicId == topicId && !it.isDeleted }
            .distinctBy { it.songKey }
}
