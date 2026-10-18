package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloBottomBarPolicy.Tab
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloBottomBarPolicyTest {

    private fun spec(profile: Profile) = MeloBottomBarPolicy.spec(profile)!!

    @Test
    fun heightsFollowTheSpecTable() {
        assertEquals(72, spec(Profile.COMPACT).heightDp)
        assertEquals(88, spec(Profile.PHONE).heightDp)
        assertEquals(96, spec(Profile.TABLET).heightDp)
        assertEquals(52, spec(Profile.COMPACT).playDp)
        assertEquals(60, spec(Profile.PHONE).playDp)
        assertEquals(64, spec(Profile.TABLET).playDp)
    }

    @Test
    fun carModeKeepsItsOwnControls() {
        assertNull(MeloBottomBarPolicy.spec(Profile.CAR))
    }

    @Test
    fun everyTouchTargetIsAtLeast48dp() {
        Profile.entries.mapNotNull { MeloBottomBarPolicy.spec(it) }.forEach {
            assertTrue(it.skipDp >= MeloBottomBarPolicy.MIN_TOUCH_DP)
            assertTrue(it.playDp >= MeloBottomBarPolicy.MIN_TOUCH_DP)
        }
    }

    @Test
    fun theTransportRowFitsInsideTheBar() {
        Profile.entries.mapNotNull { MeloBottomBarPolicy.spec(it) }.forEach {
            val row = it.heightDp - MeloBottomBarPolicy.PROGRESS_DP - it.titleLineDp
            assertTrue("play ${it.playDp} must fit the row $row", it.playDp <= row)
        }
    }

    @Test
    fun compactHasTwoTabsAndPhonesFour() {
        assertEquals(listOf(Tab.LIBRARY, Tab.TELEGRAM), spec(Profile.COMPACT).tabs)
        assertEquals(listOf(Tab.LIBRARY, Tab.TELEGRAM, Tab.PLAYLIST, Tab.DOWNLOADED), spec(Profile.PHONE).tabs)
        assertEquals(spec(Profile.PHONE).tabs, spec(Profile.TABLET).tabs)
    }

    @Test
    fun tabsSplitEvenlyAroundThePlayButton() {
        val four = spec(Profile.PHONE).tabs
        assertEquals(listOf(Tab.LIBRARY, Tab.TELEGRAM), MeloBottomBarPolicy.leftTabs(four))
        assertEquals(listOf(Tab.PLAYLIST, Tab.DOWNLOADED), MeloBottomBarPolicy.rightTabs(four))
        val two = spec(Profile.COMPACT).tabs
        assertEquals(listOf(Tab.LIBRARY), MeloBottomBarPolicy.leftTabs(two))
        assertEquals(listOf(Tab.TELEGRAM), MeloBottomBarPolicy.rightTabs(two))
    }

    @Test
    fun activeTabOnPhoneFollowsSourcesAndMode() {
        val tabs = spec(Profile.PHONE).tabs
        assertEquals(Tab.TELEGRAM, MeloBottomBarPolicy.activeTab(tabs, showingSources = true, mode = Mode.ALL))
        assertEquals(Tab.PLAYLIST, MeloBottomBarPolicy.activeTab(tabs, false, Mode.PLAYLIST))
        assertEquals(Tab.DOWNLOADED, MeloBottomBarPolicy.activeTab(tabs, false, Mode.DOWNLOADED))
        assertEquals(Tab.LIBRARY, MeloBottomBarPolicy.activeTab(tabs, false, Mode.ALL))
    }

    @Test
    fun activeTabOnCompactTreatsDownloadedAsLibraryAndPlaylistAsNoTab() {
        val tabs = spec(Profile.COMPACT).tabs
        assertEquals(Tab.LIBRARY, MeloBottomBarPolicy.activeTab(tabs, false, Mode.ALL))
        assertEquals(Tab.LIBRARY, MeloBottomBarPolicy.activeTab(tabs, false, Mode.DOWNLOADED))
        assertEquals(Tab.TELEGRAM, MeloBottomBarPolicy.activeTab(tabs, true, Mode.DOWNLOADED))
        assertNull(MeloBottomBarPolicy.activeTab(tabs, false, Mode.PLAYLIST))
    }

    @Test
    fun noSongHidesInfoAndDimsPlay() {
        assertFalse(MeloBottomBarPolicy.showsSongInfo(false))
        assertFalse(MeloBottomBarPolicy.playEnabled(false))
        assertTrue(MeloBottomBarPolicy.playAlpha(false) < 1f)
        assertTrue(MeloBottomBarPolicy.showsSongInfo(true))
        assertTrue(MeloBottomBarPolicy.playEnabled(true))
        assertEquals(1f, MeloBottomBarPolicy.playAlpha(true), 0f)
    }

    @Test
    fun playGlyphMatchesTheSongRowState() {
        // Same flag as MeloCompactPolicy.rowStatus: paused row <=> Play glyph, else pause glyph.
        assertTrue(MeloBottomBarPolicy.showsPauseGlyph(hasSong = true, paused = false))
        assertFalse(MeloBottomBarPolicy.showsPauseGlyph(hasSong = true, paused = true))
        assertFalse(MeloBottomBarPolicy.showsPauseGlyph(hasSong = false, paused = false))
        assertEquals("Dừng", MeloBottomBarPolicy.playDescription(true, false))
        assertEquals("Phát", MeloBottomBarPolicy.playDescription(true, true))
        assertEquals("Phát", MeloBottomBarPolicy.playDescription(false, false))
    }

    @Test
    fun titleLineJoinsSongAndChannel() {
        assertEquals("Mất Kết Nối · BOX", MeloBottomBarPolicy.titleLine("Mất Kết Nối", "BOX"))
        assertEquals("Mất Kết Nối", MeloBottomBarPolicy.titleLine("  Mất Kết Nối ", null))
        assertEquals("Mất Kết Nối", MeloBottomBarPolicy.titleLine("Mất Kết Nối", "  "))
        assertNotNull(MeloBottomBarPolicy.contentDescription("A", "B"))
    }

    @Test
    fun shanlingKeepsEightRowsAboveTheBar() {
        // docs/ui-spec.md budget: window 574dp (maybe 604dp), header 48dp, 56dp rows, bar 72dp.
        val bar = spec(Profile.COMPACT).heightDp
        assertTrue(MeloBottomBarPolicy.visibleRows(574, 48, bar, 56, dividerDp = 0) >= 8)
        assertTrue(MeloBottomBarPolicy.visibleRows(604, 48, bar, 56, dividerDp = 4) >= 8)
        // Old layout (mini 56dp + gaps) kept 8 rows only without dividers, so dividers must stay slim.
        assertEquals(8, MeloBottomBarPolicy.visibleRows(574, 48, bar, 56, dividerDp = 0))
    }

    @Test
    fun tabsGetEnoughWidthOnTheNarrowestScreens() {
        // Shanling 384dp wide: the label "Telegram" needs about 56dp at 11sp.
        assertTrue(MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.COMPACT), sideMarginsDp = 16) >= 56)
        // S25 Ultra (384dp) with four tabs: two tabs per side, still above the 48dp touch width.
        assertTrue(MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.PHONE), sideMarginsDp = 32) >= 48)
    }
}
