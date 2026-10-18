package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile

/**
 * Rules for the "Thanh đáy gộp" (docs/ui-spec.md, Thành phần dùng chung): one bottom block that
 * replaces the mini player and the bottom navigation in the portrait layouts (Gọn, Điện thoại,
 * Tablet dọc). Pure JVM: sizes in dp, which tab is lit, what the title line and Play button show.
 */
internal object MeloBottomBarPolicy {

    enum class Tab(val icon: String, val label: String) {
        LIBRARY("♫", "Thư viện"),
        TELEGRAM("☷", "Telegram"),
        PLAYLIST("☆", "Playlist"),
        DOWNLOADED("✓", "Đã tải")
    }

    /** All sizes in dp (spec table); the view multiplies by the screen density. */
    data class Spec(
        val heightDp: Int,
        val titleLineDp: Int,
        val titleSp: Float,
        val playDp: Int,
        val skipDp: Int,
        val tabs: List<Tab>
    )

    private val SPEC_COMPACT = Spec(72, 18, 13f, 52, 48, listOf(Tab.LIBRARY, Tab.TELEGRAM))
    private val SPEC_PHONE = Spec(88, 22, 14f, 60, 48, listOf(Tab.LIBRARY, Tab.TELEGRAM, Tab.PLAYLIST, Tab.DOWNLOADED))
    private val SPEC_TABLET = Spec(96, 24, 15f, 64, 56, listOf(Tab.LIBRARY, Tab.TELEGRAM, Tab.PLAYLIST, Tab.DOWNLOADED))

    const val PROGRESS_DP = 2
    const val MIN_TOUCH_DP = 48

    /** Null for Car Mode: it keeps its own transport and navigation column. */
    fun spec(profile: Profile): Spec? = when (profile) {
        Profile.COMPACT -> SPEC_COMPACT
        Profile.PHONE -> SPEC_PHONE
        Profile.TABLET -> SPEC_TABLET
        Profile.CAR -> null
    }

    /** Tabs shown to the left of the transport buttons (the first half, rounded down). */
    fun leftTabs(tabs: List<Tab>): List<Tab> = tabs.take(tabs.size / 2)

    fun rightTabs(tabs: List<Tab>): List<Tab> = tabs.drop(tabs.size / 2)

    /**
     * The lit tab. Null when nothing matches, e.g. Playlist on Gọn (that screen is reached from ☰
     * and has no tab of its own).
     */
    fun activeTab(tabs: List<Tab>, showingSources: Boolean, mode: Mode): Tab? {
        val wanted = when {
            showingSources -> Tab.TELEGRAM
            mode == Mode.PLAYLIST -> Tab.PLAYLIST
            mode == Mode.DOWNLOADED -> Tab.DOWNLOADED
            else -> Tab.LIBRARY
        }
        if (wanted in tabs) return wanted
        // Gọn has no "Đã tải" tab: the list of downloaded songs is still the library.
        return if (wanted == Tab.DOWNLOADED && Tab.LIBRARY in tabs) Tab.LIBRARY else null
    }

    /** Title line and progress hair-line only exist while a song is loaded. */
    fun showsSongInfo(hasSong: Boolean): Boolean = hasSong

    /** Play is dimmed and does nothing until there is a song. */
    fun playEnabled(hasSong: Boolean): Boolean = hasSong

    fun playAlpha(hasSong: Boolean): Float = if (hasSong) 1f else 0.4f

    /**
     * Play/Pause follows the same flag as the song rows ("Tạm dừng" vs "Đang phát"): the pause
     * glyph shows whenever the player is not paused by the user, also while it is still buffering.
     */
    fun showsPauseGlyph(hasSong: Boolean, paused: Boolean): Boolean = hasSong && !paused

    fun playDescription(hasSong: Boolean, paused: Boolean): String =
        if (showsPauseGlyph(hasSong, paused)) "Dừng" else "Phát"

    /** "Tên bài · tên kênh"; the channel is left out when unknown. */
    fun titleLine(title: String, sourceTitle: String?): String {
        val song = title.trim()
        val channel = sourceTitle?.trim().orEmpty()
        return if (channel.isEmpty()) song else "$song · $channel"
    }

    fun contentDescription(title: String, sourceTitle: String?): String =
        "Đang phát: ${titleLine(title, sourceTitle)}. Bấm hoặc vuốt lên để mở màn Đang phát"

    /** Width each tab may take: what is left of the row after the three transport buttons. */
    fun tabWidthDp(windowWidthDp: Int, spec: Spec, sideMarginsDp: Int): Int {
        val transport = spec.skipDp * 2 + spec.playDp
        val perSide = maxOf(spec.tabs.size / 2, 1)
        return ((windowWidthDp - sideMarginsDp - transport) / (perSide * 2)).coerceAtLeast(0)
    }

    /** Rows that fit between the header and the bar: used to check the Shanling budget (8 rows). */
    fun visibleRows(windowHeightDp: Int, headerDp: Int, barDp: Int, rowDp: Int, dividerDp: Int): Int {
        val free = windowHeightDp - headerDp - barDp
        if (free <= 0 || rowDp <= 0) return 0
        return (free + dividerDp) / (rowDp + dividerDp)
    }
}
