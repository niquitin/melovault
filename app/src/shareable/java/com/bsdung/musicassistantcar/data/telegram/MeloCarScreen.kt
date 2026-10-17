package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.bsdung.musicassistantcar.data.telegram.MeloCarPolicy.Nav
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloComponents.SongRowModel
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.CarReferencePx
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.Snapshot

/**
 * Car Mode for the FYT 7870 head unit, docs/ui-spec.md step 5. Landscape, left to right:
 * navigation column (160px), player pane (760px), list pane (1000px) at the panel's real
 * pixels. Five numbered songs per page with page buttons, no swiping required, no
 * transitions. Every list and action goes through [MeloScreenHost].
 */
internal class MeloCarScreen(
    private val context: Context,
    sizes: Sizes,
    private val host: MeloScreenHost,
    private val backDispatcher: OnBackPressedDispatcher,
    private val voiceAvailable: Boolean
) {
    private val c = MeloComponents(context, sizes)
    private val s = c.sizes
    private val p = c.palette
    private val handler = Handler(Looper.getMainLooper())

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(p.background)
        setPadding(0, s.gap, s.screenMargin, s.gap)
    }

    private val navColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val navButtons = mutableMapOf<Nav, LinearLayout>()
    private val leftPane = FrameLayout(context)
    private val playerPane = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val searchPane = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val listPane = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val listTitle = c.text("", s.titleText, p.text, bold = true)
    private val listStatus = c.text("", s.secondaryText, p.textSecondary)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var searchField: EditText

    // Player pane parts.
    private val artHost = FrameLayout(context)
    private val songTitle = c.text("Chọn một bài để phát", s.playerTitleText, p.text, bold = true, maxLines = 2)
    private val songSource = c.text("", s.secondaryText, p.textSecondary, maxLines = 1)
    private val songQuality = c.text("", s.secondaryText, p.accent, maxLines = 1)
    private val seek = SeekBar(context)
    private val time = c.text("", s.secondaryText, p.textSecondary)
    private lateinit var play: TextView
    private var userSeeking = false
    private var lastArt: Bitmap? = null
    private var lastArtKey: Long? = null

    private var snapshot: Snapshot? = null
    private var listKey: String? = null
    private var page = 0
    private var searchOpen = false
    private var online = true
    private var pendingQuery: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                searchOpen -> closeSearch()
                snapshot?.showingSources == false && snapshot?.sourceTitle != null -> host.showSources()
                else -> { isEnabled = false; backDispatcher.onBackPressed() }
            }
        }
    }

    init {
        buildNav()
        buildPlayer()
        buildSearch()
        buildList()
        view.addView(navColumn, LinearLayout.LayoutParams(s.navColumnWidth, ViewGroup.LayoutParams.MATCH_PARENT))
        leftPane.addView(playerPane, matchFrame())
        leftPane.addView(searchPane, matchFrame())
        // Player and list keep the spec's 760:1000 ratio inside whatever width the panel has.
        view.addView(leftPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
            CarReferencePx.PLAYER_PANE.toFloat()).apply { marginEnd = s.screenMargin })
        view.addView(listPane, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT,
            CarReferencePx.LIST_PANE.toFloat()))
    }

    fun attach() {
        backDispatcher.addCallback(backCallback)
        watchNetwork()
        onLibraryChanged()
        onPlaybackChanged()
    }

    fun detach() {
        backCallback.remove()
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let { cb ->
            runCatching { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb) }
        }
        networkCallback = null
    }

    private fun matchFrame() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)


    private fun bigButton(label: String, description: String, primary: Boolean = false, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.titleText)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) p.onAccent else p.text)
            background = c.roundedBackground(if (primary) p.accent else p.surfaceRaised)
            contentDescription = description
            minHeight = s.minTouch
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    // ---- Navigation column -------------------------------------------------------------------

    private fun buildNav() {
        val iconSize = s.secondaryText * 56f / CarReferencePx.SECONDARY
        val labelSize = s.secondaryText * 26f / CarReferencePx.SECONDARY
        Nav.entries.forEach { nav ->
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = nav.label
                addView(c.text(nav.icon, iconSize, p.text).apply { gravity = Gravity.CENTER })
                addView(c.text(nav.label, labelSize, p.text).apply { gravity = Gravity.CENTER })
                setOnClickListener { onNav(nav) }
            }
            navButtons[nav] = item
            navColumn.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                bottomMargin = s.gap / 2
                marginStart = s.gap / 2
                marginEnd = s.gap
            })
        }
    }

    private fun onNav(nav: Nav) {
        if (nav != Nav.SEARCH && searchOpen) closeSearch()
        when (nav) {
            Nav.MUSIC -> { host.openSource(null); host.setMode(Mode.ALL) }
            Nav.TELEGRAM -> host.showSources()
            Nav.PLAYLIST -> host.setMode(Mode.PLAYLIST)
            Nav.DOWNLOADED -> host.setMode(Mode.DOWNLOADED)
            Nav.SEARCH -> openSearch()
            Nav.SETTINGS -> host.openUiLab()
        }
    }

    private fun renderNav() {
        val snap = snapshot ?: return
        val active = MeloCarPolicy.activeNav(snap.mode, snap.showingSources, searchOpen)
        navButtons.forEach { (nav, button) ->
            val on = nav == active
            button.background = if (on) c.roundedBackground(p.accentSoft, stroke = p.accent) else null
            for (i in 0 until button.childCount) (button.getChildAt(i) as? TextView)?.setTextColor(if (on) p.accent else p.text)
        }
    }

    // ---- Player pane -----------------------------------------------------------------------------

    private fun buildPlayer() {
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(artHost, LinearLayout.LayoutParams(s.playerArt, s.playerArt))
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(s.gap * 3, 0, 0, 0)
        }
        texts.addView(songTitle, matchWrap())
        texts.addView(songSource, matchWrap().apply { topMargin = s.gap })
        texts.addView(songQuality, matchWrap().apply { topMargin = s.gap / 2 })
        top.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playerPane.addView(top, matchWrap())

        seek.apply {
            max = 1000
            progressDrawable = progressTrack()
            thumb = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(p.accent)
                setSize(s.progressThumb, s.progressThumb)
            }
            splitTrack = false
            minimumHeight = s.progressThumb
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(bar: SeekBar?) { userSeeking = true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    userSeeking = false
                    val dur = host.nowPlaying()?.durationMs ?: return
                    if (dur > 0L && bar != null) host.seekTo(dur * bar.progress / 1000L)
                }
            })
        }
        playerPane.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.progressThumb).apply { topMargin = s.gap * 3 })
        playerPane.addView(time.apply { gravity = Gravity.END }, matchWrap())

        val transport = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        transport.addView(bigButton("⏮", "Bài trước") { host.previous() },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f).apply { marginEnd = s.gap * 2 })
        play = bigButton("▶", "Phát", primary = true) { host.playPause() }.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.playerTitleText * 1.4f)
        }
        transport.addView(play, LinearLayout.LayoutParams(s.playButtonWidth, s.playButtonHeight))
        transport.addView(bigButton("⏭", "Bài kế") { host.next() },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f).apply { marginStart = s.gap * 2 })
        playerPane.addView(transport, matchWrap().apply { topMargin = s.gap * 2 })

        val seekRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        seekRow.addView(bigButton("⏪ 30s", "Tua lùi 30 giây") { seekBy(MeloCarPolicy.SEEK_BACK_MS) },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f).apply { marginEnd = s.gap * 2 })
        seekRow.addView(bigButton("60s ⏩", "Tua tới 60 giây") { seekBy(MeloCarPolicy.SEEK_FORWARD_MS) },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f))
        playerPane.addView(seekRow, matchWrap().apply { topMargin = s.gap * 2 })
    }

    private fun progressTrack(): LayerDrawable {
        val radius = s.progressHeight / 2f
        val track = GradientDrawable().apply { setColor(p.border); cornerRadius = radius; setSize(1, s.progressHeight) }
        val fill = ClipDrawable(GradientDrawable().apply { setColor(p.accent); cornerRadius = radius }, Gravity.START, ClipDrawable.HORIZONTAL)
        return LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
    }

    private fun seekBy(deltaMs: Long) {
        val now = host.nowPlaying() ?: return
        host.seekTo(MeloCarPolicy.seekTarget(now.positionMs, now.durationMs, deltaMs))
    }

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        renderPlayer(now)
        if (snapshot != null && snapshot?.playingKey != now?.songKey) onLibraryChanged()
    }

    private fun renderPlayer(now: NowPlaying?) {
        if (now == null) {
            songTitle.text = "Chọn một bài để phát"
            songSource.text = ""
            songQuality.text = ""
            time.text = ""
            seek.progress = 0
            play.text = "▶"
            showArt(null, null, "♪", null)
            return
        }
        songTitle.text = now.title
        songSource.text = now.sourceTitle.orEmpty()
        val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(null, now.filename), null, null, null)
        val downloaded = now.downloadNote == null || now.downloadNote.startsWith("Đã tải")
        songQuality.text = listOfNotNull(quality, if (downloaded) "Đã tải" else now.downloadNote).joinToString(" · ")
        if (!userSeeking) {
            seek.progress = if (now.durationMs > 0L) ((now.positionMs * 1000L) / now.durationMs).toInt().coerceIn(0, 1000) else 0
        }
        time.text = MeloComponentPolicy.progressText((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt())
        play.text = if (now.playing) "❚❚" else "▶"
        play.contentDescription = if (now.playing) "Dừng" else "Phát"
        showArt(now.art, now.sourceTitle, now.title, now.songKey)
    }

    private fun showArt(art: Bitmap?, sourceTitle: String?, title: String, key: Long?) {
        if (artHost.childCount > 0 && art === lastArt && key == lastArtKey) return
        artHost.removeAllViews()
        artHost.addView(c.artView(art, sourceTitle, title, s.playerArt).apply {
            layoutParams = FrameLayout.LayoutParams(s.playerArt, s.playerArt)
        })
        lastArt = art
        lastArtKey = key
    }

    // ---- Search pane ------------------------------------------------------------------------------

    private fun buildSearch() {
        searchPane.addView(c.text("Tìm bài hát", s.playerTitleText, p.text, bold = true), matchWrap())
        searchField = EditText(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.playerTitleText)
            setTextColor(p.text)
            setHintTextColor(p.textSecondary)
            background = c.roundedBackground(p.surface, stroke = p.accent)
            setPadding(s.gap * 3, 0, s.gap * 3, 0)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, d: Int) = Unit
                override fun onTextChanged(t: CharSequence?, a: Int, b: Int, d: Int) = Unit
                override fun afterTextChanged(e: Editable?) = queueQuery(e?.toString().orEmpty())
            })
        }
        searchPane.addView(searchField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.transportHeight).apply {
            topMargin = s.gap * 2
        })
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        if (voiceAvailable) {
            actions.addView(bigButton("🎤 Nói", "Tìm bằng giọng nói", primary = true) {
                if (!host.startVoiceSearch { text -> searchField.setText(text); searchField.setSelection(text.length) }) {
                    Toast.makeText(context, "Máy này không có nhận giọng nói", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, s.transportHeight, 1f).apply { marginEnd = s.gap * 2 })
        }
        actions.addView(bigButton("Xoá", "Xoá chữ đã gõ") { searchField.setText("") },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f).apply { marginEnd = s.gap * 2 })
        actions.addView(bigButton("Đóng", "Đóng tìm kiếm") { closeSearch() },
            LinearLayout.LayoutParams(0, s.transportHeight, 1f))
        searchPane.addView(actions, matchWrap().apply { topMargin = s.gap * 2 })
        searchPane.addView(c.text("Kết quả hiện ở danh sách bên phải. Gõ đúng dấu tiếng Việt để tìm chính xác.",
            s.secondaryText, p.textSecondary, maxLines = 3), matchWrap().apply { topMargin = s.gap * 2 })
    }

    private fun openSearch() {
        if (searchOpen) return
        searchOpen = true
        if (snapshot?.showingSources == true) host.openSource(null)
        playerPane.visibility = View.GONE
        searchPane.visibility = View.VISIBLE
        searchField.hint = MeloComponentPolicy.searchHint(snapshot?.total)
        searchField.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        backCallback.isEnabled = true
        renderNav()
    }

    private fun closeSearch() {
        searchOpen = false
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(searchField.windowToken, 0)
        searchPane.visibility = View.GONE
        playerPane.visibility = View.VISIBLE
        onLibraryChanged()
    }

    private fun queueQuery(query: String) {
        pendingQuery?.let(handler::removeCallbacks)
        val task = Runnable { host.setQuery(query) }
        pendingQuery = task
        handler.postDelayed(task, 300L)
    }

    // ---- List pane ---------------------------------------------------------------------------------

    private fun buildList() {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = s.minTouch * 7 / 10
        }
        header.addView(listTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(listStatus)
        listPane.addView(header, matchWrap())
        listPane.addView(rows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val paging = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        paging.addView(bigButton("▲ Cuộn trang lên", "Trang trước") { turnPage(-1) },
            LinearLayout.LayoutParams(0, s.pageButtonHeight, 1f).apply { marginEnd = s.gap * 2 })
        paging.addView(bigButton("▼ Cuộn trang xuống", "Trang sau") { turnPage(1) },
            LinearLayout.LayoutParams(0, s.pageButtonHeight, 1f))
        listPane.addView(paging, matchWrap().apply { topMargin = s.gap })
    }

    private fun perPage(snap: Snapshot) = if (snap.showingSources) MeloCarPolicy.SOURCES_PER_PAGE else MeloCarPolicy.ROWS_PER_PAGE

    private fun itemCount(snap: Snapshot) = if (snap.showingSources) visibleSources(snap).size else snap.rows.size

    /** Car Telegram page drops the "Các kênh chưa có nhạc" sources. */
    private fun visibleSources(snap: Snapshot) = snap.sources.filter { it.count > 0 }

    private fun turnPage(delta: Int) {
        val snap = snapshot ?: return
        page = MeloCarPolicy.clampPage(page + delta, itemCount(snap), perPage(snap))
        if (!snap.showingSources && MeloCarPolicy.shouldLoadMore(page, perPage(snap), snap.rows.size, snap.total)) host.loadMore()
        renderList()
    }

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        val key = "${snap.showingSources}:${snap.mode}:${snap.sourceTitle}:${snap.query}"
        if (key != listKey) {
            listKey = key
            page = 0
        }
        page = MeloCarPolicy.clampPage(page, itemCount(snap), perPage(snap))
        backCallback.isEnabled = searchOpen || (!snap.showingSources && snap.sourceTitle != null)
        renderNav()
        renderList()
    }

    private fun renderList() {
        val snap = snapshot ?: return
        val per = perPage(snap)
        val pages = MeloCarPolicy.pageCount(itemCount(snap), per)
        listTitle.text = if (snap.showingSources) {
            "Telegram · ${MeloComponentPolicy.formatCount(visibleSources(snap).count { it.chatId != 0L })} nguồn"
        } else if (searchOpen && snap.query.isNotBlank()) {
            "Kết quả: \"${snap.query}\"" + (snap.total?.let { " · ${MeloComponentPolicy.formatCount(it)} bài" } ?: "")
        } else {
            MeloCompactPolicy.headerTitle(snap.mode, snap.sourceTitle, snap.total)
        }
        listStatus.text = listOfNotNull(if (online) null else "Mất kết nối", "Trang ${page + 1}/$pages").joinToString(" · ")
        listStatus.setTextColor(if (online) p.textSecondary else p.warning)
        if (snap.showingSources) {
            rows.removeAllViews()
            renderSources(snap)
        } else {
            renderSongs(snap)
        }
    }

    /** Five rows built once; a page turn only rebinds them (Sony test F3: no view creation per tap). */
    private val holders = List(MeloCarPolicy.ROWS_PER_PAGE) { c.songRowHolder() }
    private val emptySongs = c.text("", s.titleText, p.textSecondary, maxLines = 3)
    private val songBox = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(emptySongs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        holders.forEachIndexed { i, h ->
            addView(h.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.rowHeight).apply {
                if (i < holders.size - 1) bottomMargin = s.gap
            })
        }
    }

    private fun renderSongs(snap: Snapshot) {
        if (rows.childCount != 1 || rows.getChildAt(0) !== songBox) {
            rows.removeAllViews()
            rows.addView(songBox, matchWrap())
        }
        val per = MeloCarPolicy.ROWS_PER_PAGE
        val start = page * per
        val shown = MeloCarPolicy.pageItems(snap.rows, page, per)
        emptySongs.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        if (shown.isEmpty()) emptySongs.text = snap.note ?: "Không có bài nào"
        holders.forEachIndexed { i, holder ->
            val row = shown.getOrNull(i)
            if (row == null) {
                holder.view.visibility = View.GONE
                return@forEachIndexed
            }
            val index = start + i
            val playing = row.songKey == snap.playingKey
            val dimmed = MeloCarPolicy.isOfflineDimmed(online, row.downloaded)
            val status = MeloCompactPolicy.rowStatus(row.downloaded, row.downloadingPercent, playing)
            val quality = MeloComponentPolicy.qualityLabel(MeloComponentPolicy.formatOf(null, row.filename), null, null, null)
            holder.bind(
                SongRowModel(row.title, row.sourceTitle, row.durationSeconds, status, quality, row.downloadingPercent, row.art, index + 1),
                onClick = {
                    if (dimmed) Toast.makeText(context, "Mất kết nối – chỉ phát được bài đã tải", Toast.LENGTH_SHORT).show()
                    else host.playAt(index)
                },
                onLongClick = { host.openSongMenu(index, rows) }
            )
            holder.view.alpha = if (dimmed) 0.4f else 1f
            holder.view.visibility = View.VISIBLE
        }
    }

    private fun renderSources(snap: Snapshot) {
        val all = visibleSources(snap)
        val shown = MeloCarPolicy.pageItems(all, page, MeloCarPolicy.SOURCES_PER_PAGE)
        shown.chunked(2).forEach { pair ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, source ->
                line.addView(sourceCard(source), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i == 0) marginEnd = s.gap * 2
                })
            }
            if (pair.size == 1) line.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            rows.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { bottomMargin = s.gap * 2 })
        }
    }

    private fun sourceCard(source: MeloScreenHost.SourceRow): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(s.gap * 3, s.gap * 2, s.gap * 3, s.gap * 2)
        background = c.roundedBackground(p.surface, stroke = p.border)
        isClickable = true
        isFocusable = true
        val isAll = source.chatId == 0L
        val name = if (isAll) "Tất cả nguồn" else source.title
        contentDescription = "$name, ${source.count} bài"
        // Spec asks ≥22sp; on the FYT panel the list title size reads better from 70cm.
        addView(c.text(name, s.titleText, p.text, bold = true, maxLines = 2), matchWrap())
        addView(c.text("${MeloComponentPolicy.formatCount(source.count)} bài", s.secondaryText, p.textSecondary), matchWrap().apply {
            topMargin = s.gap
        })
        setOnClickListener { host.openSource(source.chatId.takeIf { it != 0L }) }
    }

    // ---- Network ---------------------------------------------------------------------------------

    private fun watchNetwork() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = setOnline(true)
            override fun onLost(network: Network) = setOnline(cm.activeNetwork != null && cm.activeNetwork != network)
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun setOnline(value: Boolean) {
        handler.post {
            if (online == value) return@post
            online = value
            renderList()
        }
    }
}
