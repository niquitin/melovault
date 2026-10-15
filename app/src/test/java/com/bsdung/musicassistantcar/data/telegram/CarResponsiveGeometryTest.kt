package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarResponsiveGeometryTest {
    @Test fun narrowSplitPane() {
        assertEquals(CarPaneLayoutPolicy.Mode.NARROW, CarPaneLayoutPolicy.modeFor(370, 520))
        val art = CarPaneLayoutPolicy.coverSizeDp(370, 520)
        assertTrue(art in 96..342)
    }
    @Test fun mediumTabletPane() {
        assertEquals(CarPaneLayoutPolicy.Mode.MEDIUM, CarPaneLayoutPolicy.modeFor(600, 750))
        assertTrue(CarPaneLayoutPolicy.coverSizeDp(600, 750) <= 340)
    }
    @Test fun wideCarLandscapeTwoColumns() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(850, 475))
        assertTrue(CarPaneLayoutPolicy.coverSizeDp(850, 475) in 96..400)
    }
    @Test fun fullScreen720pWindow() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(960, 600))
    }
    @Test fun fyt7870CarLandscapeWindow() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(1152, 720))
        val art = CarPaneLayoutPolicy.coverSizeDp(1152, 720)
        assertTrue(art in 96..430)
    }
    @Test fun sonyLandscapeWide() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(1920, 1080))
        val art = CarPaneLayoutPolicy.coverSizeDp(1920, 1080)
        assertTrue(art in 96..430)
    }
    @Test fun fytSplitPaneNarrow() {
        assertEquals(CarPaneLayoutPolicy.Mode.NARROW, CarPaneLayoutPolicy.modeFor(400, 600))
        assertTrue(CarPaneLayoutPolicy.compactFor(400, 600))
        val art = CarPaneLayoutPolicy.coverSizeDp(400, 600)
        assertTrue(art in 96..330)
    }
    @Test fun tinyHeightKeepsAtLeastValidArtwork() {
        assertTrue(CarPaneLayoutPolicy.coverSizeDp(340, 220) >= 96)
    }
}
