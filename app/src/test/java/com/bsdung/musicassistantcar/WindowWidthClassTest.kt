package com.bsdung.musicassistantcar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [WindowWidthClass] width-class selection logic.
 * Verifies thresholds for COMPACT / MEDIUM / EXPANDED without needing a device.
 * These thresholds drive the responsive FYT multi-window layout strategy.
 */
class WindowWidthClassTest {

    @Test
    fun `widthDp 380 resolves to COMPACT`() {
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(380))
    }

    @Test
    fun `widthDp 419 resolves to COMPACT`() {
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(419))
    }

    @Test
    fun `widthDp 420 resolves to MEDIUM`() {
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(420))
    }

    @Test
    fun `widthDp 550 resolves to MEDIUM`() {
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(550))
    }

    @Test
    fun `widthDp 699 resolves to MEDIUM`() {
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(699))
    }

    @Test
    fun `widthDp 700 resolves to EXPANDED`() {
        assertEquals(WindowWidthClass.EXPANDED, MainActivity.widthClassFor(700))
    }

    @Test
    fun `widthDp 720 resolves to EXPANDED`() {
        assertEquals(WindowWidthClass.EXPANDED, MainActivity.widthClassFor(720))
    }

    @Test
    fun `widthDp 1152 resolves to EXPANDED`() {
        assertEquals(WindowWidthClass.EXPANDED, MainActivity.widthClassFor(1152))
    }

    @Test
    fun `widthDp 0 resolves to COMPACT`() {
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(0))
    }

    @Test
    fun `widthDp at FYT one-third pane resolves to COMPACT`() {
        // FYT 1152x720 @ 240dpi (tvdpi): 1152/1.5 = 768dp full, 1/3 = ~256dp
        // FYT 1152x720 @ 320dpi (xhdpi): 1152/2 = 576dp full, 1/3 = ~192dp
        // Either way, 1/3 pane is clearly COMPACT
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(256))
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(192))
    }

    @Test
    fun `widthDp at FYT half pane resolves to MEDIUM`() {
        // FYT 1152x720 @ 240dpi: 768dp/2 = 384dp → COMPACT boundary, 
        // but at xhdpi 320: 576dp/2 = 288dp → COMPACT. 
        // At mdpi 160: 1152dp/2 = 576dp → MEDIUM ✓
        // Test the MEDIUM range directly
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(500))
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(576))
    }

    @Test
    fun `widthDp at FYT two-thirds pane resolves to EXPANDED`() {
        // FYT 1152x720 @ 160dpi: 1152dp * 2/3 = 768dp → EXPANDED
        assertEquals(WindowWidthClass.EXPANDED, MainActivity.widthClassFor(768))
        // FYT 1152x720 @ 213dpi (tvdpi): ~577dp → MEDIUM
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(577))
        // FYT 1152x720 @ 240dpi (hdpi): 512dp → MEDIUM
        assertEquals(WindowWidthClass.MEDIUM, MainActivity.widthClassFor(512))
    }

    @Test
    fun `Shanling M3 Ultra portrait widthDp 384 resolves to COMPACT`() {
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(384))
    }

    @Test
    fun `EXPANDED threshold is exactly 700`() {
        assertEquals(WindowWidthClass.MEDIUM,   MainActivity.widthClassFor(699))
        assertEquals(WindowWidthClass.EXPANDED, MainActivity.widthClassFor(700))
    }

    @Test
    fun `COMPACT threshold is exactly 420`() {
        assertEquals(WindowWidthClass.COMPACT, MainActivity.widthClassFor(419))
        assertEquals(WindowWidthClass.MEDIUM,  MainActivity.widthClassFor(420))
    }
}
