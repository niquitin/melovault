package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bsdung.musicassistantcar.data.telegram.MeloComponents.SongRowModel
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.DeckLayout
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.SleepOption
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.QueueItem
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Studio Deck Now Playing (docs/ui-spec.md step 6). Portrait: big cover, title, channel,
 * quality pills, Đảo / Lặp / Lưu, then the queue below. Landscape: player on the left, numbered
 * queue on the right. EQ and Hẹn giờ sit at the top (EQ is not available yet).
 *
 * E95: the waveform scrubber is gone. The time line and the −30s / Lùi / Phát / Tiến / +60s row
 * form a "bến điều khiển" pinned under the scrolling part, so the controls never leave the screen
 * while the queue scrolls. E103: a draggable seek line is back in that dock, above the time line.
 */
internal class MeloStudioDeck(private val activity: Activity, private val host: MeloScreenHost) {
    private val browseOwner = host.accountId()

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "melo-probe").apply { isDaemon = true } }
    private val jobGeneration = AtomicLong(0)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar)

    private val c: MeloComponents
    private lateinit var playFeedbackView: MeloPlayFeedbackView
    private val s get() = c.sizes
    private val p get() = c.palette
    private val layout: DeckLayout
    /** Height of the −30s / Lùi / Tiến / +60s buttons (56dp in a tall window, else the 48dp touch minimum). */
    private val transportPx: Int

    private val artHost = FrameLayout(activity)
    private val title: TextView
    private val source: TextView
    private val qualityChips: MeloQualityChipRow
    /** Only the download progress ("Đang tải 45%"); the quality itself is the pill row above it. */
    private val qualityNote: TextView
    private val time: TextView
    private val seekLine: MeloSeekLine
    /** The drag target shown in the time line while the finger is on the seek line; null otherwise. */
    private var previewMs: Long? = null
    private lateinit var play: TextView
    private lateinit var shuffle: TextView
    private lateinit var repeat: TextView
    private lateinit var favorite: TextView
    private lateinit var sleep: TextView
    private val prefetchSwitch: Switch
    private lateinit var queuePane: MeloQueuePane
    private lateinit var lyricsPane: MeloLyricsPane
    /** "Tiếp theo | Lời nhạc" (P2-B); null on a host without lyrics, which then keeps the plain queue. */
    private var lyricsTabs: MeloLyricsTabs? = null

    private var currentKey: Long? = null
    private var probedKey: Long? = null
    private var probing = false
    private var fileBitDepth: Int? = null
    private var coverFromFile: Bitmap? = null
    private var artKey: Any? = null
    private var fitScroll: ScrollView? = null
    private var fitColumn: LinearLayout? = null
    /** Horizontal room the scroll view's column loses to its own padding (portrait). */
    private var fitInsetPx = 0
    private var fitPosted = false
    private var playerTight = false
    private val transportRow = LinearLayout(activity)
    private val extrasBox = LinearLayout(activity)
    private val extrasRow1 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val extrasRow2 = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private var extrasStacked = false

    private val update = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        val metrics = MeloUiLabActivity.realMeasurements(activity)
        val d = metrics.display
        layout = MeloNowPlayingPolicy.deckLayout(metrics.windowWidthDp, metrics.windowHeightDp)
        // One profile rule for the library, the settings screen and this deck (sw 560–599 stays consistent).
        val profile = MeloLibraryPolicy.profileFor(metrics.smallestWidthDp)
        c = MeloComponents(activity, MeloDesignTokens.sizes(profile, d.density, d.fontScale, d.physicalPpi))
        playFeedbackView = MeloPlayFeedbackView(activity, c, host)
        transportPx = maxOf(c.sizes.minTouch, (MeloNowPlayingPolicy.transportHeightDp(metrics.windowHeightDp) * d.density).toInt())
        title = c.text("", s.playerTitleText, p.text, bold = true, maxLines = 2)
        source = c.text("", s.titleText, p.textSecondary, maxLines = 1)
        qualityChips = MeloQualityChipRow(activity, c, s.minTouch * 2 / 3).apply { visibility = View.GONE }
        qualityNote = c.text("", s.secondaryText, p.textSecondary, maxLines = 3).apply { visibility = View.GONE }
        time = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
        seekLine = MeloSeekLine(
            activity, p.border, p.accent, p.accent, s.progressHeight, s.progressThumb, s.minTouch,
            onPreview = { previewMs = it; renderTime(host.nowPlaying()) },
            onSeek = { host.seekTo(it); refresh() }
        )
        queuePane = MeloQueuePane(activity, c, host, preview = layout == DeckLayout.PORTRAIT)
        lyricsPane = MeloLyricsPane(
            activity, c, host,
            fixedHeightPx = if (layout == DeckLayout.PORTRAIT) {
                MeloLyricsUiPolicy.portraitPaneHeight((metrics.windowHeightDp * d.density).toInt(), c.sizes.minTouch)
            } else null
        )
        prefetchSwitch = Switch(activity).apply {
            text = "Tải trước bài kế"
            setTextColor(p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
            isChecked = host.prefetchEnabled()
            minHeight = s.minTouch
            setOnCheckedChangeListener { _, checked -> host.setPrefetchEnabled(checked) }
        }
        dialog.setContentView(buildRoot())
        dialog.setOnDismissListener { stop() }
        dialog.window?.let { w ->
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(w, false)
        }
    }

    fun show() {
        if (dialog.isShowing) return
        dialog.show()
        reloadQueue()
        val saved = host.deckState()
        queuePane.view.post { queuePane.restore(saved.songKey, saved.offsetPx) }
        fitScroll?.post { fitScroll?.scrollTo(0, saved.bodyScrollY) }
        handler.removeCallbacks(update)
        handler.post(update)
        lyricsTabs?.setActive(true)
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss() else stop()
    }

    fun saveUiState() {
        if (browseOwner <= 0L || host.accountId() != browseOwner) return
        val anchor = queuePane.anchor()
        host.saveDeckState(MeloBrowseState.Deck(songKey = anchor.first,
            offsetPx = anchor.second, bodyScrollY = fitScroll?.scrollY ?: 0))
    }

    private fun stop() {
        saveUiState()
        lyricsTabs?.setActive(false)
        playFeedbackView.stop()
        handler.removeCallbacksAndMessages(null)
        jobGeneration.incrementAndGet()
        worker.shutdownNow()
    }

    // ---- Layout ----------------------------------------------------------------------------

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun buildRoot(): View {
        val root = FrameLayout(activity).apply { setBackgroundColor(p.background) }
        val content: View = if (layout == DeckLayout.LANDSCAPE) buildLandscape() else buildPortrait()
        root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top + MeloUiLabActivity.handleBandPx(v, insets), bars.right, maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
            insets
        }
        // The window's place on the screen is only known after the first layout: measure the band again then.
        root.post { ViewCompat.requestApplyInsets(root) }
        return root
    }

    /** One column: [scrolling player block + queue] above the pinned control dock. */
    private fun buildPortrait(): View {
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.screenMargin, 0, s.screenMargin, s.screenMargin)
        }
        // The cover takes what this block leaves of the viewport (the queue below is not counted).
        val player = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        player.addView(topBar(), matchWrap())
        player.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap })
        addPlayerBody(player)
        column.addView(player, matchWrap())
        column.addView(c.spacer(s.gap * 3))
        val queueBlock = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        queueBlock.addView(queueHeader(), matchWrap())
        queueBlock.addView(queuePane.view, matchWrap())
        queueBlock.addView(c.chip("Xem cả hàng đợi", false) { host.openQueue() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, s.minTouch).apply { topMargin = s.gap })
        column.addView(withLyricsTabs(queueBlock, fillHeight = false), matchWrap())
        scroll.addView(column)
        fitScroll = scroll
        fitColumn = player
        fitInsetPx = s.screenMargin * 2
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) requestArtFit()
        }
        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(buildDock(s.screenMargin), matchWrap())
        return page
    }

    private fun buildLandscape(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(s.screenMargin, 0, s.screenMargin, s.gap)
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val player = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        player.addView(topBar(), matchWrap())
        player.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap })
        addPlayerBody(player)
        scroll.addView(player)
        val left = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        left.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        left.addView(buildDock(0), matchWrap())
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 45f).apply { marginEnd = s.screenMargin })
        val queueBlock = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        queueBlock.addView(queueHeader(), matchWrap().apply { topMargin = s.gap })
        queueBlock.addView(queuePane.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        right.addView(withLyricsTabs(queueBlock, fillHeight = true), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 55f))
        fitScroll = scroll
        fitColumn = player
        fitInsetPx = 0
        scroll.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (b - t != ob - ot || r - l != or_ - ol) requestArtFit()
        }
        return row
    }

    /**
     * "Bến điều khiển": the seek line (E103), the time line and the five buttons, fixed height, outside every
     * ScrollView and never touched by [applyTight], so they stay on screen whatever the queue does.
     */
    private fun buildDock(horizontalPadding: Int): View {
        val dock = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.background)
            setPadding(horizontalPadding, s.gap, horizontalPadding, s.gap)
        }
        dock.addView(seekLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch))
        dock.addView(time.apply { gravity = Gravity.END }, matchWrap().apply { topMargin = s.gap / 2 })
        val transport = transportRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun small(label: String, description: String, action: () -> Unit) =
            c.iconButton(label, description) { action() }.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
                layoutParams = LinearLayout.LayoutParams(0, transportPx, 1f).apply { marginEnd = s.gap / 2 }
            }
        val backSeek = small("−30s", "Tua lùi 30 giây") { seekBy(MeloNowPlayingPolicy.SEEK_BACK_MS) }
        val previous = small("⏮", "Bài trước") { host.previous() }
        play = c.iconButton("▶", "Phát", primary = true, width = s.playButtonWidth, height = s.playButtonHeight) { host.playPause() }
        val next = small("⏭", "Bài kế") { host.next() }
        val forwardSeek = small("+60s", "Tua tới 60 giây") { seekBy(MeloNowPlayingPolicy.SEEK_FORWARD_MS) }
        val seekExtras = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        var stacked: Boolean? = null
        fun arrange(width: Int) {
            val side = maxOf(s.minTouch, listOf(backSeek, previous, next, forwardSeek).maxOf {
                kotlin.math.ceil(it.paint.measureText(it.text.toString()).toDouble()).toInt() + it.paddingLeft + it.paddingRight
            })
            val compact = MeloQueueUiPolicy.dockStacked(width, s.minTouch, s.playButtonWidth, s.gap, side)
            if (stacked == compact) return
            stacked = compact
            transport.removeAllViews(); seekExtras.removeAllViews()
            val main = if (compact) listOf(previous, play, next) else listOf(backSeek, previous, play, next, forwardSeek)
            main.forEachIndexed { index, button ->
                val playWidth = minOf(s.playButtonWidth, (width - s.minTouch * 2 - s.gap).coerceAtLeast(s.minTouch))
                button.layoutParams = if (button === play) LinearLayout.LayoutParams(playWidth, s.playButtonHeight)
                    else LinearLayout.LayoutParams(0, transportPx, 1f)
                (button.layoutParams as LinearLayout.LayoutParams).marginEnd = if (index == main.lastIndex) 0 else s.gap / 2
                transport.addView(button)
            }
            if (compact) listOf(backSeek, forwardSeek).forEach { button ->
                seekExtras.addView(button, LinearLayout.LayoutParams(0, transportPx, 1f))
            }
            seekExtras.visibility = if (compact) View.VISIBLE else View.GONE
        }
        arrange(Int.MAX_VALUE / 8)
        dock.addView(transport, matchWrap().apply { topMargin = s.gap })
        dock.addView(seekExtras, matchWrap().apply { topMargin = s.gap / 2 })
        dock.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            arrange((r - l - dock.paddingLeft - dock.paddingRight).coerceAtLeast(0))
        }
        return dock
    }

    /** The pill row and the download note take no room when empty or when the window is too low. */
    private fun updateQualityVisibility() {
        qualityChips.visibility = if (playerTight || qualityChips.isEmpty) View.GONE else View.VISIBLE
        qualityNote.visibility = if (playerTight || qualityNote.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Low window (a phone on its side): no quality pills and half the gaps, so the cover keeps its minimum. */
    private fun applyTight(tight: Boolean) {
        if (tight == playerTight) return
        playerTight = tight
        updateQualityVisibility()
        val top = if (tight) s.gap else s.gap * 2
        listOf<View>(title, extrasBox).forEach { v ->
            (v.layoutParams as LinearLayout.LayoutParams).topMargin = top
            v.requestLayout()
        }
    }

    /** One row of chips, or two when the column is too narrow for all three. */
    private fun arrangeExtras(stacked: Boolean) {
        if (extrasStacked == stacked && extrasRow1.childCount > 0) return
        extrasStacked = stacked
        extrasRow1.removeAllViews()
        extrasRow2.removeAllViews()
        val chips = listOf(shuffle, repeat, favorite)
        chips.forEachIndexed { i, chip ->
            val row = if (stacked && i >= 2) extrasRow2 else extrasRow1
            val last = if (stacked) i % 2 == 1 || i == chips.lastIndex else i == chips.lastIndex
            // Equal cells across the whole width, label centred.
            chip.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = if (last) 0 else s.gap }
            row.addView(chip)
        }
        extrasRow2.visibility = if (stacked) View.VISIBLE else View.GONE
    }

    /** Cover = the viewport height minus every other row of the player block, so nothing but the queue scrolls off. */
    private fun requestArtFit() {
        val scroll = fitScroll ?: return
        if (fitPosted) return
        fitPosted = true
        scroll.post {
            fitPosted = false
            val column = fitColumn ?: return@post
            val inner = scroll.width - fitInsetPx
            val h = scroll.height
            if (inner <= 0 || h <= 0) return@post
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val widths = listOf(shuffle, repeat, favorite).map { chip ->
                chip.measure(unspecified, unspecified)
                chip.measuredWidth
            }
            arrangeExtras(MeloNowPlayingPolicy.extrasStacked(inner, widths, s.gap))
            applyTight(false)
            var rest = MeloArtFit.restHeight(column, artHost, inner)
            if (MeloNowPlayingPolicy.playerTight(h, rest, s.rowArt)) {
                applyTight(true)
                rest = MeloArtFit.restHeight(column, artHost, inner)
            }
            sizeArt(MeloNowPlayingPolicy.artSide(inner, h, rest, s.rowArt))
        }
    }

    private fun topBar(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch
            addView(c.iconButton("˅", "Đóng màn Đang phát") { dismiss() }.apply { background = null; setTextColor(p.text) })
            addView(c.text("Đang phát", s.titleText, p.textSecondary, maxLines = 2),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }, matchWrap())
        addView(LinearLayout(activity).apply {
            orientation = if (activity.resources.configuration.fontScale > 1.3f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(c.chip("EQ", false) {
                Toast.makeText(activity, "EQ sắp có trong bản sau", Toast.LENGTH_SHORT).show()
            }.apply { contentDescription = "Bộ chỉnh âm, sắp có" }, matchWrap())
            sleep = c.chip("Hẹn giờ", false) { showSleepMenu(sleep) }.apply {
                contentDescription = "Hẹn giờ tắt nhạc"; maxLines = 2
            }
            // Equal-width horizontal cells or full-width stacked cells, each can grow with font size.
            if (orientation == LinearLayout.HORIZONTAL) {
                (getChildAt(0).layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f }
                addView(sleep, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            } else addView(sleep, matchWrap())
        }, matchWrap())
    }

    private fun addPlayerBody(column: LinearLayout) {
        column.addView(title, matchWrap().apply { topMargin = s.gap * 2 })
        column.addView(source, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(qualityChips, matchWrap().apply { topMargin = s.gap })
        column.addView(qualityNote, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(playFeedbackView, matchWrap())

        shuffle = c.chip("⇄ Đảo", false) { host.toggleShuffle(); refresh() }
        repeat = c.chip("↻ Lặp", false) { host.cycleRepeat(); refresh() }
        favorite = c.chip("☆ Lưu", false) { host.toggleFavorite(); refresh() }
        listOf(shuffle, repeat, favorite).forEach { it.setPadding(s.gap / 2, 0, s.gap / 2, 0) }
        extrasBox.apply {
            orientation = LinearLayout.VERTICAL
            addView(extrasRow1, matchWrap())
            addView(extrasRow2, matchWrap().apply { topMargin = s.gap })
        }
        column.addView(extrasBox, matchWrap().apply { topMargin = s.gap * 2 })
        arrangeExtras(false)
    }

    /** The queue block alone, or under the "Tiếp theo | Lời nhạc" strip when this host has lyrics. */
    private fun withLyricsTabs(queueBlock: View, fillHeight: Boolean): View {
        if (host.lyrics() == null) return queueBlock
        val tabs = MeloLyricsTabs(activity, c, host, queueBlock, lyricsPane, fillHeight)
        lyricsTabs = tabs
        return tabs.view
    }

    private fun queueHeader(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(prefetchSwitch)
    }

    private fun sizeArt(side: Int) {
        if (side <= 0) return
        val current = artHost.layoutParams as LinearLayout.LayoutParams
        if (current.width == side && current.height == side) return
        artHost.layoutParams = current.apply {
            width = side
            height = side
            topMargin = s.gap
        }
        artKey = null
        artHost.requestLayout()
        refresh()
    }

    // ---- Updates ---------------------------------------------------------------------------

    private fun seekBy(deltaMs: Long) {
        val now = host.nowPlaying() ?: return
        host.seekTo(MeloNowPlayingPolicy.seekTarget(now.positionMs, now.durationMs, deltaMs))
        refresh()
    }

    private fun refresh() {
        playFeedbackView.refresh()
        val now = host.nowPlaying()
        renderNow(now)
        val (deadline, endOfTrack) = host.sleepTimer()
        sleep.show(MeloNowPlayingPolicy.sleepLabel(deadline, endOfTrack, SystemClock.elapsedRealtime()))
        if (now?.songKey != currentKey) {
            currentKey = now?.songKey
            probedKey = null
            fileBitDepth = null
            coverFromFile = null
            reloadQueue()
        }
        queuePane.update(now)
        if (now != null && probedKey != now.songKey && !probing) startProbe(now)
    }

    /** Setting the same text still re-lays out a TextView; the 1 s tick must not do that. */
    private fun TextView.show(value: CharSequence): Boolean {
        if (text?.toString() == value.toString()) return false
        text = value
        return true
    }

    /** Seek line plus time text; while the line is being dragged the shown time is the drag target. */
    private fun renderTime(now: NowPlaying?) {
        seekLine.set(now?.positionMs ?: 0L, now?.durationMs ?: 0L)
        time.show(
            if (now == null) "" else MeloComponentPolicy.progressText(
                ((previewMs ?: now.positionMs) / 1000).toInt(), (now.durationMs / 1000).toInt()
            )
        )
    }

    private fun renderNow(now: NowPlaying?) {
        if (now == null) {
            if (title.show("Chưa phát bài nào")) requestArtFit()
            source.show("")
            showQuality(emptyList(), "")
            renderTime(null)
            play.show("▶")
            showArt(null, null, "♪")
            return
        }
        var relayout = title.show(now.title)
        source.show(MeloNowPlayingPolicy.sourceLine(now.sourceTitle, now.local, now.sourceIsFolder).orEmpty())
        showQuality(host.qualityChips(fileBitDepth), now.downloadNote?.takeUnless { now.download?.state == MeloDownloadUiPolicy.State.READY }.orEmpty())
        renderTime(now)
        play.show(if (now.playing) "❚❚" else "▶")
        play.contentDescription = if (now.playing) "Dừng" else "Phát"
        relayout = styleToggle(shuffle, now.shuffle, "⇄ Đảo") or relayout
        relayout = styleToggle(repeat, now.repeatLabel != "tắt", MeloNowPlayingPolicy.repeatChipLabel(now.repeatLabel)) or relayout
        val save = MeloSavePolicy.buttonState(now.saveAvailability, now.favorite, now.saveBusy)
        relayout = styleToggle(favorite, save.saved, MeloSavePolicy.chipLabel(save.saved)) or relayout
        shuffle.contentDescription = if (now.shuffle) "Đảo bài: bật" else "Đảo bài: tắt"
        repeat.contentDescription = "Lặp: ${now.repeatLabel}"
        favorite.contentDescription = save.contentDescription
        showArt(now.art ?: coverFromFile, now.sourceTitle, now.title)
        if (relayout) requestArtFit()
    }

    private fun showQuality(chips: List<MeloQualityChipPolicy.Chip>, note: String) {
        val chipsChanged = qualityChips.setChips(chips)
        val noteChanged = qualityNote.show(note)
        if (chipsChanged || noteChanged) {
            updateQualityVisibility()
            requestArtFit()
        }
    }

    private fun styleToggle(chip: TextView, on: Boolean, label: String): Boolean {
        val state = "$on|$label"
        if (chip.tag == state) return false
        chip.tag = state
        chip.text = label
        chip.setTextColor(if (on) p.onAccent else p.text)
        chip.background = if (on) c.pill(p.accent) else c.pill(p.surfaceRaised, p.border)
        return true
    }

    private fun showArt(art: Bitmap?, sourceTitle: String?, songTitle: String) {
        val side = artHost.layoutParams?.width ?: 0
        if (side <= 0) return
        val key = Triple(art, songTitle, side)
        if (key == artKey) return
        artKey = key
        artHost.removeAllViews()
        artHost.addView(c.artView(art, sourceTitle, songTitle, side).apply {
            layoutParams = FrameLayout.LayoutParams(side, side)
        })
    }

    private fun reloadQueue() { queuePane.update() }

    private fun showSleepMenu(anchor: View) {
        PopupMenu(activity, anchor).apply {
            SleepOption.entries.forEachIndexed { i, option -> menu.add(0, i, i, option.label) }
            setOnMenuItemClickListener { item ->
                val option = SleepOption.entries[item.itemId]
                host.setSleepTimer(option)
                val msg = when {
                    option == SleepOption.OFF -> "Đã tắt hẹn giờ"
                    option == SleepOption.END_OF_TRACK -> "Sẽ dừng khi hết bài này"
                    else -> "Sẽ dừng nhạc sau ${option.label}"
                }
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                refresh()
                true
            }
        }.show()
    }

    // ---- Bit depth + embedded cover (background, header reads only) ---------------------------

    /**
     * Two light jobs on the worker once the song's file is complete: the bit depth of a LOSSLESS
     * file (header only, so the Hi-Res pill is true) and, for any format, the embedded cover when
     * the player has none. Nothing is decoded and no waveform is computed.
     */
    private fun startProbe(now: NowPlaying) {
        val key = now.songKey ?: return
        val needDepth = MeloNowPlayingPolicy.shouldProbeBitDepth(now.filename)
        val needCover = now.art == null
        if (!needDepth && !needCover) {
            probedKey = key
            return
        }
        val file = host.currentLocalFile() ?: return // not fully downloaded yet: retry next tick
        val generation = jobGeneration.incrementAndGet()
        val side = (artHost.layoutParams?.width ?: 0).coerceAtLeast(s.rowArt)
        probing = true
        runCatching {
            worker.execute {
                val cancelled = { jobGeneration.get() != generation }
                val depth = if (needDepth && !cancelled()) MeloWaveform.probeBitDepth(file) else null
                val cover = if (needCover && !cancelled()) embeddedCover(file, side) else null
                handler.post {
                    if (jobGeneration.get() != generation) return@post
                    probing = false
                    probedKey = key
                    depth?.let { fileBitDepth = it }
                    if (cover != null) coverFromFile = cover
                    refresh()
                }
            }
        }.onFailure { probing = false }
    }

    private fun embeddedCover(file: File, side: Int): Bitmap? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val bytes = retriever.embeddedPicture ?: return null
            if (bytes.size > 8_000_000) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) retriever.close() else retriever.release()
        }
    }.getOrNull()
}
