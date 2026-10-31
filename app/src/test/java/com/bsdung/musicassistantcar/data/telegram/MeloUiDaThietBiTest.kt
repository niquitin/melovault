package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules behind the multi-device UI fixes UI-01..UI-08 (docs/plans/2026-10-31-giao-dien-da-thiet-bi.md, GD6). */
class MeloUiDaThietBiTest {

    // ---- UI-01: optional parts above the song list give way before the list does ----------------

    @Test
    fun ui01_tallPaneKeepsEveryOptionalPart() {
        assertEquals(0, MeloLibraryPolicy.partsToTrim(2000, 600, listOf(60, 60, 120, 140), 700))
    }

    @Test
    fun ui01_shortPaneHidesOnlyAsManyPartsAsNeeded() {
        // 1000 - 500 fixed = 500 for the list and the four parts (380 together).
        val parts = listOf(60, 60, 120, 140)
        assertEquals(0, MeloLibraryPolicy.partsToTrim(1000, 500, parts, 120))
        assertEquals(1, MeloLibraryPolicy.partsToTrim(1000, 500, parts, 180))
        assertEquals(2, MeloLibraryPolicy.partsToTrim(1000, 500, parts, 240))
        assertEquals(3, MeloLibraryPolicy.partsToTrim(1000, 500, parts, 360))
    }

    @Test
    fun ui01_paneTooSmallEvenAfterTrimmingHidesAllParts() {
        assertEquals(4, MeloLibraryPolicy.partsToTrim(600, 500, listOf(60, 60, 120, 140), 300))
    }

    @Test
    fun ui01_partsNotShownCostNothingAndUnknownPaneChangesNothing() {
        assertEquals(0, MeloLibraryPolicy.partsToTrim(1000, 500, listOf(0, 0, 0, 0), 300))
        assertEquals(0, MeloLibraryPolicy.partsToTrim(0, 500, listOf(60, 60), 300))
        assertEquals(0, MeloLibraryPolicy.partsToTrim(500, 100, emptyList(), 300))
    }

    // ---- UI-02: the bar steps aside while the keyboard is up in a window too short for both ------

    @Test
    fun ui02_keyboardHidesTheBarInShortWindows() {
        assertTrue(MeloBottomBarPolicy.hiddenForKeyboard(800, 881)) // M3 portrait
        assertTrue(MeloBottomBarPolicy.hiddenForKeyboard(800, 537)) // M1 portrait
        assertTrue(MeloBottomBarPolicy.hiddenForKeyboard(1, 899))
    }

    @Test
    fun ui02_barStaysWithoutKeyboardOrInTallWindows() {
        assertFalse(MeloBottomBarPolicy.hiddenForKeyboard(0, 537))
        assertFalse(MeloBottomBarPolicy.hiddenForKeyboard(800, 900))
        assertFalse(MeloBottomBarPolicy.hiddenForKeyboard(800, 1128)) // M7 portrait tablet
    }

    // ---- UI-03: the full-screen queue gets the screen's side margin ----------------------------------

    @Test
    fun ui03_queueKeepsSideMarginAndTopGapOnTopOfTheSystemBars() {
        assertEquals(listOf(40, 98, 40, 120), MeloQueueUiPolicy.fullScreenPadding(0, 90, 0, 120, 0, 40, 8))
        // A display cutout on the left adds to the margin instead of replacing it.
        assertEquals(listOf(112, 98, 40, 120), MeloQueueUiPolicy.fullScreenPadding(72, 90, 0, 120, 0, 40, 8))
    }

    @Test
    fun ui03_keyboardWinsOverTheNavigationBarAtTheBottom() {
        assertEquals(900, MeloQueueUiPolicy.fullScreenPadding(0, 90, 0, 120, 900, 40, 8)[3])
    }

    // ---- UI-07: chips wrap, the hint loses whole words, the song menu has short labels --------------

    @Test
    fun ui07_flowRowStaysOneLineWhenEverythingFits() {
        // Four chips of 100 + 8 margin each in a 440 px row: the last margin does not have to fit.
        val place = MeloFlowRow.place(intArrayOf(108, 108, 108, 108), intArrayOf(48, 48, 48, 48), intArrayOf(8, 8, 8, 8), 424, 4)
        assertEquals(listOf(0, 0, 0, 0), place.ys.toList())
        assertEquals(listOf(0, 108, 216, 324), place.xs.toList())
        assertEquals(48, place.height)
    }

