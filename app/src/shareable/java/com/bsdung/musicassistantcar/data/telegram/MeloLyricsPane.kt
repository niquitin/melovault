package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsUiPolicy.Action
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsUiPolicy.Tab
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import com.bsdung.musicassistantcar.lyrics.LyricsLine

/** What the lyrics pane needs from the app: the controller, the song now playing, and the stored choices. */
internal interface MeloLyricsAccess {
    fun controller(): ShareableLyricsController
    /** Cache owner; "" while signed out. */
    fun owner(): String
    /** Title, artist, album and length of the song now playing, null when nothing plays. */
    fun meta(): Meta?
    /** File name of the song now playing, only to pre-fill the search box. */
    fun filename(): String?
    fun consent(): LyricsConsent
    fun setConsent(granted: Boolean)
    fun tab(): Tab
    fun setTab(tab: Tab)
}

/**
 * The "Lời nhạc" tab body (Android Views). It draws a [LyricsState] from [ShareableLyricsController]: a message
 * with buttons, or the lyrics (the sung line is highlighted and followed, until the owner scrolls), and the
 * manual search. It asks for lyrics only while it is shown (LD8), and a lyrics failure never touches the player.
 */
internal class MeloLyricsPane(
    private val context: Context,
    private val c: MeloComponents,
    private val host: MeloScreenHost,
    /** Fixed height when the pane sits in a scrolling column (Deck portrait); null = fills what it is given. */
    private val fixedHeightPx: Int? = null
) {
    private val s = c.sizes
    private val p = c.palette
    private val handler = Handler(Looper.getMainLooper())

    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val message = c.text("", s.titleText, p.textSecondary, maxLines = 8)
    private val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val searchBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val searchInput = EditText(context)
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { isFillViewport = false; isVerticalScrollBarEnabled = false }
    private val jump = c.chip("Về dòng đang hát", true) { followAgain() }.apply { visibility = View.GONE }
    private val source = c.text(MeloLyricsUiPolicy.SOURCE_LINE, s.secondaryText, p.textSecondary)

    private data class Key(val owner: String, val signature: String?, val consent: LyricsConsent)

    private var shown = false
    private var requestedKey: Key? = null
    private var state: LyricsState? = null
    private var lines: List<LyricsLine> = emptyList()
    private var lineViews: List<TextView> = emptyList()
    private var activeIndex = -1
    private var autoFollow = true

    private val tick = object : Runnable {
        override fun run() {
            if (!shown) return
            update(host.nowPlaying())
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        view.addView(message, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        view.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        buildSearchBox()
        view.addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        scroll.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.setOnTouchListener { _, event ->
            // A finger on the lyrics means the owner is reading elsewhere: stop following until asked.
            if (event.actionMasked == MotionEvent.ACTION_MOVE && autoFollow && state is LyricsState.Synced) {
                autoFollow = false
                refreshJump()
            }
            false
        }
        view.addView(scroll, if (fixedHeightPx != null) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fixedHeightPx).apply { topMargin = s.gap }
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = s.gap }
        })
        view.addView(jump, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        })
        view.addView(source, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
    }

    // ---- life ------------------------------------------------------------------------------

    /** The pane is on screen (tab selected and its window open). Lyrics are only asked for while it is. */
    fun setShown(on: Boolean) {
        if (on == shown) return
        shown = on
        handler.removeCallbacks(tick)
        if (on) {
            requestedKey = null
            update(host.nowPlaying())
            handler.postDelayed(tick, 1000L)
        } else {
            host.lyrics()?.controller()?.cancel()
            requestedKey = null
            hideKeyboard()
        }
    }

    /** Asks again when the song, the account or the consent changed; otherwise moves the highlight. */
    fun update(now: NowPlaying?) {
        if (!shown) return
        val access = host.lyrics() ?: return
        val meta = access.meta()
        val key = Key(access.owner(), meta?.let(MeloLyricsPolicy::signature), access.consent())
        if (key != requestedKey) {
            requestedKey = key
            start(access, meta)
        } else {
            highlight(now?.positionMs ?: 0L)
        }
    }

    private fun start(access: MeloLyricsAccess, meta: Meta?) {
        autoFollow = true
        closeSearch()
        if (meta == null) {
            access.controller().cancel()
            render(null)
        } else {
            access.controller().request(access.owner(), meta) { render(it) }
        }
    }

    private fun askAgain() {
        requestedKey = null
        update(host.nowPlaying())
    }

    // ---- drawing -----------------------------------------------------------------------------

    private fun render(next: LyricsState?) {
        state = next
        val presentation = MeloLyricsUiPolicy.presentation(next)
        message.text = presentation.text.orEmpty()
        message.visibility = if (presentation.text == null) View.GONE else View.VISIBLE
        message.setTextColor(if (next is LyricsState.NeedConsent) p.text else p.textSecondary)
        buttons.removeAllViews()
        listOfNotNull(presentation.primary, presentation.secondary).forEachIndexed { index, action ->
            buttons.addView(button(action, primary = index == 0), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) marginStart = s.gap })
        }
        buttons.visibility = if (buttons.childCount == 0) View.GONE else View.VISIBLE

        body.removeAllViews()
        lines = emptyList()
        lineViews = emptyList()
        activeIndex = -1
        when (next) {
            is LyricsState.Synced -> {
                lines = next.lines
                lineViews = next.lines.map { line ->
                    c.text(line.text, s.titleText, p.textSecondary, maxLines = Int.MAX_VALUE).apply {
                        setPadding(0, s.gap, 0, s.gap)
                    }.also { body.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
                }
            }
            is LyricsState.Plain -> body.addView(
                c.text(next.text, s.titleText, p.text, maxLines = Int.MAX_VALUE),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            is LyricsState.Ambiguous -> addCandidateRows(next.candidates)
            else -> Unit
        }
        scroll.scrollTo(0, 0)
        source.visibility = if (next is LyricsState.Synced || next is LyricsState.Plain) View.VISIBLE else View.GONE
        highlight(host.nowPlaying()?.positionMs ?: 0L)
        refreshJump()
    }

    private fun button(action: Action, primary: Boolean): TextView =
        c.chip(action.label, primary) { onAction(action) }.apply {
            minHeight = MeloLyricsUiPolicy.controlHeightPx(s.minTouch, s.secondaryText, context.resources.configuration.fontScale)
            contentDescription = action.label
        }

    private fun onAction(action: Action) {
        val access = host.lyrics() ?: return
        when (action) {
            Action.ENABLE -> { access.setConsent(true); askAgain() }
            Action.DECLINE -> { access.setConsent(false); askAgain() }
            Action.ENABLE_AGAIN -> render(LyricsState.NeedConsent)
            Action.SEARCH -> openSearch()
            Action.RETRY -> askAgain()
            Action.CLEAR_CHOICE -> {
                access.meta()?.let { access.controller().clearChoice(access.owner(), it) }
                askAgain()
            }
        }
    }

    private fun addCandidateRows(candidates: List<Candidate>) {
        candidates.forEach { candidate ->
            val row = c.text(MeloLyricsUiPolicy.candidateLabel(candidate), s.titleText, p.text, maxLines = 3).apply {
                minHeight = s.minTouch
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(s.gap, s.gap, s.gap, s.gap)
                background = c.roundedBackground(p.surface, stroke = p.border)
                isClickable = true
                isFocusable = true
                setOnClickListener { choose(candidate) }
            }
            body.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = s.gap / 2
            })
        }
    }

    private fun choose(candidate: Candidate) {
        val access = host.lyrics() ?: return
        val meta = access.meta() ?: return
        closeSearch()
        autoFollow = true
        render(access.controller().choose(access.owner(), meta, candidate))
    }

    // ---- highlight and follow ----------------------------------------------------------------

    private fun highlight(positionMs: Long) {
        if (lines.isEmpty()) return
        val index = MeloLyricsPolicy.currentLineIndex(lines, positionMs)
        if (index != activeIndex) {
            lineViews.getOrNull(activeIndex)?.let { style(it, false) }
            lineViews.getOrNull(index)?.let { style(it, true) }
            activeIndex = index
            if (autoFollow) scrollToActive()
            refreshJump()
        }
    }

    private fun style(line: TextView, active: Boolean) {
        line.setTextColor(if (active) p.accent else p.textSecondary)
        line.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun scrollToActive() {
        val line = lineViews.getOrNull(activeIndex) ?: return
        scroll.post {
            val target = MeloLyricsUiPolicy.scrollTarget(line.top, line.height, scroll.height)
            scroll.smoothScrollTo(0, target)
        }
    }

    private fun followAgain() {
        autoFollow = true
        scrollToActive()
        refreshJump()
    }

    private fun refreshJump() {
        jump.visibility = if (MeloLyricsUiPolicy.showJumpButton(autoFollow, activeIndex)) View.VISIBLE else View.GONE
    }

    // ---- manual search (LD10) ----------------------------------------------------------------

    private fun buildSearchBox() {
        searchInput.apply {
            hint = "Tên bài hoặc nghệ sĩ"
            setHintTextColor(p.textSecondary)
            setTextColor(p.text)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s.titleText)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine(true)
            minHeight = MeloLyricsUiPolicy.controlHeightPx(s.minTouch, s.titleText, context.resources.configuration.fontScale)
            setPadding(s.gap, 0, s.gap, 0)
            background = c.roundedBackground(p.surface, stroke = p.border)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
            }
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        row.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(c.chip("Tìm", true) { runSearch() }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = s.gap })
        row.addView(c.chip("Đóng", false) { closeSearch(); host.lyrics()?.let { askAgain() } }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = s.gap })
        searchBox.addView(row)
    }

    private fun openSearch() {
        val access = host.lyrics() ?: return
        searchBox.visibility = View.VISIBLE
        searchInput.setText(MeloLyricsUiPolicy.searchPrefill(access.meta(), access.filename()))
        searchInput.setSelection(searchInput.text.length)
        searchInput.requestFocus()
    }

    private fun closeSearch() {
        if (searchBox.visibility != View.VISIBLE) return
        searchBox.visibility = View.GONE
        hideKeyboard()
    }

    private fun runSearch() {
        val access = host.lyrics() ?: return
        val meta = access.meta() ?: return
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        body.removeAllViews()
        lines = emptyList(); lineViews = emptyList(); activeIndex = -1
        body.addView(c.text("Đang tìm…", s.titleText, p.textSecondary))
        access.controller().searchManual(access.owner(), meta, query) { result ->
            body.removeAllViews()
            when (result) {
                is LyricsManualSearch.Results ->
                    if (result.candidates.isEmpty()) body.addView(c.text("Không có kết quả.", s.titleText, p.textSecondary))
                    else addCandidateRows(result.candidates)
                is LyricsManualSearch.TemporaryError ->
                    body.addView(c.text("Chưa tìm được. Thử lại sau.", s.titleText, p.textSecondary))
            }
        }
    }

    private fun hideKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }
}

