package com.bsdung.musicassistantcar.data.telegram

/**
 * Layout and dimension policies for short-height displays, multiwindow / split-screen,
 * and compact car head units.
 *
 * Prevents vertical clipping/occlusion of the header title, sync subtitle,
 * navigation tabs, mini player, and system bars.
 */
internal object ShortWindowLayoutPolicy {

    /**
     * Threshold for short-height mode (e.g. FYT 7870 landscape split-screen,
     * Android multiwindow / split-screen, or compact landscape panes).
     */
    fun isShort(heightDp: Int): Boolean = heightDp in 1..600

    /**
     * Minimum height for the top bar header (Title + Subtitle + Overflow action).
     * The top bar itself uses WRAP_CONTENT at runtime so two lines of text
     * (Title + Subtitle) are NEVER clipped, but minimum height provides visual balance.
     */
    fun topBarMinHeightDp(isShort: Boolean): Int = if (isShort) 40 else 48

    /**
     * Estimated vertical height required by the 2-line title column (title sp + subtitle sp).
     * Must be <= topBarMinHeightDp to guarantee no overflow/occlusion.
     */
    fun titleColumnEstimatedHeightDp(isShort: Boolean): Int = if (isShort) 36 else 44

    /**
     * Height of the top navigation tab bar (used in wide / landscape layouts).
     */
    fun topNavigationHeightDp(isShort: Boolean): Int = if (isShort) 36 else 44

    /**
     * Height of individual buttons within top navigation bar.
     * Guaranteed <= topNavigationHeightDp.
     */
    fun topNavButtonHeightDp(isShort: Boolean): Int = if (isShort) 34 else 42

    /**
     * Height of the bottom navigation bar (used in Portrait Edge-to-Edge Pro).
     */
    fun bottomNavHeightDp(isShort: Boolean): Int = if (isShort) 42 else 52

    /**
     * Height of individual buttons within bottom navigation bar.
     * Guaranteed <= bottomNavHeightDp.
     */
    fun bottomNavButtonHeightDp(isShort: Boolean): Int = if (isShort) 38 else 46

    /**
     * Mini-player transport control row height.
     */
    fun miniControlHeightDp(isShort: Boolean): Int = if (isShort) 34 else 42

    /**
     * Mini-player artwork frame dimension (square width & height).
     */
    fun miniArtSizeDp(isShort: Boolean): Int = if (isShort) 36 else 42

    /**
     * Whether the UI should route navigation to the bottom navigation bar
     * vs the legacy top navigation bar.
     *
     * In Portrait Edge Pro, bottom navigation is used for portrait / medium / narrow panes.
     * Wide / landscape panes preserve the wide horizontal top navigation bar.
     */
    fun shouldUseBottomNav(
        preset: UiThemePolicy.ThemePreset,
        paneMode: CarPaneLayoutPolicy.Mode
    ): Boolean = preset == UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO && paneMode != CarPaneLayoutPolicy.Mode.WIDE

    /**
     * Calculates the truthfully displayed source count, excluding the aggregate row (chatId = 0L)
     * so that the header subtitle matches the individual source rows.
     */
    fun truthfulSourceCount(visibleChatIds: List<Long>): Int {
        if (visibleChatIds.isEmpty()) return 0
        val nonAggregate = visibleChatIds.count { it != 0L }
        return if (nonAggregate > 0) nonAggregate else visibleChatIds.size
    }

    /**
     * Verifies that the header and top navigation tab bar do not overlap vertically.
     * Returns true if top navigation begins strictly after the top bar's bottom edge.
     */
    fun verifyHeaderNavNonOverlap(
        topBarTop: Int,
        topBarHeight: Int,
        navTopMargin: Int,
        navTop: Int
    ): Boolean {
        val expectedNavTop = topBarTop + topBarHeight + navTopMargin
        return navTop >= expectedNavTop
    }
}
