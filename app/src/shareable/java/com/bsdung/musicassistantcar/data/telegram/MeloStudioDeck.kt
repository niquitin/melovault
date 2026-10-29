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
    private val queueTitle: TextView
    private val prefetchSwitch: Switch
    private val queueRows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private var queueList: ListView? = null
    private val queueAdapter = QueueAdapter()

    private var queue: List<QueueItem> = emptyList()
    private var currentKey: Long? = null
    private var probedKey: Long? = null
    private var probing = false
    private var fileBitDepth: Int? = null
    private var coverFromFile: Bitmap? = null
    private var artKey: Any? = null
    private var tick = 0
    private var scrolledFor: Long? = null
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
        transportPx = maxOf(c.sizes.minTouch, (MeloNowPlayingPolicy.transportHeightDp(metrics.windowHeightDp) * d.density).toInt())
        title = c.text("", s.playerTitleText, p.text, bold = true, maxLines = 2)
        source = c.text("", s.titleText, p.textSecondary, maxLines = 1)
        qualityChips = MeloQualityChipRow(activity, c, s.minTouch * 2 / 3).apply { visibility = View.GONE }
        qualityNote = c.text("", s.secondaryText, p.accent, maxLines = 1).apply { visibility = View.GONE }
        time = c.text("", s.secondaryText, p.textSecondary)
        seekLine = MeloSeekLine(
            activity, p.border, p.accent, p.accent, s.progressHeight, s.progressThumb, s.minTouch,
            onPreview = { previewMs = it; renderTime(host.nowPlaying()) },
            onSeek = { host.seekTo(it); refresh() }
        )
        queueTitle = c.text("Hàng đợi", s.titleText, p.text, bold = true)
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
        queueList?.post {
            val index = queue.indexOfFirst { it.songKey == saved.songKey }
            if (index >= 0) { queueList?.setSelectionFromTop(index, saved.offsetPx); scrolledFor = currentKey }
        }
        fitScroll?.post { fitScroll?.scrollTo(0, saved.bodyScrollY) }
        handler.removeCallbacks(update)
        handler.post(update)
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss() else stop()
    }

    fun saveUiState() {
        if (browseOwner <= 0L || host.accountId() != browseOwner) return
        val list = queueList
        host.saveDeckState(MeloBrowseState.Deck(songKey = list?.firstVisiblePosition?.let { queue.getOrNull(it)?.songKey },
            offsetPx = list?.getChildAt(0)?.top ?: 0, bodyScrollY = fitScroll?.scrollY ?: 0))
    }

    private fun stop() {
        saveUiState()
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
            v.setPadding(bars.left, bars.top + MeloUiLabActivity.handleBandPx(v, insets), bars.right, bars.bottom)
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
        column.addView(queueHeader(), matchWrap())
        column.addView(queueRows, matchWrap())
        column.addView(c.chip("Xem cả hàng đợi", false) { host.openQueue() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, s.minTouch).apply { topMargin = s.gap })
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
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        right.addView(queueHeader(), matchWrap().apply { topMargin = s.gap })
        val list = ListView(activity).apply {
            adapter = queueAdapter
            divider = null
            dividerHeight = s.gap / 2
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ -> host.playQueueAt(position) }
            setOnItemLongClickListener { _, v, position, _ -> host.openQueueSongMenu(position, v); true }
        }
        queueList = list
        right.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
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
        transport.addView(small("−30s", "Tua lùi 30 giây") { seekBy(MeloNowPlayingPolicy.SEEK_BACK_MS) })
        transport.addView(small("⏮", "Bài trước") { host.previous() })
        play = c.iconButton("▶", "Phát", primary = true, width = s.playButtonWidth, height = s.playButtonHeight) { host.playPause() }
            .apply { layoutParams = LinearLayout.LayoutParams(s.playButtonWidth, s.playButtonHeight).apply { marginEnd = s.gap / 2 } }
        transport.addView(play)
        transport.addView(small("⏭", "Bài kế") { host.next() })
        transport.addView(small("+60s", "Tua tới 60 giây") { seekBy(MeloNowPlayingPolicy.SEEK_FORWARD_MS) }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = 0
        })
        dock.addView(transport, matchWrap().apply { topMargin = s.gap })
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
            chip.layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginEnd = if (last) 0 else s.gap }
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
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = s.minTouch
        addView(c.iconButton("˅", "Đóng màn Đang phát") { dismiss() }.apply { background = null; setTextColor(p.text) })
        addView(c.text("Đang phát", s.titleText, p.textSecondary).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(c.chip("EQ", false) {
            Toast.makeText(activity, "EQ sắp có trong bản sau", Toast.LENGTH_SHORT).show()
        }.apply { contentDescription = "Bộ chỉnh âm, sắp có" })
        sleep = c.chip("Hẹn giờ", false) { showSleepMenu(sleep) }.apply { contentDescription = "Hẹn giờ tắt nhạc" }
        addView(sleep)
    }

    private fun addPlayerBody(column: LinearLayout) {
        column.addView(title, matchWrap().apply { topMargin = s.gap * 2 })
        column.addView(source, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(qualityChips, matchWrap().apply { topMargin = s.gap })
        column.addView(qualityNote, matchWrap().apply { topMargin = s.gap / 2 })

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

    private fun queueHeader(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(queueTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        } else if (now?.downloadNote?.startsWith("Đã tải") == false && ++tick % 5 == 0) {
            // Only while the current song is still downloading does a queue row change by itself.
            reloadQueue()
        }
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
        showQuality(host.qualityChips(fileBitDepth), now.downloadNote?.takeUnless { it.startsWith("Đã tải") }.orEmpty())
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

    private fun reloadQueue() {
        queue = host.queue()
        val current = queue.indexOfFirst { it.songKey == currentKey }
        queueTitle.text = "Hàng đợi · ${MeloComponentPolicy.formatCount(queue.size)} bài"
        prefetchSwitch.isChecked = host.prefetchEnabled()
        val list = queueList
        if (list != null) {
            queueAdapter.notifyDataSetChanged()
            // Bring the current song into view once per track change, not on every refresh.
            if (current >= 0 && scrolledFor != currentKey) {
                scrolledFor = currentKey
                list.setSelection((current - 2).coerceAtLeast(0))
            }
        } else {
            // Portrait: a short window around the current song; the full list opens separately.
            queueRows.removeAllViews()
            val from = (current - 1).coerceAtLeast(0)
            queue.drop(from).take(12).forEachIndexed { i, item ->
                val index = from + i
                queueRows.addView(queueRow(item, index).apply {
                    setOnClickListener { host.playQueueAt(index) }
                }, matchWrap().apply { topMargin = s.gap / 2 })
            }
            if (queue.isEmpty()) queueRows.addView(c.text("Hàng đợi trống. Chọn một bài trong thư viện.", s.secondaryText, p.textSecondary, maxLines = 2))
        }
    }

    private fun queueRow(item: QueueItem, index: Int): View {
        val status = MeloNowPlayingPolicy.queueStatus(item.songKey == currentKey, item.downloaded, item.downloadingPercent)
        var row: View? = null
        row = c.songRow(SongRowModel(
            title = "${index + 1}. ${item.title}",
            sourceTitle = item.sourceTitle,
            durationSeconds = item.durationSeconds,
            status = status,
            progressPercent = item.downloadingPercent,
            local = item.local
        ), onMore = { row?.let { host.openQueueSongMenu(index, it) } })
        return row
    }

    private inner class QueueAdapter : BaseAdapter() {
        override fun getCount(): Int = queue.size
        override fun getItem(position: Int): Any = queue[position]
        override fun getItemId(position: Int): Long = queue[position].songKey
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            queueRow(queue[position], position).also { unclickable(it, position) }
    }

    /** The ListView drives the row (tap = play, long press = menu); only the ⋮ stays clickable. */
    private fun unclickable(v: View, position: Int) {
        if (v.tag == MELO_SONG_ROW_MORE_TAG) {
            v.setOnClickListener { host.openQueueSongMenu(position, v) }
            v.isFocusable = false
            return
        }
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) unclickable(v.getChildAt(i), position)
    }

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