    @Test
    fun ui07_flowRowMovesTheChipThatDoesNotFitToTheNextLine() {
        val place = MeloFlowRow.place(intArrayOf(108, 108, 108, 108), intArrayOf(48, 48, 48, 48), intArrayOf(8, 8, 8, 8), 300, 4)
        assertEquals(listOf(0, 0, 52, 52), place.ys.toList())
        assertEquals(listOf(0, 108, 0, 108), place.xs.toList())
        assertEquals(100, place.height)
    }

    @Test
    fun ui07_flowRowLineHeightIsTheTallestChipAndEmptyRowIsFlat() {
        val place = MeloFlowRow.place(intArrayOf(200, 200), intArrayOf(48, 72), intArrayOf(0, 0), 300, 4)
        assertEquals(48 + 4 + 72, place.height)
        assertEquals(0, MeloFlowRow.place(IntArray(0), IntArray(0), IntArray(0), 300, 4).height)
    }

    @Test
    fun ui07_flowRowKeepsAChipWiderThanTheRowOnItsOwnLine() {
        val place = MeloFlowRow.place(intArrayOf(500, 100), intArrayOf(48, 48), intArrayOf(0, 0), 300, 4)
        assertEquals(listOf(0, 0), place.xs.toList())
        assertEquals(listOf(0, 52), place.ys.toList())
    }

    private val perChar: (String) -> Float = { it.length * 10f }

    @Test
    fun ui07_hintThatFitsIsUntouched() {
        assertEquals("Tìm bài hát…", MeloLibraryPolicy.fitHint("Tìm bài hát…", 120f, perChar))
        assertEquals("Tìm bài hát…", MeloLibraryPolicy.fitHint("Tìm bài hát…", 0f, perChar))
    }

    @Test
    fun ui07_longHintLosesWholeWordsFromTheEnd() {
        val full = "Tìm trong thư viện đã tải…"
        assertEquals("Tìm trong thư viện đã…", MeloLibraryPolicy.fitHint(full, 220f, perChar))
        assertEquals("Tìm trong thư viện…", MeloLibraryPolicy.fitHint(full, 190f, perChar))
        assertEquals("Tìm trong…", MeloLibraryPolicy.fitHint(full, 100f, perChar))
        // Not even the first word fits: left to the field's own ellipsis.
        assertEquals(full, MeloLibraryPolicy.fitHint(full, 20f, perChar))
    }

    @Test
    fun ui07_songMenuKeepsFullWordingUpToFontScale1_3() {
        val labels = MeloLibraryPolicy.songMenuLabels(1.3f, favorite = false, versions = 3)
        assertEquals("☆ Thêm vào Playlist", labels.favorite)
        assertEquals("☰ Chọn bản khác (3 bản)", labels.versions)
        assertEquals("ℹ Thông tin chi tiết", labels.details)
        assertEquals(null, MeloLibraryPolicy.songMenuLabels(1.0f, false, null).versions)
    }

    // ---- UI-08: a tab label that does not fit its cell gives way to the icon ----------------------------

    @Test
    fun ui08_labelWiderThanItsCellDropsToTheIcon() {
        assertTrue(MeloBottomBarPolicy.labelOverflows(100, 112f, 2.0f)) // "Telegram" at font scale 2 in a 600 dp window
        assertTrue(MeloBottomBarPolicy.labelOverflows(100, 112f, 1.5f))
        assertFalse(MeloBottomBarPolicy.labelOverflows(100, 100f, 2.0f))
        assertFalse(MeloBottomBarPolicy.labelOverflows(100, 60f, 2.0f))
    }

    @Test
    fun ui08_unknownCellWidthNeverForcesIconOnly() {
        assertFalse(MeloBottomBarPolicy.labelOverflows(0, 500f, 2.0f))
    }

    @Test
    fun ui08_normalFontKeepsTheLabelsTheOldWidthRuleDecides() {
        // Sony / S25 at x1.0 and x1.3: not touched by the measured check.
        assertFalse(MeloBottomBarPolicy.labelOverflows(48, 49f, 1.0f))
        assertFalse(MeloBottomBarPolicy.labelOverflows(60, 70f, 1.3f))
    }

    @Test
    fun ui07_songMenuUsesShortWordingFromFontScale1_5() {
        val labels = MeloLibraryPolicy.songMenuLabels(1.5f, favorite = false, versions = 3)
        assertEquals("☆ Lưu Playlist", labels.favorite)
        assertEquals("☰ Bản khác (3)", labels.versions)
        assertEquals("ℹ Chi tiết", labels.details)
        assertEquals("★ Bỏ Playlist", MeloLibraryPolicy.songMenuLabels(2.0f, favorite = true, versions = null).favorite)
    }
}
