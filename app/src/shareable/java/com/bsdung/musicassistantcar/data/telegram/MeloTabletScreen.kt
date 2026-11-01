package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.ImageView
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
import com.bsdung.musicassistantcar.R
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
    private val backDispatcher: OnBackPressedDispatcher,
    theme: MeloThemePolicy.ColorTheme = MeloThemePolicy.ColorTheme.CURRENT
) {
    private val c = MeloComponents(context, sizes, theme)
    private val s = c.sizes
    private val p = c.palette
    /** E98 teal theme: no navigation column (the four tabs and ⚙ replace it), a new player pane, the sync row. */
    private val teal = c.teal
    private val syncRow by lazy { c.syncRow { host.openUiLab() } }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(p.background)
        // UI-04: the player pane on the left sits inside the same margin as the list pane on the right
        // (the navigation column that once took the left side is no longer built).
        setPadding(s.screenMargin, s.gap, s.screenMargin, s.gap)
    }

    private val navColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val navItems = mutableMapOf<NavItem, LinearLayout>()
    private val syncLine = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val rightSlot = FrameLayout(context)
    private var showingQueue = false
    private lateinit var queuePane: MeloQueuePane
    private var lyricsTabs: MeloLyricsTabs? = null
    private val pane =MeloLibraryPane(context, c, host, NewLayer.TWO_PANE)
    private val playFeedbackView = MeloPlayFeedbackView(context, c, host)

    // Player pane parts.
    private val artHost = FrameLayout(context)
    private val title = c.text("Chọn một bài để phát", s.playerTitleText, p.text, bold = true, maxLines = 2)
    private val source = c.text("", s.titleText, p.textSecondary, maxLines = 1)
    private val quality = c.text("", s.secondaryText, p.accent, maxLines = 1)
    private val time = c.text("", s.secondaryText, p.textSecondary)
    private lateinit var play: TextView
    // E103: the draggable seek line + time(s) block, both colour styles. Teal also has two times and the vector play button.
    private lateinit var timeBlock: View
    private lateinit var seekLine: MeloSeekLine
    /** The drag target shown in the time line while the finger is on the seek line; null otherwise. */
    private var previewMs: Long? = null
    private val timeLeft = c.text("", s.secondaryText, p.textSecondary)
    private val timeRight = c.text("", s.secondaryText, p.textSecondary).apply { gravity = Gravity.END }
    private var playIcon: ImageView? = null
    private var playButton: View? = null
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
            if (showingQueue) closeQueue()
            else if (snap?.showingSources == true) host.closeSources()
            else if (snap != null && snap.local) host.closeLocal()
            else if (snap != null && !snap.showingSources && snap.sourceTitle != null) host.showSources()
            else { isEnabled = false; backDispatcher.onBackPressed() }
        }
    }

    init {
        if (teal) {
            view.background = c.gradient(p.background, p.backgroundEnd, 0)
            view.setPadding(s.screenMargin, s.gap, s.screenMargin, s.gap)
        }
        playerPane = if (teal) buildThemedPlayerPane() else buildPlayerPane()
        leftSlot.addView(playerPane, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view.addView(leftSlot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 40f).apply { marginEnd = s.screenMargin })
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val heading = if (teal) syncRow.view else LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(syncLine, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(c.iconButton("⤢", "Mở Đang phát") { host.openNowPlaying() })
            addView(c.iconButton("⚙", "Cài đặt") { host.openUiLab() })
        }
        right.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap
        })
        // P2-B: the queue panel gets a "Tiếp theo | Lời nhạc" strip; "Thư viện" then moves to the end of the strip.
        val hasLyrics = host.lyrics() != null
        queuePane = MeloQueuePane(context, c, host, onLibrary = if (hasLyrics) null else ({ closeQueue() }))
        if (hasLyrics) {
            lyricsTabs = MeloLyricsTabs(
                context, c, host, queuePane.view, MeloLyricsPane(context, c, host), fillHeight = true,
                trailing = c.chip("Thư viện", false) { closeQueue() }
            )
        }
        rightSlot.addView(pane.view)
        right.addView(rightSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 60f))
    }

    fun openQueue() {
        if (showingQueue) { queuePane.update(); return }
        // The pane remains mounted, preserving pending query and list anchor from S2.
        showingQueue = true
        pane.view.visibility = View.GONE
        rightSlot.addView(lyricsTabs?.view ?: queuePane.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        backCallback.isEnabled = true
        queuePane.update()
        lyricsTabs?.setActive(true)
    }

    private fun closeQueue() {
        if (!showingQueue) return
        showingQueue = false
        lyricsTabs?.setActive(false)
        rightSlot.removeView(lyricsTabs?.view ?: queuePane.view)
        pane.view.visibility = View.VISIBLE
        onLibraryChanged()
    }

    fun attach() {
        backDispatcher.addCallback(backCallback)
        onLibraryChanged()
        onPlaybackChanged()
    }

    fun detach() {
        lyricsTabs?.setActive(false)
        playFeedbackView.stop()
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
            NavItem.LIBRARY -> host.openBrowseScope(MeloLibraryPolicy.tabScope(MeloLibraryPolicy.TabKind.ALL))
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
        column.addView(playFeedbackView, matchWrap())
        seekLine = newSeekLine(p.accent)
        timeBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(seekLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch))
            addView(time.apply { gravity = Gravity.END }, matchWrap())
        }
        column.addView(timeBlock, matchWrap().apply { topMargin = s.gap * 2 })

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
        return pinPlayerDock(scroll, column)
    }

    /**
     * E98 teal theme: logo, big cover (tap = Đang phát), centred title and source, quality pill, a
     * display-only progress line with the two times, three large transport buttons and a 2x2 grid
     * of Đảo / Lặp / Lưu / Hàng đợi with icons. Same fields as [buildPlayerPane], so the cover fit
     * and the low-window rules below keep working.
     */
    private fun buildThemedPlayerPane(): View {
        val scroll = ScrollView(context).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(c.logo(s.playerTitleText), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = s.gap })
        column.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL })
        artHost.apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Mở màn hình đang phát"
            setOnClickListener { host.openNowPlaying() }
        }
        title.gravity = Gravity.CENTER_HORIZONTAL
        source.gravity = Gravity.CENTER_HORIZONTAL
        column.addView(title, matchWrap().apply { topMargin = s.gap * 2 })
        column.addView(source, matchWrap().apply { topMargin = s.gap / 2 })
        quality.apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = c.pill(0x00000000, p.accent)
            setPadding(s.gap * 2, s.gap / 2, s.gap * 2, s.gap / 2)
            visibility = View.GONE
        }
        column.addView(quality, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap })

        column.addView(playFeedbackView, matchWrap())

        seekLine = newSeekLine(p.text)
        val progressBlock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        progressBlock.addView(seekLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch))
        progressBlock.addView(LinearLayout(context).apply {
            val stacked = context.resources.configuration.fontScale > 1.3f
            orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(timeLeft, if (stacked) matchWrap() else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(timeRight, if (stacked) matchWrap() else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, matchWrap().apply { topMargin = s.gap / 2 })
        timeBlock = progressBlock
        column.addView(progressBlock, matchWrap().apply { topMargin = s.gap * 2 })

        val transport = transportRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        transport.addView(themedTransportButton(R.drawable.ic_skip_previous, "Bài trước", false) { host.previous() },
            LinearLayout.LayoutParams(0, s.playButtonHeight, 1f).apply { marginEnd = s.gap })
        val playBtn = themedTransportButton(R.drawable.ic_play_arrow, "Phát", true) { host.playPause() }
        playButton = playBtn
        playIcon = playBtn.getChildAt(0) as ImageView
        transport.addView(playBtn, LinearLayout.LayoutParams(0, s.playButtonHeight, 1f).apply { marginEnd = s.gap })
        transport.addView(themedTransportButton(R.drawable.ic_skip_next, "Bài kế", false) { host.next() },
            LinearLayout.LayoutParams(0, s.playButtonHeight, 1f))
        column.addView(transport, matchWrap().apply { topMargin = s.gap * 2 })

        shuffle = c.chip(MeloNowPlayingPolicy.themedShuffleLabel(false), false) { host.toggleShuffle(); onPlaybackChanged() }
        repeat = c.chip(MeloNowPlayingPolicy.themedRepeatLabel("tắt"), false) { host.cycleRepeat(); onPlaybackChanged() }
        favorite = c.chip(MeloNowPlayingPolicy.themedFavoriteLabel(false), false) { host.toggleFavorite(); onPlaybackChanged() }
        queueChip = c.chip(MeloNowPlayingPolicy.THEMED_QUEUE_LABEL, false) { host.openQueue() }
        toggleStyle(shuffle, false, MeloNowPlayingPolicy.themedShuffleLabel(false), R.drawable.ic_shuffle)
        toggleStyle(repeat, false, MeloNowPlayingPolicy.themedRepeatLabel("tắt"), R.drawable.ic_repeat)
        toggleStyle(favorite, false, MeloNowPlayingPolicy.themedFavoriteLabel(false), R.drawable.ic_heart_outline)
        toggleStyle(queueChip, false, MeloNowPlayingPolicy.THEMED_QUEUE_LABEL, R.drawable.ic_queue_music)
        listOf(shuffle, repeat, favorite, queueChip).forEach { it.setPadding(s.gap, 0, s.gap, 0) }
        column.addView(extrasBox.apply {
            orientation = LinearLayout.VERTICAL
            addView(extrasRow1, matchWrap())
            addView(extrasRow2, matchWrap().apply { topMargin = s.gap })
        }, matchWrap().apply { topMargin = s.gap * 2 })
        arrangeExtras(true)
        scroll.addView(column)
        playerScroll = scroll
        playerColumn = column
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) requestArtFit()
        }
        return pinPlayerDock(scroll, column)
    }

    /** Controls stay outside secondary-content scrolling at every font size. */
    private fun pinPlayerDock(scroll: ScrollView, column: LinearLayout): View {
        column.removeView(timeBlock)
        column.removeView(transportRow)
        val dock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
            addView(timeBlock, matchWrap())
            addView(transportRow, matchWrap().apply { topMargin = s.gap })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(dock, matchWrap())
        }
    }

    /** Large transport button, with a vector and the primary accent background. */
    private fun themedTransportButton(iconRes: Int, description: String, primary: Boolean, onClick: () -> Unit): FrameLayout {
        val radius = s.corner * 9 / 8
        val icon = (s.playButtonHeight * 0.5f).toInt()
        return FrameLayout(context).apply {
            background = if (primary) {
                c.gradient(p.accent, p.accentEnd, radius, android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
            } else {
                c.roundedBackground(p.surfaceRaised, radius, stroke = p.border)
            }
            contentDescription = description
            isClickable = true
            isFocusable = true
            addView(c.vectorIcon(iconRes, icon, if (primary) p.onAccent else p.text), FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
            setOnClickListener { onClick() }
        }
    }

    private fun renderPlay(playing: Boolean) {
        if (!teal) return
        playIcon?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow)
        playButton?.contentDescription = if (playing) "Dừng" else "Phát"
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
            chip.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = if (last) 0 else s.gap }
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
        // Teal theme: always the 2x2 grid of the reference design.
        arrangeExtras(teal || MeloNowPlayingPolicy.extrasStacked(w - playerColumn.paddingLeft - playerColumn.paddingRight, widths, s.gap))
        applyTight(false)
        var rest = MeloArtFit.restHeight(playerColumn, artHost, w)
        if (MeloNowPlayingPolicy.playerTight(h, rest, s.rowArt)) {
            applyTight(true)
            rest = MeloArtFit.restHeight(playerColumn, artHost, w)
        }
        sizeArt(MeloNowPlayingPolicy.artSide(w, h, rest, s.rowArt))
    }

    /** Teal pill: shown only with something to say. True when its visibility changed (the column then needs a new fit). */
    private fun syncQualityVisibility(): Boolean {
        val next = qualityVisibility(playerTight)
        if (quality.visibility == next) return false
        quality.visibility = next
        return true
    }

    /** The quality line goes in a low window; the teal pill also goes while it has nothing to say. */
    private fun qualityVisibility(tight: Boolean): Int =
        if (tight || (teal && quality.text.isNullOrEmpty())) View.GONE else View.VISIBLE

    /** Low window (a phone on its side): one-line title, no quality line, half the gaps, so the buttons stay on screen. */
    private fun applyTight(tight: Boolean) {
        if (tight == playerTight) return
        playerTight = tight
        title.maxLines = if (tight) 1 else 2
        quality.visibility = qualityVisibility(tight)
        val top = if (tight) s.gap else s.gap * 2
        listOf<View>(title, timeBlock, transportRow, extrasBox).forEach { v ->
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
        // Teal theme: a rounder cover (20dp) with a soft shadow.
        val radius = if (teal) s.corner * 5 / 4 else s.corner
        artHost.addView(c.artView(art, sourceTitle, songTitle, artSide, radius).apply {
            layoutParams = FrameLayout.LayoutParams(artSide, artSide)
            if (teal) elevation = s.gap.toFloat()
        })
    }

    private fun TextView.show(value: CharSequence): Boolean {
        if (text?.toString() == value.toString()) return false
        text = value
        return true
    }

    private fun toggleStyle(chip: TextView, on: Boolean, label: String, iconRes: Int? = null): Boolean {
        val state = "$on|$label|$iconRes"
        if (chip.tag == state) return false
        chip.tag = state
        chip.text = label
        chip.setTextColor(if (on) p.onAccent else p.text)
        chip.background = if (on) c.pill(p.accent) else c.pill(p.surfaceRaised, p.border)
        if (teal && iconRes != null) {
            // Teal grid cells carry an icon in front of the label, in the text colour of the state.
            val size = (s.secondaryText * 1.4f).toInt()
            chip.setCompoundDrawablesRelative(c.iconDrawable(iconRes, size, if (on) p.onAccent else p.text), null, null, null)
            chip.compoundDrawablePadding = s.gap
        }
        return true
    }

    fun onLibraryChanged() {
        if (showingQueue) queuePane.update()
        playFeedbackView.refresh()
        val snap = host.snapshot()
        snapshot = snap
        val playlistState = if (snap.mode == Mode.PLAYLIST && !snap.showingSources) host.playlistStatus() else null
        val syncText = host.syncSubtitle()
        if (teal) {
            // Playlist tab: the honest Playlist state replaces the headline; tapping the row shows its detail.
            val headline = if (playlistState != null) PlaylistSyncStatusPolicy.libraryLine(playlistState, "")
            else MeloThemePolicy.syncHeadline(host.sourceSummaries().firstOrNull { it.kind == MeloSourceCardPolicy.Kind.TELEGRAM }?.total ?: snap.total)
            syncRow.update(headline, MeloThemePolicy.syncDot(true, syncText))
            if (playlistState != null) {
                syncRow.view.setOnClickListener {
                    android.widget.Toast.makeText(context, playlistState.text + "\n" + playlistState.fullDetail(), android.widget.Toast.LENGTH_LONG).show()
                }
            } else {
                syncRow.view.setOnClickListener(null)
                syncRow.view.isClickable = false
            }
        } else {
            syncLine.show(PlaylistSyncStatusPolicy.libraryLine(
                playlistState, "☁ " + MeloLibraryPolicy.syncLine(syncText, snap.total, false)
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
        }
        backCallback.isEnabled = showingQueue || snap.showingSources || snap.local || snap.sourceTitle != null
        // Sources and songs share the right pane. The full player is never replaced by quickPane.
        playerPane.visibility = View.VISIBLE
        pane.onLibraryChanged()
    }

    private fun newSeekLine(thumbColor: Int) = MeloSeekLine(
        context, p.border, p.accent, thumbColor, s.progressHeight, s.progressThumb, s.minTouch,
        onPreview = { previewMs = it; renderTimes(host.nowPlaying()) },
        onSeek = { host.seekTo(it); onPlaybackChanged() }
    )

    /** Seek line plus the time text(s); while the line is being dragged the shown time is the drag target. */
    private fun renderTimes(now: NowPlaying?) {
        seekLine.set(now?.positionMs ?: 0L, now?.durationMs ?: 0L)
        val shownMs = if (now == null) 0L else previewMs ?: now.positionMs
        if (teal) {
            timeLeft.show(if (now == null) "" else TelegramSongDisplayPolicy.formatDuration((shownMs / 1000).toInt().coerceAtLeast(0)) ?: "00:00")
            timeRight.show(if (now == null) "" else TelegramSongDisplayPolicy.formatDuration((now.durationMs / 1000).toInt()).orEmpty())
        } else {
            time.show(if (now == null) "" else MeloComponentPolicy.progressText((shownMs / 1000).toInt(), (now.durationMs / 1000).toInt()))
        }
    }

    fun onPlaybackChanged() {
        if (showingQueue) queuePane.update()

        playFeedbackView.refresh()
        val now: NowPlaying? = host.nowPlaying()
        var relayout: Boolean
        if (now == null) {
            relayout = title.show("Chọn một bài để phát")
            relayout = source.show("") or relayout
            quality.show("")
            renderTimes(null)
            if (teal) {
                renderPlay(false)
                relayout = syncQualityVisibility() or relayout
            } else {
                play.show("▶")
            }
            showArt(null, null, "♪")
        } else {
            relayout = title.show(now.title)
            relayout = source.show(MeloNowPlayingPolicy.sourceLine(now.sourceTitle, now.local, now.sourceIsFolder).orEmpty()) or relayout
            val qualityParts = listOfNotNull(host.qualityLabel(null), now.downloadNote?.takeUnless { now.download?.state == MeloDownloadUiPolicy.State.READY })
            val save = MeloSavePolicy.buttonState(now.saveAvailability, now.favorite, now.saveBusy)
            if (teal) {
                quality.show(MeloNowPlayingPolicy.qualityPillText(qualityParts))
                renderTimes(now)
                renderPlay(now.playing)
                relayout = syncQualityVisibility() or relayout
                relayout = toggleStyle(shuffle, now.shuffle, MeloNowPlayingPolicy.themedShuffleLabel(now.shuffle), R.drawable.ic_shuffle) or relayout
                relayout = toggleStyle(repeat, now.repeatLabel != "tắt", MeloNowPlayingPolicy.themedRepeatLabel(now.repeatLabel),
                    if (now.repeatLabel == "một bài") R.drawable.ic_repeat_one else R.drawable.ic_repeat) or relayout
                relayout = toggleStyle(favorite, save.saved, MeloNowPlayingPolicy.themedFavoriteLabel(save.saved),
                    if (save.saved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline) or relayout
            } else {
                quality.show(qualityParts.joinToString(" · "))
                renderTimes(now)
                play.show(if (now.playing) "❚❚" else "▶")
                play.contentDescription = if (now.playing) "Dừng" else "Phát"
                relayout = toggleStyle(shuffle, now.shuffle, "⇄ Đảo") or relayout
                relayout = toggleStyle(repeat, now.repeatLabel != "tắt", MeloNowPlayingPolicy.repeatChipLabel(now.repeatLabel)) or relayout
                relayout = toggleStyle(favorite, save.saved, MeloSavePolicy.chipLabel(save.saved)) or relayout
            }
            shuffle.contentDescription = if (now.shuffle) "Đảo bài: bật" else "Đảo bài: tắt"
            repeat.contentDescription = "Lặp: ${now.repeatLabel}"
            favorite.contentDescription = save.contentDescription
            showArt(now.art, now.sourceTitle, now.title)
        }
        if (relayout && ::playerScroll.isInitialized) requestArtFit()
        pane.onPlaybackChanged(now?.songKey, now?.paused == true, now?.playing == true, now?.buffering == true)
    }
}
