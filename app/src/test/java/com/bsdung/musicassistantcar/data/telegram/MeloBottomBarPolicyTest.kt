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
        // REWORK-01: 2dp progress + "Đang phát" card + the unchanged button row (52 / 64 / 70).
        assertEquals(102, spec(Profile.COMPACT).heightDp)
        assertEquals(122, spec(Profile.PHONE).heightDp)
        assertEquals(132, spec(Profile.TABLET).heightDp)
        assertEquals(52, spec(Profile.COMPACT).playDp)
        assertEquals(60, spec(Profile.PHONE).playDp)
        assertEquals(64, spec(Profile.TABLET).playDp)
        assertEquals(48, spec(Profile.COMPACT).skipDp)
        assertEquals(48, spec(Profile.PHONE).skipDp)
        assertEquals(56, spec(Profile.TABLET).skipDp)
    }

    @Test
    fun theCardFollowsTheSpecTable() {
        assertEquals(48, spec(Profile.COMPACT).cardDp)
        assertEquals(56, spec(Profile.PHONE).cardDp)
        assertEquals(60, spec(Profile.TABLET).cardDp)
        assertEquals(36, spec(Profile.COMPACT).artDp)
        assertEquals(40, spec(Profile.PHONE).artDp)
        assertEquals(44, spec(Profile.TABLET).artDp)
        assertEquals(14f, spec(Profile.COMPACT).titleSp, 0f)
        assertEquals(15f, spec(Profile.PHONE).titleSp, 0f)
        assertEquals(16f, spec(Profile.TABLET).titleSp, 0f)
        assertEquals(12f, spec(Profile.COMPACT).channelSp, 0f)
        assertEquals(12f, spec(Profile.PHONE).channelSp, 0f)
        assertEquals(13f, spec(Profile.TABLET).channelSp, 0f)
    }

    @Test
    fun theButtonRowKeepsItsHeight() {
        assertEquals(52, MeloBottomBarPolicy.rowDp(spec(Profile.COMPACT)))
        assertEquals(64, MeloBottomBarPolicy.rowDp(spec(Profile.PHONE)))
        assertEquals(70, MeloBottomBarPolicy.rowDp(spec(Profile.TABLET)))
    }

    @Test
    fun theCardIsNeverBelow48dpAndGrowsWithTheSystemFontScale() {
        Profile.entries.mapNotNull { MeloBottomBarPolicy.spec(it) }.forEach {
            for (scale in listOf(0.85f, 1.0f, 1.3f)) {
                val h = MeloBottomBarPolicy.cardHeightDp(it, scale)
                assertTrue("card ${it.cardDp} at x$scale = $h", h >= MeloBottomBarPolicy.MIN_TOUCH_DP && h >= it.cardDp)
            }
            // The table value already holds two lines at font scale 1; the art tile fits inside the card.
            assertEquals(it.cardDp, MeloBottomBarPolicy.cardHeightDp(it, 1.0f))
            assertTrue(MeloBottomBarPolicy.cardHeightDp(it, 1.3f) >= MeloBottomBarPolicy.cardHeightDp(it, 1.0f))
            assertTrue(it.artDp + MeloBottomBarPolicy.CARD_PAD_DP * 2 <= it.cardDp)
        }
        assertEquals(48, MeloBottomBarPolicy.cardHeightDp(spec(Profile.COMPACT), 1.0f))
        // Gọn at x1.3 needs (14 + 12) * 1.2 * 1.3 + 12 = 52.6dp: the card grows, it does not clip.
        assertEquals(53, MeloBottomBarPolicy.cardHeightDp(spec(Profile.COMPACT), 1.3f))
        assertEquals(56, MeloBottomBarPolicy.cardHeightDp(spec(Profile.PHONE), 1.3f))
    }

    @Test
    fun theCardShowsTheChannelOnlyWhenKnown() {
        assertEquals("BOX", MeloBottomBarPolicy.channelLine(" BOX "))
        assertNull(MeloBottomBarPolicy.channelLine(null))
        assertNull(MeloBottomBarPolicy.channelLine("   "))
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
            val row = it.heightDp - MeloBottomBarPolicy.PROGRESS_DP - it.cardDp
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
        // docs/ui-spec.md budget: window 574dp (maybe 604dp), header 48dp, 52dp rows, bar 102dp.
        val bar = spec(Profile.COMPACT).heightDp
        val pad = MeloBottomBarPolicy.COMPACT_LIST_BOTTOM_PAD_DP
        val rowDp = MeloDesignTokens.sizes(Profile.COMPACT, 1f, 1f, 160f).rowHeight
        assertEquals(52, rowDp)
        // The Gọn list has no divider (the row inset draws the gap), so even 574dp keeps 8 rows.
        assertTrue(MeloBottomBarPolicy.visibleRows(574, 48, bar, rowDp, dividerDp = 0, reservedDp = pad) >= 8)
        assertTrue(MeloBottomBarPolicy.visibleRows(604, 48, bar, rowDp, dividerDp = 0, reservedDp = pad) >= 8)
        // 574 - 48 - 102 - 2 = 422dp of list for 8 x 52 = 416dp.
        assertEquals(8, MeloBottomBarPolicy.visibleRows(574, 48, bar, rowDp, dividerDp = 0, reservedDp = pad))
        // With the old 4dp divider 574dp only fits 7 rows: the reason the divider was removed.
        assertEquals(7, MeloBottomBarPolicy.visibleRows(574, 48, bar, rowDp, dividerDp = 4, reservedDp = 8))
    }

    @Test
    fun tabCellWidthsAreUnchangedByTheCard() {
        // Same transport buttons as before REWORK-01: 384dp - 16dp margins - (48 + 52 + 48)dp = 220dp over two tabs.
        assertEquals(110, MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.COMPACT), sideMarginsDp = 16))
        // Four tabs: (384 - 32 - (48 + 60 + 48)) / 4 = 49dp.
        assertEquals(49, MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.PHONE), sideMarginsDp = 32))
    }

    @Test
    fun tabsGetEnoughWidthOnTheNarrowestScreens() {
        // Shanling 384dp wide: the label "Telegram" needs about 56dp at 11sp.
        assertTrue(MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.COMPACT), sideMarginsDp = 16) >= 56)
        // S25 Ultra (384dp) with four tabs: two tabs per side, still above the 48dp touch width.
        assertTrue(MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.PHONE), sideMarginsDp = 32) >= 48)
    }

    @Test
    fun tabsShowOnlyTheIconWhenTheCellIsTooNarrowForTheLabel() {
        // FYT 7870 at font ×1.25: the label needs 53dp.
        assertTrue(MeloBottomBarPolicy.tabIconOnly(52, 1.25f))
        assertFalse(MeloBottomBarPolicy.tabIconOnly(53, 1.25f))
        // FYT 1/3 window (388dp, four tabs) gives about 50dp per tab; the 1/2 window (592dp) about 100dp.
        val third = MeloBottomBarPolicy.tabWidthDp(388, spec(Profile.PHONE), sideMarginsDp = 32)
        val half = MeloBottomBarPolicy.tabWidthDp(592, spec(Profile.PHONE), sideMarginsDp = 32)
        assertTrue("third=$third", MeloBottomBarPolicy.tabIconOnly(third, 1.25f))
        assertFalse("half=$half", MeloBottomBarPolicy.tabIconOnly(half, 1.25f))
        // S25 Ultra (49dp) and Sony (43dp) at font ×1.0 keep their labels, as they did before E95.
        val s25 = MeloBottomBarPolicy.tabWidthDp(384, spec(Profile.PHONE), sideMarginsDp = 32)
        val sony = MeloBottomBarPolicy.tabWidthDp(360, spec(Profile.PHONE), sideMarginsDp = 32)
        assertFalse("s25=$s25", MeloBottomBarPolicy.tabIconOnly(s25, 1.0f))
        assertFalse("sony=$sony", MeloBottomBarPolicy.tabIconOnly(sony, 1.0f))
        assertTrue(MeloBottomBarPolicy.tabIconOnly(41, 1.0f))
        assertFalse(MeloBottomBarPolicy.tabIconOnly(42, 1.0f))
        // A font scale below 1 never shrinks the label need below the base.
        assertTrue(MeloBottomBarPolicy.tabIconOnly(41, 0.85f))
    }
}
