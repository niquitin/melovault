package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying

/**
 * Hai khung ngang (docs/ui-spec.md step 7): navigation column (80dp), player pane (~40%)
 * and library pane (~60%). The player pane scrolls when the window is short (a phone turned
 * sideways is only about 384dp tall).
 */
internal class MeloTabletScreen(
    private val context: Context,
    sizes: Sizes,
    private val host: MeloScreenHost,
    private val backDispatcher: OnBackPressedDispatcher
) {
    private val c = MeloComponents(context, sizes)
    private val s = c.sizes
    private val p = c.palette

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(p.background)
        setPadding(0, s.gap, s.screenMargin, s.gap)
    }

    private val navColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val navItems = mutableMapOf<NavItem, LinearLayout>()
    private val syncLine = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val pane = MeloLibraryPane(context, c, host, NewLayer.TWO_PANE)

    // Player pane parts.
    private val artHost = FrameLayout(context)
    private val title = c.text("Chọn một bài để phát", s.playerTitleText, p.text, bold = true, maxLines = 2)
    private val source = c.text("", s.titleText, p.textSecondary, maxLines = 1)
    private val quality = c.text("", s.secondaryText, p.accent, maxLines = 1)
    private val time = c.text("", s.secondaryText, p.textSecondary)
    private lateinit var play: TextView
    private lateinit var shuffle: TextView
    private lateinit var repeat: TextView
    private lateinit var favorite: TextView
    private lateinit var queueChip: TextView
    private lateinit var playerScroll: ScrollView
    private lateinit var playerColumn: LinearLayout
    private val extrasBox = LinearLayout(context)
    private val transportRow = LinearLayout(context)
    private var playerTight = false
    private val extrasRow1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val extrasRow2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private var extrasStacked = false
    private var fitPosted = false
    // Left slot: the big player, or the channel list + mini player while one channel is open.
    private val leftSlot = FrameLayout(context)
    private lateinit var playerPane: View
    private val quickPane = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val quickList = ListView(context)
    private val quickAdapter = QuickAdapter()
    private var quickRows: List<MeloScreenHost.SourceRow> = emptyList()
    private var quickOpenId: Long? = null
    private var quickShown = false
    private var quickScrolledId: Long? = null
    private val miniArtHost = FrameLayout(context)
    private val miniTitle = c.text("Chọn một bài để phát", s.titleText, p.text, bold = true, maxLines = 2)
    /** Cover of the mini player: the 48dp touch size, not the taller list-row cover. */
    private val miniArtPx = s.minTouch
    private lateinit var miniPlay: TextView
    private var miniArtKey: Any? = null
    private var snapshot: MeloScreenHost.Snapshot? = null
    private var lastArt: Bitmap? = null
    private var artKey: Any? = null
    private var artSide = 0

    private enum class NavItem(val icon: String, val label: String) {
        LIBRARY("♫", "Thư viện"), TELEGRAM("☷", "Telegram"), PLAYLIST("☆", "Playlist"), NOW("▶", "Đang phát"), SETTINGS("⚙", "Cài đặt")
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val snap = snapshot
            if (snap != null && snap.local) host.closeLocal()
            else if (snap != null && !snap.showingSources && snap.sourceTitle != null) host.showSources()
            else { isEnabled = false; backDispatcher.onBackPressed() }
        }
    }

    init {
        buildNav()
        view.addView(navColumn, LinearLayout.LayoutParams(s.minTouch * 80 / 48, ViewGroup.LayoutParams.MATCH_PARENT))
        playerPane = buildPlayerPane()
        leftSlot.addView(playerPane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        leftSlot.addView(buildQuickPane(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(leftSlot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 40f).apply { marginEnd = s.screenMargin })
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        right.addView(syncLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap
        })
        right.addView(pane.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 60f))
    }

    fun attach() {
        backDispatcher.addCallback(backCallback)
        onLibraryChanged()
        onPlaybackChanged()
    }

    fun detach() {
        backCallback.remove()
        pane.detach()
    }

    // ---- Navigation column -------------------------------------------------------------------

    private fun buildNav() {
        NavItem.entries.forEach { item ->
            val button = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = item.label
                addView(c.text(item.icon, s.titleText * 1.3f, p.text).apply { gravity = Gravity.CENTER })
                addView(c.text(item.label, s.secondaryText * 0.85f, p.text).apply { gravity = Gravity.CENTER })
                setOnClickListener { onNav(item) }
            }
            navItems[item] = button
            navColumn.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = s.gap / 2
                marginStart = s.gap / 2
                marginEnd = s.gap
            })
        }
    }

    private fun onNav(item: NavItem) {
        when (item) {
            NavItem.LIBRARY -> { host.openSource(null); host.setMode(Mode.ALL) }
            NavItem.TELEGRAM -> host.showSources()
            NavItem.PLAYLIST -> host.setMode(Mode.PLAYLIST)
            NavItem.NOW -> host.openNowPlaying()
            NavItem.SETTINGS -> host.openUiLab()
        }
    }

    private fun renderNav(snap: MeloScreenHost.Snapshot) {
        val active = when {
            snap.showingSources -> NavItem.TELEGRAM
            snap.mode == Mode.PLAYLIST -> NavItem.PLAYLIST
            else -> NavItem.LIBRARY
        }
        navItems.forEach { (item, button) ->
            val on = item == active
            button.background = if (on) c.roundedBackground(p.accentSoft, stroke = p.accent) else null
            for (i in 0 until button.childCount) (button.getChildAt(i) as? TextView)?.setTextColor(if (on) p.accent else p.text)
        }
    }

    // ---- Player pane -----------------------------------------------------------------------------

    private fun buildPlayerPane(): View {
        val scroll = ScrollView(context).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL })
        column.addView(title, matchWrap().apply { topMargin = s.gap * 2 })
        column.addView(source, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(quality, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(time.apply { gravity = Gravity.END }, matchWrap().apply { topMargin = s.gap * 2 })

        val transport = transportRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        transport.addView(c.iconButton("⏮", "Bài trước") { host.previous() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginEnd = s.gap }
        })
        play = c.iconButton("▶", "Phát", primary = true, width = s.playButtonWidth, height = s.playButtonHeight) { host.playPause() }
            .apply { layoutParams = LinearLayout.LayoutParams(s.playButtonWidth, s.playButtonHeight).apply { marginEnd = s.gap } }
        transport.addView(play)
        transport.addView(c.iconButton("⏭", "Bài kế") { host.next() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f)
        })
        column.addView(transport, matchWrap().apply { topMargin = s.gap * 2 })

        shuffle = c.chip("⇄ Đảo", false) { host.toggleShuffle(); onPlaybackChanged() }
        repeat = c.chip("↻ Lặp", false) { host.cycleRepeat(); onPlaybackChanged() }
        favorite = c.chip("☆ Lưu", false) { host.toggleFavorite(); onPlaybackChanged() }
        queueChip = c.chip("☰ Hàng đợi", false) { host.openQueue() }
        listOf(shuffle, repeat, favorite, queueChip).forEach { it.setPadding(s.gap / 2, 0, s.gap / 2, 0) }
        column.addView(extrasBox.apply {
            orientation = LinearLayout.VERTICAL
            addView(extrasRow1, matchWrap())
            addView(extrasRow2, matchWrap().apply { topMargin = s.gap })
        }, matchWrap().apply { topMargin = s.gap * 2 })
        arrangeExtras(false)
        scroll.addView(column)
        playerScroll = scroll
        playerColumn = column
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) requestArtFit()
        }
        return scroll
    }

    // ---- Cover size and chip rows follow the height the other rows leave ---------------------

    /** One row of chips, or two when the column is too narrow for all four. */
    private fun arrangeExtras(stacked: Boolean) {
        if (extrasStacked == stacked && extrasRow1.childCount > 0) return
        extrasStacked = stacked
        extrasRow1.removeAllViews()
        extrasRow2.removeAllViews()
        val chips = listOf(shuffle, repeat, favorite, queueChip)
        chips.forEachIndexed { i, chip ->
            val row = if (stacked && i >= 2) extrasRow2 else extrasRow1
            val last = if (stacked) i % 2 == 1 else i == chips.lastIndex
            // Equal cells across the whole width, label centred.
            chip.layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginEnd = if (last) 0 else s.gap }
            row.addView(chip)
        }
        extrasRow2.visibility = if (stacked) View.VISIBLE else View.GONE
    }

    private fun requestArtFit() {
        if (fitPosted) return
        fitPosted = true
        playerScroll.post {
            fitPosted = false
            fitArt()
        }
    }

    private fun fitArt() {
        val w = playerScroll.width
        val h = playerScroll.height
        if (w <= 0 || h <= 0) return
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val widths = listOf(shuffle, repeat, favorite, queueChip).map { chip ->
            chip.measure(unspecified, unspecified)
            chip.measuredWidth
        }
        arrangeExtras(MeloNowPlayingPolicy.extrasStacked(w - playerColumn.paddingLeft - playerColumn.paddingRight, widths, s.gap))
        applyTight(false)
        var rest = MeloArtFit.restHeight(playerColumn, artHost, w)
        if (MeloNowPlayingPolicy.playerTight(h, rest, s.rowArt)) {
            applyTight(true)
            rest = MeloArtFit.restHeight(playerColumn, artHost, w)
        }
        sizeArt(MeloNowPlayingPolicy.artSide(w, h, rest, s.rowArt))
    }

    /** Low window (a phone on its side): one-line title, no quality line, half the gaps, so the buttons stay on screen. */
    private fun applyTight(tight: Boolean) {
        if (tight == playerTight) return
        playerTight = tight
        title.maxLines = if (tight) 1 else 2
        quality.visibility = if (tight) View.GONE else View.VISIBLE
        val top = if (tight) s.gap else s.gap * 2
        listOf<View>(title, time, transportRow, extrasBox).forEach { v ->
            (v.layoutParams as LinearLayout.LayoutParams).topMargin = top
            v.requestLayout()
        }
    }

    // ---- Quick channel switch (left slot while a channel is open) -----------------------------

    private fun buildQuickPane(): View {
        quickPane.addView(c.chip("☷ Tất cả kênh", false) { host.showSources() }.apply {
            contentDescription = "Tất cả kênh"
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, s.minTouch).apply { bottomMargin = s.gap })
        quickList.apply {
            adapter = quickAdapter
            divider = null
            dividerHeight = s.gap / 2
            setSelector(android.R.color.transparent)
            isVerticalScrollBarEnabled = false
            setOnItemClickListener { _, _, position, _ ->
                val row = quickRows.getOrNull(position) ?: return@setOnItemClickListener
                if (row.chatId != quickOpenId) host.openSource(row.chatId)
            }
        }
        quickPane.addView(quickList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Two rows (F07): [cover | title on two lines] over [⏮ ⏸ ⏭], so the title gets the pane's width
        // instead of the ~22dp the fixed 224dp of the old one-row bar left it.
        val mini = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedMini()
            setPadding(s.gap, s.gap, s.gap, s.gap)
        }
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info.addView(miniArtHost, LinearLayout.LayoutParams(miniArtPx, miniArtPx).apply { marginEnd = s.gap })
        miniTitle.apply {
            isClickable = true
            isFocusable = true
            minimumHeight = s.minTouch
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Mở màn hình đang phát"
            setOnClickListener { host.openNowPlaying() }
        }
        info.addView(miniTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        mini.addView(info, matchWrap())
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(c.iconButton("⏮", "Bài trước") { host.previous() },
            LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginEnd = s.gap / 2 })
        miniPlay = c.iconButton("▶", "Phát", primary = true) { host.playPause() }
        controls.addView(miniPlay, LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginStart = s.gap / 2; marginEnd = s.gap / 2 })
        controls.addView(c.iconButton("⏭", "Bài kế") { host.next() },
            LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginStart = s.gap / 2 })
        mini.addView(controls, matchWrap().apply { topMargin = s.gap })
        quickPane.addView(mini, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        return quickPane
    }

    private fun roundedMini() = c.roundedBackground(p.surface, stroke = p.border)

    private inner class QuickAdapter : BaseAdapter() {
        override fun getCount(): Int = quickRows.size
        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = quickRows[position].chatId
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = quickRows[position]
            val open = row.chatId == quickOpenId
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = s.minTouch
                setPadding(s.gap * 2, s.gap, s.gap * 2, s.gap)
                background = if (open) c.roundedBackground(p.accentSoft, stroke = p.accent) else null
                contentDescription = "${row.title}, ${row.count} bài"
                addView(c.text(row.title, s.titleText, if (open) p.accent else p.text, bold = open, maxLines = 2))
                addView(c.text("${MeloComponentPolicy.formatCount(row.count)} bài", s.secondaryText, p.textSecondary))
            }
        }
    }

    private fun renderQuickSwitch(snap: MeloScreenHost.Snapshot) {
        // Channels are only read while one channel is open. No channel with songs (still loading,
        // or every count 0) keeps the big player: never an empty column.
        val channelOpen = MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, snap.showingSources, snap.mode, snap.selectedChatId, 1)
        val rows = if (channelOpen) MeloLibraryPolicy.quickSwitchRows(host.channels()) else emptyList()
        val show = MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, snap.showingSources, snap.mode, snap.selectedChatId, rows.size)
        if (show) {
            val wasShown = quickShown
            val selectionChanged = snap.selectedChatId != quickOpenId
            if (rows != quickRows || selectionChanged || !wasShown) {
                quickRows = rows
                quickOpenId = snap.selectedChatId
                quickAdapter.notifyDataSetChanged()
            }
            val index = MeloLibraryPolicy.quickSwitchIndex(rows, snap.selectedChatId)
            // The channel list can arrive after the pane is shown (new screen after a rotation or font change).
            if (index >= 0 && quickScrolledId != snap.selectedChatId) {
                val force = !wasShown || quickScrolledId == null
                quickScrolledId = snap.selectedChatId
                quickList.post {
                    if (force || index < quickList.firstVisiblePosition || index > quickList.lastVisiblePosition) {
                        quickList.setSelectionFromTop(index, MeloLibraryPolicy.quickSwitchOffset(quickList.height, s.rowHeight))
                    }
                }
            }
        } else {
            quickScrolledId = null
        }
        quickShown = show
        quickPane.visibility = if (show) View.VISIBLE else View.GONE
        playerPane.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateMini(now: NowPlaying?) {
        miniTitle.show(now?.title ?: "Chọn một bài để phát")
        miniPlay.show(if (now?.playing == true) "❚❚" else "▶")
        miniPlay.contentDescription = if (now?.playing == true) "Dừng" else "Phát"
        val key = Triple(now?.art, now?.title, miniArtPx)
        if (key == miniArtKey) return
        miniArtKey = key
        miniArtHost.removeAllViews()
        miniArtHost.addView(c.artView(now?.art, now?.sourceTitle, now?.title ?: "♪", miniArtPx).apply {
            layoutParams = FrameLayout.LayoutParams(miniArtPx, miniArtPx)
        })
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun sizeArt(side: Int) {
        val value = side.coerceAtLeast(s.rowArt)
        if (value == artSide) return
        artSide = value
        artHost.layoutParams = (artHost.layoutParams as LinearLayout.LayoutParams).apply { width = value; height = value }
        artKey = null
        artHost.requestLayout()
        onPlaybackChanged()
    }

    private fun showArt(art: Bitmap?, sourceTitle: String?, songTitle: String) {
        if (artSide <= 0) return
        val key = Triple(art, songTitle, artSide)
        if (key == artKey) return
        artKey = key
        artHost.removeAllViews()
        artHost.addView(c.artView(art, sourceTitle, songTitle, artSide).apply { layoutParams = FrameLayout.LayoutParams(artSide, artSide) })
    }

    private fun TextView.show(value: CharSequence): Boolean {
        if (text?.toString() == value.toString()) return false
        text = value
        return true
    }

    private fun toggleStyle(chip: TextView, on: Boolean, label: String): Boolean {
        val state = "$on|$label"
        if (chip.tag == state) return false
        chip.tag = state
        chip.text = label
        chip.setTextColor(if (on) p.onAccent else p.text)
        chip.background = if (on) c.pill(p.accent) else c.pill(p.surfaceRaised, p.border)
        return true
    }

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        val playlistState = if (snap.mode == Mode.PLAYLIST && !snap.showingSources) host.playlistStatus() else null
        syncLine.show(PlaylistSyncStatusPolicy.libraryLine(
            playlistState, "☁ " + MeloLibraryPolicy.syncLine(host.syncSubtitle(), snap.total, false)
        ))
        // E96 P4: on the Playlist tab the line is the honest Playlist state; tap = detail.
        if (playlistState != null) {
            syncLine.setOnClickListener {
                android.widget.Toast.makeText(syncLine.context, playlistState.text + "\n" + playlistState.fullDetail(), android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            syncLine.setOnClickListener(null)
            syncLine.isClickable = false
        }
        backCallback.isEnabled = !snap.showingSources && snap.sourceTitle != null
        renderNav(snap)
        renderQuickSwitch(snap)
        pane.onLibraryChanged()
    }

    fun onPlaybackChanged() {
        val now: NowPlaying? = host.nowPlaying()
        var relayout: Boolean
        if (now == null) {
            relayout = title.show("Chọn một bài để phát")
            relayout = source.show("") or relayout
            quality.show("")
            time.show("")
            play.show("▶")
            showArt(null, null, "♪")
        } else {
            relayout = title.show(now.title)
            relayout = source.show(MeloNowPlayingPolicy.sourceLine(now.sourceTitle, now.local, now.sourceIsFolder).orEmpty()) or relayout
            quality.show(listOfNotNull(host.qualityLabel(null), now.downloadNote?.takeUnless { it.startsWith("Đã tải") }).joinToString(" · "))
            time.show(MeloComponentPolicy.progressText((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt()))
            play.show(if (now.playing) "❚❚" else "▶")
            play.contentDescription = if (now.playing) "Dừng" else "Phát"
            relayout = toggleStyle(shuffle, now.shuffle, "⇄ Đảo") or relayout
            relayout = toggleStyle(repeat, now.repeatLabel != "tắt", MeloNowPlayingPolicy.repeatChipLabel(now.repeatLabel)) or relayout
            val save = MeloSavePolicy.buttonState(now.saveAvailability, now.favorite, now.saveBusy)
            relayout = toggleStyle(favorite, save.saved, MeloSavePolicy.chipLabel(save.saved)) or relayout
            shuffle.contentDescription = if (now.shuffle) "Đảo bài: bật" else "Đảo bài: tắt"
            repeat.contentDescription = "Lặp: ${now.repeatLabel}"
            favorite.contentDescription = save.contentDescription
            showArt(now.art, now.sourceTitle, now.title)
        }
        if (relayout && ::playerScroll.isInitialized) requestArtFit()
        updateMini(now)
        pane.onPlaybackChanged(now?.songKey, now?.paused == true)
    }
}