/**
 * "Tiếp theo | Lời nhạc" tab strip over the queue block and the lyrics pane. [trailing] (Hai khung: "Thư viện") sits at
 * the end of the strip. The chosen tab is remembered through [MeloLyricsAccess.setTab], so rotation and a new screen
 * open on the same tab (S2).
 */
internal class MeloLyricsTabs(
    private val context: Context,
    private val c: MeloComponents,
    private val host: MeloScreenHost,
    private val queueSide: View,
    private val lyrics: MeloLyricsPane,
    /** True when the strip's parent gives a definite height (landscape / Hai khung); false inside a scrolling column. */
    fillHeight: Boolean,
    private val trailing: View? = null
) {
    val view = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
    private val frame = FrameLayout(context)
    private var selected = host.lyrics()?.tab() ?: Tab.QUEUE
    private var active = false

    init {
        val childHeight = if (fillHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        frame.addView(queueSide, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, childHeight))
        frame.addView(lyrics.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, childHeight))
        view.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(frame, if (fillHeight) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }.apply { topMargin = c.sizes.gap })
        apply()
    }

    /** The window that holds the tabs is open (Deck shown, queue open in Hai khung). */
    fun setActive(on: Boolean) {
        active = on
        lyrics.setShown(on && selected == Tab.LYRICS)
    }

    private fun select(tab: Tab) {
        if (tab == selected) return
        selected = tab
        host.lyrics()?.setTab(tab)
        apply()
    }

    private fun apply() {
        strip.removeAllViews()
        Tab.entries.forEachIndexed { index, tab ->
            strip.addView(c.chip(tab.label, tab == selected) { select(tab) }.apply {
                contentDescription = tab.label
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (index > 0) marginStart = c.sizes.gap
            })
        }
        strip.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        trailing?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            strip.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        queueSide.visibility = if (selected == Tab.QUEUE) View.VISIBLE else View.GONE
        lyrics.view.visibility = if (selected == Tab.LYRICS) View.VISIBLE else View.GONE
        lyrics.setShown(active && selected == Tab.LYRICS)
    }
}
