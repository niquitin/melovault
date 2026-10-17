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
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes

/**
 * Gọn layout for small players (Shanling M3 Ultra), docs/ui-spec.md step 4.
 * Pure view layer: every list, count and action comes from [Host], which maps to
 * the existing library / download / playback logic of TelegramPreviewActivity.
 */
internal class MeloCompactScreen(
    private val context: Context,
    sizes: Sizes,
    private val host: Host,
    private val backDispatcher: OnBackPressedDispatcher
) {

    interface Host {
        fun snapshot(): Snapshot
        fun setMode(mode: Mode)
        fun showSources()
        fun openSource(chatId: Long?)
        fun setQuery(query: String)
        fun loadMore()
        fun playAt(index: Int)
        fun openSongMenu(index: Int, anchor: View)
        fun nowPlaying(): NowPlaying?
        fun playPause()
        fun next()
        fun previous()
        fun seekTo(positionMs: Long)
        fun toggleFavorite()
        fun toggleShuffle()
        fun cycleRepeat()
        fun openQueue()
        fun openLegacyOverflow(anchor: View)
        fun openUiLab()
    }

    data class Row(
        val title: String,
        val sourceTitle: String?,
        val filename: String,
        val durationSeconds: Int,
        val songKey: Long,
        val downloaded: Boolean,
        val downloadingPercent: Int?,
        val art: Bitmap?
    )

    data class SourceRow(val chatId: Long, val title: String, val count: Int)

    data class Snapshot(
        val showingSources: Boolean,
        val mode: Mode,
        val rows: List<Row>,
        val sources: List<SourceRow>,
        val total: Int?,
        val sourceTitle: String?,
        val playingKey: Long?,
        val query: String,
        val note: String?
    )

    data class NowPlaying(
        val songKey: Long?,
        val title: String,
        val sourceTitle: String?,
        val filename: String?,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val art: Bitmap?,
        val favorite: Boolean,
        val shuffle: Boolean,
        val repeatLabel: String,
        val downloadNote: String?
    )

    private val c = MeloComponents(context, sizes)
    private val s = c.sizes
    private val p = c.palette
    private val handler = Handler(Looper.getMainLooper())

    val view: FrameLayout = FrameLayout(context).apply { setBackgroundColor(p.background) }
    private val libraryPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = c.text("", s.titleText, p.text, bold = true)
    private val note = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val list = ListView(context)
    private val adapter = RowsAdapter()
    private val miniHost = FrameLayout(context)
    private var mini: MeloComponents.MiniPlayerHandle? = null
    private var miniTitle: String? = null
    private lateinit var searchBar: LinearLayout
    private var nowPage: NowPlayingPage? = null
    private var snapshot: Snapshot? = null
    private var pendingQuery: Runnable? = null
    private var lastQuery = ""

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                nowPage != null -> closeNowPlaying()
                searchBar.visibility == View.VISIBLE -> hideSearch()
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
        onLibraryChanged()
        onPlaybackChanged()
    }

    fun detach() {
        backCallback.remove()
        pendingQuery?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
    }

    // ---- Library -------------------------------------------------------------------------

    private fun buildLibraryPage() {
        libraryPage.setPadding(s.gap, 0, s.gap, s.gap / 2)
        libraryPage.addView(header(), matchWrap())
        searchBar = c.searchBar(null, onQuery = ::queueQuery).apply { visibility = View.GONE }
        libraryPage.addView(searchBar, matchWrap().apply { bottomMargin = s.gap / 2 })
        libraryPage.addView(note.apply { visibility = View.GONE; setPadding(s.gap / 2, 0, 0, s.gap / 2) }, matchWrap())
        list.apply {
            adapter = this@MeloCompactScreen.adapter
            divider = null
            dividerHeight = s.gap / 2
            setSelector(android.R.color.transparent)
            isVerticalScrollBarEnabled = true
            setOnItemClickListener { _, _, position, _ -> onRowClick(position) }
            setOnItemLongClickListener { _, v, position, _ -> onRowLongClick(position, v); true }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit
                override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                    val snap = snapshot ?: return
                    if (!snap.showingSources && MeloCompactPolicy.shouldLoadMore(first, visible, total)) host.loadMore()
                }
            })
        }
        libraryPage.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        libraryPage.addView(miniHost, matchWrap().apply { topMargin = s.gap / 2 })
    }

    private fun header(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = s.minTouch
        addView(flat(c.iconButton("☰", "Menu") { showMainMenu(this) }))
        addView(title.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
            MeloCompactPolicy.headerTitle(snap.mode, snap.sourceTitle, snap.total)
        }
        note.text = snap.note.orEmpty()
        note.visibility = if (snap.note.isNullOrBlank()) View.GONE else View.VISIBLE
        backCallback.isEnabled = nowPage != null || searchBar.visibility == View.VISIBLE ||
            (!snap.showingSources && snap.sourceTitle != null)
        adapter.notifyDataSetChanged()
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
            return if (snap.showingSources) sourceRow(snap.sources[position]) else songRow(snap, snap.rows[position])
        }
    }

    private fun songRow(snap: Snapshot, row: Row): View {
        val playing = row.songKey == snap.playingKey
        val status = MeloCompactPolicy.rowStatus(row.downloaded, row.downloadingPercent, playing)
        val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(null, row.filename), null, null, null)
        // Rows are drawn by the ListView itself: the row view must not swallow clicks.
        return c.songRow(SongRowModel(
            title = row.title,
            sourceTitle = row.sourceTitle,
            durationSeconds = row.durationSeconds,
            status = status,
            qualityLabel = quality,
            progressPercent = row.downloadingPercent,
            art = row.art
        )).also { unclickable(it) }
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
    }

    private fun unclickable(v: View) {
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) {
            // A clickable child (the row's ⋮) would steal the ListView row click; long press opens the same menu.
            for (i in 0 until v.childCount) unclickable(v.getChildAt(i))
        }
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
            menu.add(0, 4, 4, "▶ Đang phát")
            menu.add(0, 5, 5, "⚙ Cài đặt giao diện")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { host.openSource(null); host.setMode(Mode.ALL) }
                    2 -> host.showSources()
                    3 -> host.setMode(Mode.PLAYLIST)
                    4 -> openNowPlaying()
                    5 -> host.openUiLab()
                }
                true
            }
        }.show()
    }

    private fun showFilterMenu(anchor: View) {
        val mode = snapshot?.mode ?: Mode.ALL
        PopupMenu(context, anchor).apply {
            menu.add(0, 1, 1, (if (mode == Mode.ALL) "✓ " else "") + "Tất cả")
            menu.add(0, 2, 2, (if (mode == Mode.DOWNLOADED) "✓ " else "") + "Đã tải")
            menu.add(0, 3, 3, (if (mode == Mode.PLAYLIST) "✓ " else "") + "Playlist")
            menu.add(0, 4, 4, "Thêm… (đồng bộ, đăng xuất)")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> host.setMode(Mode.ALL)
                    2 -> host.setMode(Mode.DOWNLOADED)
                    3 -> host.setMode(Mode.PLAYLIST)
                    4 -> host.openLegacyOverflow(anchor)
                }
                true
            }
        }.show()
    }

    // ---- Playback ----------------------------------------------------------------------------

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        if (now == null) {
            miniHost.removeAllViews()
            mini = null
            miniTitle = null
        } else if (mini == null || miniTitle != now.title) {
            miniHost.removeAllViews()
            val handle = c.miniPlayer(
                MeloComponents.MiniPlayerModel(now.title, now.sourceTitle,
                    (now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt(), now.playing, now.art),
                onPlayPause = { host.playPause() },
                onNext = { host.next() },
                onOpen = { openNowPlaying() }
            )
            miniHost.addView(handle.view)
            mini = handle
            miniTitle = now.title
        } else {
            mini?.update((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt(), now.playing)
        }
        nowPage?.update(now)
        // The playing row highlight follows track changes without re-reading the list every tick.
        if (snapshot != null && snapshot?.playingKey != now?.songKey) onLibraryChanged()
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
     * Lùi / Phát / Tiến; scrolling up reveals channel, quality and the extra actions.
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
        private val quality = c.text("", s.secondaryText, p.accent, maxLines = 2)
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
            column.addView(quality, matchWrap().apply { topMargin = s.gap / 2 })
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
            val side = minOf(width, viewportHeight - reserved).coerceAtLeast(s.rowArt)
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
            source.text = now.sourceTitle?.let { "Kênh: $it" } ?: ""
            val label = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(null, now.filename), null, null, null)
            quality.text = label?.let { "Chất lượng: $it" } ?: ""
            download.text = now.downloadNote.orEmpty()
            shuffle.text = if (now.shuffle) "⇄ Đảo: bật" else "⇄ Đảo: tắt"
            repeat.text = "↻ Lặp: ${now.repeatLabel}"
            favorite.text = if (now.favorite) "★ Đã lưu vào Playlist" else "☆ Lưu vào Playlist"
            setTextPx(shuffle); setTextPx(repeat); setTextPx(favorite); setTextPx(queue)
        }

        private fun setTextPx(v: TextView) = v.setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
    }
}
