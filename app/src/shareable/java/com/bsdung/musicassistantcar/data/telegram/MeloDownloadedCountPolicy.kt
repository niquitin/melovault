package com.bsdung.musicassistantcar.data.telegram

/**
 * Số "đã tải" trên thẻ nguồn Telegram ngay khi mở app (docs/plans/da-tai-khi-mo-app.md):
 * hiện số của lần quét gần nhất đã lưu, rồi quét nền một lần (có điều kiện) để làm mới.
 * Logic thuần JVM, không import Android.
 */
internal object MeloDownloadedCountPolicy {

    /** Trễ sau khi thư viện sẵn sàng rồi mới xét quét nền. */
    const val START_DELAY_MS = 20_000L

    /** Số đã lưu còn "mới" thì bỏ quét nền. */
    const val MAX_STORED_AGE_MS = 6L * 60L * 60L * 1000L

    const val PREFS_NAME = "melovault_library_counts"

    fun valueKey(accountId: Long) = "downloaded_$accountId"
    fun timeKey(accountId: Long) = "downloaded_at_$accountId"

    data class ScanState(
        val loggedIn: Boolean,
        val hasSongs: Boolean,
        val syncing: Boolean,
        val playingOrOpening: Boolean,
        val powerSave: Boolean,
        val foreground: Boolean,
        val alreadyScannedThisProcess: Boolean,
        /** Tuổi số đã lưu (ms); null = chưa có hoặc đồng hồ lệch. */
        val storedAgeMs: Long?
    )

    enum class Decision(val logReason: String) {
        SCAN("scan"),
        NOT_LOGGED_IN("not_logged_in"),
        NO_SONGS("no_songs"),
        SYNCING("syncing"),
        PLAYING_OR_OPENING("playing_or_opening"),
        POWER_SAVE("power_save"),
        BACKGROUND("background"),
        ALREADY_SCANNED("already_scanned"),
        STORED_FRESH("stored_fresh")
    }

    /** Điều kiện kiểm theo thứ tự cố định; chỉ khi tất cả đạt mới quét. */
    fun decide(state: ScanState): Decision = when {
        !state.loggedIn -> Decision.NOT_LOGGED_IN
        !state.hasSongs -> Decision.NO_SONGS
        state.alreadyScannedThisProcess -> Decision.ALREADY_SCANNED
        !state.foreground -> Decision.BACKGROUND
        state.syncing -> Decision.SYNCING
        state.playingOrOpening -> Decision.PLAYING_OR_OPENING
        state.powerSave -> Decision.POWER_SAVE
        isFresh(state.storedAgeMs) -> Decision.STORED_FRESH
        else -> Decision.SCAN
    }

    fun shouldBackgroundScan(state: ScanState): Boolean = decide(state) == Decision.SCAN

    fun isFresh(storedAgeMs: Long?): Boolean =
        storedAgeMs != null && storedAgeMs >= 0L && storedAgeMs < MAX_STORED_AGE_MS

    /** Tuổi số đã lưu; null khi chưa lưu hoặc đồng hồ chạy lùi (coi như cũ). */
    fun storedAge(nowMs: Long, storedAtMs: Long?): Long? =
        storedAtMs?.takeIf { it > 0L && nowMs >= it }?.let { nowMs - it }

    data class Stored(val accountId: Long, val value: Int, val atMs: Long)

    /** Lần quét xong thắng số đã lưu; số đã lưu chỉ dùng cho đúng tài khoản và không âm. */
    fun displayValue(accountId: Long, stored: Stored?, scanned: Int?): Int? =
        scanned?.takeIf { it >= 0 }
            ?: stored?.takeIf { it.accountId == accountId && accountId > 0L && it.value >= 0 }?.value
}
