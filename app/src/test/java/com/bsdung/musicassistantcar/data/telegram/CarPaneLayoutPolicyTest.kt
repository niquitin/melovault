package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPaneLayoutPolicyTest {
    @Test fun narrowCarNavigationPaneGetsCompactControls() {
        assertTrue(CarPaneLayoutPolicy.compactFor(370, 600))
        assertTrue(CarPaneLayoutPolicy.compactFor(420, 650))
    }
    @Test fun fullLandscapeKeepsWidePlayer() {
        assertFalse(CarPaneLayoutPolicy.compactFor(1152, 600))
    }
    @Test fun fullHeightPhoneKeepsLargeArt() {
        assertFalse(CarPaneLayoutPolicy.compactFor(360, 800))
    }
    @Test fun zeroSizeDoesNotTriggerCompact() {
        assertFalse(CarPaneLayoutPolicy.compactFor(0, 0))
    }
}
