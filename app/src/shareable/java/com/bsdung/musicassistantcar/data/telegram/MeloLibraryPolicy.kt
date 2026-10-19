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

    /** "27.971 bài · Đã đồng bộ"; text from the existing sync display policy wins when present. */
    fun syncLine(existing: String?, total: Int?, syncing: Boolean): String = when {
        !existing.isNullOrBlank() -> existing
        syncing -> "Đang đồng bộ…"
        total != null && total > 0 -> "${MeloComponentPolicy.formatCount(total)} bài · Đã đồng bộ"
        else -> "Chưa có bài"
    }

    /** "Phát hết" plays from the first loaded song; disabled when the group is empty. */
    fun playAllEnabled(loadedRows: Int): Boolean = loadedRows > 0

    fun loadStyle(prefs: SharedPreferences): LibraryStyle = LibraryStyle.fromKey(prefs.getString(KEY_STYLE, null))

    fun saveStyle(prefs: SharedPreferences, style: LibraryStyle) {
        prefs.edit().putString(KEY_STYLE, style.key).apply()
    }
}
