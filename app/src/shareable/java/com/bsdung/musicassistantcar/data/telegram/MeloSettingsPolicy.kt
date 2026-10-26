package com.bsdung.musicassistantcar.data.telegram

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * E102 "Cài đặt": what the ⋮ menu and the settings pages say, as pure functions (no Android, no
 * TDLib). The screens only draw these answers; every choice still writes through its old save
 * function and old preferences file.
 */
internal object MeloSettingsPolicy {

    /** Pages of the settings screen; a sub page goes back to [HOME], [HOME] closes the screen. */
    enum class Page { HOME, APPEARANCE, DEVICES, ADVANCED;

        val isSubPage: Boolean get() = this != HOME

        companion object {
            fun fromName(name: String?): Page = entries.firstOrNull { it.name == name } ?: HOME
        }
    }

    // ---- Sync card at the top of the ⋮ menu ----

    enum class SyncState { SIGNED_OUT, RUNNING, PAUSED, IDLE_SYNCED, IDLE_NEVER }

    /** The four buttons the card can carry; the host maps each to the action the old menu item ran. */
    enum class SyncButton(val label: String) {
        PAUSE("Tạm dừng"),
        RESUME("Tiếp tục"),
        FETCH_NEW("Lấy bài mới"),
        FULL_SCAN("Quét toàn bộ")
    }

    data class SyncCard(
        val state: SyncState,
        val line1: String,
        val line2: String?,
        val primary: SyncButton?,
        val secondary: SyncButton?
    ) {
        val visible: Boolean get() = state != SyncState.SIGNED_OUT
    }

    /**
     * Paused wins over syncing: [TelegramSyncCoordinator.pauseSync] clears the busy flag and sets
     * the paused flag, and the next start clears the paused flag again.
     */
    fun syncState(authReady: Boolean, syncing: Boolean, paused: Boolean, lastSyncCompletedMs: Long): SyncState = when {
        !authReady -> SyncState.SIGNED_OUT
        paused -> SyncState.PAUSED
        syncing -> SyncState.RUNNING
        lastSyncCompletedMs > 0L -> SyncState.IDLE_SYNCED
        else -> SyncState.IDLE_NEVER
    }

    fun syncCard(
        authReady: Boolean,
        syncing: Boolean,
        paused: Boolean,
        liveSongs: Int?,
        lastSyncCompletedMs: Long,
        nowMs: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): SyncCard {
        val state = syncState(authReady, syncing, paused, lastSyncCompletedMs)
        val songs = songsText(liveSongs)
        return when (state) {
            SyncState.SIGNED_OUT -> SyncCard(state, "Chưa đăng nhập Telegram", null, null, null)
            SyncState.RUNNING -> SyncCard(state, "Đang đồng bộ…", songs, SyncButton.PAUSE, null)
            SyncState.PAUSED -> SyncCard(state, "Đã tạm dừng đồng bộ", null, SyncButton.RESUME, SyncButton.FULL_SCAN)
            SyncState.IDLE_SYNCED -> SyncCard(
                state, "Thư viện đã đồng bộ",
                listOfNotNull("Cập nhật lúc ${formatSyncTime(lastSyncCompletedMs, nowMs, zone)}", songs).joinToString(" · "),
                SyncButton.FETCH_NEW, SyncButton.FULL_SCAN
            )
            SyncState.IDLE_NEVER -> SyncCard(state, "Chưa đồng bộ", null, SyncButton.FETCH_NEW, SyncButton.FULL_SCAN)
        }
    }

    /** "1.234 bài", or null when the host has no number (null or negative). */
    private fun songsText(liveSongs: Int?): String? =
        if (liveSongs == null || liveSongs < 0) null else "${MeloComponentPolicy.formatCount(liveSongs)} bài"

