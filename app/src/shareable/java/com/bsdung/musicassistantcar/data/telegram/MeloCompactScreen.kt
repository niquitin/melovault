package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloComponents.SongRowModel
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.Row
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.Snapshot
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.SourceRow
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes

/**
 * Gọn layout for small players (Shanling M3 Ultra), docs/ui-spec.md step 4.
 * Pure view layer: every list, count and action comes from [MeloScreenHost], which maps to
 * the existing library / download / playback logic of TelegramPreviewActivity.
 */
internal class MeloCompactScreen(
    private val context: Context,
    sizes: Sizes,
    private val host: MeloScreenHost,
    private val backDispatcher: OnBackPressedDispatcher,
    /** E99: the song rows carry their cover (teal theme chosen); colours and sizes stay Gọn's own. */
    private val rowArt: Boolean = false
) {

    private val c = MeloComponents(context, sizes)
    private val s = c.sizes
    private val p = c.palette
    private val handler = Handler(Looper.getMainLooper())

    val view: FrameLayout = FrameLayout(context).apply { setBackgroundColor(p.background) }
    private val libraryPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = c.text("", s.titleText, p.text, bold = true)
    private val note = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    // Two lines: the Playlist status sentence is longer than one line on a narrow screen (REWORK-04, M3 showed "Chưa kiểm tra đồng ...").
    private val downloadedLine = c.text("", s.secondaryText, MeloCompactPolicy.DOWNLOADED_LINE_COLOR, maxLines = 2)
    private val list = ListView(context)
    private val adapter = RowsAdapter()
    private val bar = MeloBottomBar(context, c, host, onTab = ::onBarTab, onOpenNowPlaying = ::openNowPlaying)
    private lateinit var searchBar: LinearLayout
    private var nowPage: NowPlayingPage? = null
    private var snapshot: Snapshot? = null
    private var pendingQuery: Runnable? = null
    private var lastQuery = ""
    private var lastLocal = false
    /** E99 row covers: the list's scroll state (nothing is requested while it is flung) and the first row asked for. */
    private var scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
    private var lastArtFirst = -1

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                nowPage != null -> closeNowPlaying()
                searchBar.visibility == View.VISIBLE -> hideSearch()
                snapshot?.local == true -> host.closeLocal()
                snapshot?.showingSources == false && snapshot?.sourceTitle != null -> host.showSources()
                else -> { isEnabled = false; backDispatcher.onBackPressed() }
            }
        }
    }

    init {
        buildLibraryPage()
        view.addView(libraryPage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun attach() {
        backDispatcher.addCallback(backCallback)
        if (rowArt) host.setRowArtListener(::refreshVisibleArt)
        onLibraryChanged()
        onPlaybackChanged()
    }

    fun detach() {
        backCallback.remove()
        if (rowArt) host.setRowArtListener(null)
        pendingQuery?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
    }

    // ---- Library -------------------------------------------------------------------------

    private fun buildLibraryPage() {
        libraryPage.setPadding(s.gap, 0, s.gap, dp(MeloBottomBarPolicy.COMPACT_LIST_BOTTOM_PAD_DP))
        libraryPage.addView(header(), matchWrap())
        searchBar = c.searchBar(null, onQuery = ::queueQuery).apply { visibility = View.GONE }
        libraryPage.addView(searchBar, matchWrap().apply { bottomMargin = s.gap / 2 })
        libraryPage.addView(note.apply { visibility = View.GONE; setPadding(s.gap / 2, 0, 0, s.gap / 2) }, matchWrap())
        list.apply {
            adapter = this@MeloCompactScreen.adapter
            divider = null
            dividerHeight = 0 // rows touch; see rowGap() for the visible gap
            setSelector(android.R.color.transparent)
            isVerticalScrollBarEnabled = true
            setOnItemClickListener { _, _, position, _ -> onRowClick(position) }
            setOnItemLongClickListener { _, v, position, _ -> onRowLongClick(position, v); true }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, state: Int) {
                    scrollState = state
                    // The covers of a fast scroll are fetched once the list slows down.
                    if (rowArt && state != AbsListView.OnScrollListener.SCROLL_STATE_FLING) requestVisibleArt()
                }
                override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                    val snap = snapshot ?: return
                    if (rowArt && first != lastArtFirst) requestVisibleArt()
                    if (!snap.showingSources && MeloCompactPolicy.shouldLoadMore(first, visible, total)) host.loadMore()
                }
            })
        }
        libraryPage.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        libraryPage.addView(bar.view, matchWrap())
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = s.minTouch
        addView(flat(c.iconButton("☰", "Menu") { showMainMenu(this) }))
        // The hint sits under the title inside the 48dp header row, so it costs no list height.
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(title, matchWrap())
            addView(downloadedLine.apply { visibility = View.GONE }, matchWrap())
        })
        addView(flat(c.iconButton("⌕", "Tìm kiếm") { toggleSearch() }))
        lateinit var more: View
        more = flat(c.iconButton("⋮", "Bộ lọc và tuỳ chọn") { showFilterMenu(more) })
        addView(more)
    }

    private fun flat(v: TextView): TextView = v.apply {
        background = null
        setTextColor(p.text)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        title.text = if (snap.showingSources) {
            "Telegram · ${MeloComponentPolicy.formatCount(snap.sources.count { it.chatId != 0L })} nguồn"
        } else {
            (if (snap.mode == Mode.PLAYLIST) host.named()?.headerTitle() else null)
                ?: MeloCompactPolicy.headerTitle(snap.mode, snap.sourceTitle, snap.total)
        }
        if (snap.local != lastLocal) {
            // The search box belongs to one source: show that source's own query when switching.
            lastLocal = snap.local
            pendingQuery?.let(handler::removeCallbacks)
            lastQuery = snap.query
            (searchBar.getChildAt(1) as? TextView)?.let { if (it.text.toString() != snap.query) it.text = snap.query }
        }
        val downloaded = MeloCompactPolicy.downloadedLine(
            snap.mode, snap.sourceTitle, snap.showingSources,
            host.sourceSummaries().firstOrNull { it.kind == MeloSourceCardPolicy.Kind.TELEGRAM }?.downloaded
        )
        // E96 P4: on the Playlist tab the same hint line carries the honest Playlist state; tap = detail.
        val playlistState = if (snap.mode == Mode.PLAYLIST && !snap.showingSources) host.playlistStatus() else null
        val hint = playlistState?.text ?: downloaded
        downloadedLine.text = hint.orEmpty()
        downloadedLine.visibility = if (hint == null) View.GONE else View.VISIBLE
        if (playlistState != null) {
            downloadedLine.contentDescription = "Trạng thái Playlist: ${playlistState.text}. Chạm để xem chi tiết."
            downloadedLine.setOnClickListener {
                android.widget.Toast.makeText(context, playlistState.text + "\n" + playlistState.fullDetail(), android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            downloadedLine.contentDescription = null
            downloadedLine.setOnClickListener(null)
            downloadedLine.isClickable = false
        }
        note.text = snap.note.orEmpty()
        note.visibility = if (snap.note.isNullOrBlank()) View.GONE else View.VISIBLE
        backCallback.isEnabled = nowPage != null || searchBar.visibility == View.VISIBLE ||
            (!snap.showingSources && snap.sourceTitle != null)
        bar.renderTabs(snap.showingSources, snap.mode)
        bar.refreshIdle()
        adapter.notifyDataSetChanged()
        if (rowArt) {
            lastArtFirst = -1
            list.post { requestVisibleArt() }
        }
    }

    // ---- E99 row covers (teal theme chosen) -----------------------------------------------------------

    /** Asks the host for the covers of the rows on screen and the few below (never while the list is flung). */
    private fun requestVisibleArt() {
        val snap = snapshot ?: return
        if (snap.showingSources || !MeloRowArtPolicy.shouldLoad(scrollState)) return
        val first = list.firstVisiblePosition
        lastArtFirst = first
        val range = MeloRowArtPolicy.rowsToLoad(first, list.childCount, snap.rows.size)
        if (range.isEmpty()) return
        host.requestRowArt(snap.rows.slice(range), s.rowArt)
    }

    /** New covers arrived: put them into the rows on screen in place; the list is not rebuilt. Logs counts only. */
    private fun refreshVisibleArt() {
        val snap = snapshot ?: return
        if (snap.showingSources) return
        var shown = 0
        var withArt = 0
        var settled = 0
        var appeared = 0
        var swapped = 0
        for (i in 0 until list.childCount) {
            val frame = findRowArtFrame(list.getChildAt(i)) ?: continue
            val key = (frame.tag as MeloArtTag).key
            shown++
            when (c.applyRowArt(frame, host.peekRowArt(key), animate = true)) {
                MeloRowArtPolicy.ArtChange.APPEAR -> appeared++
                MeloRowArtPolicy.ArtChange.SWAP -> swapped++
                MeloRowArtPolicy.ArtChange.NONE -> Unit
            }
            if (c.rowArtShowing(frame)) withArt++
            if (host.rowArtSettled(key)) settled++
        }
        android.util.Log.i("MeloRowArt", "compact visible=$shown withArt=$withArt settled=$settled appeared=$appeared swapped=$swapped")
    }

    private fun onRowClick(position: Int) {
        val snap = snapshot ?: return
        if (snap.showingSources) {
            snap.sources.getOrNull(position)?.let { host.openSource(it.chatId.takeIf { id -> id != 0L }) }
        } else if (position in snap.rows.indices) {
            host.playAt(position)
        }
    }

    private fun onRowLongClick(position: Int, anchor: View) {
        val snap = snapshot ?: return
        if (!snap.showingSources && position in snap.rows.indices) host.openSongMenu(position, anchor)
    }

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount(): Int = snapshot?.let { if (it.showingSources) it.sources.size else it.rows.size } ?: 0
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(position: Int): Int = if (snapshot?.showingSources == true) 1 else 0

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val snap = snapshot ?: return View(context)
            return if (snap.showingSources) sourceRow(snap.sources[position]) else songRow(snap, snap.rows[position], position)
        }
    }

    private fun songRow(snap: Snapshot, row: Row, position: Int): View {
        val playing = row.songKey == snap.playingKey
        val status = MeloCompactPolicy.rowStatus(row.downloaded, row.downloadingPercent, playing, snap.paused)
        val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(row.mimeType, row.filename), null, null, null)
        // Rows are drawn by the ListView itself: the row view must not swallow clicks.
        return c.songRow(SongRowModel(
            title = row.title,
            sourceTitle = row.sourceTitle,
            durationSeconds = row.durationSeconds,
            status = status,
            qualityLabel = quality,
            progressPercent = row.downloadingPercent,
            art = if (rowArt) MeloRowArtPolicy.rowPicture(host.peekRowArt(row.artKey), row.art) else row.art,
            local = snap.local || row.local,
            artKey = if (rowArt) row.artKey else null
        )).also { rowGap(it); unclickable(it, position) }
    }

    private fun sourceRow(source: SourceRow): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = s.rowHeight
        setPadding(s.gap, s.gap / 2, s.gap * 2, s.gap / 2)
        background = c.roundedBackground(p.surface)
        val isAll = source.chatId == 0L
        addView(c.artView(null, if (isAll) "♫" else source.title, source.title, s.rowArt))
        addView(c.text(if (isAll) "Tất cả nguồn" else source.title, s.titleText, p.text, bold = isAll).apply {
            setPadding(s.gap * 3 / 2, 0, s.gap, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(c.text("${MeloComponentPolicy.formatCount(source.count)} bài", s.secondaryText, p.textSecondary))
        contentDescription = "${source.title}, ${source.count} bài"
        rowGap(this)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    /**
     * Rows are 52dp edge to edge so 8 fit above the 102dp bar on the 574dp Shanling window;
     * insetting the drawn background by 1dp on top and bottom leaves the 2dp visual gap.
     */
    private fun rowGap(row: View) {
        val inset = dp(MeloBottomBarPolicy.COMPACT_ROW_INSET_DP)
        row.background?.let { row.background = android.graphics.drawable.InsetDrawable(it, 0, inset, 0, inset) }
    }

    private fun unclickable(v: View, position: Int = -1) {
        // Only the song row's ⋮ stays clickable (opens the song menu); the rest is driven by the ListView.
        if (position >= 0 && v.tag == MELO_SONG_ROW_MORE_TAG) {
            v.setOnClickListener { host.openSongMenu(position, v) }
            v.isFocusable = false
            return
        }
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) unclickable(v.getChildAt(i), position)
    }

    // ---- Search ----------------------------------------------------------------------------

    private fun toggleSearch() {
        if (searchBar.visibility == View.VISIBLE) hideSearch() else {
            searchBar.visibility = View.VISIBLE
            backCallback.isEnabled = true
            (searchBar.getChildAt(1) as? TextView)?.let { field ->
                field.hint = MeloComponentPolicy.searchHint(snapshot?.total)
                field.requestFocus()
            }
        }
    }

    private fun hideSearch() {
        (searchBar.getChildAt(1) as? TextView)?.text = ""
        searchBar.visibility = View.GONE
        onLibraryChanged()
    }

    private fun queueQuery(query: String) {
        pendingQuery?.let(handler::removeCallbacks)
        val task = Runnable {
            if (query != lastQuery) {
                lastQuery = query
                host.setQuery(query)
            }
        }
        pendingQuery = task
        handler.postDelayed(task, 250L)
    }

    // ---- Menus -----------------------------------------------------------------------------

    private fun showMainMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            menu.add(0, 1, 1, "♫ Thư viện")
            menu.add(0, 2, 2, "☷ Telegram")
            menu.add(0, 3, 3, "☆ Playlist")
            menu.add(0, 6, 4, "✓ Đã tải")
            menu.add(0, 7, 4, "♪ Nhạc trên máy")
            menu.add(0, 4, 5, "▶ Đang phát")
            menu.add(0, 5, 6, "⚙ Cài đặt")
            // Nhiều Playlist có tên: with one playlist open, its own actions are one tap away.
            val named = host.named()?.takeIf { snapshot?.mode == Mode.PLAYLIST }
            val detail = named?.screen() is MeloNamedPlaylistPolicy.Screen.Detail
            if (detail) {
                menu.add(0, 8, 7, "▶ Phát Playlist này")
                menu.add(0, 9, 8, "⤨ Phát ngẫu nhiên")
                menu.add(0, 10, 9, "⋮ Đổi tên · Sắp xếp · Xóa")
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { host.openSource(null); host.setMode(Mode.ALL) }
                    2 -> host.showSources()
                    3 -> openPlaylistTab()
                    4 -> openNowPlaying()
                    5 -> host.openUiLab()
                    6 -> host.setMode(Mode.DOWNLOADED)
                    7 -> host.openLocal()
                    8 -> named?.play(false)
                    9 -> named?.play(true)
                    10 -> named?.openMenu(anchor)
                }
                true
            }
        }.show()
    }

    /** Gọn has no room for an inline index: the Playlist entry opens it as a list in a box (Yêu thích, the playlists, + Tạo Playlist). */
    private fun openPlaylistTab() {
        host.setMode(Mode.PLAYLIST)
        host.named()?.openIndexDialog()
    }

    private fun showFilterMenu(anchor: View) {
        val mode = snapshot?.mode ?: Mode.ALL
        PopupMenu(context, anchor).apply {
            menu.add(0, 1, 1, (if (mode == Mode.ALL) "✓ " else "") + "Tất cả")
            menu.add(0, 2, 2, (if (mode == Mode.DOWNLOADED) "✓ " else "") + "Đã tải")
            menu.add(0, 3, 3, (if (mode == Mode.PLAYLIST) "✓ " else "") + "Playlist")
            menu.add(0, 4, 4, "Thêm… (đồng bộ, nguồn)")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> host.setMode(Mode.ALL)
                    2 -> host.setMode(Mode.DOWNLOADED)
                    3 -> openPlaylistTab()
                    4 -> host.openLegacyOverflow(anchor)
                }
                true
            }
        }.show()
    }

    // ---- Playback ----------------------------------------------------------------------------

    private fun onBarTab(tab: MeloBottomBarPolicy.Tab) {
        when (tab) {
            MeloBottomBarPolicy.Tab.LIBRARY -> { host.openSource(null); host.setMode(Mode.ALL) }
            MeloBottomBarPolicy.Tab.TELEGRAM -> host.showSources()
            MeloBottomBarPolicy.Tab.PLAYLIST -> openPlaylistTab()
            MeloBottomBarPolicy.Tab.DOWNLOADED -> host.setMode(Mode.DOWNLOADED)
        }
    }

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        bar.update(now)
        nowPage?.update(now)
        // The playing row highlight follows track changes without re-reading the list every tick.
        snapshot?.let { shown ->
            if (MeloCompactPolicy.rowsStale(shown.playingKey, shown.paused, now?.songKey, now?.paused == true)) onLibraryChanged()
        }
    }

    private fun openNowPlaying() {
        if (nowPage != null) return
        val page = NowPlayingPage()
        nowPage = page
        view.addView(page.root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        page.update(host.nowPlaying())
        backCallback.isEnabled = true
    }

    private fun closeNowPlaying() {
        nowPage?.let { view.removeView(it.root) }
        nowPage = null
        onLibraryChanged()
    }

    /**
     * Gọn Now Playing: the first screen has only cover, title, progress and
     * quality pills, Lùi / Phát / Tiến; scrolling up reveals channel and the extra actions.
     */
    private inner class NowPlayingPage {
        val root = ScrollView(context).apply {
            setBackgroundColor(p.background)
            isFillViewport = true
        }
        private val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.screenMargin, 0, s.screenMargin, s.screenMargin)
        }
        private val first = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        private val art = FrameLayout(context)
        private val songTitle = c.text("", s.playerTitleText, p.text, bold = true, maxLines = 2).apply { gravity = Gravity.CENTER }
        private val time = c.text("", s.secondaryText, p.textSecondary).apply { gravity = Gravity.CENTER }
        private val seek = SeekBar(context).apply { max = 1000 }
        private lateinit var play: TextView
        private val source = c.text("", s.titleText, p.text, maxLines = 2)
        private val qualityChips = MeloQualityChipRow(context, c, s.minTouch * 2 / 3, centered = true).apply { visibility = View.GONE }
        private var depthSongKey: Long? = null
        private var depthProbed = false
        private var fileBitDepth: Int? = null
        private val download = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
        private val shuffle = extra("⇄ Đảo") { host.toggleShuffle(); refresh() }
        private val repeat = extra("↻ Lặp") { host.cycleRepeat(); refresh() }
        private val favorite = extra("☆ Lưu") { host.toggleFavorite(); refresh() }
        private val queue = extra("☰ Hàng đợi") { host.openQueue() }
        private var userSeeking = false
        private var lastArt: Bitmap? = null
        private var artShown = false

        init {
            first.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = s.minTouch
                addView(flat(c.iconButton("˅", "Đóng màn Đang phát") { closeNowPlaying() }))
                addView(c.text("Đang phát", s.titleText, p.textSecondary).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    gravity = Gravity.CENTER
                })
                addView(View(context), LinearLayout.LayoutParams(s.minTouch, 1))
            }, matchWrap())
            first.addView(art, LinearLayout.LayoutParams(0, 0)) // sized in onLayout below
            first.addView(songTitle, matchWrap().apply { topMargin = s.gap * 2 })
            first.addView(qualityChips, matchWrap().apply { topMargin = s.gap })
            first.addView(seek, matchWrap().apply { topMargin = s.gap * 2 })
            first.addView(time, matchWrap())
            val transport = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            transport.addView(c.iconButton("⏮", "Bài trước") { host.previous() }.apply {
                layoutParams = LinearLayout.LayoutParams(s.playButtonWidth, s.playButtonHeight).apply { marginEnd = s.gap * 3 }
            })
            play = c.iconButton("▶", "Phát", primary = true, width = s.playButtonWidth, height = s.playButtonHeight) { host.playPause() }
            transport.addView(play)
            transport.addView(c.iconButton("⏭", "Bài kế") { host.next() }.apply {
                layoutParams = LinearLayout.LayoutParams(s.playButtonWidth, s.playButtonHeight).apply { marginStart = s.gap * 3 }
            })
            first.addView(transport, matchWrap().apply { topMargin = s.gap * 2 })
            first.addView(c.text("Vuốt lên để xem thêm", s.secondaryText, p.textSecondary).apply {
                gravity = Gravity.CENTER
                setPadding(0, s.gap * 2, 0, 0)
            }, matchWrap())
            column.addView(first, matchWrap())

            column.addView(c.spacer(s.gap * 3))
            column.addView(source, matchWrap())
            column.addView(download, matchWrap().apply { topMargin = s.gap / 2 })
            val extras = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            listOf(shuffle, repeat, favorite, queue).forEach {
                extras.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch).apply { topMargin = s.gap })
            }
            column.addView(extras, matchWrap().apply { topMargin = s.gap })
            root.addView(column)

            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(bar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    userSeeking = false
                    val dur = host.nowPlaying()?.durationMs ?: return
                    if (dur > 0L && bar != null) host.seekTo(dur * bar.progress / 1000L)
                }
            })
            // The first block fills the visible height so the extras only appear after scrolling.
            root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) sizeFirstBlock(bottom - top)
            }
        }

        private fun sizeFirstBlock(viewportHeight: Int) {
            if (viewportHeight <= 0) return
            first.minimumHeight = viewportHeight
            val width = root.width - s.screenMargin * 2
            val reserved = s.minTouch + s.playerTitleText.toInt() * 3 + s.minTouch + s.playButtonHeight + s.gap * 10 +
                s.secondaryText.toInt() * 3
            // The pill row may wrap to two lines on a narrow window: measure it instead of guessing.
            val chipsExtra = if (qualityChips.visibility == View.VISIBLE) {
                qualityChips.measure(
                    View.MeasureSpec.makeMeasureSpec(width.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                qualityChips.measuredHeight + s.gap
            } else 0
            val side = minOf(width, viewportHeight - reserved - chipsExtra).coerceAtLeast(s.rowArt)
            art.layoutParams = LinearLayout.LayoutParams(side, side).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap }
            artShown = false
            refresh()
        }

        private fun extra(label: String, onClick: () -> Unit): TextView = c.chip(label, false) { onClick() }

        private fun refresh() = update(host.nowPlaying())

        fun update(now: NowPlaying?) {
            if (now == null) {
                songTitle.text = "Chưa phát bài nào"
                time.text = ""
                if (qualityChips.setChips(emptyList())) {
                    qualityChips.visibility = View.GONE
                    if (root.height > 0) sizeFirstBlock(root.height)
                }
                return
            }
            songTitle.text = now.title
            val side = (art.layoutParams?.width ?: 0)
            if (side > 0 && (!artShown || now.art !== lastArt)) {
                art.removeAllViews()
                art.addView(c.artView(now.art, now.sourceTitle, now.title, side).apply {
                    layoutParams = FrameLayout.LayoutParams(side, side)
                    if (this is ImageView) adjustViewBounds = false
                })
                lastArt = now.art
                artShown = true
            }
            if (!userSeeking) {
                seek.progress = if (now.durationMs > 0L) ((now.positionMs * 1000L) / now.durationMs).toInt().coerceIn(0, 1000) else 0
            }
            time.text = MeloComponentPolicy.progressText((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt())
            play.text = if (now.playing) "❚❚" else "▶"
            play.contentDescription = if (now.playing) "Dừng" else "Phát"
            source.text = now.sourceTitle?.let { if (now.local) "Nghệ sĩ: $it" else "Kênh: $it" } ?: ""
            probeBitDepth(now)
            val chips = host.qualityChips(fileBitDepth)
            if (qualityChips.setChips(chips)) {
                qualityChips.visibility = if (qualityChips.isEmpty) View.GONE else View.VISIBLE
                // The first block lost or gained a row: give the cover the height that is left.
                if (root.height > 0) sizeFirstBlock(root.height)
            }
            download.text = now.downloadNote.orEmpty()
            shuffle.text = if (now.shuffle) "⇄ Đảo: bật" else "⇄ Đảo: tắt"
            repeat.text = "↻ Lặp: ${now.repeatLabel}"
            val save = MeloSavePolicy.buttonState(now.saveAvailability, now.favorite, now.saveBusy)
            favorite.text = if (save.saved) "★ Bỏ khỏi Yêu thích" else "☆ Lưu vào Yêu thích"
            favorite.contentDescription = save.contentDescription
            setTextPx(shuffle); setTextPx(repeat); setTextPx(favorite); setTextPx(queue)
        }

        /**
         * Bit depth is only in the downloaded file's header, so a lossless song is probed once
         * (background thread, header only) as soon as its file is complete; until then no Hi-Res claim.
         */
        private fun probeBitDepth(now: NowPlaying) {
            val key = now.songKey ?: return
            if (key != depthSongKey) {
                depthSongKey = key
                depthProbed = false
                fileBitDepth = null
            }
            if (depthProbed) return
            if (!MeloComponentPolicy.isLossless(MeloComponentPolicy.formatOf(null, now.filename))) {
                depthProbed = true
                return
            }
            val file = host.currentLocalFile() ?: return // not downloaded yet: try again on the next update
            depthProbed = true
            Thread {
                val depth = MeloWaveform.probeBitDepth(file)
                handler.post {
                    if (nowPage === this@NowPlayingPage && depthSongKey == key && depth != null) {
                        fileBitDepth = depth
                        refresh()
                    }
                }
            }.apply { isDaemon = true; name = "melo-bitdepth" }.start()
        }

        private fun setTextPx(v: TextView) = v.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
    }
}
