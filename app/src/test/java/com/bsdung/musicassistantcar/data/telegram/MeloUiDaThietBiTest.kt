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
}
