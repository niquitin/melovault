package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.LibraryStyle
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloLibraryPolicyTest {

    @Test
    fun newIsDefaultAndKeysRoundTrip() {
        assertEquals(LibraryStyle.NEW, LibraryStyle.fromKey(null))
        assertEquals(LibraryStyle.NEW, LibraryStyle.fromKey("bogus"))
        for (s in LibraryStyle.entries) assertEquals(s, LibraryStyle.fromKey(s.key))
    }

    @Test
    fun newLayerOnlyForPhoneTabletAndTwoPaneAfterLogin() {
        assertEquals(NewLayer.PHONE_PORTRAIT, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.PHONE_PORTRAIT, true))
        assertEquals(NewLayer.TABLET_PORTRAIT, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.TABLET_PORTRAIT, true))
        assertEquals(NewLayer.TWO_PANE, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.TWO_PANE, true))
        assertNull(MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.COMPACT, true))
        assertNull(MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.CAR, true))
        assertNull(MeloLibraryPolicy.newLayer(LibraryStyle.LEGACY, Layout.PHONE_PORTRAIT, true))
        assertNull(MeloLibraryPolicy.newLayer(LibraryStyle.NEW, Layout.PHONE_PORTRAIT, false))
    }

    @Test
    fun deviceNumbersPickTheRightLayer() {
        fun layer(w: Int, h: Int) = MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(w, h, false), true)
        assertEquals(NewLayer.PHONE_PORTRAIT, layer(384, 832))   // S25 Ultra portrait (measured)
        assertEquals(NewLayer.TWO_PANE, layer(832, 384))         // S25 Ultra landscape (measured)
        assertEquals(NewLayer.PHONE_PORTRAIT, layer(360, 840))   // Sony XQ-AS52 portrait
        assertEquals(NewLayer.TWO_PANE, layer(840, 360))         // Sony landscape
        assertEquals(NewLayer.TWO_PANE, layer(1024, 640))        // Y700-like landscape
        assertEquals(NewLayer.TABLET_PORTRAIT, layer(640, 1024)) // Y700-like portrait
        assertNull(layer(384, 640))                              // Shanling stays on Gọn
    }

    @Test
    fun profileFollowsShortSide() {
        assertEquals(Profile.PHONE, MeloLibraryPolicy.profileFor(384))
        assertEquals(Profile.PHONE, MeloLibraryPolicy.profileFor(599))
        assertEquals(Profile.TABLET, MeloLibraryPolicy.profileFor(600))
        assertEquals(Profile.TABLET, MeloLibraryPolicy.profileFor(800))
    }

    @Test
    fun sourceGridAndMiniPlayerFollowSpec() {
        assertEquals(3, MeloLibraryPolicy.sourceColumns(NewLayer.TWO_PANE))
        assertEquals(2, MeloLibraryPolicy.sourceColumns(NewLayer.TABLET_PORTRAIT))
        assertEquals(2, MeloLibraryPolicy.sourceColumns(NewLayer.PHONE_PORTRAIT))
        assertTrue(MeloLibraryPolicy.miniPlayerShowsPrevious(NewLayer.TABLET_PORTRAIT))
        assertFalse(MeloLibraryPolicy.miniPlayerShowsPrevious(NewLayer.PHONE_PORTRAIT))
    }

    @Test
    fun chipsNeverInventTheDownloadedCount() {
        assertEquals(listOf("Tất cả", "Đã tải", "Playlist", "Chillout", "LakHouse", "Mixset"),
            MeloLibraryPolicy.chips(null).map { it.label })
        assertEquals("Đã tải (76)", MeloLibraryPolicy.chips(76)[1].label)
        assertEquals("Đã tải (1.204)", MeloLibraryPolicy.chips(1204)[1].label)
    }

    @Test
    fun selectedChipFollowsModeAndGenreKeyword() {
        assertEquals(0, MeloLibraryPolicy.selectedChip(Mode.ALL, "", null))
        assertEquals(1, MeloLibraryPolicy.selectedChip(Mode.DOWNLOADED, "", 5))
        assertEquals(2, MeloLibraryPolicy.selectedChip(Mode.PLAYLIST, "", null))
        assertEquals(3, MeloLibraryPolicy.selectedChip(Mode.ALL, "Chillout", null))
        assertEquals(4, MeloLibraryPolicy.selectedChip(Mode.ALL, " LAKHOUSE ", null))
        assertEquals(5, MeloLibraryPolicy.selectedChip(Mode.DOWNLOADED, "mixset", 3))
        assertEquals(0, MeloLibraryPolicy.selectedChip(Mode.ALL, "something else", null))
    }

    @Test
    fun chipQueryIsKeywordOrEmpty() {
        val chips = MeloLibraryPolicy.chips(null)
        assertEquals("", MeloLibraryPolicy.queryFor(chips[0]))
        assertEquals("", MeloLibraryPolicy.queryFor(chips[1]))
        assertEquals("chillout", MeloLibraryPolicy.queryFor(chips[3]))
        assertEquals("mixset", MeloLibraryPolicy.queryFor(chips[5]))
    }

    @Test
    fun headerCollapsesOnScrollDownAndReturnsOnScrollUpOrTop() {
        assertTrue(MeloLibraryPolicy.headerCollapsed(false, 40, 3))
        assertFalse(MeloLibraryPolicy.headerCollapsed(true, -40, 3))
        assertTrue(MeloLibraryPolicy.headerCollapsed(true, 2, 3))   // jitter keeps the state
        assertFalse(MeloLibraryPolicy.headerCollapsed(false, -2, 3))
        assertFalse(MeloLibraryPolicy.headerCollapsed(true, 0, 0))  // back at the top
        assertTrue(MeloLibraryPolicy.headerCollapsed(false, 40, 0)) // still moving down from the first row
    }

    @Test
    fun syncLineUsesRealCounts() {
        assertEquals("50 nhóm / 76 bản offline", MeloLibraryPolicy.syncLine("50 nhóm / 76 bản offline", 10, false))
        assertEquals("27.971 bài · Đã đồng bộ", MeloLibraryPolicy.syncLine(null, 27_971, false))
        assertEquals("Đang đồng bộ…", MeloLibraryPolicy.syncLine("", 27_971, true))
        assertEquals("Chưa có bài", MeloLibraryPolicy.syncLine(null, 0, false))
    }

    @Test
    fun playAllNeedsLoadedRows() {
        assertTrue(MeloLibraryPolicy.playAllEnabled(1))
        assertFalse(MeloLibraryPolicy.playAllEnabled(0))
    }
}
