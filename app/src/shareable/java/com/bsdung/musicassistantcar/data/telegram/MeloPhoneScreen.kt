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
    private val backDispatcher: OnBackPressedDispatcher,
    theme: MeloThemePolicy.ColorTheme = MeloThemePolicy.ColorTheme.CURRENT
) {
    private val c = MeloComponents(context, sizes, theme)
    private val s = c.sizes
    private val p = c.palette
    /** E98 teal theme: two-colour logo, "Đồng bộ: N bài ●" row with ⚙, gradient background. */
    private val teal = c.teal
    private val syncRow by lazy { c.syncRow { host.openUiLab() } }

    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (teal) background = c.gradient(p.background, p.backgroundEnd, 0) else setBackgroundColor(p.background)
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

    /** UI-02: the keyboard height in px (0 = closed); the bar gives its place to the song list while typing. */
    fun onKeyboardChanged(keyboardPx: Int) {
        val hidden = MeloBottomBarPolicy.hiddenForKeyboard(keyboardPx, context.resources.configuration.screenHeightDp)
        bar.view.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun buildHeader() {
        if (teal) {
            // The ⚙ moves to the sync row; the logo takes the title's place.
            titleRow.addView(c.logo(s.playerTitleText), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else {
            titleRow.addView(c.text("MeloVault", s.playerTitleText, p.text, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            titleRow.addView(c.iconButton("⚙", "Cài đặt") { host.openUiLab() }.apply { background = null; setTextColor(p.text) })
        }
        lateinit var more: View
        more = c.iconButton("⋮", "Tuỳ chọn thư viện") { host.openLegacyOverflow(more) }.apply { background = null; setTextColor(p.text) }
        titleRow.addView(more)
        headerBox.addView(titleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        headerBox.addView(if (teal) syncRow.view else syncLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
            syncLine.text = PlaylistSyncStatusPolicy.libraryLine(
                playlistState, "☁ " + MeloLibraryPolicy.syncLine(syncText, snap.total, false)
            )
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
        backCallback.isEnabled = !snap.showingSources && snap.sourceTitle != null
        bar.renderTabs(snap.showingSources, snap.mode)
        bar.refreshIdle()
        pane.onLibraryChanged()
    }

    fun onPlaybackChanged() {
        val now = host.nowPlaying()
        bar.update(now)
        pane.onPlaybackChanged(now?.songKey, now?.paused == true, now?.playing == true, now?.buffering == true)
    }
}
