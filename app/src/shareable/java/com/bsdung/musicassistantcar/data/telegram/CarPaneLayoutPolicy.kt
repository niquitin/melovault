package com.bsdung.musicassistantcar.data.telegram

/** Layout depends on the *available app window*, not the physical FYT LCD. */
internal object CarPaneLayoutPolicy {
    enum class Mode { NARROW, MEDIUM, WIDE }

    fun modeFor(widthDp: Int, heightDp: Int): Mode {
        if (widthDp <= 0 || heightDp <= 0) return Mode.MEDIUM
        if (widthDp >= 680 && widthDp >= heightDp * 1.15f) return Mode.WIDE
        if (widthDp >= 500) return Mode.MEDIUM
        return Mode.NARROW
    }

    fun compactFor(widthDp: Int, heightDp: Int): Boolean =
        modeFor(widthDp, heightDp) == Mode.NARROW && heightDp in 1..739

    /** Square artwork, never stretched into a banner. */
    fun coverSizeDp(widthDp: Int, heightDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 180
        val limit = when (modeFor(widthDp, heightDp)) {
            Mode.WIDE -> minOf(widthDp / 2 - 32, heightDp - 112, 430)
            Mode.MEDIUM -> minOf(widthDp - 50, heightDp * 2 / 5, 340)
            Mode.NARROW -> minOf(widthDp - 28, heightDp * 2 / 5, 330)
        }
        return limit.coerceAtLeast(96)
    }
}
