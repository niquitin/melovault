package com.bsdung.musicassistantcar.data.telegram

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer

/**
 * Điện thoại dọc / Tablet dọc (docs/ui-spec.md step 7), top to bottom: header (MeloVault,
 * sync line, Cài đặt, ⋮) that folds away on scroll, search, chips, group title with
 * "Phát hết", song list or Telegram grid, mini player, bottom navigation.
 */
internal class MeloPhoneScreen(
    private val context: Context,
    sizes: Sizes,
    private val layer: NewLayer,
    private val host: MeloScreenHost,
    private val backDispatcher: OnBackPressedDispatcher
) {
    private val c = MeloComponents(context, sizes)
    private val s = c.sizes
    private val p = c.palette

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(p.background)
        setPadding(s.screenMargin, 0, s.screenMargin, s.gap)
    }

    private val titleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val syncLine = c.text("", s.secondaryText, p.textSecondary, maxLines = 2)
    private val headerBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val pane = MeloLibraryPane(context, c, host, layer) { collapsed -> setHeaderCollapsed(collapsed) }
    private val miniHost = FrameLayout(context)
    private var mini: MeloComponents.MiniPlayerHandle? = null
    private var miniKey: String? = null
    private val navItems = mutableMapOf<NavItem, TextView>()
    private var snapshot: MeloScreenHost.Snapshot? = null

    private enum class NavItem(val icon: String, val label: String) {
        LIBRARY("♫", "Thư viện"), TELEGRAM("☷", "Telegram"), PLAYLIST("☆", "Playlist"), NOW("▶", "Đang phát")
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val snap = snapshot
            if (snap != null && !snap.showingSources && snap.sourceTitle != null) host.showSources()
            else { isEnabled = false; backDispatcher.onBackPressed() }
        }
    }

    init {
        buildHeader()
        view.addView(headerBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(pane.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(miniHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = s.gap
        })
        view.addView(bottomNav(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, s.minTouch * 6 / 5).apply {
            topMargin = s.gap
        })
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

    private fun buildHeader() {
        titleRow.addView(c.text("MeloVault", s.playerTitleText, p.text, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(c.iconButton("⚙", "Cài đặt giao diện") { host.openUiLab() }.apply { background = null; setTextColor(p.text) })
        lateinit var more: View
        more = c.iconButton("⋮", "Tuỳ chọn thư viện") { host.openLegacyOverflow(more) }.apply { background = null; setTextColor(p.text) }
        titleRow.addView(more)
        headerBox.addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        headerBox.addView(syncLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = s.gap
        })
    }

    /** Only the title and sync rows fold; the search bar stays in reach (spec). */
    private fun setHeaderCollapsed(collapsed: Boolean) {
        headerBox.visibility = if (collapsed) View.GONE else View.VISIBLE
    }

    private fun bottomNav(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        background = c.roundedBackground(p.surface, stroke = p.border)
        NavItem.entries.forEach { item ->
            val button = TextView(context).apply {
                text = "${item.icon}\n${item.label}"
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, s.secondaryText)
                setTextColor(p.textSecondary)
                contentDescription = item.label
                isClickable = true
                isFocusable = true
                setOnClickListener { onNav(item) }
            }
            navItems[item] = button
            addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun onNav(item: NavItem) {
        when (item) {
            NavItem.LIBRARY -> { host.openSource(null); host.setMode(Mode.ALL) }
            NavItem.TELEGRAM -> host.showSources()
            NavItem.PLAYLIST -> host.setMode(Mode.PLAYLIST)
            NavItem.NOW -> host.openNowPlaying()
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
            button.setTextColor(if (on) p.accent else p.textSecondary)
            button.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            button.background = if (on) c.roundedBackground(p.accentSoft) else null
        }
    }

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        syncLine.text = "☁ " + MeloLibraryPolicy.syncLine(host.syncSubtitle(), snap.total, false)
        backCallback.isEnabled = !snap.showingSources && snap.sourceTitle != null
        renderNav(snap)
        pane.onLibraryChanged()
    }

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        if (now == null) {
            if (mini != null) { miniHost.removeAllViews(); mini = null; miniKey = null }
        } else if (mini == null || miniKey != now.title) {
            miniHost.removeAllViews()
            val handle = c.miniPlayer(
                MeloComponents.MiniPlayerModel(now.title, now.sourceTitle, (now.positionMs / 1000).toInt(),
                    (now.durationMs / 1000).toInt(), now.playing, now.art),
                showPrevious = MeloLibraryPolicy.miniPlayerShowsPrevious(layer),
                onPlayPause = { host.playPause() },
                onNext = { host.next() },
                onPrevious = { host.previous() },
                onOpen = { host.openNowPlaying() }
            )
            miniHost.addView(handle.view)
            mini = handle
            miniKey = now.title
        } else {
            mini?.update((now.positionMs / 1000).toInt(), (now.durationMs / 1000).toInt(), now.playing)
        }
        pane.onPlaybackChanged(now?.songKey)
    }
}
