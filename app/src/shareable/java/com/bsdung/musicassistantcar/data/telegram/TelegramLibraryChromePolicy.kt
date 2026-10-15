package com.bsdung.musicassistantcar.data.telegram

/** A small, testable policy for chrome hiding on vertical list gestures. */
internal object TelegramLibraryChromePolicy {
    fun collapsedAfter(
        currentlyCollapsed: Boolean,
        accumulatedDragPx: Float,
        thresholdPx: Float,
        authorized: Boolean,
        itemCount: Int
    ): Boolean {
        if (!authorized || itemCount < 2) return false
        return when {
            accumulatedDragPx <= -thresholdPx -> true
            accumulatedDragPx >= thresholdPx -> false
            else -> currentlyCollapsed
        }
    }
}
