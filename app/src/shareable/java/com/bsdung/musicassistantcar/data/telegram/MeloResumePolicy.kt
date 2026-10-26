package com.bsdung.musicassistantcar.data.telegram

/**
 * E100: "Nghe tiếp" của Thanh đáy gộp. Nhớ bài Telegram nghe lần trước của từng tài khoản
 * (chỉ số: khóa bài, vị trí, thời điểm; không tên bài, kênh, đường dẫn hay file_id) để thẻ đáy
 * không trống khi mở app. Logic thuần JVM, không import Android.
 */
internal object MeloResumePolicy {

    const val PREFS_NAME = "melovault_resume"

    /** Dưới ngưỡng này coi như chưa nghe: phát từ đầu. */
    const val MIN_RESUME_MS = 5_000L

    /** Còn ít hơn ngần này là hết bài: phát lại từ đầu thay vì nhảy vào đoạn cuối. */
    const val END_GUARD_MS = 10_000L

    private const val TELEGRAM_PREFIX = "telegram:"

    fun songKey(accountId: Long) = "resume_song_$accountId"
    fun posKey(accountId: Long) = "resume_pos_$accountId"
    fun atKey(accountId: Long) = "resume_at_$accountId"

    data class Record(val accountId: Long, val songKey: Long, val positionMs: Long, val savedAtMs: Long)

    /** Null khi chưa có bản ghi dùng được (tài khoản hoặc khóa bài không hợp lệ). */
    fun record(accountId: Long, songKey: Long?, positionMs: Long, savedAtMs: Long): Record? {
        if (accountId <= 0L || songKey == null || songKey == 0L) return null
        return Record(accountId, songKey, positionMs.coerceAtLeast(0L), savedAtMs)
    }

    /** Khóa bài của mediaId `telegram:<số>`; bài cục bộ (`local:`), `picked:audio` và id lạ trả null. */
    fun songKeyOf(mediaId: String?): Long? {
        if (mediaId == null || !mediaId.startsWith(TELEGRAM_PREFIX)) return null
        return mediaId.substring(TELEGRAM_PREFIX.length).toLongOrNull()?.takeIf { it != 0L }
    }

    /** Chỉ ghi khi đã đăng nhập xong, có tài khoản và đang là bài Telegram (chặn ghi lại sau đăng xuất). */
    fun shouldWrite(authReady: Boolean, accountId: Long, mediaId: String?): Boolean =
        authReady && accountId > 0L && songKeyOf(mediaId) != null

    /**
     * Vị trí bắt đầu khi nghe tiếp. Chỉ khi file đã tải đủ (tua vào phần chưa tải sẽ đứng);
     * dưới [MIN_RESUME_MS] hoặc sát cuối bài thì từ đầu. Thời lượng chưa rõ (<= 0) giữ nguyên vị trí đã lưu.
     */
    fun startPositionMs(savedMs: Long, durationSeconds: Int, fileComplete: Boolean): Long {
        if (!fileComplete || savedMs < MIN_RESUME_MS) return 0L
        if (durationSeconds > 0 && savedMs > durationSeconds * 1000L - END_GUARD_MS) return 0L
        return savedMs
    }
}
