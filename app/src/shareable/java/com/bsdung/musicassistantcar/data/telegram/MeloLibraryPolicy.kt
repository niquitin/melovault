package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Layout

/**
 * Rules for the main library screens on phones and tablets, docs/ui-spec.md step 7:
 * Điện thoại dọc / Tablet dọc (one column + bottom navigation) and Hai khung ngang
 * (navigation column, player pane, list pane). Pure JVM except the preference helpers.
 */
internal object MeloLibraryPolicy {

    const val KEY_STYLE = "library_style"
    const val COLLAPSE_THRESHOLD_PX = 12

    enum class LibraryStyle(val key: String, val displayName: String) {
        NEW("new", "Mới (theo đặc tả)"),
        LEGACY("legacy", "Cũ (Cockpit / Portrait)");

        companion object {
            val DEFAULT = NEW
            fun fromKey(key: String?): LibraryStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    /** Which new screen to show, or null for "keep the layer below" (Gọn / Car / legacy). */
    enum class NewLayer { PHONE_PORTRAIT, TABLET_PORTRAIT, TWO_PANE }

    /** Car and Gọn are decided earlier; this only maps the three remaining layouts. */
    fun newLayer(style: LibraryStyle, layout: Layout, authorized: Boolean): NewLayer? {
        if (!authorized || style != LibraryStyle.NEW) return null
        return when (layout) {
            Layout.PHONE_PORTRAIT -> NewLayer.PHONE_PORTRAIT
            Layout.TABLET_PORTRAIT -> NewLayer.TABLET_PORTRAIT
            Layout.TWO_PANE -> NewLayer.TWO_PANE
            Layout.COMPACT, Layout.CAR -> null
        }
    }

    /** Phone sizes below 600dp on the short side, Tablet sizes from there (spec size table). */
    fun profileFor(smallestWidthDp: Int): Profile =
        if (smallestWidthDp >= MeloScreenPolicy.TABLET_MIN_WIDTH_DP) Profile.TABLET else Profile.PHONE

    /** Telegram sources: spec grid is 3 columns on two panes, 2 on portrait; the hero card spans the row. */
    fun sourceColumns(layer: NewLayer): Int = when (layer) {
        NewLayer.TWO_PANE -> 3
        NewLayer.PHONE_PORTRAIT, NewLayer.TABLET_PORTRAIT -> 2
    }

    /**
     * Source grid split: channels with songs (grid) and channels without (folded row). The
     * "Kho tổng hợp" entry (chatId 0) belongs to neither; order is kept; counts <= 0 are empty.
     */
    fun splitSources(sources: List<MeloScreenHost.SourceRow>): Pair<List<MeloScreenHost.SourceRow>, List<MeloScreenHost.SourceRow>> =
        sources.filter { it.chatId != 0L }.partition { it.count > 0 }

    /** "▸ Các kênh chưa có nhạc (3)" / "▾ …" when open; null when there is no such channel. */
    fun emptySourcesLabel(count: Int, expanded: Boolean): String? =
        if (count <= 0) null
        else (if (expanded) "▾ " else "▸ ") + "Các kênh chưa có nhạc (${MeloComponentPolicy.formatCount(count)})"

    /**
     * Two-pane left pane turns into the channel list only while one specific channel is open AND
     * there is at least one channel to switch to ([rowCount] of [quickSwitchRows]); with none (the
     * list not loaded yet, or every count still 0) the big player stays instead of an empty column.
     */
    fun quickSwitchVisible(layer: NewLayer, showingSources: Boolean, mode: Mode, selectedChatId: Long?, rowCount: Int): Boolean =
        layer == NewLayer.TWO_PANE && !showingSources && mode == Mode.ALL && selectedChatId != null && rowCount > 0

    /** Channels offered for the quick switch: only those with songs, original (grid) order. */
    fun quickSwitchRows(channels: List<MeloScreenHost.SourceRow>): List<MeloScreenHost.SourceRow> =
        channels.filter { it.chatId != 0L && it.count > 0 }

    /** Position of the open channel in [rows], -1 when it is not there. */
    fun quickSwitchIndex(rows: List<MeloScreenHost.SourceRow>, selectedChatId: Long?): Int =
        if (selectedChatId == null) -1 else rows.indexOfFirst { it.chatId == selectedChatId }

    /**
     * Where the open channel sits when the quick-switch list scrolls to it: a third of the list
     * down, but always fully inside it (never above the top edge, never below the last full row).
     */
    fun quickSwitchOffset(listHeightPx: Int, rowHeightPx: Int): Int =
        (listHeightPx / 3).coerceIn(0, (listHeightPx - rowHeightPx).coerceAtLeast(0))

    /**
     * Group title row: the two buttons ("Mới nhất", "Phát hết") stay on the title's row while the
     * title keeps at least [minTitlePx] (or 40% of the pane, whichever is larger) or all of its
     * own text if that is shorter; otherwise the title takes a row of its own above them.
     */
    fun headerStacked(paneWidthPx: Int, titleWidthPx: Int, chipsWidthPx: Int, minTitlePx: Int): Boolean {
        if (paneWidthPx <= 0 || chipsWidthPx <= 0) return false
        val needed = minOf(titleWidthPx.coerceAtLeast(0), maxOf(minTitlePx, paneWidthPx * 2 / 5))
        return paneWidthPx - chipsWidthPx < needed
    }

    /**
     * Index of the row with [songKey] in [rows] (where the list was scrolled to before the layout
     * changed), 0 when there is no key or the row is not loaded: the list then starts at its top.
     */
    fun anchorIndex(rows: List<MeloScreenHost.Row>, songKey: Long?): Int =
        if (songKey == null) 0 else rows.indexOfFirst { it.songKey == songKey }.coerceAtLeast(0)

    /**
     * A freshly opened group starts at its first row. The jump waits for rows to arrive (they
     * load asynchronously) and never happens on the channel grid.
     */
    fun shouldResetTop(pending: Boolean, showingSources: Boolean, rowCount: Int): Boolean =
        pending && !showingSources && rowCount > 0

    /** While a sync writes pages, the channel list is rebuilt at most this often (E95 F05). */
    const val CHANNEL_REFRESH_MIN_MS = 10_000L

    /** True when the channel list may be rebuilt now ([lastMs] <= 0: never yet; a clock that went back counts as due). */
    fun channelRefreshDue(nowMs: Long, lastMs: Long, minIntervalMs: Long = CHANNEL_REFRESH_MIN_MS): Boolean =
        lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= minIntervalMs

    /**
     * Library numbers for "Thông tin màn hình": what the database really holds ([liveSongs],
     * [liveChannels]) against what the stored counts claim ([storedChannels] with track_count > 0)
     * and when a full sync last completed. Counts only, never a channel name. -1 = not known.
     */
    data class LibraryDiag(val liveSongs: Int, val storedChannels: Int, val liveChannels: Int, val lastSyncCompletedMs: Long)

    fun libraryDiagLines(diag: LibraryDiag?, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Pair<String, String>> {
        if (diag == null || diag.liveSongs < 0) return listOf("Thư viện (dữ liệu thật)" to "chưa có số (mở lại màn này sau khi thư viện tải xong)")
        return listOf(
            "Tổng bài trong thư viện" to "${MeloComponentPolicy.formatCount(diag.liveSongs)} bài",
            "Kênh có bài (số đã lưu)" to MeloComponentPolicy.formatCount(diag.storedChannels),
            "Kênh có bài (đếm thật)" to MeloComponentPolicy.formatCount(diag.liveChannels),
            "Đồng bộ hoàn tất lần cuối" to lastSyncText(diag.lastSyncCompletedMs, zone)
        )
    }

    fun lastSyncText(completedAtMs: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String {
        if (completedAtMs <= 0L) return "chưa từng hoàn tất"
        val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).apply { timeZone = zone }
        return format.format(java.util.Date(completedAtMs))
    }

    /** Tablet portrait gets Previous on the mini player; phones keep Play and Next only (spec). */
    fun miniPlayerShowsPrevious(layer: NewLayer): Boolean = layer == NewLayer.TABLET_PORTRAIT

    /** Keyword chips filter by name only; no extra data, no invented counts. */
    enum class GenreChip(val label: String, val keyword: String) {
        CHILLOUT("Chillout", "chillout"),
        LAKHOUSE("LakHouse", "lakhouse"),
        MIXSET("Mixset", "mixset")
    }

    data class Chip(val label: String, val mode: Mode?, val genre: GenreChip?)

    /** "Đã tải (N)" only when N is known (after the offline scan); never a placeholder number. */
    fun chips(downloadedCount: Int?): List<Chip> = buildList {
        add(Chip("Tất cả", Mode.ALL, null))
        add(Chip(MeloComponentPolicy.chipLabel("Đã tải", downloadedCount), Mode.DOWNLOADED, null))
        add(Chip("Playlist", Mode.PLAYLIST, null))
        GenreChip.entries.forEach { add(Chip(it.label, null, it)) }
    }

    // ---- E98 teal theme: the four menu tabs ----------------------------------------------------

    enum class TabKind { ALL, DOWNLOADED, PLAYLIST, SOURCES }

    /** Each global tab names a full scope, so flags from the previous channel cannot leak. */
    fun tabScope(tab: TabKind): MeloBrowseUiPolicy.Scope = MeloBrowseUiPolicy.Scope(when (tab) {
        TabKind.ALL -> MeloBrowseUiPolicy.Kind.LIBRARY
        TabKind.DOWNLOADED -> MeloBrowseUiPolicy.Kind.DOWNLOADED
        TabKind.PLAYLIST -> MeloBrowseUiPolicy.Kind.PLAYLIST
        TabKind.SOURCES -> MeloBrowseUiPolicy.Kind.SOURCES
    })

    data class SourcePresentation(val selected: Boolean, val detail: String)
    fun sourcePresentation(source: MeloScreenHost.SourceRow, selectedChatId: Long?): SourcePresentation =
        SourcePresentation(source.chatId == selectedChatId,
            (if (source.count >= 0) "${MeloComponentPolicy.formatCount(source.count)} bài" else "Chưa rõ số bài") +
                if (source.chatId == selectedChatId) " · Đang duyệt" else "")

    enum class BrowsePlayDecision { WAIT, PLAY, CANCEL }
    fun pendingBrowsePlay(request: MeloBrowseUiPolicy.Request, snapshot: MeloScreenHost.Snapshot): BrowsePlayDecision = when {
        request.ownerId != snapshot.browseOwnerId || request.generation != snapshot.browseGeneration ||
            request.scope != snapshot.browseScope -> BrowsePlayDecision.CANCEL
        snapshot.browseLoadState == MeloBrowseUiPolicy.LoadState.ERROR -> BrowsePlayDecision.CANCEL
        snapshot.browseLoadState != MeloBrowseUiPolicy.LoadState.READY -> BrowsePlayDecision.WAIT
        snapshot.rows.isEmpty() -> BrowsePlayDecision.CANCEL
        else -> BrowsePlayDecision.PLAY
    }

    data class ThemeTab(val kind: TabKind, val label: String, val selected: Boolean)

    /** Below this pane width the tab labels shrink to one word. */
    const val NARROW_TABS_DP = 520

    /**
     * "Tất cả (27K)", "Đã tải về máy", "Playlist", "Nguồn Telegram" - exactly four tabs. On a pane
     * narrower than [NARROW_TABS_DP] the labels are "Tất cả", "Đã tải", "Playlist", "Nguồn".
     * [totalSongs] is the whole library; an unknown count shows no brackets.
     */
    fun themeTabs(totalSongs: Int?, widthDp: Int, mode: Mode, showingSources: Boolean,
        scope: MeloBrowseUiPolicy.Scope? = null): List<ThemeTab> {
        val narrow = widthDp in 1 until NARROW_TABS_DP
        val short = MeloComponentPolicy.shortCount(totalSongs)
        val all = if (short != null && !narrow) "Tất cả ($short)" else "Tất cả"
        val tabs = listOf(
            ThemeTab(TabKind.ALL, all, !showingSources && mode == Mode.ALL),
            ThemeTab(TabKind.DOWNLOADED, if (narrow) "Đã tải" else "Đã tải về máy", !showingSources && mode == Mode.DOWNLOADED),
            ThemeTab(TabKind.PLAYLIST, "Playlist", !showingSources && mode == Mode.PLAYLIST),
            ThemeTab(TabKind.SOURCES, if (narrow) "Nguồn" else "Nguồn Telegram", showingSources)
        )
        return if (scope == null) tabs else tabs.map { it.copy(selected = tabScope(it.kind) == scope) }
    }

    /** The genre the search box currently filters by (its keyword), or null. */
    fun activeGenre(query: String): GenreChip? {
        val q = MeloComponentPolicy.searchKey(query)
        return GenreChip.entries.firstOrNull { it.keyword == q }
    }

    /** Index of the selected chip from the current state: a genre keyword wins, else the mode. */
    fun selectedChip(mode: Mode, query: String, downloadedCount: Int?): Int {
        val list = chips(downloadedCount)
        val q = MeloComponentPolicy.searchKey(query)
        val genre = GenreChip.entries.firstOrNull { it.keyword == q }
        if (genre != null) return list.indexOfFirst { it.genre == genre }
        return list.indexOfFirst { it.mode == mode }.coerceAtLeast(0)
    }

    /** Query to send when a chip is tapped: its keyword, or an empty query for the mode chips. */
    fun queryFor(chip: Chip): String = chip.genre?.keyword.orEmpty()

    /** The title row folds away while scrolling down; scrolling up or reaching the top shows it again. */
    fun headerCollapsed(previouslyCollapsed: Boolean, scrollDeltaPx: Int, firstVisible: Int): Boolean = when {
        firstVisible <= 0 && scrollDeltaPx <= 0 -> false
        scrollDeltaPx > COLLAPSE_THRESHOLD_PX -> true
        scrollDeltaPx < -COLLAPSE_THRESHOLD_PX -> false
        else -> previouslyCollapsed
    }

    /** "27.971 bài · Đã cập nhật thư viện" (catalog index, not the cloud Playlist); text from the existing display policy wins when present. */
    fun syncLine(existing: String?, total: Int?, syncing: Boolean): String = when {
        !existing.isNullOrBlank() -> existing
        syncing -> TelegramLibrarySyncDisplayPolicy.LIBRARY_UPDATING
        total != null && total > 0 -> "${MeloComponentPolicy.formatCount(total)} bài · ${TelegramLibrarySyncDisplayPolicy.LIBRARY_UPDATED}"
        else -> "Chưa có bài"
    }

    /** "Phát hết" plays from the first loaded song; disabled when the group is empty. */
    fun playAllEnabled(loadedRows: Int): Boolean = loadedRows > 0

    /** UI-01: the song list should keep this many rows before the optional parts above it give way. */
    const val MIN_LIST_ROWS = 3

    /**
     * UI-01: how many of the optional parts above the song list ([optionalPx], first = hidden first, 0 for a
     * part that is not shown anyway) have to be hidden so the list keeps [minListPx] of the pane's height.
     * Never hides more than needed; all of them when even that is not enough (the list then gets the rest).
     */
    fun partsToTrim(paneHeightPx: Int, fixedPx: Int, optionalPx: List<Int>, minListPx: Int): Int {
        if (paneHeightPx <= 0) return 0
        var remaining = optionalPx.sum()
        for (hidden in optionalPx.indices) {
            if (paneHeightPx - fixedPx - remaining >= minListPx) return hidden
            remaining -= optionalPx[hidden]
        }
        return optionalPx.size
    }

    /**
     * UI-07: a search hint that is wider than the field ([availablePx], measured by [measure]) loses whole words
     * from its end ("Tìm trong thư viện đã tải…" -> "Tìm trong thư viện…") instead of being cut mid-word.
     */
    fun fitHint(full: String, availablePx: Float, measure: (String) -> Float): String {
        if (availablePx <= 0f || measure(full) <= availablePx) return full
        val words = full.trimEnd('…', '.', ' ').split(' ').filter { it.isNotEmpty() }
        for (count in words.size - 1 downTo 1) {
            val candidate = words.take(count).joinToString(" ") + "…"
            if (measure(candidate) <= availablePx) return candidate
        }
        return full
    }

    /** UI-07: from this system font scale the song menu uses its short labels (the popup cuts long ones). */
    const val SHORT_MENU_FONT_SCALE = 1.5f

    /** UI-07: the entries of the song menu (⋮ / long press): full wording, or short wording at a large font. */
    fun songMenuLabels(fontScale: Float, favorite: Boolean, versions: Int?): SongMenuLabels {
        val short = fontScale >= SHORT_MENU_FONT_SCALE
        return SongMenuLabels(
            play = "▶ Phát bài này",
            versions = versions?.let { if (short) "☰ Bản khác ($it)" else "☰ Chọn bản khác ($it bản)" },
            playNext = if (short) "⏭ Phát tiếp" else "⏭ Phát tiếp theo",
            favorite = when {
                favorite -> if (short) "★ Bỏ Yêu thích" else "★ Bỏ khỏi Yêu thích"
                else -> if (short) "☆ Lưu Yêu thích" else "☆ Thêm vào Yêu thích"
            },
            download = if (short) "⬇ Tải và phát" else "⬇ Tải về và phát",
            details = if (short) "ℹ Chi tiết" else "ℹ Thông tin chi tiết"
        )
    }

    data class SongMenuLabels(
        val play: String, val versions: String?, val playNext: String,
        val favorite: String, val download: String, val details: String
    )

    fun loadStyle(prefs: SharedPreferences): LibraryStyle = LibraryStyle.fromKey(prefs.getString(KEY_STYLE, null))

    fun saveStyle(prefs: SharedPreferences, style: LibraryStyle) {
        prefs.edit().putString(KEY_STYLE, style.key).apply()
    }
}
