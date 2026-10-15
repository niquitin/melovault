package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CarPaneLayoutPolicy — E9.1-T.1 boundary cases.
 *
 * FYT 7870 pane sizes (approximate, no physical device needed):
 *   2/3 ≈ 1280×1200 px  → 853×800 dp @240  |  640×600 dp @320
 *   1/2 ≈  960×1200 px  → 600×750 dp @320
 *   1/3 ≈  640×1200 px  → 400×750 dp @320
 *
 * Sony proxy QA sizes:
 *   Landscape @240 dpi override → 1080×2520 capped to ~1096×1027 → 732×549 dp @240
 *   Portrait  @240 dpi override → 549×732 dp @240
 */
class CarPaneLayoutPolicyTest {

    // ── E9.1-T.1 FYT pane regressions ────────────────────────────────────────

    @Test fun fyt_twoThirds_240dpi_isWide() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(853, 800))
    }

    @Test fun fyt_twoThirds_320dpi_isWide() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(640, 600))
    }

    @Test fun fyt_oneHalf_320dpi_isMedium() {
        assertEquals(CarPaneLayoutPolicy.Mode.MEDIUM, CarPaneLayoutPolicy.modeFor(600, 750))
    }

    @Test fun fyt_oneThird_320dpi_isNarrow() {
        assertEquals(CarPaneLayoutPolicy.Mode.NARROW, CarPaneLayoutPolicy.modeFor(400, 750))
    }

    // ── Sony proxy boundaries ─────────────────────────────────────────────────

    @Test fun sony_landscape_240dpi_approximation_isWide() {
        // Sony clamped to ~1096×1027 at 240 dpi → 732×549 dp (width > height).
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(732, 549))
    }

    @Test fun sony_portrait_240dpi_isMedium() {
        // Sony portrait at 240 dpi override → 549×732 dp.
        assertEquals(CarPaneLayoutPolicy.Mode.MEDIUM, CarPaneLayoutPolicy.modeFor(549, 732))
    }

    // ── Phone behaviour preserved ─────────────────────────────────────────────

    @Test fun phone_landscape_isWide() {
        assertEquals(CarPaneLayoutPolicy.Mode.WIDE, CarPaneLayoutPolicy.modeFor(800, 400))
    }

    @Test fun phone_portrait_isNarrow() {
        assertEquals(CarPaneLayoutPolicy.Mode.NARROW, CarPaneLayoutPolicy.modeFor(360, 780))
    }

    @Test fun phone_largePortrait_isMedium() {
        // Large phone in portrait (e.g. 460×960 dp, >= 450 threshold) — MEDIUM, not WIDE.
        assertEquals(CarPaneLayoutPolicy.Mode.MEDIUM, CarPaneLayoutPolicy.modeFor(460, 960))
    }

    // ── Legacy compact tests from beta69 ──────────────────────────────────────

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

    // ── coverSizeDp sanity ────────────────────────────────────────────────────

    @Test fun coverSizeDp_wide_bounded_to_45pct_width() {
        // FYT 2/3 @240dpi: widthDp=853, heightDp=800
        // Expected: min(853*45/100, 800-120, 420) = min(383, 680, 420) = 383
        assertEquals(383, CarPaneLayoutPolicy.coverSizeDp(853, 800))
    }

    @Test fun coverSizeDp_wide_320dpi_bounded_correctly() {
        // FYT 2/3 @320dpi: widthDp=640, heightDp=600
        // Expected: min(640*45/100, 600-120, 420) = min(288, 480, 420) = 288
        assertEquals(288, CarPaneLayoutPolicy.coverSizeDp(640, 600))
    }

    @Test fun coverSizeDp_medium_fyt_half() {
        // FYT 1/2 @320dpi: widthDp=600, heightDp=750
        // MEDIUM: min(600-50, 750*2/5, 340) = min(550, 300, 340) = 300
        assertEquals(300, CarPaneLayoutPolicy.coverSizeDp(600, 750))
    }

    @Test fun coverSizeDp_narrow_fyt_third() {
        // FYT 1/3 @320dpi: widthDp=400, heightDp=750
        // NARROW: min(400-28, 750*2/5, 330) = min(372, 300, 330) = 300
        assertEquals(300, CarPaneLayoutPolicy.coverSizeDp(400, 750))
    }

    @Test fun coverSizeDp_default_zero() {
        assertEquals(180, CarPaneLayoutPolicy.coverSizeDp(0, 0))
    }
}
