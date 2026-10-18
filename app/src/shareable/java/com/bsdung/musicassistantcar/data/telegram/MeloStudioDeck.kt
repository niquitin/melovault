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
 * Studio Deck Now Playing (docs/ui-spec.md step 6). Portrait: big cover, title,
 * channel, real quality label, waveform scrubber, −30s / Lùi / Phát / Tiến / +60s,
 * Đảo / Lặp / Lưu, then the queue below. Landscape: player on the left, numbered
 * queue on the right. EQ and Hẹn giờ sit at the top (EQ is not available yet).
 */
internal class MeloStudioDeck(private val activity: Activity, private val host: MeloScreenHost) {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "melo-waveform").apply { isDaemon = true } }
    private val jobGeneration = AtomicLong(0)
    private val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar)

    private val c: MeloComponents
    private val s get() = c.sizes
    private val p get() = c.palette
    private val layout: DeckLayout

    private val artHost = FrameLayout(activity)
    private val title: TextView
    private val source: TextView
    private val quality: TextView
    private val time: TextView
    private val waveform: MeloWaveformView
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
    private var peaksKey: Long? = null
    private var analysing = false
    private var fileBitDepth: Int? = null
    private var coverFromFile: Bitmap? = null
    private var artKey: Any? = null
    private var tick = 0
    private var scrolledFor: Long? = null
    private var fitScroll: ScrollView? = null
    private var fitColumn: LinearLayout? = null
    private var fitPosted = false
    private var playerTight = false
    private val transportRow = LinearLayout(activity)
    private val extrasRow = LinearLayout(activity)

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
        val chosen = MeloScreenPolicy.profileFor(
            MeloScreenPolicy.chooseLayout(metrics.windowWidthDp, metrics.windowHeightDp, carMode = false), metrics.smallestWidthDp
        )
        val profile = if (chosen == MeloDesignTokens.Profile.TABLET) chosen else MeloDesignTokens.Profile.PHONE
        c = MeloComponents(activity, MeloDesignTokens.sizes(profile, d.density, d.fontScale, d.physicalPpi))
        title = c.text("", s.playerTitleText, p.text, bold = true, maxLines = 2)
        source = c.text("", s.titleText, p.textSecondary, maxLines = 1)
        quality = c.text("", s.secondaryText, p.accent, maxLines = 1)
        time = c.text("", s.secondaryText, p.textSecondary)
        waveform = MeloWaveformView(activity, p.accent, p.border) { fraction ->
            val dur = host.nowPlaying()?.durationMs ?: 0L
            if (dur > 0L) host.seekTo((dur * fraction).toLong())
        }
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
        handler.removeCallbacks(update)
        handler.post(update)
    }

    fun isShowing(): Boolean = dialog.isShowing

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss() else stop()
    }

    private fun stop() {
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
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return root
    }

    private fun buildPortrait(): View {
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.screenMargin, 0, s.screenMargin, s.screenMargin)
        }
        column.addView(topBar(), matchWrap())
        column.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap })
        addPlayerBody(column)
        column.addView(c.spacer(s.gap * 3))
        column.addView(queueHeader(), matchWrap())
        column.addView(queueRows, matchWrap())
        column.addView(c.chip("Xem cả hàng đợi", false) { host.openQueue() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, s.minTouch).apply { topMargin = s.gap })
        scroll.addView(column)
        scroll.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ ->
            if (r - l != or_ - ol) sizeArt(minOf(r - l - s.screenMargin * 2, (scroll.height * 0.45f).toInt()))
        }
        return scroll
    }

    private fun buildLandscape(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(s.screenMargin, 0, s.screenMargin, s.gap)
        }
        val left = ScrollView(activity).apply { isFillViewport = true }
        val player = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        player.addView(topBar(), matchWrap())
        player.addView(artHost, LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = s.gap })
        addPlayerBody(player)
        left.addView(player)
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 45f).apply { marginEnd = s.screenMargin })
        val right = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        right.addView(queueHeader(), matchWrap().apply { topMargin = s.gap })
        val list = ListView(activity).apply {
            adapter = queueAdapter
            divider = null
            dividerHeight = s.gap / 2
            setSelector(android.R.color.transparent)
            setOnItemClickListener { _, _, position, _ -> host.playQueueAt(position) }
        }
        queueList = list
        right.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        row.addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 55f))
        fitScroll = left
        fitColumn = player
        row.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (b - t != ob - ot || r - l != or_ - ol) requestArtFit()
        }
        return row
    }

    /** Low window (a phone on its side): no quality line, shorter waveform, half the gaps, so the buttons stay on screen. */
    private fun applyTight(tight: Boolean) {
        if (tight == playerTight) return
        playerTight = tight
        quality.visibility = if (tight) View.GONE else View.VISIBLE
        val top = if (tight) s.gap else s.gap * 2
        listOf<View>(title, waveform, transportRow, extrasRow).forEach { v ->
            (v.layoutParams as LinearLayout.LayoutParams).topMargin = top
            v.requestLayout()
        }
        (waveform.layoutParams as LinearLayout.LayoutParams).height = if (tight) s.minTouch else s.minTouch * 3 / 2
    }

    /** Cover = the column height minus every other row, so the controls never fall below the screen. */
    private fun requestArtFit() {
        val scroll = fitScroll ?: return
        if (fitPosted) return
        fitPosted = true
        scroll.post {
            fitPosted = false
            val column = fitColumn ?: return@post
            val w = scroll.width
            val h = scroll.height
            if (w <= 0 || h <= 0) return@post
            applyTight(false)
            var rest = MeloArtFit.restHeight(column, artHost, w)
            if (MeloNowPlayingPolicy.playerTight(h, rest, s.rowArt)) {
                applyTight(true)
                rest = MeloArtFit.restHeight(column, artHost, w)
            }
            sizeArt(MeloNowPlayingPolicy.artSide(w, h, rest, s.rowArt))
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
        column.addView(quality, matchWrap().apply { topMargin = s.gap / 2 })
        column.addView(waveform, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch * 3 / 2).apply {
            topMargin = s.gap * 2
        })
        column.addView(time.apply { gravity = Gravity.END }, matchWrap())

        val transport = transportRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun small(label: String, description: String, action: () -> Unit) =
            c.iconButton(label, description) { action() }.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
                layoutParams = LinearLayout.LayoutParams(0, s.minTouch, 1f).apply { marginEnd = s.gap / 2 }
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
        column.addView(transport, matchWrap().apply { topMargin = s.gap * 2 })

        val extras = extrasRow.apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL }
        shuffle = c.chip("⇄ Đảo", false) { host.toggleShuffle(); refresh() }
        repeat = c.chip("↻ Lặp", false) { host.cycleRepeat(); refresh() }
        favorite = c.chip("☆ Lưu", false) { host.toggleFavorite(); refresh() }
        listOf(shuffle, repeat, favorite).forEach { extras.addView(it) }
        column.addView(extras, matchWrap().apply { topMargin = s.gap * 2 })
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
            peaksKey = null
            fileBitDepth = null
            coverFromFile = null
            waveform.setPeaks(null)
            reloadQueue()
        } else if (now?.downloadNote?.startsWith("Đã tải") == false && ++tick % 5 == 0) {
            // Only while the current song is still downloading does a queue row change by itself.
            reloadQueue()
        }
        if (now != null && peaksKey != now.songKey && !analysing) startAnalysis(now)
    }

    /** Setting the same text still re-lays out a TextView; the 1 s tick must not do that. */
    private fun TextView.show(value: CharSequence): Boolean {
        if (text?.toString() == value.toString()) return false
        text = value
        return true
    }

    private fun renderNow(now: NowPlaying?) {
        if (now == null) {
            if (title.show("Chưa phát bài nào")) requestArtFit()
            source.show("")
            quality.show("")
            time.show("")
            waveform.setProgress(0f)
            play.show("▶")
            showArt(null, null, "♪")
            return
        }
        if (title.show(now.title)) requestArtFit()
        source.show(now.sourceTitle.orEmpty())
        val q = host.qualityLabel(fileBitDepth)
        quality.show(listOfNotNull(q, now.downloadNote?.takeUnless { it.startsWith("Đã tải") }).joinToString(" · "))
        waveform.setProgress(if (now.durationMs > 0L) now.positionMs.toFloat() / now.durationMs else 0f)
        time.show(MeloComponentPolicy.progressText((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt()))
        play.show(if (now.playing) "❚❚" else "▶")
        play.contentDescription = if (now.playing) "Dừng" else "Phát"
        styleToggle(shuffle, now.shuffle, if (now.shuffle) "⇄ Đảo: bật" else "⇄ Đảo")
        styleToggle(repeat, now.repeatLabel != "tắt", "↻ Lặp: ${now.repeatLabel}")
        styleToggle(favorite, now.favorite, if (now.favorite) "★ Đã lưu" else "☆ Lưu")
        showArt(now.art ?: coverFromFile, now.sourceTitle, now.title)
    }

    private fun styleToggle(chip: TextView, on: Boolean, label: String) {
        val state = "$on|$label"
        if (chip.tag == state) return
        chip.tag = state
        chip.text = label
        chip.setTextColor(if (on) p.onAccent else p.text)
        chip.background = if (on) c.pill(p.accent) else c.pill(p.surfaceRaised, p.border)
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
        return c.songRow(SongRowModel(
            title = "${index + 1}. ${item.title}",
            sourceTitle = item.sourceTitle,
            durationSeconds = item.durationSeconds,
            status = status,
            progressPercent = item.downloadingPercent
        ))
    }

    private inner class QueueAdapter : BaseAdapter() {
        override fun getCount(): Int = queue.size
        override fun getItem(position: Int): Any = queue[position]
        override fun getItemId(position: Int): Long = queue[position].songKey
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            queueRow(queue[position], position).also { unclickable(it) }
    }

    private fun unclickable(v: View) {
        v.isClickable = false
        v.isLongClickable = false
        v.isFocusable = false
        if (v is ViewGroup) for (i in 0 until v.childCount) unclickable(v.getChildAt(i))
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

    // ---- Waveform + embedded cover (background) -------------------------------------------

    private fun startAnalysis(now: NowPlaying) {
        val key = now.songKey ?: return
        val file = host.currentLocalFile() ?: return // not fully downloaded yet: plain bar, retry next tick
        val account = host.accountId()
        val cache = MeloWaveform.cacheFile(activity.cacheDir, MeloNowPlayingPolicy.waveformCacheName(account, key, file.length()))
        val generation = jobGeneration.incrementAndGet()
        val side = (artHost.layoutParams?.width ?: 0).coerceAtLeast(s.rowArt)
        val needCover = now.art == null
        analysing = true
        runCatching {
            worker.execute {
                val cancelled = { jobGeneration.get() != generation }
                val cached = MeloWaveform.loadCached(cache)
                val result = if (cached != null) MeloWaveform.Result(cached, null)
                else MeloWaveform.analyze(file, MeloNowPlayingPolicy.WAVEFORM_BUCKETS, cancelled)?.also {
                    MeloWaveform.saveCached(cache, it.peaks)
                }
                val cover = if (needCover && !cancelled()) embeddedCover(file, side) else null
                handler.post {
                    if (jobGeneration.get() != generation) return@post
                    analysing = false
                    peaksKey = key
                    waveform.setPeaks(result?.peaks)
                    result?.bitDepth?.let { fileBitDepth = it }
                    if (cover != null) coverFromFile = cover
                    refresh()
                }
            }
        }.onFailure { analysing = false }
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