    /** "HH:mm" on the same day as [nowMs], "dd/MM HH:mm" on another day, "chưa rõ" without a time. */
    fun formatSyncTime(ms: Long, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        if (ms <= 0L) return "chưa rõ"
        fun day(t: Long) = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = zone }.format(Date(t))
        val pattern = if (day(ms) == day(nowMs)) "HH:mm" else "dd/MM HH:mm"
        return SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(ms))
    }

    // ---- ⋮ menu rows below the card ----

    enum class MenuId { SAVED, LOCAL, SOURCES, OPEN_FILE, SETTINGS }

    data class MenuItem(val id: MenuId, val label: String, val subtitle: String?, val enabled: Boolean)

    /**
     * Same gates as the old menu: saved messages, channel list and file picker need a signed-in
     * account; device music (only when the layer has it) and settings do not.
     */
    fun menuItems(authReady: Boolean, showLocal: Boolean): List<MenuItem> = buildList {
        add(MenuItem(MenuId.SAVED, "☆ Tin đã lưu", null, authReady))
        if (showLocal) add(MenuItem(MenuId.LOCAL, "♪ ${MeloLocalSourcePolicy.SOURCE_TITLE}", null, true))
        add(MenuItem(MenuId.SOURCES, "Nhóm / kênh / bot Telegram", null, authReady))
        add(MenuItem(MenuId.OPEN_FILE, "Mở tệp nhạc (Drive / thiết bị)", null, authReady))
        add(MenuItem(MenuId.SETTINGS, "Cài đặt", "Giao diện, tài khoản…", true))
    }

    // ---- Home page summaries ----

    fun libraryName(style: MeloLibraryPolicy.LibraryStyle): String = when (style) {
        MeloLibraryPolicy.LibraryStyle.NEW -> "Mới"
        MeloLibraryPolicy.LibraryStyle.LEGACY -> "Cũ"
    }

    fun colorName(theme: MeloThemePolicy.ColorTheme): String = when (theme) {
        MeloThemePolicy.ColorTheme.CURRENT -> "Hiện tại"
        MeloThemePolicy.ColorTheme.TEAL_BRIGHT -> "Teal sáng"
    }

    fun sourceModeName(mode: TelegramVaultBentoPolicy.SourceDisplayMode): String = when (mode) {
        TelegramVaultBentoPolicy.SourceDisplayMode.LIST -> "Danh sách"
        TelegramVaultBentoPolicy.SourceDisplayMode.BENTO -> "Bento"
    }

    fun nowPlayingName(style: MeloNowPlayingPolicy.NowPlayingStyle): String = when (style) {
        MeloNowPlayingPolicy.NowPlayingStyle.STUDIO_DECK -> "Studio Deck"
        MeloNowPlayingPolicy.NowPlayingStyle.LEGACY -> "Màn cũ"
    }

    fun homeSummary(
        style: MeloLibraryPolicy.LibraryStyle,
        theme: MeloThemePolicy.ColorTheme,
        sourceMode: TelegramVaultBentoPolicy.SourceDisplayMode
    ): String = "Thư viện ${libraryName(style)} · Màu ${colorName(theme)} · Nguồn dạng ${sourceModeName(sourceMode)}"

    /** Short name of the current Gọn choice for the home row; the emulation values are not in the 3-way picker. */
    fun compactName(setting: MeloCompactPolicy.CompactSetting): String = when (setting) {
        MeloCompactPolicy.CompactSetting.AUTO -> "Tự động"
        MeloCompactPolicy.CompactSetting.ON -> "Luôn bật"
        MeloCompactPolicy.CompactSetting.OFF -> "Tắt"
        MeloCompactPolicy.CompactSetting.EMULATE_SHANLING -> "Đang thử khung Shanling"
    }

    fun carName(setting: MeloCarPolicy.CarSetting): String = when (setting) {
        MeloCarPolicy.CarSetting.OFF -> "Tắt"
        MeloCarPolicy.CarSetting.ON -> "Bật"
        MeloCarPolicy.CarSetting.EMULATE_FYT_160, MeloCarPolicy.CarSetting.EMULATE_FYT_240 -> "Đang thử khung FYT"
    }

    /** "Thư viện 24.601 bài · 38 nguồn": counts the host gave, a missing or negative one is left out. */
    fun accountSubtitle(liveSongs: Int?, liveChannels: Int?): String = listOfNotNull(
        "Đã đăng nhập",
        songsText(liveSongs),
        liveChannels?.takeIf { it >= 0 }?.let { "${MeloComponentPolicy.formatCount(it)} nguồn" }
    ).joinToString(" · ")

    /** "Đồng bộ xong lúc …", or null when no full sync ever finished. */
    fun accountSyncLine(lastSyncCompletedMs: Long, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): String? =
        if (lastSyncCompletedMs <= 0L) null else "Đồng bộ xong lúc ${formatSyncTime(lastSyncCompletedMs, nowMs, zone)}"

    // ---- Appearance, devices ----

    /** Portrait / Cockpit / Classic only mean something while the old library is chosen. */
    fun presetVisible(style: MeloLibraryPolicy.LibraryStyle): Boolean = style == MeloLibraryPolicy.LibraryStyle.LEGACY

    fun compactHint(setting: MeloCompactPolicy.CompactSetting): String = when (setting) {
        MeloCompactPolicy.CompactSetting.AUTO -> "Tự động dùng bản Gọn trên máy có màn hình nhỏ như Shanling."
        MeloCompactPolicy.CompactSetting.ON -> "Luôn dùng bản Gọn trên mọi máy sau khi đăng nhập."
        MeloCompactPolicy.CompactSetting.OFF -> "Không dùng bản Gọn, giữ giao diện thư viện cũ."
        MeloCompactPolicy.CompactSetting.EMULATE_SHANLING -> "Đang thử bản Gọn trong khung Shanling 768 × 1280."
    }

    fun bandHint(setting: WindowInsetsPolicy.HandleBand): String = when (setting) {
        WindowInsetsPolicy.HandleBand.AUTO ->
            "Tự động chừa một dải khi app chạy trong thẻ chia màn mà Android không báo thanh trạng thái."
        WindowInsetsPolicy.HandleBand.ALWAYS -> "Luôn chừa một dải trống ở đầu màn. Chọn khi chữ đầu trang vẫn bị nút ••• che."
        WindowInsetsPolicy.HandleBand.NEVER -> "Không chừa dải nào. Chọn khi thấy dải trống thừa."
    }

    // ---- Advanced: the short measurement list ----

    /** Labels (as written in the full list) that make the short list, in this order. "Máy" and "Android" share one row. */
    val SHORT_INFO_LABELS: List<String> = listOf(
        "Bố cục thực tế đang dùng",
        "Độ rộng × cao (dp)",
        "Cạnh ngắn nhất",
        "Mật độ Android",
        "Kích thước ước tính",
        "Máy",
        "Android"
    )

    const val SHORT_INFO_DEVICE_LABEL = "Máy + Android"

    /** The short list picked from [lines] by label; a missing label is skipped, nothing is invented. */
    fun shortInfo(lines: List<Pair<String, String>>): List<Pair<String, String>> {
        val byLabel = lines.associate { it.first to it.second }
        val result = mutableListOf<Pair<String, String>>()
        SHORT_INFO_LABELS.filter { it != "Máy" && it != "Android" }.forEach { label ->
            byLabel[label]?.let { result += label to it }
        }
        val device = byLabel["Máy"]
        val android = byLabel["Android"]
        if (device != null || android != null) {
            result += SHORT_INFO_DEVICE_LABEL to listOfNotNull(device, android?.let { "Android $it" }).joinToString(" · ")
        }
        return result
    }
}
