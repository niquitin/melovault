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
    private val bar = MeloBottomBar(context, c, host, onTab = ::onNav, onOpenNowPlaying = { host.openNowPlaying() })
    private var snapshot: MeloScreenHost.Snapshot? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val snap = snapshot
            if (snap != null && snap.local) host.closeLocal()
            else if (snap != null && !snap.showingSources && snap.sourceTitle != null) host.showSources()
            else { isEnabled = false; backDispatcher.onBackPressed() }
        }
    }

    init {
        buildHeader()
        view.addView(headerBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.addView(pane.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view.addView(bar.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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

    private fun onNav(tab: MeloBottomBarPolicy.Tab) {
        when (tab) {
            MeloBottomBarPolicy.Tab.LIBRARY -> { host.openSource(null); host.setMode(Mode.ALL) }
            MeloBottomBarPolicy.Tab.TELEGRAM -> host.showSources()
            MeloBottomBarPolicy.Tab.PLAYLIST -> host.setMode(Mode.PLAYLIST)
            MeloBottomBarPolicy.Tab.DOWNLOADED -> host.setMode(Mode.DOWNLOADED)
        }
    }

    fun onLibraryChanged() {
        val snap = host.snapshot()
        snapshot = snap
        syncLine.text = "☁ " + MeloLibraryPolicy.syncLine(host.syncSubtitle(), snap.total, false)
        backCallback.isEnabled = !snap.showingSources && snap.sourceTitle != null
        bar.renderTabs(snap.showingSources, snap.mode)
        pane.onLibraryChanged()
    }

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        bar.update(now)
        pane.onPlaybackChanged(now?.songKey, now?.paused == true)
    }
}
