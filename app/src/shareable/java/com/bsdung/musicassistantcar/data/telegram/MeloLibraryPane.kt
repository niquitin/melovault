package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloComponents.SongRowModel
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.Snapshot
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.SourceRow

/**
 * The library column shared by the Điện thoại / Tablet dọc screen and the right pane of the
 * Hai khung screen: search, chips, group title with "Phát hết", song list or Telegram source
 * grid. Pure view layer over [MeloScreenHost].
 */
internal class MeloLibraryPane(
    private val context: Context,
    private val c: MeloComponents,
    private val host: MeloScreenHost,
    private val layer: NewLayer,
    /** Called while the list scrolls: true when the owner should fold its title row away. */
    private val onHeaderCollapse: (Boolean) -> Unit = {}
) {
    private val s = c.sizes
    private val p = c.palette
    private val handler = Handler(Looper.getMainLooper())

    val view: LinearLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val searchHolder = FrameLayout(context)
    private val chipHolder = FrameLayout(context)
    private val groupTitle = c.text("", s.titleText, p.text, bold = true)
    private val playAll = c.chip("▶ Phát hết", true) { host.playAll(false) }
    private val sortChip = c.chip("↕ Mới nhất", false) {
        host.setSort(if (host.sort() == SongSort.NAME) SongSort.NEWEST else SongSort.NAME)
    }
    private val header = LinearLayout(context)
    private val headerChips = LinearLayout(context)
    private var headerStacked = false
    private val note = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val list = ListView(context)
    private val listFrame = FrameLayout(context)
    private val bubble = c.text("", 36f * context.resources.displayMetrics.density, p.text, bold = true).apply {
        gravity = Gravity.CENTER
        visibility = View.GONE
        background = c.roundedBackground(p.surfaceRaised, stroke = p.accent)
    }
    private val bar = MeloAlphabetBar(
        context, p.textSecondary, p.accent,
        onTouching = { ch -> bubble.visibility = if (ch == null) View.GONE else View.VISIBLE; if (ch != null) bubble.text = ch.toString() },
        onLetter = ::onBarLetter
    ).apply { visibility = View.GONE }
    private var pendingJump: Runnable? = null
    private val adapter = RowsAdapter()
    private val sourcesScroll = ScrollView(context)
    private val sourcesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val searchBar = c.searchBar(null, onQuery = ::queueQuery)
    private val searchField: EditText get() = searchBar.getChildAt(1) as EditText

    private var snapshot: Snapshot? = null
    private var chipKey: String? = null
    private var lastQuery = ""
    private var pendingQuery: Runnable? = null
    private var collapsed = false
    private var lastScrollKey = 0
    private var playAllWhenLoaded = false
    private var groupKey: String? = null
    private var emptyExpanded = false
    private var pendingTopReset = false

    init {
        searchHolder.addView(searchBar)
        view.addView(searchHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(chipHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        headerChips.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(sortChip)
            addView(playAll)
        }
        header.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, s.gap, 0, s.gap / 2)
            addView(groupTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(headerChips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) post { fitHeader() } }
        }
        view.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap / 2
        })
        list.apply {
            adapter = this@MeloLibraryPane.adapter
            divider = null
            dividerHeight = s.gap / 2
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ -> snapshot?.takeIf { !it.showingSources && position in it.rows.indices }?.let { host.playAt(position) } }
            setOnItemLongClickListener { _, v, position, _ ->
                if (snapshot?.showingSources == false) host.openSongMenu(position, v)
                true
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, state: Int) = Unit
                override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {
                    val snap = snapshot ?: return
                    if (snap.showingSources) return
                    if (MeloCompactPolicy.shouldLoadMore(first, visible, total)) host.loadMore()
                    if (bar.visibility == View.VISIBLE) {
                        snap.rows.getOrNull(first)?.sortKey?.let { bar.setActive(MeloAlphabetPolicy.letterOf(it)) }
                    }
                    val key = first * (s.rowHeight + s.gap / 2) - (getChildAt(0)?.top ?: 0)
                    val next = MeloLibraryPolicy.headerCollapsed(collapsed, key - lastScrollKey, first)
                    lastScrollKey = key
                    if (next != collapsed) {
                        collapsed = next
                        onHeaderCollapse(next)
                    }
                }
            })
        }
        sourcesScroll.addView(sourcesBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        listFrame.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        listFrame.addView(bar, FrameLayout.LayoutParams(bar.barWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        val bubbleSize = (72 * context.resources.displayMetrics.density).toInt()
        listFrame.addView(bubble, FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.CENTER))
        view.addView(listFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(sourcesScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun detach() {
    pendingJump?.let(handler::removeCallbacks)
        pendingQuery?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
    }

    /** Back at the top with the owner's title row visible, e.g. after switching group or page. */
    private fun resetScroll() {
        if (collapsed) {
            collapsed = false
            onHeaderCollapse(false)
        }
        lastScrollKey = 0
        pendingTopReset = true
        list.setSelectionFromTop(0, 0)
        sourcesScroll.scrollTo(0, 0)
    }

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        val key = "${snap.showingSources}|${snap.mode}|${snap.sourceTitle}|${snap.query}|${snap.sort}|${snap.letterFloor}"
        if (key != groupKey) {
            groupKey = key
            resetScroll()
        }
        searchField.hint = if (snap.showingSources) "Tìm nhóm, kênh hoặc bot…" else MeloComponentPolicy.searchHint(snap.total)
        renderChips(snap)
        header.post { fitHeader() }
        if (snap.showingSources) {
            groupTitle.text = "Telegram · ${MeloComponentPolicy.formatCount(snap.sources.count { it.chatId != 0L })} nguồn"
            playAll.visibility = View.GONE
            sortChip.visibility = View.GONE
            note.visibility = View.GONE
            listFrame.visibility = View.GONE
            sourcesScroll.visibility = View.VISIBLE
            renderSources(snap)
        } else {
            groupTitle.text = MeloCompactPolicy.headerTitle(snap.mode, snap.sourceTitle, snap.total)
            playAll.visibility = View.VISIBLE
            playAll.isEnabled = MeloLibraryPolicy.playAllEnabled(snap.rows.size)
            playAll.alpha = if (playAll.isEnabled) 1f else 0.4f
            note.text = snap.note.orEmpty()
            note.visibility = if (snap.note.isNullOrBlank()) View.GONE else View.VISIBLE
            sourcesScroll.visibility = View.GONE
            listFrame.visibility = View.VISIBLE
            sortChip.visibility = View.VISIBLE
            sortChip.text = if (snap.sort == SongSort.NAME) "↕ Tên A–Z" else "↕ Mới nhất"
            val showBar = MeloAlphabetPolicy.barVisible(snap.sort, false, snap.total ?: snap.rows.size, snap.letterFloor != null)
            bar.visibility = if (showBar) View.VISIBLE else View.GONE
            list.setPadding(0, 0, if (showBar) bar.barWidthPx else 0, 0)
            if (!showBar) bar.setActive(null)
            adapter.notifyDataSetChanged()
            if (MeloLibraryPolicy.shouldResetTop(pendingTopReset, snap.showingSources, snap.rows.size)) {
                // The rows of the new group just arrived; the list still remembers the old group's offset.
                pendingTopReset = false
                list.post { list.setSelectionFromTop(0, 0) }
            }
            if (playAllWhenLoaded && snap.rows.isNotEmpty()) {
                playAllWhenLoaded = false
                host.playAll(true)
            }
        }
    }

    /** Long group name on a narrow pane: title on its own row (2 lines), the two buttons under it at the right. */
    private fun fitHeader() {
        val width = header.width
        if (width <= 0) return
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        headerChips.measure(unspecified, unspecified)
        val titleWidth = groupTitle.paint.measureText(groupTitle.text.toString()).toInt() + groupTitle.paddingLeft + groupTitle.paddingRight
        val stacked = MeloLibraryPolicy.headerStacked(width, titleWidth, headerChips.measuredWidth, s.minTouch * 4)
        if (stacked == headerStacked) return
        headerStacked = stacked
        header.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        groupTitle.maxLines = if (stacked) 2 else 1
        groupTitle.layoutParams = if (stacked) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerChips.layoutParams = if (stacked) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = s.gap / 2 }
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /** Playing-row highlight follows track changes. */
    fun onPlaybackChanged(playingKey: Long?, paused: Boolean = false) {
        val shown = snapshot ?: return
        if (MeloCompactPolicy.rowsStale(shown.playingKey, shown.paused, playingKey, paused)) onLibraryChanged()
    }

    private fun onBarLetter(letter: Char, final: Boolean) {
        pendingJump?.let(handler::removeCallbacks)
        val task = Runnable {
            val index = host.jumpToLetter(MeloAlphabetPolicy.lowerBound(letter))
            bar.setActive(letter)
            if (index != null) {
                onLibraryChanged()
                android.util.Log.i("MeloVaultAz", "jump $letter -> index $index of ${snapshot?.rows?.size}")
                list.post { list.setSelectionFromTop(index, 0) }
            }
        }
        pendingJump = task
        if (final) task.run() else handler.postDelayed(task, 150L)
    }

    // ---- Search and chips ---------------------------------------------------------------------

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

    private fun renderChips(snap: Snapshot) {
        if (snap.showingSources) {
            chipHolder.visibility = View.GONE
            return
        }
        chipHolder.visibility = View.VISIBLE
        val downloaded = host.downloadedCount()
        val chips = MeloLibraryPolicy.chips(downloaded)
        val selected = MeloLibraryPolicy.selectedChip(snap.mode, snap.query, downloaded)
        val key = chips.joinToString("|") { it.label } + "#" + selected
        if (key == chipKey) return
        chipKey = key
        chipHolder.removeAllViews()
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        chips.forEachIndexed { i, chip ->
            strip.addView(c.chip(chip.label, i == selected) { onChip(chip) })
        }
        chipHolder.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })
    }

    private fun onChip(chip: MeloLibraryPolicy.Chip) {
        val query = MeloLibraryPolicy.queryFor(chip)
        if (chip.mode != null) host.setMode(chip.mode)
        lastQuery = query
        if (searchField.text.toString() != query) searchField.setText(query)
        host.setQuery(query)
    }

    // ---- Song list ------------------------------------------------------------------------------

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount(): Int = snapshot?.takeIf { !it.showingSources }?.rows?.size ?: 0
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val snap = snapshot ?: return View(context)
            val row = snap.rows.getOrNull(position) ?: return View(context)
            val playing = row.songKey == snap.playingKey
            val status = if (TelegramPlayFeedbackPolicy.opening(host.playStatus(), row.songKey, playing)) {
                MeloComponentPolicy.Status.OPENING
            } else {
                MeloCompactPolicy.rowStatus(row.downloaded, row.downloadingPercent, playing, snap.paused)
            }
            val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(null, row.filename), null, null, null)
            return c.songRow(
                SongRowModel(row.title, row.sourceTitle, row.durationSeconds, status, quality, row.downloadingPercent, row.art)
            ).also { unclickable(it, position) }
        }
    }

    /**
     * Rows are driven by the ListView (tap = play, long press = menu), so the row must not
     * swallow clicks. Only the ⋮ button stays clickable and opens the same song menu.
     */
    private fun unclickable(v: View, position: Int) {
        if (v.tag == MELO_SONG_ROW_MORE_TAG) {
            v.setOnClickListener { host.openSongMenu(position, v) }
            v.isFocusable = false
            return
        }
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) unclickable(v.getChildAt(i), position)
    }

    // ---- Telegram sources ---------------------------------------------------------------------

    private fun renderSources(snap: Snapshot) {
        sourcesBox.removeAllViews()
        val all = snap.sources.firstOrNull { it.chatId == 0L }
        val (channels, emptyChannels) = MeloLibraryPolicy.splitSources(snap.sources)
        sourcesBox.addView(heroCard(all?.count ?: snap.total))
        val tools = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, s.gap, 0, s.gap)
        }
        tools.addView(c.chip(if (host.hideZeroSources()) "Hiện kênh 0 bài" else "Ẩn kênh 0 bài", host.hideZeroSources()) {
            host.toggleHideZeroSources()
        })
        tools.addView(c.chip("Quét lại", false) { host.rescan() })
        sourcesBox.addView(tools)
        if (channels.isEmpty() && emptyChannels.isEmpty()) {
            sourcesBox.addView(c.text("Chưa có nguồn nào. Bấm Quét lại để đồng bộ.", s.secondaryText, p.textSecondary, maxLines = 2))
            return
        }
        val columns = MeloLibraryPolicy.sourceColumns(layer)
        channels.chunked(columns).forEach { rowItems ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEachIndexed { i, source ->
                line.addView(sourceCard(source), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < columns - 1) marginEnd = s.gap
                })
            }
            repeat(columns - rowItems.size) {
                line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = s.gap })
            }
            sourcesBox.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = s.gap
            })
        }
        addEmptyChannelsRow(emptyChannels)
    }

    /** Folded "Các kênh chưa có nhạc (N)" row; open it to list the names, each still opens its channel. */
    private fun addEmptyChannelsRow(emptyChannels: List<SourceRow>) {
        val label = MeloLibraryPolicy.emptySourcesLabel(emptyChannels.size, emptyExpanded) ?: return
        val header = c.text(label, s.titleText, p.text, bold = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener {
                emptyExpanded = !emptyExpanded
                snapshot?.let(::renderSources)
            }
        }
        sourcesBox.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!emptyExpanded) return
        emptyChannels.forEach { source ->
            val row = c.text(source.title, s.secondaryText, p.textSecondary, maxLines = 1).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = s.minTouch
                setPadding(s.gap * 2, 0, s.gap, 0)
                isClickable = true
                isFocusable = true
                contentDescription = "${source.title}, chưa có nhạc"
                setOnClickListener { host.openSource(source.chatId) }
            }
            sourcesBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** "Kho tổng hợp": total songs, a button to open everything and one to shuffle-play it. */
    private fun heroCard(total: Int?): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = c.roundedBackground(p.accentSoft, stroke = p.accent)
        setPadding(s.gap * 2, s.gap * 2, s.gap * 2, s.gap * 2)
        addView(c.text("Kho tổng hợp", s.titleText, p.text, bold = true))
        val downloaded = host.downloadedCount()
        val detail = listOfNotNull(
            total?.let { "${MeloComponentPolicy.formatCount(it)} bài" },
            downloaded?.let { "${MeloComponentPolicy.formatCount(it)} đã tải" }
        ).joinToString(" · ")
        if (detail.isNotEmpty()) addView(c.text(detail, s.secondaryText, p.textSecondary).apply { setPadding(0, s.gap / 2, 0, s.gap) })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(c.chip("Mở tất cả", false) { host.openSource(null) })
        buttons.addView(c.chip("⇄ Phát ngẫu nhiên", true) {
            playAllWhenLoaded = true
            host.openSource(null)
            host.setMode(Mode.ALL)
        })
        addView(buttons)
    }

    private fun sourceCard(source: SourceRow): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumHeight = s.rowHeight
        gravity = Gravity.CENTER_VERTICAL
        setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
        background = c.roundedBackground(p.surface, stroke = p.border)
        isClickable = true
        isFocusable = true
        contentDescription = "${source.title}, ${source.count} bài"
        addView(c.text(source.title, s.titleText, p.text, bold = true, maxLines = 2))
        addView(c.text(if (source.count > 0) "${MeloComponentPolicy.formatCount(source.count)} bài" else "Chưa có nhạc",
            s.secondaryText, p.textSecondary).apply { setPadding(0, s.gap / 2, 0, 0) })
        setOnClickListener { host.openSource(source.chatId) }
    }
}
