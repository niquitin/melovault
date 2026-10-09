package com.bsdung.musicassistantcar.ui.util

import kotlin.math.abs

/** Explicit intent detection to avoid accidental track changes while scrolling. */
internal enum class ArtworkGestureAction {
    OPEN_QUEUE, NEXT_TRACK, PREVIOUS_TRACK, IGNORE
}

internal object ArtworkGesturePolicy {
    fun classify(
        dx: Float,
        dy: Float,
        swipeThresholdPx: Float,
        tapSlopPx: Float,
    ): ArtworkGestureAction {
        val horizontal = abs(dx)
        val vertical = abs(dy)
        return when {
            horizontal <= tapSlopPx && vertical <= tapSlopPx -> ArtworkGestureAction.OPEN_QUEUE
            horizontal >= swipeThresholdPx && horizontal > vertical * 1.5f ->
                if (dx < 0) ArtworkGestureAction.NEXT_TRACK else ArtworkGestureAction.PREVIOUS_TRACK
            else -> ArtworkGestureAction.IGNORE
        }
    }
}
