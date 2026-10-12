package com.bsdung.musicassistantcar.ui.util

import kotlin.math.abs

/** Explicit intent detection to avoid accidental track changes while scrolling. */
internal enum class ArtworkGestureAction {
    OPEN_QUEUE, NEXT_TRACK, PREVIOUS_TRACK, SHOW_LYRICS, SHOW_COVER, IGNORE
}

internal object ArtworkGesturePolicy {
    fun classify(
        dx: Float,
        dy: Float,
        swipeThresholdPx: Float,
        tapSlopPx: Float,
        preferLyricsSwitch: Boolean = false,
    ): ArtworkGestureAction {
        val horizontal = abs(dx)
        val vertical = abs(dy)
        return when {
            horizontal <= tapSlopPx && vertical <= tapSlopPx -> ArtworkGestureAction.OPEN_QUEUE
            horizontal >= swipeThresholdPx && horizontal > vertical * 1.5f -> {
                if (preferLyricsSwitch) {
                    if (dx < 0) ArtworkGestureAction.SHOW_LYRICS else ArtworkGestureAction.SHOW_COVER
                } else {
                    if (dx < 0) ArtworkGestureAction.NEXT_TRACK else ArtworkGestureAction.PREVIOUS_TRACK
                }
            }
            else -> ArtworkGestureAction.IGNORE
        }
    }
}
