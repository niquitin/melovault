package com.bsdung.musicassistantcar.data.telegram

/**
 * What the Melo screens say while a tapped song is being opened, and the fixed sentences for
 * every way that can fail. Pure JVM. No sentence ever contains a file or song name, and raw
 * TDLib text is matched, never shown.
 */
internal object TelegramPlayFeedbackPolicy {

    enum class Stage { RESOLVING, BUFFERING }

    /** What the library list shows for [songKey]; null elsewhere means "nothing in progress". */
    data class PlayStatus(val stage: Stage, val songKey: Long, val text: String)

    const val RESOLVING_TEXT = "Đang mở…"
    const val BUFFERING_TEXT = "Đang đệm nhạc…"

    /** Resolve timeout used by the activity (also the wait named in the timeout sentence). */
    const val RESOLVE_TIMEOUT_SECONDS = 12

    const val NOT_READY_MESSAGE = "Phiên Telegram chưa sẵn sàng"
    const val TIMEOUT_MESSAGE = "Quá thời gian kết nối tới máy chủ Telegram"
    const val NO_SOURCE_MESSAGE = "Không xác định được nguồn Telegram của bài hát."
    const val CACHE_MESSAGE = "Không tạo được bộ đệm nhạc của tài khoản Telegram."
    const val DOWNLOAD_START_MESSAGE = "Không thể tải bài này."

    /** The row of the song being opened shows "Đang mở…" only until it becomes the playing song. */
    fun opening(status: PlayStatus?, rowSongKey: Long, rowIsPlaying: Boolean): Boolean =
        status != null && !rowIsPlaying && status.songKey == rowSongKey

    /** Toast sentence for a failed resolve, from the TDLib error code and the app's own fixed messages. */
    fun errorText(message: String?, code: Int?): String {
        val prefix = "Không mở được bài này: "
        return prefix + when {
            message == TIMEOUT_MESSAGE -> "Telegram không trả lời sau $RESOLVE_TIMEOUT_SECONDS giây, thử lại sau"
            message == NOT_READY_MESSAGE -> "Telegram chưa sẵn sàng, đợi một chút rồi thử lại"
            message == NO_SOURCE_MESSAGE -> "không xác định được kênh hoặc tin nhắn của bài"
            message == CACHE_MESSAGE -> "không tạo được bộ đệm trên máy"
            message == DOWNLOAD_START_MESSAGE -> "không bắt đầu được việc tải"
            code == 400 || code == 404 -> "Telegram không tìm thấy tin nhắn ($code)"
            code == 401 || code == 403 -> "tài khoản không được phép đọc tin nhắn này ($code)"
            code == 429 -> "Telegram đang giới hạn tốc độ, thử lại sau ($code)"
            code != null && code > 0 -> "Telegram báo lỗi (mã $code)"
            else -> "tin nhắn này không phải tệp nhạc phát được"
        }
    }

    /** Toast for a download that TDLib refused after the player already started waiting for it. */
    fun downloadErrorText(code: Int?): String =
        "Không tải được bài này" + if (code != null && code > 0) " (mã $code)" else ""

    /** Value of the `error` field in the diagnostic log line: a short code, never free text. */
    fun logReason(message: String?, code: Int?): String = when {
        message == TIMEOUT_MESSAGE -> "timeout"
        message == NOT_READY_MESSAGE -> "not_ready"
        message == NO_SOURCE_MESSAGE -> "no_source"
        message == CACHE_MESSAGE -> "cache"
        message == DOWNLOAD_START_MESSAGE -> "download_start"
        code != null && code > 0 -> "tdlib_$code"
        else -> "not_audio"
    }
}
