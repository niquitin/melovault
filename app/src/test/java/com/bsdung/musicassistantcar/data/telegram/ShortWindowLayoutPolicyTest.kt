package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortWindowLayoutPolicyTest {

    @Test
    fun shortWindowDetection() {
        assertTrue(ShortWindowLayoutPolicy.isShort(640 / 2)) // 320dp
        assertTrue(ShortWindowLayoutPolicy.isShort(426))     // 640px @240dpi
        assertTrue(ShortWindowLayoutPolicy.isShort(500))
        assertTrue(ShortWindowLayoutPolicy.isShort(600))
        assertFalse(ShortWindowLayoutPolicy.isShort(601))
        assertFalse(ShortWindowLayoutPolicy.isShort(800))
        assertFalse(ShortWindowLayoutPolicy.isShort(1200))
        assertFalse(ShortWindowLayoutPolicy.isShort(0))
        assertFalse(ShortWindowLayoutPolicy.isShort(-100))
    }

    @Test
    fun topBarDimensionsContainTwoLinesWithoutClipping() {
        val shortMin = ShortWindowLayoutPolicy.topBarMinHeightDp(isShort = true)
        val normalMin = ShortWindowLayoutPolicy.topBarMinHeightDp(isShort = false)
        assertTrue("Short topBar min height must be >= 40dp", shortMin >= 40)
        assertTrue("Normal topBar min height must be >= 48dp", normalMin >= 48)

        val shortTitleEst = ShortWindowLayoutPolicy.titleColumnEstimatedHeightDp(isShort = true)
        val normalTitleEst = ShortWindowLayoutPolicy.titleColumnEstimatedHeightDp(isShort = false)
        assertTrue("Title column must fit within topBar min height (short)", shortTitleEst <= shortMin)
        assertTrue("Title column must fit within topBar min height (normal)", normalTitleEst <= normalMin)
    }

    @Test
    fun topNavigationContainsButtonsSafely() {
        val shortNav = ShortWindowLayoutPolicy.topNavigationHeightDp(isShort = true)
        val shortBtn = ShortWindowLayoutPolicy.topNavButtonHeightDp(isShort = true)
        assertTrue("Top nav button must not exceed parent height in short mode", shortBtn <= shortNav)

        val normalNav = ShortWindowLayoutPolicy.topNavigationHeightDp(isShort = false)
        val normalBtn = ShortWindowLayoutPolicy.topNavButtonHeightDp(isShort = false)
        assertTrue("Top nav button must not exceed parent height in normal mode", normalBtn <= normalNav)
    }

    @Test
    fun bottomNavigationContainsButtonsSafely() {
        val shortNav = ShortWindowLayoutPolicy.bottomNavHeightDp(isShort = true)
        val shortBtn = ShortWindowLayoutPolicy.bottomNavButtonHeightDp(isShort = true)
        assertTrue("Bottom nav button must not exceed parent height in short mode", shortBtn <= shortNav)

        val normalNav = ShortWindowLayoutPolicy.bottomNavHeightDp(isShort = false)
        val normalBtn = ShortWindowLayoutPolicy.bottomNavButtonHeightDp(isShort = false)
        assertTrue("Bottom nav button must not exceed parent height in normal mode", normalBtn <= normalNav)
    }

    @Test
    fun miniPlayerDimensionsRemainCompact() {
        val shortCtrl = ShortWindowLayoutPolicy.miniControlHeightDp(isShort = true)
        val normalCtrl = ShortWindowLayoutPolicy.miniControlHeightDp(isShort = false)
        assertTrue(shortCtrl < normalCtrl)
        assertTrue(shortCtrl in 32..36)

        val shortArt = ShortWindowLayoutPolicy.miniArtSizeDp(isShort = true)
        val normalArt = ShortWindowLayoutPolicy.miniArtSizeDp(isShort = false)
        assertTrue(shortArt < normalArt)
        assertTrue(shortArt in 32..38)
    }

    @Test
    fun headerAndNavigationDoNotOverlapVertically() {
        // In short mode: topBarTop = 0, topBarHeight = 42dp, navTopMargin = 2dp
        val topBarTop = 0
        val topBarHeight = 42
        val navTopMargin = 2
        val validNavTop = topBarTop + topBarHeight + navTopMargin // 44dp

        assertTrue(
            "Navigation placed below topBar must not overlap",
            ShortWindowLayoutPolicy.verifyHeaderNavNonOverlap(topBarTop, topBarHeight, navTopMargin, validNavTop)
        )
        assertFalse(
            "Navigation overlapping topBar must fail verification",
            ShortWindowLayoutPolicy.verifyHeaderNavNonOverlap(topBarTop, topBarHeight, navTopMargin, 36)
        )
    }

    @Test
    fun truthfulSourceCountExcludesAggregateRow() {
        // Aggregate 0L ("♫ Tất cả nguồn") + 29 real sources = 30 entries
        val listWithAggregate = listOf(0L) + (101L..129L).toList()
        assertEquals(30, listWithAggregate.size)
        assertEquals(29, ShortWindowLayoutPolicy.truthfulSourceCount(listWithAggregate))

        // Normal list without aggregate row
        val listWithoutAggregate = (101L..129L).toList()
        assertEquals(29, ShortWindowLayoutPolicy.truthfulSourceCount(listWithoutAggregate))

        // Empty list
        assertEquals(0, ShortWindowLayoutPolicy.truthfulSourceCount(emptyList()))

        // Only aggregate row
        assertEquals(1, ShortWindowLayoutPolicy.truthfulSourceCount(listOf(0L)))
    }

    @Test
    fun navigationRoutingPolicy() {
        // Portrait Edge Pro uses bottom nav in NARROW and MEDIUM
        assertTrue(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
                CarPaneLayoutPolicy.Mode.NARROW
            )
        )
        assertTrue(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
                CarPaneLayoutPolicy.Mode.MEDIUM
            )
        )
        // Portrait Edge Pro uses top nav in WIDE (landscape)
        assertFalse(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
                CarPaneLayoutPolicy.Mode.WIDE
            )
        )

        // Classic always uses top nav
        assertFalse(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.CLASSIC,
                CarPaneLayoutPolicy.Mode.NARROW
            )
        )
        assertFalse(
            ShortWindowLayoutPolicy.shouldUseBottomNav(
                UiThemePolicy.ThemePreset.CLASSIC,
                CarPaneLayoutPolicy.Mode.WIDE
            )
        )
    }
}
