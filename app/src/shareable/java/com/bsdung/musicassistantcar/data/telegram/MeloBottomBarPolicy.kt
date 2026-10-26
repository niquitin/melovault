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
        /** Minimum height of the "Đang phát" card above the button row. */
        val cardDp: Int,
        val artDp: Int,
        val titleSp: Float,
        val channelSp: Float,
        val playDp: Int,
        val skipDp: Int,
        val tabs: List<Tab>
    )

    // heightDp = PROGRESS_DP + cardDp + button row (52 / 64 / 70).
    private val SPEC_COMPACT = Spec(102, 48, 36, 14f, 12f, 52, 48, listOf(Tab.LIBRARY, Tab.TELEGRAM))
    private val SPEC_PHONE = Spec(122, 56, 40, 15f, 12f, 60, 48, listOf(Tab.LIBRARY, Tab.TELEGRAM, Tab.PLAYLIST, Tab.DOWNLOADED))
    private val SPEC_TABLET = Spec(132, 60, 44, 16f, 13f, 64, 56, listOf(Tab.LIBRARY, Tab.TELEGRAM, Tab.PLAYLIST, Tab.DOWNLOADED))

    const val PROGRESS_DP = 2
    const val MIN_TOUCH_DP = 48
    const val CARD_PAD_DP = 6
    const val CARD_MARGIN_DP = 4
    /** Width of the "⌃" hint on the right of the card. */
    const val CARD_CHEVRON_DP = 24

    /** Gọn list: rows sit edge to edge (pitch = row height); the 1dp inset on each side draws the gap. */
    const val COMPACT_ROW_INSET_DP = 1
    const val COMPACT_LIST_BOTTOM_PAD_DP = 2

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

    /** Second line of the card: the channel (or artist / folder for local songs); null when unknown. */
    fun channelLine(sourceTitle: String?): String? = sourceTitle?.trim()?.takeIf { it.isNotEmpty() }

    // ---- E100: the card while nothing is loaded in the player --------------------------------

    /** What the card offers with no song loaded: continue the last song, play the list on screen, or nothing yet. */
    enum class Idle { RESUME, PLAY_ALL, PICK }

    fun idle(hasResume: Boolean, canPlayAll: Boolean): Idle = when {
        hasResume -> Idle.RESUME
        canPlayAll -> Idle.PLAY_ALL
        else -> Idle.PICK
    }

    /** The card and Play do something in every state but PICK. */
    fun idleActionable(idle: Idle): Boolean = idle != Idle.PICK

    const val HINT_TITLE = "Chưa phát bài nào"
    const val HINT_PLAY_ALL = "Bấm để phát danh sách đang xem"
    const val HINT_PICK = "Chọn một bài trong danh sách"
    const val RESUME_LABEL = "Nghe tiếp"

    /** Second line of the resume card: "Nghe tiếp · kênh", or just "Nghe tiếp" when the channel is unknown. */
    fun resumeLine(sourceTitle: String?): String {
        val channel = sourceTitle?.trim().orEmpty()
        return if (channel.isEmpty()) RESUME_LABEL else "$RESUME_LABEL · $channel"
    }

    /** Second line of the idle card for the two states without a remembered song. */
    fun hintLine(idle: Idle): String = if (idle == Idle.PLAY_ALL) HINT_PLAY_ALL else HINT_PICK

    fun idleDescription(idle: Idle, title: String?, sourceTitle: String?): String = when (idle) {
        Idle.RESUME -> "Nghe tiếp: ${titleLine(title.orEmpty(), sourceTitle)}. Bấm để phát"
        Idle.PLAY_ALL -> "$HINT_TITLE. $HINT_PLAY_ALL"
        Idle.PICK -> "$HINT_TITLE. $HINT_PICK"
    }

    /** What the Play button says while nothing is loaded. */
    fun idlePlayDescription(idle: Idle): String = when (idle) {
        Idle.RESUME -> RESUME_LABEL
        Idle.PLAY_ALL -> "Phát hết"
        Idle.PICK -> "Phát"
    }

    /** Height of the transport row under the card; it never changes with the card. */
    fun rowDp(spec: Spec): Int = spec.heightDp - PROGRESS_DP - spec.cardDp

    /**
     * Card height: the table value, grown when two text lines at the system font scale need more,
     * and never below the 48dp touch target.
     */
    fun cardHeightDp(spec: Spec, fontScale: Float): Int {
        val lines = (spec.titleSp + spec.channelSp) * 1.2f * fontScale.coerceAtLeast(1f)
        val needed = Math.ceil((lines + CARD_PAD_DP * 2).toDouble()).toInt()
        return maxOf(spec.cardDp, needed, MIN_TOUCH_DP)
    }

    /** Width each tab may take: what is left of the row after the three transport buttons. */
    fun tabWidthDp(windowWidthDp: Int, spec: Spec, sideMarginsDp: Int): Int {
        val transport = spec.skipDp * 2 + spec.playDp
        val perSide = maxOf(spec.tabs.size / 2, 1)
        return ((windowWidthDp - sideMarginsDp - transport) / (perSide * 2)).coerceAtLeast(0)
    }

    /**
     * Width one tab label ("Thư viện" at 11sp) needs at font scale 1; it grows with the system
     * font scale, so the head unit's ×1.25 needs 53dp (its 1/3 window gives ~50dp) while S25 and
     * Sony at ×1.0 keep their labels in their 43–49dp cells (as checked in Beta106).
     */
    const val TAB_LABEL_BASE_DP = 42

    /** A tab cell too narrow for its label shows only the icon (the label stays in the content description). */
    fun tabIconOnly(tabWidthDp: Int, fontScale: Float): Boolean =
        tabWidthDp < Math.round(TAB_LABEL_BASE_DP * fontScale.coerceAtLeast(1f))

    /** Rows that fit between the header and the bar: used to check the Shanling budget (8 rows). */
    fun visibleRows(windowHeightDp: Int, headerDp: Int, barDp: Int, rowDp: Int, dividerDp: Int, reservedDp: Int = 0): Int {
        val free = windowHeightDp - headerDp - barDp - reservedDp
        if (free <= 0 || rowDp <= 0) return 0
        return (free + dividerDp) / (rowDp + dividerDp)
    }
}
