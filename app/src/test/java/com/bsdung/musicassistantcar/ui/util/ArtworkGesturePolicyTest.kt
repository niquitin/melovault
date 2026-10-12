package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkGesturePolicyTest {
    @Test fun horizontalSwipeLeftMovesToNextTrack() {
        assertEquals(ArtworkGestureAction.NEXT_TRACK, ArtworkGesturePolicy.classify(-120f, 10f, 64f, 8f))
    }

    @Test fun horizontalSwipeRightMovesToPreviousTrack() {
        assertEquals(ArtworkGestureAction.PREVIOUS_TRACK, ArtworkGesturePolicy.classify(110f, 12f, 64f, 8f))
    }

    @Test fun shortTapOpensQueue() {
        assertEquals(ArtworkGestureAction.OPEN_QUEUE, ArtworkGesturePolicy.classify(4f, 3f, 64f, 8f))
    }

    @Test fun verticalScrollNeverSkipsTrack() {
        assertEquals(ArtworkGestureAction.IGNORE, ArtworkGesturePolicy.classify(18f, 130f, 64f, 8f))
    }

    @Test fun tinyAndDiagonalSwipesAreIgnored() {
        assertEquals(ArtworkGestureAction.IGNORE, ArtworkGesturePolicy.classify(24f, 12f, 64f, 8f))
        assertEquals(ArtworkGestureAction.IGNORE, ArtworkGesturePolicy.classify(95f, 90f, 64f, 8f))
    }

    @Test fun swipeMustClearThreshold() {
        assertEquals(ArtworkGestureAction.IGNORE, ArtworkGesturePolicy.classify(-63f, 0f, 64f, 8f))
        assertEquals(ArtworkGestureAction.NEXT_TRACK, ArtworkGesturePolicy.classify(-65f, 0f, 64f, 8f))
    }

    @Test fun horizontalSwipeLeftShowsLyricsWhenPreferringLyricsSwitch() {
        assertEquals(
            ArtworkGestureAction.SHOW_LYRICS,
            ArtworkGesturePolicy.classify(-120f, 10f, 64f, 8f, preferLyricsSwitch = true),
        )
    }

    @Test fun horizontalSwipeRightShowsCoverWhenPreferringLyricsSwitch() {
        assertEquals(
            ArtworkGestureAction.SHOW_COVER,
            ArtworkGesturePolicy.classify(110f, 12f, 64f, 8f, preferLyricsSwitch = true),
        )
    }

    @Test fun tapOpensQueueEvenWhenPreferringLyricsSwitch() {
        assertEquals(
            ArtworkGestureAction.OPEN_QUEUE,
            ArtworkGesturePolicy.classify(4f, 3f, 64f, 8f, preferLyricsSwitch = true),
        )
    }

    @Test fun tinyOrDiagonalSwipeIgnoredWhenPreferringLyricsSwitch() {
        assertEquals(
            ArtworkGestureAction.IGNORE,
            ArtworkGesturePolicy.classify(24f, 12f, 64f, 8f, preferLyricsSwitch = true),
        )
        assertEquals(
            ArtworkGestureAction.IGNORE,
            ArtworkGesturePolicy.classify(95f, 90f, 64f, 8f, preferLyricsSwitch = true),
        )
    }
}
