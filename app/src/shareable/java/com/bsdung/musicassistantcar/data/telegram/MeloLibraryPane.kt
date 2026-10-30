package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Typeface
import android.graphics.Paint
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
import androidx.appcompat.app.AlertDialog
import com.bsdung.musicassistantcar.R
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
    /** P2-A: whether this pane's rows show a row cover, for either colour theme (not just teal). */
    private val rowArtEnabled = MeloThemePolicy.rowArtLayer(c.theme, layer, c.sizes.profile)

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
    /** Source cards between the group title and the list; folds away with the owner's title row. */
    private val sourceCardsHolder = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        visibility = View.GONE
    }
    private var sourceCards: List<MeloSourceCardPolicy.Card> = emptyList()
    private var sourceCardsKey: String? = null
    private var sourceRowVisible = false
    private val note = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val scopeNote = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val playFeedbackView = MeloPlayFeedbackView(context, c, host)
    private val listContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val listStatus = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(s.gap, s.gap, s.gap, s.gap)
    }
    private val statusText = c.text("", s.secondaryText, p.textSecondary, maxLines = 4)
    private val retry = c.chip("Thử lại", false) { host.retryBrowse() }
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
        onLetter = ::onBarLetter,
        onAccent = if (c.teal) p.onAccent else null
    ).apply { visibility = View.GONE }
    private var pendingJump: Runnable? = null
    private val adapter = RowsAdapter()
    private val durationPaint = Paint()
    private val sourcesScroll = ScrollView(context)
    private val sourcesBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sourceList = ListView(context)
    private sealed class SourceItem {
        object Header : SourceItem()
        data class Channel(val row: SourceRow) : SourceItem()
        data class EmptyToggle(val count: Int) : SourceItem()
    }
    private data class SourceHolder(val root: LinearLayout, val title: TextView, val detail: TextView)
    private var sourceItems: List<SourceItem> = emptyList()
    private val sourceAdapter = SourceAdapter()
    // E98: the filter button does something only on the teal theme; the current theme keeps its inert button.
    private val searchHandle = c.searchBarWithField(null, onQuery = ::queueQuery, onFilter = if (c.teal) ::openFilterDialog else null)
    private val searchBar = searchHandle.view
    private val searchField: EditText get() = searchHandle.field
    /** Teal theme: "Chillout ✕" under the search box while a genre filter is on. */
    private val genreHolder = FrameLayout(context)

    private var snapshot: Snapshot? = null
    private var chipKey: String? = null
    private val queryDraft = MeloBrowseState.QueryDraft()
    private var lastQuery: String
        get() = queryDraft.committed
        set(value) { queryDraft.sync(value) }
    private var pendingQuery: Runnable? = null
    private var collapsed = false
    /** Folds or shows the owner's title row and the source cards for the current [collapsed]. */
    private val applyCollapse = Runnable { onHeaderCollapse(collapsed); applySourceCardsVisibility() }
    private var lastScrollKey = 0
    private var playAllWhenLoaded: MeloBrowseUiPolicy.Request? = null
    private var groupKey: String? = null
    private var emptyExpanded = false
    private var pendingTopReset = false
    /** Null until the first snapshot: the search box then starts from the host's query, not empty. */
    private var lastBrowseKey: MeloBrowseState.Key? = null
    private var restoringAnchor: MeloBrowseState.Pending? = null
    private var restoringSourceScroll = false
    private var restoringSongScroll = false
    private val songRestore = MeloBrowseState.SongRestore()
    /** E98 row covers: the list's scroll state (nothing is requested while it is flung) and the first row asked for. */
    private var scrollState = AbsListView.OnScrollListener.SCROLL_STATE_IDLE
    private var lastArtFirst = -1

    init {
        host.setBrowseCapture(::flushAndSave)
        if (rowArtEnabled) host.setRowArtListener(::refreshVisibleArt)
        if (c.teal) {
            // The tab labels shorten on a narrow pane: render them again once the width is known or changes.
            view.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ ->
                if (r - l != or_ - ol) view.post { snapshot?.let(::renderThemeTabs) }
            }
        }
        searchHolder.addView(searchBar)
        view.addView(searchHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        genreHolder.visibility = View.GONE
        view.addView(genreHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap / 2
        })
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
        sourceCardsHolder.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ ->
            if (r - l != or_ - ol && sourceRowVisible) sourceCardsHolder.post { buildSourceCards() }
        }
        view.addView(sourceCardsHolder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap
        })
        view.addView(note, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap / 2
        })
        view.addView(scopeNote, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap / 2
        })
        if (layer != NewLayer.TWO_PANE) view.addView(playFeedbackView)
        list.apply {
            itemsCanFocus = true
            adapter = this@MeloLibraryPane.adapter
            divider = null
            dividerHeight = if (c.teal) s.gap else s.gap / 2
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ -> snapshot?.takeIf { !it.showingSources && position in it.rows.indices }?.let { host.playAt(position) } }
            setOnItemLongClickListener { _, v, position, _ ->
                if (snapshot?.showingSources == false) host.openSongMenu(position, v)
                true
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, state: Int) {
                    scrollState = state
                    if (state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) endSongRestore()
                    // The covers of a fast scroll are fetched once the list slows down.
                    if (rowArtEnabled && state != AbsListView.OnScrollListener.SCROLL_STATE_FLING) requestVisibleArt()
                }
                override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {
                    val snap = snapshot ?: return
                    if (snap.showingSources) return
                    if (restoringSongScroll && songRestore.onScrolled(first, visible, total)) {
                        endSongRestore()
                        anchorLog { "stage=settled layer=$layer first=$first" }
                    }
                    if (snap.browseLoadState == MeloBrowseUiPolicy.LoadState.READY && restoringAnchor == null &&
                        !restoringSongScroll && !pendingTopReset) {
                        snap.rows.getOrNull(first)?.let { row ->
                            host.saveBrowsePosition(snap.browseGeneration, row.songKey, getChildAt(0)?.top ?: 0, 0)
                        }
                    }
                    if (rowArtEnabled && first != lastArtFirst) requestVisibleArt()
                    if (MeloCompactPolicy.shouldLoadMore(first, visible, total)) host.loadMore()
                    if (bar.visibility == View.VISIBLE) {
                        snap.rows.getOrNull(first)?.sortKey?.let { bar.setActive(MeloAlphabetPolicy.letterOf(it)) }
                    }
                    val key = first * (s.rowHeight + s.gap / 2) - (getChildAt(0)?.top ?: 0)
                    val next = MeloLibraryPolicy.headerCollapsed(collapsed, key - lastScrollKey, first)
                    lastScrollKey = key
                    if (next != collapsed) {
                        collapsed = next
                        // A restore (setSelectionFromTop) or a refresh calls this inside the list's layout pass;
                        // hiding a view there leaves its space behind (D3), so the change runs after that pass.
                        list.removeCallbacks(applyCollapse)
                        if (list.isInLayout) list.post(applyCollapse) else applyCollapse.run()
                    }
                }
            })
        }
        list.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL) list.post { refreshRows() }
        }
        sourcesScroll.addView(sourcesBox, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sourcesScroll.setOnScrollChangeListener { _, _, y, _, _ ->
            snapshot?.takeIf { it.showingSources && !restoringSourceScroll && it.browseLoadState == MeloBrowseUiPolicy.LoadState.READY }?.let {
                host.saveBrowsePosition(it.browseGeneration, null, 0, y)
            }
        }
        listFrame.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        listFrame.addView(bar, FrameLayout.LayoutParams(bar.barWidthPx, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        val bubbleSize = (72 * context.resources.displayMetrics.density).toInt()
        listFrame.addView(bubble, FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.CENTER))
        listStatus.addView(statusText)
        listStatus.addView(retry)
        listContent.addView(listStatus)
        listContent.addView(listFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(listContent, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (layer == NewLayer.TWO_PANE) {
            sourceList.adapter = sourceAdapter
            sourceList.divider = null
            sourceList.visibility = View.GONE
            sourceList.setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, state: Int) = Unit
                override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {
                    val snap = snapshot ?: return
                    if (snap.showingSources && !restoringSourceScroll && snap.browseLoadState == MeloBrowseUiPolicy.LoadState.READY) {
                        host.saveSourcePosition(snap.browseGeneration, sourceItemId(first), sourceList.getChildAt(0)?.top ?: 0)
                    }
                }
            })
            view.addView(sourceList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        } else view.addView(sourcesScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** A pending anchor survives a row refresh; without this the list re-syncs to the row it showed before. */
    private fun refreshRows() {
        adapter.notifyDataSetChanged()
        songRestore.afterRefresh()?.let { list.setSelectionFromTop(it.index, it.offsetPx) }
    }

    /** R1b/R1c list-anchor diagnostics: Debug builds only, never written by a Release build (D1). */
    private inline fun anchorLog(message: () -> String) {
        if (com.bsdung.musicassistantcar.BuildConfig.DEBUG) android.util.Log.w("MeloAnchor", message())
    }

    private fun endSongRestore() {
        songRestore.cancel()
        restoringSongScroll = false
        list.viewTreeObserver.removeOnGlobalLayoutListener(restoreLayoutCheck)
    }

    /**
     * Checked after every real layout while an anchor is pending: something between the request and the
     * layout (a key event leaving touch mode, a refresh) can still put the old first row back.
     */
    private var restoreAttempts = 0
    private val maxRestoreAttempts = 5
    private val restoreLayoutCheck = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val wanted = songRestore.pending ?: return@OnGlobalLayoutListener
        val first = list.firstVisiblePosition
        anchorLog { "stage=layout layer=$layer first=$first want=${wanted.index} children=${list.childCount} " +
            "touch=${list.isInTouchMode} selected=${list.selectedItemPosition} attempt=$restoreAttempts" }
        if (songRestore.onScrolled(first, list.childCount, adapter.count)) {
            endSongRestore()
            anchorLog { "stage=settled layer=$layer first=$first" }
        } else if (list.childCount > 0) {
            if (restoreAttempts++ < maxRestoreAttempts) list.post { songRestore.pending?.let { list.setSelectionFromTop(it.index, it.offsetPx) } }
            else { endSongRestore(); anchorLog { "stage=gave_up layer=$layer first=$first" } }
        }
    }

    private fun startSongRestore(index: Int, offsetPx: Int): MeloBrowseState.SongRestore.Selection {
        restoreAttempts = 0
        list.viewTreeObserver.removeOnGlobalLayoutListener(restoreLayoutCheck)
        list.viewTreeObserver.addOnGlobalLayoutListener(restoreLayoutCheck)
        return songRestore.start(index, offsetPx)
    }

    fun detach() {
        playFeedbackView.stop()
        flushAndSave()
        endSongRestore()
        list.removeCallbacks(applyCollapse)
        host.setBrowseCapture(null)
        if (rowArtEnabled) host.setRowArtListener(null)
        pendingJump?.let(handler::removeCallbacks)
        pendingQuery?.let(handler::removeCallbacks)
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * A window-size change that needs another screen type tears this pane down; hand the first
     * visible song to the host so the next pane scrolls back to it (E95 F03d).
     */
    private fun saveScrollAnchor() {
        val snap = snapshot ?: return
        val key = groupKey ?: return
        if (snap.browseLoadState != MeloBrowseUiPolicy.LoadState.READY) return
        if (snap.showingSources) {
            if (layer == NewLayer.TWO_PANE) {
                host.saveSourcePosition(snap.browseGeneration, sourceItemId(sourceList.firstVisiblePosition), sourceList.getChildAt(0)?.top ?: 0)
            } else host.saveBrowsePosition(snap.browseGeneration, null, 0, sourcesScroll.scrollY)
            return
        }
        if (restoringAnchor != null || restoringSongScroll || snap.rows.isEmpty() || listFrame.visibility != View.VISIBLE) {
            anchorLog { "stage=capture_skip layer=$layer restoring=${restoringAnchor != null}/$restoringSongScroll rows=${snap.rows.size}" }
            return
        }
        val first = list.firstVisiblePosition
        val row = snap.rows.getOrNull(first) ?: return
        anchorLog { "stage=capture layer=$layer first=$first key=${row.songKey} top=${list.getChildAt(0)?.top} gen=${snap.browseGeneration}" }
        host.saveListAnchor(key, row.songKey, list.getChildAt(0)?.top ?: 0)
        host.saveBrowsePosition(snap.browseGeneration, row.songKey, list.getChildAt(0)?.top ?: 0, 0)
    }

    private fun flushPendingQuery(): Boolean {
        pendingQuery?.let(handler::removeCallbacks)
        pendingQuery = null
        val query = queryDraft.take() ?: return false
        host.setQuery(query)
        return true
    }

    private fun flushAndSave() { if (!flushPendingQuery()) saveScrollAnchor() }

    /** Back at the top with the owner's title row visible, e.g. after switching group or page. */
    private fun resetScroll() {
        if (collapsed) {
            collapsed = false
            onHeaderCollapse(false)
            applySourceCardsVisibility()
        }
        lastScrollKey = 0
        pendingTopReset = true
        list.setSelectionFromTop(0, 0)
        sourcesScroll.scrollTo(0, 0)
    }

    fun onLibraryChanged() {
        playFeedbackView.refresh()
        val snap = host.snapshot()
        snapshot = snap
        val pendingPlay = playAllWhenLoaded?.let { MeloLibraryPolicy.pendingBrowsePlay(it, snap) }
        if (pendingPlay == MeloLibraryPolicy.BrowsePlayDecision.CANCEL) playAllWhenLoaded = null
        val browseKey = snap.browseOwnerId.takeIf { it > 0L }?.let { MeloBrowseState.Key(it, snap.browseScope) }
        val key = "${snap.browseOwnerId}|${snap.browseScope}|${snap.query}|${snap.sort}|${snap.letterFloor}"
        val contextChanged = key != groupKey
        if (contextChanged) {
            groupKey = key
            endSongRestore()
            val state = host.browseContext()
            restoringAnchor = if (browseKey != null && state?.anchorSongKey != null) {
                MeloBrowseState.Pending(browseKey, snap.browseGeneration, state)
            } else null
            anchorLog { "stage=context layer=$layer anchor=${state?.anchorSongKey} gen=${snap.browseGeneration} rows=${snap.rows.size}" }
            if (restoringAnchor == null) resetScroll() else pendingTopReset = false
        }
        if (contextChanged || browseKey != lastBrowseKey || (pendingQuery == null && snap.query != lastQuery)) {
            // The search box belongs to one source: show that source's own query when switching.
            lastBrowseKey = browseKey
            pendingQuery?.let(handler::removeCallbacks)
            pendingQuery = null
            lastQuery = snap.query
            if (searchField.text.toString() != snap.query) searchField.setText(snap.query)
        }
        val presentation = MeloBrowseUiPolicy.present(
            snap.browseScope, snap.sourceTitle, snap.browseLoadState,
            if (snap.showingSources) snap.sources.count { it.chatId != 0L } else snap.rows.size, snap.query
        )
        searchField.hint = presentation.hint
        scopeNote.text = presentation.scopeLabel
        statusText.text = presentation.message.orEmpty()
        retry.visibility = if (presentation.retry) View.VISIBLE else View.GONE
        listStatus.visibility = if (!snap.showingSources && presentation.message != null) View.VISIBLE else View.GONE
        listStatus.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            if (snap.rows.isEmpty()) 0 else ViewGroup.LayoutParams.WRAP_CONTENT,
            if (snap.rows.isEmpty()) 1f else 0f)
        renderChips(snap)
        renderSourceCards(snap)
        header.post { fitHeader() }
        if (snap.showingSources) {
            groupTitle.text = if (snap.browseLoadState == MeloBrowseUiPolicy.LoadState.READY) {
                "Telegram · ${MeloComponentPolicy.formatCount(snap.sources.count { it.chatId != 0L })} nguồn"
            } else "Telegram"
            playAll.visibility = View.GONE
            sortChip.visibility = View.GONE
            note.visibility = View.GONE
            listContent.visibility = View.GONE
            sourcesScroll.visibility = if (layer == NewLayer.TWO_PANE) View.GONE else View.VISIBLE
            sourceList.visibility = if (layer == NewLayer.TWO_PANE) View.VISIBLE else View.GONE
            if (layer == NewLayer.TWO_PANE) {
                renderVirtualSources(snap)
            } else {
                val sourceY = host.browseContext()?.sourceScrollY ?: 0
                restoringSourceScroll = true
                renderSources(snap)
                sourcesScroll.post {
                    if (groupKey == key && snapshot?.browseGeneration == snap.browseGeneration) {
                        sourcesScroll.scrollTo(0, sourceY)
                        restoringSourceScroll = false
                    }
                }
            }
        } else {
            groupTitle.text = MeloCompactPolicy.headerTitle(snap.mode, snap.sourceTitle, snap.total)
            playAll.visibility = View.VISIBLE
            playAll.isEnabled = MeloLibraryPolicy.playAllEnabled(snap.rows.size)
            playAll.alpha = if (playAll.isEnabled) 1f else 0.4f
            val detailNote = snap.note.takeIf { !snap.local || snap.browseLoadState == MeloBrowseUiPolicy.LoadState.READY }
            note.text = detailNote.orEmpty()
            note.visibility = if (detailNote.isNullOrBlank()) View.GONE else View.VISIBLE
            sourcesScroll.visibility = View.GONE
            sourceList.visibility = View.GONE
            listContent.visibility = View.VISIBLE
            listFrame.visibility = if (snap.rows.isEmpty()) View.GONE else View.VISIBLE
            // E98: on the teal theme the sort lives in the filter panel, so the chip goes.
            sortChip.visibility = if (c.teal) View.GONE else View.VISIBLE
            sortChip.text = if (snap.sort == SongSort.NAME) "↕ Tên A–Z" else "↕ Mới nhất"
            val rowCount = snap.total ?: snap.rows.size
            val showBar = if (c.teal) MeloAlphabetPolicy.barVisibleThemed(false, rowCount, snap.letterFloor != null)
            else MeloAlphabetPolicy.barVisible(snap.sort, false, rowCount, snap.letterFloor != null)
            bar.visibility = if (showBar) View.VISIBLE else View.GONE
            list.setPadding(0, 0, if (showBar) bar.barWidthPx else 0, 0)
            if (!showBar) bar.setActive(null)
            refreshRows()
            if (rowArtEnabled) {
                lastArtFirst = -1
                list.post { requestVisibleArt() }
            }
            val restore = restoringAnchor
            if (restore != null && browseKey != null) {
                val current = restore.copy(generation = snap.browseGeneration)
                restoringAnchor = current
                val decision = MeloBrowseState.locate(current, browseKey, snap.browseGeneration,
                    snap.query, snap.sort, snap.letterFloor, snap.rows.map { it.songKey }, snap.browseComplete)
                anchorLog { "stage=locate layer=$layer decision=${decision.javaClass.simpleName} " +
                    "rows=${snap.rows.size} complete=${snap.browseComplete} gen=${snap.browseGeneration} " +
                    "sortOk=${current.context.sort == snap.sort} floorOk=${current.context.letterFloor == snap.letterFloor} queryOk=${current.context.query == snap.query}" }
                when (decision) {
                    is MeloBrowseState.Anchor.Found -> {
                        restoringAnchor = null
                        restoringSongScroll = true
                        val selection = startSongRestore(decision.index, decision.offsetPx)
                        list.post {
                            if (songRestore.pending != selection) return@post
                            if (groupKey == key && snapshot?.browseGeneration == snap.browseGeneration) {
                                list.setSelectionFromTop(selection.index, selection.offsetPx)
                                anchorLog { "stage=apply layer=$layer index=${selection.index}" }
                            } else {
                                // Rows were re-queried before the selection ran: locate the same song again.
                                endSongRestore()
                                if (groupKey == key) restoringAnchor = current
                                anchorLog { "stage=apply_requeue layer=$layer same=${groupKey == key}" }
                            }
                        }
                    }
                    MeloBrowseState.Anchor.Drop -> { restoringAnchor = null; pendingTopReset = true }
                    MeloBrowseState.Anchor.Wait -> Unit
                }
            }
            if (MeloLibraryPolicy.shouldResetTop(pendingTopReset, snap.showingSources, snap.rows.size)) {
                // The rows of the new group just arrived; the list still remembers the old group's offset.
                list.post {
                    if (groupKey == key && snapshot?.browseGeneration == snap.browseGeneration) {
                        list.setSelectionFromTop(0, 0)
                        pendingTopReset = false
                    }
                }
            }
            if (pendingPlay == MeloLibraryPolicy.BrowsePlayDecision.PLAY) {
                playAllWhenLoaded = null
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
    fun onPlaybackChanged(playingKey: Long?, paused: Boolean = false, playing: Boolean = false, buffering: Boolean = false) {
        val shown = snapshot ?: return
        if (MeloCompactPolicy.rowsStale(shown.playingKey, shown.paused, playingKey, paused) || shown.playing != playing || shown.buffering != buffering) onLibraryChanged()
    }

    private fun onBarLetter(letter: Char, final: Boolean) {
        pendingJump?.let(handler::removeCallbacks)
        val task = Runnable {
            // E98: a letter only means something in name order, so "Mới nhất" is switched first, once, with a notice.
            if (c.teal && MeloAlphabetPolicy.needsSortSwitch(host.sort())) {
                host.setSort(SongSort.NAME)
                android.widget.Toast.makeText(context, "Đã sắp xếp theo tên A–Z", android.widget.Toast.LENGTH_SHORT).show()
            }
            val index = host.jumpToLetter(MeloAlphabetPolicy.lowerBound(letter))
            bar.setActive(letter)
            if (index != null) {
                endSongRestore()
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
        queryDraft.edit(query)
        val task = Runnable { flushPendingQuery() }
        pendingQuery = task
        handler.postDelayed(task, 250L)
    }

    // ---- E98 teal theme: four menu tabs, genre chip, filter panel -------------------------------------

    private fun renderThemeTabs(snap: Snapshot) {
        renderGenreChip(snap)
        if (snap.local) {
            chipHolder.visibility = View.GONE
            return
        }
        chipHolder.visibility = View.VISIBLE
        val density = context.resources.displayMetrics.density
        val widthDp = (view.width / density).toInt()
        val total = host.sourceSummaries().firstOrNull { it.kind == MeloSourceCardPolicy.Kind.TELEGRAM }?.total
        val tabs = MeloLibraryPolicy.themeTabs(total, widthDp, snap.mode, snap.showingSources, snap.browseScope)
        val narrow = widthDp in 1 until MeloLibraryPolicy.NARROW_TABS_DP
        val key = tabs.joinToString("|") { "${it.label}:${it.selected}" } + "#$narrow"
        if (key == chipKey) return
        chipKey = key
        chipHolder.removeAllViews()
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.forEachIndexed { i, tab ->
            val icon = when (tab.kind) {
                MeloLibraryPolicy.TabKind.ALL -> R.drawable.ic_library_music
                MeloLibraryPolicy.TabKind.DOWNLOADED -> R.drawable.ic_download
                MeloLibraryPolicy.TabKind.PLAYLIST -> R.drawable.ic_playlist
                MeloLibraryPolicy.TabKind.SOURCES -> R.drawable.ic_send
            }
            row.addView(c.tabCard(icon, tab.label, tab.selected, narrow) { onThemeTab(tab.kind) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i < tabs.lastIndex) marginEnd = s.gap
                })
        }
        chipHolder.addView(row)
    }

    private fun onThemeTab(kind: MeloLibraryPolicy.TabKind) {
        host.openBrowseScope(MeloLibraryPolicy.tabScope(kind))
    }

    private fun clearSearch() = applyGenre(null)

    /** "Chillout ✕" under the search box while a genre filter is on; tapping it removes the filter. */
    private fun renderGenreChip(snap: Snapshot) {
        val genre = if (snap.showingSources || snap.local) null else MeloLibraryPolicy.activeGenre(snap.query)
        genreHolder.removeAllViews()
        if (genre == null) {
            genreHolder.visibility = View.GONE
            return
        }
        genreHolder.visibility = View.VISIBLE
        genreHolder.addView(c.chip("${genre.label} ✕", true) { applyGenre(null) }.apply {
            contentDescription = "Bỏ lọc ${genre.label}"
        })
    }

    /** Sets (or clears, when null) the genre keyword exactly like tapping a genre chip did. */
    private fun applyGenre(genre: MeloLibraryPolicy.GenreChip?) {
        val query = genre?.keyword.orEmpty()
        lastQuery = query
        if (searchField.text.toString() != query) searchField.setText(query)
        host.setQuery(query)
    }

    /** The ⚙-like button of the search box: sort order and genre, applied at once. */
    private fun openFilterDialog() {
        val snap = snapshot ?: return
        lateinit var dialog: AlertDialog
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.gap * 2, s.gap * 2, s.gap * 2, s.gap)
        }
        fun chips(build: LinearLayout.() -> Unit): View = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; build() })
        }
        box.addView(c.text("Sắp xếp", s.titleText, p.text, bold = true).apply { setPadding(0, 0, 0, s.gap) })
        box.addView(chips {
            listOf(SongSort.NEWEST to "Mới nhất", SongSort.NAME to "Tên A–Z").forEach { (sort, label) ->
                addView(c.chip(label, host.sort() == sort) { host.setSort(sort); dialog.dismiss() })
            }
        })
        if (!snap.local) {
            val active = MeloLibraryPolicy.activeGenre(snap.query)
            box.addView(c.text("Thể loại", s.titleText, p.text, bold = true).apply { setPadding(0, s.gap * 2, 0, s.gap) })
            box.addView(chips {
                addView(c.chip("Tất cả", active == null) { applyGenre(null); dialog.dismiss() })
                MeloLibraryPolicy.GenreChip.entries.forEach { genre ->
                    addView(c.chip(genre.label, active == genre) { applyGenre(genre); dialog.dismiss() })
                }
            })
        }
        dialog = AlertDialog.Builder(context, R.style.Theme_MeloVault_Dialog)
            .setTitle("Bộ lọc")
            .setView(box)
            .setNegativeButton("Đóng", null)
            .create()
        dialog.show()
    }

    private fun renderChips(snap: Snapshot) {
        if (c.teal) {
            renderThemeTabs(snap)
            return
        }
        if (snap.local) {
            chipHolder.visibility = View.GONE
            return
        }
        chipHolder.visibility = View.VISIBLE
        val tabs = MeloLibraryPolicy.themeTabs(null, 1, snap.mode, snap.showingSources, snap.browseScope)
        val key = "${snap.browseScope}|${snap.query}"
        if (key == chipKey) return
        chipKey = key
        chipHolder.removeAllViews()
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun strip(build: LinearLayout.() -> Unit) = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; build() })
        }
        column.addView(strip {
            tabs.forEach { tab ->
                addView(c.chip(if (tab.kind == MeloLibraryPolicy.TabKind.ALL) "Thư viện" else tab.label,
                    tab.selected) { onThemeTab(tab.kind) })
            }
        })
        if (!snap.showingSources) column.addView(strip {
            if (snap.browseScope.chatId != null) {
                addView(c.chip("Tất cả trong kênh", snap.mode == Mode.ALL) { host.filterInSource(Mode.ALL) })
                addView(c.chip("Đã tải trong kênh", snap.mode == Mode.DOWNLOADED) { host.filterInSource(Mode.DOWNLOADED) })
            }
            MeloLibraryPolicy.GenreChip.entries.forEach { genre ->
                addView(c.chip(genre.label, MeloLibraryPolicy.activeGenre(snap.query) == genre) { applyGenre(genre) })
            }
        })
        chipHolder.addView(column)
    }

    // ---- Source cards -------------------------------------------------------------------------

    private fun renderSourceCards(snap: Snapshot) {
        val selected = MeloSourceCardPolicy.selectedKind(snap.selectedChatId, snap.showingSources, snap.local)
        val channelOpen = snap.selectedChatId != null && !snap.showingSources && !snap.local
        val cards = MeloSourceCardPolicy.cards(host.sourceSummaries(), selected, channelOpen)
        sourceRowVisible = MeloSourceCardPolicy.rowVisible(
            layer, snap.showingSources, snap.mode, context.resources.configuration.screenHeightDp, cards.size
        )
        applySourceCardsVisibility()
        if (!sourceRowVisible) return
        val key = cards.joinToString("|") { "${it.kind}:${it.detail}:${it.selected}" }
        if (key == sourceCardsKey) return
        sourceCardsKey = key
        sourceCards = cards
        buildSourceCards()
    }

    private fun applySourceCardsVisibility() {
        sourceCardsHolder.visibility = if (sourceRowVisible && !collapsed) View.VISIBLE else View.GONE
    }

    /** Needs the pane width, so it also runs again once the holder has been laid out or resized. */
    private fun buildSourceCards() {
        val width = sourceCardsHolder.width
        if (width <= 0 || sourceCards.isEmpty()) return
        val density = context.resources.displayMetrics.density
        val minCard = ((if (s.profile == MeloDesignTokens.Profile.TABLET) 240 else 200) * density).toInt()
        val cardWidth = MeloSourceCardPolicy.cardWidthPx(width, sourceCards.size, minCard, s.gap)
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        sourceCards.forEachIndexed { i, card ->
            strip.addView(c.sourceCard(card, (88 * density).toInt()) {
                if (!card.selected) when (card.kind) {
                    MeloSourceCardPolicy.Kind.TELEGRAM -> host.openSource(null)
                    MeloSourceCardPolicy.Kind.LOCAL -> host.openLocal()
                    MeloSourceCardPolicy.Kind.DRIVE -> Unit
                }
            }, LinearLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                if (i < sourceCards.size - 1) marginEnd = s.gap
            })
        }
        sourceCardsHolder.removeAllViews()
        sourceCardsHolder.addView(strip)
    }

    private fun onChip(chip: MeloLibraryPolicy.Chip) {
        val query = MeloLibraryPolicy.queryFor(chip)
        if (chip.mode != null) { host.filterInSource(chip.mode); return }
        lastQuery = query
        if (searchField.text.toString() != query) searchField.setText(query)
        host.setQuery(query)
    }

    // ---- Song list ------------------------------------------------------------------------------

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount(): Int = snapshot?.takeIf { !it.showingSources }?.rows?.size ?: 0
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = snapshot?.rows?.getOrNull(position)?.songKey ?: position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val snap = snapshot ?: return View(context)
            val row = snap.rows.getOrNull(position) ?: return View(context)
            val playing = row.songKey == snap.playingKey
            val status = if (TelegramPlayFeedbackPolicy.opening(host.playStatus(), row.songKey, playing)) {
                MeloComponentPolicy.Status.OPENING
            } else {
                MeloNowPlayingPolicy.queueStatus(playing, row.downloaded, row.downloadingPercent, snap.paused, snap.buffering)
            }
            val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(row.mimeType, row.filename), null, null, null)
            durationPaint.textSize = s.secondaryText
            val durationWidth = kotlin.math.ceil(durationPaint.measureText(TelegramSongDisplayPolicy.formatDuration(row.durationSeconds).orEmpty()).toDouble()).toInt()
            val metrics = context.resources.displayMetrics
            val directSave = !row.local && !snap.local && MeloSavePolicy.directRowSave(
                (parent?.width ?: list.width) - list.paddingLeft - list.paddingRight, s.rowArt,
                s.gap * 5 + if (c.teal) (s.secondaryText * 1.5f).toInt() + s.gap else 0,
                durationWidth, s.minTouch, metrics.density, metrics.scaledDensity / metrics.density)
            fun index(): Int? {
                val current = host.snapshot()
                if (current.browseOwnerId != snap.browseOwnerId) return null
                return current.rows.indexOfFirst { it.songKey == row.songKey }.takeIf { it >= 0 }
            }
            return c.songRow(
                SongRowModel(
                    row.title, row.sourceTitle, row.durationSeconds, status, quality, row.downloadingPercent,
                    if (rowArtEnabled) MeloRowArtPolicy.rowPicture(host.peekRowArt(row.artKey), row.art) else row.art,
                    local = snap.local || row.local,
                    artKey = if (rowArtEnabled) row.artKey else null,
                    browseMetadata = MeloBrowseUiPolicy.rowMetadata(row.performer, row.sourceTitle,
                        snap.local || row.local, row.sourceIsFolder).orEmpty(),
                    statusTextOverride = (row.download ?: if (snap.local || row.local)
                        MeloDownloadUiPolicy.Presentation(MeloDownloadUiPolicy.State.READY) else null)?.let { MeloDownloadUiPolicy.rowText(it, playing,
                        snap.playing, snap.paused, snap.buffering,
                        opening = status == MeloComponentPolicy.Status.OPENING,
                        local = snap.local || row.local, quality = quality) },
                    rowSave = if (directSave) MeloSavePolicy.rowButtonState(row.title, row.saveAvailability, row.saved, row.saveBusy) else null
                ),
                onClick = { index()?.let(host::playAt) },
                onLongClick = { index()?.let { host.openSongMenu(it, list) } },
                onMore = { index()?.let { host.openSongMenu(it, list) } },
                onSave = { if (index() != null) host.toggleRowFavorite(row.songKey) }
            ).also {
                unclickable(it) { anchor -> index()?.let { position -> host.openSongMenu(position, anchor) } }
                it.isClickable = true; it.isLongClickable = true; it.isFocusable = true
                it.setOnLongClickListener { anchor -> index()?.let { position -> host.openSongMenu(position, anchor) }; true }
                (it as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            }
        }
    }

    // ---- E98/P2-A row covers (Phone/Tablet, any colour theme) ------------------------------------------

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
            val artHost = findRowArtFrame(list.getChildAt(i)) ?: continue
            val key = (artHost.tag as MeloArtTag).key
            shown++
            when (c.applyRowArt(artHost, host.peekRowArt(key), animate = true)) {
                MeloRowArtPolicy.ArtChange.APPEAR -> appeared++
                MeloRowArtPolicy.ArtChange.SWAP -> swapped++
                MeloRowArtPolicy.ArtChange.NONE -> Unit
            }
            if (c.rowArtShowing(artHost)) withArt++
            if (host.rowArtSettled(key)) settled++
        }
        android.util.Log.i("MeloRowArt", "visible=$shown withArt=$withArt settled=$settled appeared=$appeared swapped=$swapped")
    }

    /**
     * Rows are driven by the ListView (tap = play, long press = menu), so the row must not
     * swallow clicks. Only the ⋮ button stays clickable and opens the same song menu.
     */
    private fun unclickable(v: View, onMore: (View) -> Unit) {
        if (v.tag == MELO_SONG_ROW_MORE_TAG || v.tag == MELO_SONG_ROW_SAVE_TAG) {
            if (v.tag == MELO_SONG_ROW_MORE_TAG) v.setOnClickListener { onMore(v) }
            v.isFocusable = true
            return
        }
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) unclickable(v.getChildAt(i), onMore)
    }

    // ---- Telegram sources ---------------------------------------------------------------------

    private fun sourceItemId(index: Int): Long? = when (val item = sourceItems.getOrNull(index)) {
        is SourceItem.Channel -> item.row.chatId
        is SourceItem.EmptyToggle -> 0L
        else -> null
    }

    /** Two-pane sources are recycled rows, rather than one View per channel. */
    private fun renderVirtualSources(snap: Snapshot) {
        val saved = host.browseContext()
        restoringSourceScroll = true
        val positive = MeloLibraryPolicy.quickSwitchRows(snap.sources)
        val zeros = snap.sources.filter { it.chatId != 0L && it.count == 0 }
        sourceItems = buildList {
            add(SourceItem.Header)
            positive.forEach { add(SourceItem.Channel(it)) }
            if (zeros.isNotEmpty()) {
                add(SourceItem.EmptyToggle(zeros.size))
                if (emptyExpanded) zeros.forEach { add(SourceItem.Channel(it)) }
            }
        }
        sourceAdapter.notifyDataSetChanged()
        val index = sourceItems.indices.firstOrNull { sourceItemId(it) == saved?.sourceAnchorChatId } ?: 0
        sourceList.post {
            if (snapshot?.browseGeneration == snap.browseGeneration && snapshot?.showingSources == true) {
                sourceList.setSelectionFromTop(index, saved?.sourceOffsetPx ?: 0)
                restoringSourceScroll = false
            }
        }
    }

    private inner class SourceAdapter : BaseAdapter() {
        override fun getCount() = sourceItems.size
        override fun getItem(position: Int): Any = sourceItems[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 3
        override fun getItemViewType(position: Int) = when (sourceItems[position]) {
            SourceItem.Header -> 0
            is SourceItem.Channel -> 1
            is SourceItem.EmptyToggle -> 2
        }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return when (val item = sourceItems[position]) {
                SourceItem.Header -> buildSourceHeader(snapshot ?: return View(context))
                is SourceItem.EmptyToggle -> c.text(MeloLibraryPolicy.emptySourcesLabel(item.count, emptyExpanded).orEmpty(),
                    s.titleText, p.text, bold = true, maxLines = 2).apply {
                    minimumHeight = s.minTouch
                    gravity = Gravity.CENTER_VERTICAL
                    setOnClickListener { emptyExpanded = !emptyExpanded; snapshot?.let(::renderVirtualSources) }
                }
                is SourceItem.Channel -> {
                    val holder = convertView?.tag as? SourceHolder ?: run {
                        val title = c.text("", s.titleText, p.text, maxLines = 2)
                        val detail = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
                        val root = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            minimumHeight = s.minTouch
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
                            addView(title); addView(detail)
                        }
                        SourceHolder(root, title, detail).also { root.tag = it }
                    }
                    val facts = MeloLibraryPolicy.sourcePresentation(item.row, host.selectedBrowseSource())
                    holder.title.text = (if (facts.selected) "✓ " else "") + item.row.title
                    holder.detail.text = facts.detail
                    holder.root.isSelected = facts.selected
                    holder.root.background = c.roundedBackground(if (facts.selected) p.accentSoft else p.surface)
                    holder.root.contentDescription = item.row.title + ", " + facts.detail
                    holder.root.setOnClickListener { host.openSource(item.row.chatId) }
                    holder.root
                }
            }
        }
    }

    private fun buildSourceHeader(snap: Snapshot): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val presentation = MeloBrowseUiPolicy.present(snap.browseScope, snap.sourceTitle, snap.browseLoadState,
            snap.sources.count { it.chatId != 0L }, snap.query)
        presentation.message?.let { addView(c.text(it, s.secondaryText, p.textSecondary, maxLines = 4)) }
        if (presentation.retry) addView(c.chip("Thử lại", false) { host.retryBrowse() })
        if (snap.sources.any { it.count < 0 }) addView(c.text("Đang cập nhật số bài của nguồn…", s.secondaryText, p.textSecondary))
        addView(heroCard(snap.sources.firstOrNull { it.chatId == 0L }?.count ?: snap.total))
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(c.chip("‹ Thư viện", false) { host.closeSources() })
                addView(c.chip(if (host.hideZeroSources()) "Hiện kênh 0 bài" else "Ẩn kênh 0 bài", host.hideZeroSources()) { host.toggleHideZeroSources() })
                addView(c.chip("Quét lại", false) { host.rescan() })
            })
        })
    }

    private fun renderSources(snap: Snapshot) {
        sourcesBox.removeAllViews()
        val presentation = MeloBrowseUiPolicy.present(snap.browseScope, snap.sourceTitle, snap.browseLoadState,
            snap.sources.count { it.chatId != 0L }, snap.query)
        presentation.message?.let { message ->
            sourcesBox.addView(c.text(message, s.secondaryText, p.textSecondary, maxLines = 4))
        }
        if (presentation.retry) sourcesBox.addView(c.chip("Thử lại", false) { host.retryBrowse() })
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
            total?.takeIf { it >= 0 }?.let { "${MeloComponentPolicy.formatCount(it)} bài" },
            downloaded?.let { "${MeloComponentPolicy.formatCount(it)} đã tải" }
        ).joinToString(" · ")
        if (detail.isNotEmpty()) addView(c.text(detail, s.secondaryText, p.textSecondary).apply { setPadding(0, s.gap / 2, 0, s.gap) })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(c.chip("Mở tất cả", false) { host.openBrowseScope(MeloLibraryPolicy.tabScope(MeloLibraryPolicy.TabKind.ALL)) })
        buttons.addView(c.chip("⇄ Phát ngẫu nhiên", true) {
            host.openBrowseScope(MeloLibraryPolicy.tabScope(MeloLibraryPolicy.TabKind.ALL))
            val target = host.snapshot()
            playAllWhenLoaded = target.browseOwnerId.takeIf { it > 0L }?.let {
                MeloBrowseUiPolicy.Request(it, target.browseGeneration, target.browseScope)
            }
            onLibraryChanged()
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

/** Inline, attempt-bound feedback. The timer only enables retry; it never invokes playback. */
internal class MeloPlayFeedbackView(context: Context, c: MeloComponents, private val host: MeloScreenHost) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val message = c.text("", c.sizes.secondaryText, c.palette.textSecondary, maxLines = 3)
    private var bound: MeloPlayUiState.Attempt? = null
    private val retry = c.chip("Thử lại", false) { bound?.let(host::retryPlay) }
    private var attached = false
    private val ticker = MeloPlayUiState.CountdownTicker({ job, delay -> handler.postDelayed(job, delay) }, handler::removeCallbacks, ::refresh)

    init {
        orientation = VERTICAL
        visibility = View.GONE
        addView(message, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(retry, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    fun refresh() {
        ticker.stop()
        val feedback = host.playUiFeedback()
        visibility = if (feedback == null) View.GONE else View.VISIBLE
        bound = feedback?.attempt
        if (feedback == null) return
        message.text = (if (feedback.phase == MeloPlayUiState.Phase.ERROR) "Bài vừa chọn: " else "") + feedback.text
        retry.visibility = if (feedback.showRetry) View.VISIBLE else View.GONE
        retry.isEnabled = feedback.retryEnabled
        retry.alpha = if (feedback.retryEnabled) 1f else 0.45f
        retry.contentDescription = if (feedback.retryEnabled) "Thử mở lại bài vừa chọn" else "Chờ Telegram cho phép thử lại"
        ticker.update(attached, isShown && windowVisibility == View.VISIBLE, hasWindowFocus(), feedback.remainingSeconds ?: 0L)
    }
    fun stop() { attached = false; ticker.stop(); handler.removeCallbacksAndMessages(null) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); attached = true; refresh() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!attached) return
        if (hasWindowFocus) refresh() else ticker.stop()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!attached) return
        if (visibility == View.VISIBLE) refresh() else ticker.stop()
    }
}
