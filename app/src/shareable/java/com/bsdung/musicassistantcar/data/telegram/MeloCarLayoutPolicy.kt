package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Sizes

/**
 * E96 P4: the vertical budget of Car Mode, from the same [Sizes] the screen is built with, so a test can prove that the
 * new Save row (and the status line) fit on every window the Car screen can be drawn in without hiding the transport
 * controls, the page buttons or the list. Pure JVM; the numbers mirror [MeloCarScreen]'s layout, line by line.
 */
internal object MeloCarLayoutPolicy {

    /** Space the window must keep free for the head unit's own bars (status bar + card handle band) in the plan's budget. */
    const val SYSTEM_BARS_REFERENCE_PX = 96

    private fun secondaryLine(s: Sizes): Int = (s.secondaryText * 1.3f).toInt()

    /** Player pane: art row, seek bar, time, transport, −30s/+60s row, then the Save row. */
    fun playerPaneHeightPx(s: Sizes): Int {
        val artRow = s.playerArt
        val seek = s.gap * 3 + s.progressThumb
        val time = secondaryLine(s)
        val transport = s.gap * 2 + maxOf(s.transportHeight, s.playButtonHeight)
        val seekRow = s.gap * 2 + s.transportHeight
        val save = s.gap * 2 + MeloSavePolicy.carButtonHeightPx(s)
        return artRow + seek + time + transport + seekRow + save
    }

    /** Header of the list pane: one line, or two when the Playlist status line is shown. */
    fun listHeaderHeightPx(s: Sizes, statusLine: Boolean): Int {
        val minimum = s.minTouch * 7 / 10
        val title = (s.titleText * 1.2f).toInt()
        return maxOf(minimum, if (statusLine) title + secondaryLine(s) else title)
    }

    /** List pane: header, five fixed-height rows with gaps, the page buttons. */
    fun listPaneHeightPx(s: Sizes, statusLine: Boolean): Int {
        val rows = MeloCarPolicy.ROWS_PER_PAGE * s.rowHeight + (MeloCarPolicy.ROWS_PER_PAGE - 1) * s.gap
        return listHeaderHeightPx(s, statusLine) + rows + s.gap + s.pageButtonHeight
    }

    /** What is left of a [windowHeightPx]-tall window after the screen's own top/bottom padding and the bars budget. */
    fun availableHeightPx(windowHeightPx: Int, s: Sizes, barsPx: Int): Int = windowHeightPx - 2 * s.gap - barsPx

    /** Bars budget scaled the way the Car layout is scaled. */
    fun barsBudgetPx(scale: Float): Int = (SYSTEM_BARS_REFERENCE_PX * scale).toInt()

    fun fits(windowHeightPx: Int, s: Sizes, scale: Float): Boolean {
        val available = availableHeightPx(windowHeightPx, s, barsBudgetPx(scale))
        return playerPaneHeightPx(s) <= available && listPaneHeightPx(s, statusLine = true) <= available
    }

    /** The Save row is never smaller than the minimum touch target or the page buttons. */
    fun saveTargetOk(s: Sizes): Boolean = MeloSavePolicy.carButtonHeightPx(s) >= s.minTouch
}
