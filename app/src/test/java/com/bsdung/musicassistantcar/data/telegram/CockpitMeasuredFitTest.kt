package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class CockpitMeasuredFitTest {
    @Test fun artworkUsesRemainingHeightAfterActualControlsAndInsets() {
        // Synthetic measured post-inset panes, not claims about physical device density.
        for (height in listOf(520, 380, 280, 210)) {
            for (controls in listOf(190, 230, 270)) {
                val art = CockpitLayoutPolicy.measuredCoverSize(height, 340, controls, 360)
                assertTrue(art >= 0)
                if (controls <= height) assertTrue(art + controls <= height)
                else assertEquals(0, art)
            }
        }
    }
    @Test fun noMinimumArtworkCanPushButtonsOutOfViewport() {
        assertEquals(20, CockpitLayoutPolicy.measuredCoverSize(300, 300, 280, 360))
        assertEquals(0, CockpitLayoutPolicy.measuredCoverSize(200, 300, 280, 360))
    }
    @Test fun measuredFontGrowthShrinksArtworkAndNarrowPaneCapsWidth() {
        assertEquals(160, CockpitLayoutPolicy.measuredCoverSize(500, 160, 220, 360))
        assertEquals(80, CockpitLayoutPolicy.measuredCoverSize(500, 160, 420, 360))
        assertEquals(0, CockpitLayoutPolicy.measuredCoverSize(500, 160, 500, 360))
    }
}
