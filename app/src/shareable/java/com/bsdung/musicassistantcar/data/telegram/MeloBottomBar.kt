package com.bsdung.musicassistantcar.data.telegram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bsdung.musicassistantcar.data.telegram.MeloBottomBarPolicy.Tab
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloScreenHost.NowPlaying
import kotlin.math.abs

/**
 * "Thanh đáy gộp": the only playback control on the portrait layouts. Top to bottom: a 2dp
 * progress line, one "tên bài · tên kênh" line, then tab · Lùi · Phát/Dừng · Tiến · tab.
 * Tapping the bar (or swiping up on it) opens Now Playing. Pure view: every action goes to [host].
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
    private val titleLine: TextView = c.text("", spec.titleSp * density * fontScale, p.text, bold = true).apply {
        gravity = Gravity.CENTER
        setPadding(s.gap, 0, s.gap, 0)
    }
    private val play: TextView
    private val tabViews = LinkedHashMap<Tab, TextView>()
    private var hasSong = false
    private var shownTitle: String? = null

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
        titleLine.apply { minHeight = dp(spec.titleLineDp); visibility = View.INVISIBLE }
        view.addView(titleLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val rowHeight = dp(spec.heightDp - MeloBottomBarPolicy.PROGRESS_DP - spec.titleLineDp)
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
        installOpenGestures()
        // No song yet: setOnClickListener made the bar clickable, so switch it and Play off again.
        view.isClickable = false
        play.alpha = MeloBottomBarPolicy.playAlpha(false)
        play.isEnabled = MeloBottomBarPolicy.playEnabled(false)
        update(null)
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

    private fun tabView(tab: Tab): TextView {
        val label = TextView(context).apply {
            text = "${tab.icon}\n${tab.label}"
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
            val showInfo = MeloBottomBarPolicy.showsSongInfo(has)
            progress.visibility = if (showInfo) View.VISIBLE else View.INVISIBLE
            titleLine.visibility = if (showInfo) View.VISIBLE else View.INVISIBLE
            view.isClickable = showInfo
            val alpha = MeloBottomBarPolicy.playAlpha(has)
            play.alpha = alpha
            play.isEnabled = MeloBottomBarPolicy.playEnabled(has)
        }
        if (now == null) {
            shownTitle = null
            play.text = "▶"
            play.contentDescription = MeloBottomBarPolicy.playDescription(false, false)
            view.contentDescription = null
            return
        }
        val line = MeloBottomBarPolicy.titleLine(now.title, now.sourceTitle)
        if (line != shownTitle) {
            shownTitle = line
            titleLine.text = line
            view.contentDescription = MeloBottomBarPolicy.contentDescription(now.title, now.sourceTitle)
        }
        val durationSeconds = (now.durationMs / 1000).toInt()
        progress.max = maxOf(durationSeconds, 1)
        progress.progress = (now.positionMs / 1000).toInt().coerceIn(0, progress.max)
        val pause = MeloBottomBarPolicy.showsPauseGlyph(true, now.paused)
        val glyph = if (pause) "❚❚" else "▶"
        if (play.text.toString() != glyph) play.text = glyph
        play.contentDescription = MeloBottomBarPolicy.playDescription(true, now.paused)
    }

    private fun onPlayClick() {
        if (hasSong) host.playPause()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installOpenGestures() {
        view.setOnClickListener { if (hasSong) onOpenNowPlaying() }
        val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (hasSong && velocityY < 0 && abs(velocityY) > abs(velocityX)) { onOpenNowPlaying(); return true }
                return false
            }
        })
        // A swipe up consumes its ACTION_UP so the tap listener does not open a second time.
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) && event.action == MotionEvent.ACTION_UP }
    }
}
