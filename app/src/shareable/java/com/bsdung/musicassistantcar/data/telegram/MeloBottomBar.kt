package com.bsdung.musicassistantcar.data.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloBottomBarPolicy.Tab
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import kotlin.math.abs

/**
 * "Thanh đáy gộp": the only playback control on the portrait layouts. Top to bottom: a 2dp
 * progress line, the "Đang phát" card (cover · song title · channel · ⌃), then
 * tab · Lùi · Phát/Dừng · Tiến · tab. Tapping the card (or swiping up on it) opens Now Playing;
 * the tabs and the transport buttons keep their own actions. Pure view: every action goes to [host].
 */
internal class MeloBottomBar(
    private val context: Context,
    private val c: MeloComponents,
    private val host: MeloScreenHost,
    private val onTab: (Tab) -> Unit,
    private val onOpenNowPlaying: () -> Unit
) {
    private val s = c.sizes
    private val p = c.palette
    private val density = context.resources.displayMetrics.density
    private val fontScale = context.resources.configuration.fontScale
    val spec: MeloBottomBarPolicy.Spec = requireNotNull(MeloBottomBarPolicy.spec(s.profile)) { "No bottom bar for ${s.profile}" }

    private fun dp(v: Int) = (v * density).toInt()

    val view: LinearLayout = LinearLayout(context)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val card = LinearLayout(context)
    private val artSlot = FrameLayout(context)
    private val titleText: TextView = c.text("", spec.titleSp * density * fontScale, p.text, bold = true)
    private val channelText: TextView = c.text("", spec.channelSp * density * fontScale, p.textSecondary)
    private val play: TextView
    private val tabViews = LinkedHashMap<Tab, TextView>()
    private var hasSong = false
    private var shownTitle: String? = null
    private var shownArt: Bitmap? = null
    private var shownArtKey: String? = null
    private var iconOnly = false
    /** E100: what the card offers while no song is loaded, and the key of what is drawn (so a tick does not redraw it). */
    private var idle = MeloBottomBarPolicy.Idle.PICK
    private var idleKey: String? = null

    init {
        view.apply {
            orientation = LinearLayout.VERTICAL
            background = c.roundedBackground(p.surfaceRaised, stroke = p.border)
            minimumHeight = dp(spec.heightDp)
            isClickable = false
            isFocusable = true
        }
        progress.apply {
            isIndeterminate = false
            progressDrawable = c.thinProgressDrawable()
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.INVISIBLE
        }
        view.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(MeloBottomBarPolicy.PROGRESS_DP)))
        buildCard()
        view.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(MeloBottomBarPolicy.CARD_MARGIN_DP)
            marginEnd = dp(MeloBottomBarPolicy.CARD_MARGIN_DP)
        })

        val rowHeight = dp(MeloBottomBarPolicy.rowDp(spec))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        MeloBottomBarPolicy.leftTabs(spec.tabs).forEach { row.addView(tabView(it), tabParams()) }
        row.addView(transportButton("⏮", "Bài trước") { host.previous() })
        play = c.iconButton("▶", "Phát", primary = true, width = dp(spec.playDp), height = dp(spec.playDp)) { onPlayClick() }
        row.addView(play, LinearLayout.LayoutParams(dp(spec.playDp), dp(spec.playDp)).apply {
            marginStart = s.gap / 2
            marginEnd = s.gap / 2
        })
        row.addView(transportButton("⏭", "Bài kế") { host.next() })
        MeloBottomBarPolicy.rightTabs(spec.tabs).forEach { row.addView(tabView(it), tabParams()) }
        view.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight))
        // The window can be resized without rebuilding this bar: re-check the label fit each time.
        row.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) row.post { updateTabLabels() } }
        installOpenGestures()
        // setOnClickListener made the bar clickable; with no song only the card may be (update(null) decides).
        view.isClickable = false
        update(null)
    }

    /** Cover on the left, song title (bold, one line, "…" at the end) over the channel, "⌃" on the right. */
    private fun buildCard() {
        card.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = c.roundedBackground(p.surface, stroke = p.border)
            minimumHeight = dp(MeloBottomBarPolicy.cardHeightDp(spec, fontScale))
            setPadding(dp(MeloBottomBarPolicy.CARD_PAD_DP), dp(MeloBottomBarPolicy.CARD_PAD_DP), 0, dp(MeloBottomBarPolicy.CARD_PAD_DP))
        }
        card.addView(artSlot, LinearLayout.LayoutParams(dp(spec.artDp), dp(spec.artDp)).apply { marginEnd = s.gap })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(titleText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        texts.addView(channelText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chevron = c.text("⌃", spec.titleSp * density * fontScale, p.textSecondary).apply {
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(chevron, LinearLayout.LayoutParams(dp(MeloBottomBarPolicy.CARD_CHEVRON_DP), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** The cover goes through [MeloComponents.artView]: no picture means the letter tile that is already there. */
    private fun showArt(art: Bitmap?, sourceTitle: String?, title: String) {
        val key = sourceTitle ?: title
        if (shownArt === art && shownArtKey == key && artSlot.childCount > 0) return
        shownArt = art
        shownArtKey = key
        artSlot.removeAllViews()
        artSlot.addView(
            c.artView(art, sourceTitle, title, dp(spec.artDp)),
            FrameLayout.LayoutParams(dp(spec.artDp), dp(spec.artDp))
        )
    }

    private fun tabParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
        topMargin = dp(2)
        bottomMargin = dp(2)
    }

    private fun transportButton(glyph: String, description: String, onClick: () -> Unit): TextView {
        val side = dp(spec.skipDp)
        return c.iconButton(glyph, description, width = side, height = side, onClick = onClick).apply {
            background = null
            layoutParams = LinearLayout.LayoutParams(side, side)
        }
    }

    /** A tab cell too narrow for its label (head unit's 1/3 window, font ×1.25) shows only the icon, larger. */
    private fun updateTabLabels() {
        val cell = tabViews.values.firstOrNull()?.width ?: return
        if (cell <= 0) return
        val only = MeloBottomBarPolicy.tabIconOnly((cell / density).toInt(), fontScale)
        if (only == iconOnly) return
        iconOnly = only
        tabViews.forEach { (tab, v) ->
            v.text = tabText(tab)
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (only) s.titleText else s.secondaryText * 0.85f)
        }
    }

    private fun tabText(tab: Tab): String = if (iconOnly) tab.icon else "${tab.icon}\n${tab.label}"

    private fun tabView(tab: Tab): TextView {
        val label = TextView(context).apply {
            text = tabText(tab)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText * 0.85f)
            setTextColor(p.textSecondary)
            maxLines = 2
            contentDescription = tab.label
            isClickable = true
            isFocusable = true
            minHeight = s.minTouch
            setOnClickListener { onTab(tab) }
        }
        tabViews[tab] = label
        return label
    }

    /** Lights the tab matching the library state ([MeloBottomBarPolicy.activeTab]). */
    fun renderTabs(showingSources: Boolean, mode: Mode) {
        val active = MeloBottomBarPolicy.activeTab(spec.tabs, showingSources, mode)
        tabViews.forEach { (tab, v) ->
            val on = tab == active
            v.setTextColor(if (on) p.accent else p.textSecondary)
            v.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            v.background = if (on) c.roundedBackground(p.accentSoft) else null
            v.isSelected = on
        }
    }

    /** Called on every playback tick; cheap when nothing changed. */
    fun update(now: NowPlaying?) {
        val has = now != null
        if (has != hasSong) {
            hasSong = has
            // Whatever the card showed before (song or idle text) is redrawn for the new state.
            shownTitle = null
            shownArtKey = null
            idleKey = null
            progress.visibility = if (MeloBottomBarPolicy.showsSongInfo(has)) View.VISIBLE else View.INVISIBLE
            card.visibility = View.VISIBLE
            card.isClickable = has
            view.isClickable = has
            play.alpha = MeloBottomBarPolicy.playAlpha(has)
            play.isEnabled = MeloBottomBarPolicy.playEnabled(has)
        }
        if (now == null) {
            showIdle()
            return
        }
        val line = MeloBottomBarPolicy.titleLine(now.title, now.sourceTitle)
        if (line != shownTitle) {
            shownTitle = line
            val channel = MeloBottomBarPolicy.channelLine(now.sourceTitle)
            titleText.text = now.title.trim()
            channelText.text = channel.orEmpty()
            channelText.visibility = if (channel == null) View.GONE else View.VISIBLE
            card.contentDescription = MeloBottomBarPolicy.contentDescription(now.title, now.sourceTitle)
        }
        showArt(now.art, now.sourceTitle, now.title)
        val durationSeconds = (now.durationMs / 1000).toInt()
        progress.max = maxOf(durationSeconds, 1)
        progress.progress = (now.positionMs / 1000).toInt().coerceIn(0, progress.max)
        val pause = MeloBottomBarPolicy.showsPauseGlyph(true, now.paused)
        val glyph = if (pause) "❚❚" else "▶"
        if (play.text.toString() != glyph) play.text = glyph
        play.contentDescription = MeloBottomBarPolicy.playDescription(true, now.paused)
    }

    /**
     * E100: nothing is loaded. The card says what a tap does: continue the last song ("Nghe tiếp"),
     * play the list on screen, or ask for a song. Only reads host state, so it is cheap to repeat.
     */
    private fun showIdle() {
        val resume = host.resumeCard()
        idle = MeloBottomBarPolicy.idle(resume != null, host.canPlayAll())
        val actionable = MeloBottomBarPolicy.idleActionable(idle)
        val key = "$idle|${resume?.songKey}|${resume?.title}|${resume?.sourceTitle}"
        if (key != idleKey) {
            idleKey = key
            val title = if (resume != null) resume.title.trim() else MeloBottomBarPolicy.HINT_TITLE
            titleText.text = title
            channelText.text = if (resume != null) MeloBottomBarPolicy.resumeLine(resume.sourceTitle) else MeloBottomBarPolicy.hintLine(idle)
            channelText.visibility = View.VISIBLE
            card.contentDescription = MeloBottomBarPolicy.idleDescription(idle, resume?.title, resume?.sourceTitle)
            card.isClickable = actionable
            play.alpha = MeloBottomBarPolicy.playAlpha(actionable)
            play.isEnabled = actionable
            play.text = "▶"
            play.contentDescription = MeloBottomBarPolicy.idlePlayDescription(idle)
        }
        // A cover can arrive later than the record (the host only reads what is already in memory).
        if (resume != null) showArt(resume.art, resume.sourceTitle, resume.title)
        else showArt(null, null, MeloBottomBarPolicy.HINT_TITLE)
    }

    /** Redraws the idle card after the list on screen changed (what "Phát hết" would play). */
    fun refreshIdle() {
        if (!hasSong) update(null)
    }

    private fun idleAction() {
        when (idle) {
            MeloBottomBarPolicy.Idle.RESUME -> host.playResume()
            MeloBottomBarPolicy.Idle.PLAY_ALL -> host.playAll(false)
            MeloBottomBarPolicy.Idle.PICK -> Unit
        }
    }

    private fun onPlayClick() {
        if (hasSong) host.playPause() else idleAction()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installOpenGestures() {
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (hasSong && velocityY < 0 && abs(velocityY) > abs(velocityX)) { onOpenNowPlaying(); return true }
                return false
            }
        })
        // The card is clickable itself (a tap anywhere on it opens once), so a swipe up must be read on it as well.
        listOf(view, card).forEach { target ->
            target.setOnClickListener { if (hasSong) onOpenNowPlaying() else if (target === card) idleAction() }
            // A swipe up consumes its ACTION_UP so the tap listener does not open a second time.
            target.setOnTouchListener { _, event -> detector.onTouchEvent(event) && event.action == MotionEvent.ACTION_UP }
        }
    }
}
