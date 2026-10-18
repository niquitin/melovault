package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlayFeedbackPolicy.PlayStatus
import com.bsdung.musicassistantcar.data.telegram.TelegramPlayFeedbackPolicy.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlayFeedbackPolicyTest {

    private val prefix = "Không mở được bài này: "

    @Test
    fun tdlibCodesMapToFixedSentences() {
        assertEquals(prefix + "Telegram không tìm thấy tin nhắn (400)", TelegramPlayFeedbackPolicy.errorText("Message not found", 400))
        assertEquals(prefix + "Telegram không tìm thấy tin nhắn (404)", TelegramPlayFeedbackPolicy.errorText("Chat not found", 404))
        assertEquals(prefix + "tài khoản không được phép đọc tin nhắn này (403)", TelegramPlayFeedbackPolicy.errorText("x", 403))
        assertEquals(prefix + "Telegram đang giới hạn tốc độ, thử lại sau (429)", TelegramPlayFeedbackPolicy.errorText("Too Many Requests: retry after 5", 429))
        assertEquals(prefix + "Telegram báo lỗi (mã 500)", TelegramPlayFeedbackPolicy.errorText("Internal", 500))
    }

    @Test
    fun appOwnErrorsMapToFixedSentences() {
        assertEquals(
            prefix + "Telegram không trả lời sau 12 giây, thử lại sau",
            TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.TIMEOUT_MESSAGE, null)
        )
        assertTrue(TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.NOT_READY_MESSAGE, null).contains("chưa sẵn sàng"))
        assertTrue(TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.NO_SOURCE_MESSAGE, null).contains("không xác định"))
        assertTrue(TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.CACHE_MESSAGE, null).contains("bộ đệm"))
        assertTrue(TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.DOWNLOAD_START_MESSAGE, null).contains("việc tải"))
    }

    @Test
    fun parserErrorsNeverShowTheFileName() {
        val parser = "Tệp tài liệu 'Bí mật - Tên riêng.mp3x' không phải định dạng âm thanh hỗ trợ"
        val text = TelegramPlayFeedbackPolicy.errorText(parser, null)
        assertEquals(prefix + "tin nhắn này không phải tệp nhạc phát được", text)
        assertFalse(text.contains("Bí mật"))
        assertEquals("not_audio", TelegramPlayFeedbackPolicy.logReason(parser, null))
    }

    @Test
    fun tdlibMessageTextIsNeverShownOrLogged() {
        val raw = "Message not found: Tên Tệp Riêng.mp3"
        assertFalse(TelegramPlayFeedbackPolicy.errorText(raw, 400).contains("Riêng"))
        assertEquals("tdlib_400", TelegramPlayFeedbackPolicy.logReason(raw, 400))
        assertEquals("tdlib_429", TelegramPlayFeedbackPolicy.logReason(raw, 429))
        assertEquals("timeout", TelegramPlayFeedbackPolicy.logReason(TelegramPlayFeedbackPolicy.TIMEOUT_MESSAGE, null))
        assertEquals("not_ready", TelegramPlayFeedbackPolicy.logReason(TelegramPlayFeedbackPolicy.NOT_READY_MESSAGE, null))
        assertEquals("no_source", TelegramPlayFeedbackPolicy.logReason(TelegramPlayFeedbackPolicy.NO_SOURCE_MESSAGE, null))
        assertEquals("cache", TelegramPlayFeedbackPolicy.logReason(TelegramPlayFeedbackPolicy.CACHE_MESSAGE, null))
        assertEquals("download_start", TelegramPlayFeedbackPolicy.logReason(TelegramPlayFeedbackPolicy.DOWNLOAD_START_MESSAGE, null))
    }

    @Test
    fun stallMessageAppearsOnlyWhenNoBytesArrived() {
        assertTrue(TelegramPlayFeedbackPolicy.stalled(0L))
        assertTrue(TelegramPlayFeedbackPolicy.stalled(-1L))
        assertFalse(TelegramPlayFeedbackPolicy.stalled(1L))
        assertTrue(TelegramPlayFeedbackPolicy.STALL_TEXT.contains("15 giây"))
    }

    @Test
    fun downloadErrorSentence() {
        assertEquals("Không tải được bài này (mã 406)", TelegramPlayFeedbackPolicy.downloadErrorText(406))
        assertEquals("Không tải được bài này", TelegramPlayFeedbackPolicy.downloadErrorText(null))
        assertEquals("Không tải được bài này", TelegramPlayFeedbackPolicy.downloadErrorText(0))
    }

    @Test
    fun rowShowsOpeningOnlyForTheTappedSongUntilItPlays() {
        val resolving = PlayStatus(Stage.RESOLVING, 7L, TelegramPlayFeedbackPolicy.RESOLVING_TEXT)
        val buffering = PlayStatus(Stage.BUFFERING, 7L, TelegramPlayFeedbackPolicy.BUFFERING_TEXT)
        assertTrue(TelegramPlayFeedbackPolicy.opening(resolving, 7L, rowIsPlaying = false))
        assertTrue(TelegramPlayFeedbackPolicy.opening(buffering, 7L, rowIsPlaying = false))
        assertFalse(TelegramPlayFeedbackPolicy.opening(resolving, 7L, rowIsPlaying = true))
        assertFalse(TelegramPlayFeedbackPolicy.opening(resolving, 8L, rowIsPlaying = false))
        assertFalse(TelegramPlayFeedbackPolicy.opening(null, 7L, rowIsPlaying = false))
    }

    @Test
    fun openingStatusHasWordsAndNoHighlight() {
        val p = MeloComponentPolicy.presentStatus(MeloComponentPolicy.Status.OPENING)
        assertEquals("Đang mở…", p.label)
        assertFalse(p.highlightRow)
    }
}
