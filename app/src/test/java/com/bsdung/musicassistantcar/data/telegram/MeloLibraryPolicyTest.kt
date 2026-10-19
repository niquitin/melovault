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
        fun layer(w: Int, h: Int, panelCompact: Boolean = false) =
            MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(w, h, false, panelCompact), true)
        assertEquals(NewLayer.PHONE_PORTRAIT, layer(384, 832))   // S25 Ultra portrait (measured)
        assertEquals(NewLayer.TWO_PANE, layer(832, 384))         // S25 Ultra landscape (measured)
        assertEquals(NewLayer.PHONE_PORTRAIT, layer(360, 840))   // Sony XQ-AS52 portrait
        assertEquals(NewLayer.TWO_PANE, layer(840, 360))         // Sony landscape
        assertEquals(NewLayer.TWO_PANE, layer(1024, 640))        // Y700-like landscape
        assertEquals(NewLayer.TABLET_PORTRAIT, layer(640, 1024)) // Y700-like portrait
        assertNull(layer(384, 640, panelCompact = true))         // Shanling stays on Gọn
    }

    @Test
    fun fytWindowsPickTheNewLayerNeverTheOldOne() {
        // FYT 7870 (panel 1209 × 756dp, so panelCompact is false) at full, 2/3, 1/2 and 1/3.
        assertEquals(NewLayer.TWO_PANE, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(1179, 642, false, false), true))
        assertEquals(NewLayer.TABLET_PORTRAIT, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(782, 642, false, false), true))
        assertEquals(NewLayer.PHONE_PORTRAIT, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(592, 642, false, false), true))
        assertEquals(NewLayer.PHONE_PORTRAIT, MeloLibraryPolicy.newLayer(LibraryStyle.NEW, MeloScreenPolicy.chooseLayout(388, 642, false, false), true))
    }

    @Test
    fun anchorIndexFindsTheRowOrStartsAtTheTop() {
        fun row(key: Long) = MeloScreenHost.Row("t$key", null, "f$key.mp3", 60, key, false, null, null)
        val rows = listOf(row(10), row(20), row(30))
        assertEquals(0, MeloLibraryPolicy.anchorIndex(rows, 10))
        assertEquals(2, MeloLibraryPolicy.anchorIndex(rows, 30))
        assertEquals(0, MeloLibraryPolicy.anchorIndex(rows, 99)) // not loaded (yet): top
        assertEquals(0, MeloLibraryPolicy.anchorIndex(rows, null))
        assertEquals(0, MeloLibraryPolicy.anchorIndex(emptyList(), 10))
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

    private fun src(id: Long, count: Int) = MeloScreenHost.SourceRow(id, "K$id", count)

    @Test
    fun splitSourcesSeparatesEmptyChannelsAndDropsAllEntry() {
        val (full, empty) = MeloLibraryPolicy.splitSources(listOf(src(0, 99), src(1, 5), src(2, 0), src(3, 7), src(4, -2)))
        assertEquals(listOf(1L, 3L), full.map { it.chatId })
        assertEquals(listOf(2L, 4L), empty.map { it.chatId })
    }

    @Test
    fun splitSourcesEdgeCases() {
        assertEquals(emptyList<MeloScreenHost.SourceRow>() to emptyList<MeloScreenHost.SourceRow>(), MeloLibraryPolicy.splitSources(emptyList()))
        val (full, empty) = MeloLibraryPolicy.splitSources(listOf(src(1, 0), src(2, 0)))
        assertTrue(full.isEmpty())
        assertEquals(2, empty.size)
    }

    @Test
    fun emptySourcesLabelFoldsAndFormats() {
        assertNull(MeloLibraryPolicy.emptySourcesLabel(0, false))
        assertNull(MeloLibraryPolicy.emptySourcesLabel(0, true))
        assertEquals("▸ Các kênh chưa có nhạc (3)", MeloLibraryPolicy.emptySourcesLabel(3, false))
        assertEquals("▾ Các kênh chưa có nhạc (3)", MeloLibraryPolicy.emptySourcesLabel(3, true))
        assertEquals("▸ Các kênh chưa có nhạc (1.234)", MeloLibraryPolicy.emptySourcesLabel(1234, false))
    }

    @Test
    fun quickSwitchOnlyOnTwoPaneWithOneChannelOpen() {
        assertTrue(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.ALL, 7L, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, true, Mode.ALL, 7L, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.ALL, null, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.PLAYLIST, 7L, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.DOWNLOADED, 7L, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 7L, 5))
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TABLET_PORTRAIT, false, Mode.ALL, 7L, 5))
    }

    @Test
    fun quickSwitchStaysHiddenWhileThereIsNoChannelToSwitchTo() {
        // F02: no channel with songs (list loading, or every count still 0) keeps the big player.
        assertFalse(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.ALL, 7L, 0))
        assertTrue(MeloLibraryPolicy.quickSwitchVisible(NewLayer.TWO_PANE, false, Mode.ALL, 7L, 1))
        val none = listOf(MeloScreenHost.SourceRow(7L, "Kênh", 0), MeloScreenHost.SourceRow(0L, "Kho", 10))
        assertEquals(0, MeloLibraryPolicy.quickSwitchRows(none).size)
    }

    @Test
    fun wideHeaderKeepsOneRow() {
        assertFalse(MeloLibraryPolicy.headerStacked(paneWidthPx = 1400, titleWidthPx = 2000, chipsWidthPx = 480, minTitlePx = 400))
    }

    @Test
    fun narrowHeaderWithLongTitleStacks() {
        // pane 700, buttons 480 -> 220 left for the title, which needs min(2000, max(400, 280)) = 400
        assertTrue(MeloLibraryPolicy.headerStacked(700, 2000, 480, 400))
    }

    @Test
    fun shortTitleStaysOnTheRowWhenItFits() {
        // 220 left, the whole title is only 150 wide
        assertFalse(MeloLibraryPolicy.headerStacked(700, 150, 480, 400))
        // but not when 220 is less than the title's own width
        assertTrue(MeloLibraryPolicy.headerStacked(700, 260, 480, 400))
    }

    @Test
    fun buttonsWiderThanThePaneStack() {
        assertTrue(MeloLibraryPolicy.headerStacked(300, 100, 500, 100))
    }

    @Test
    fun headerBoundaryIsFortyPercentOfThePane() {
        // pane 1000: the title needs 400 (40%) because minTitle 100 is smaller
        assertFalse(MeloLibraryPolicy.headerStacked(1000, 3000, 600, 100)) // exactly 400 left
        assertTrue(MeloLibraryPolicy.headerStacked(1000, 3000, 601, 100))  // 399 left
        // minTitle larger than 40%: it wins
        assertTrue(MeloLibraryPolicy.headerStacked(1000, 3000, 600, 450))
    }

    @Test
    fun headerWithoutButtonsOrWidthNeverStacks() {
        assertFalse(MeloLibraryPolicy.headerStacked(1000, 3000, 0, 400))
        assertFalse(MeloLibraryPolicy.headerStacked(0, 3000, 100, 400))
    }

    @Test
    fun topResetWaitsForRowsAndNeverRunsOnTheGrid() {
        assertTrue(MeloLibraryPolicy.shouldResetTop(pending = true, showingSources = false, rowCount = 30))
        assertFalse(MeloLibraryPolicy.shouldResetTop(pending = false, showingSources = false, rowCount = 30))
        assertFalse(MeloLibraryPolicy.shouldResetTop(pending = true, showingSources = false, rowCount = 0))
        assertFalse(MeloLibraryPolicy.shouldResetTop(pending = true, showingSources = true, rowCount = 30))
    }

    @Test
    fun quickSwitchOffsetKeepsTheOpenRowInsideTheList() {
        assertEquals(200, MeloLibraryPolicy.quickSwitchOffset(600, 80))
        assertEquals(0, MeloLibraryPolicy.quickSwitchOffset(0, 80))
        // short list: a third would push the row past the bottom edge
        assertEquals(40, MeloLibraryPolicy.quickSwitchOffset(120, 80))
        assertEquals(0, MeloLibraryPolicy.quickSwitchOffset(60, 80))
        for (h in 0..900 step 30) {
            val offset = MeloLibraryPolicy.quickSwitchOffset(h, 80)
            assertTrue(offset >= 0)
            assertTrue(h < 80 || offset + 80 <= h)
        }
    }

    @Test
    fun quickSwitchRowsAndIndex() {
        val rows = MeloLibraryPolicy.quickSwitchRows(listOf(src(0, 10), src(5, 9), src(6, 0), src(8, 3)))
        assertEquals(listOf(5L, 8L), rows.map { it.chatId })
        assertEquals(1, MeloLibraryPolicy.quickSwitchIndex(rows, 8L))
        assertEquals(-1, MeloLibraryPolicy.quickSwitchIndex(rows, 6L))
        assertEquals(-1, MeloLibraryPolicy.quickSwitchIndex(rows, null))
        assertEquals(-1, MeloLibraryPolicy.quickSwitchIndex(emptyList(), 5L))
    }

    @Test
    fun channelListRefreshesAtMostEveryTenSecondsWhileSyncing() {
        assertTrue(MeloLibraryPolicy.channelRefreshDue(5_000, 0))              // never refreshed yet
        assertFalse(MeloLibraryPolicy.channelRefreshDue(14_999, 5_000))
        assertTrue(MeloLibraryPolicy.channelRefreshDue(15_000, 5_000))
        assertTrue(MeloLibraryPolicy.channelRefreshDue(1_000, 5_000))          // the clock went back: do not stall forever
        assertTrue(MeloLibraryPolicy.channelRefreshDue(7_000, 5_000, minIntervalMs = 2_000))
    }

    @Test
    fun libraryDiagShowsCountsOnlyAndAdmitsWhenUnknown() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val lines = MeloLibraryPolicy.libraryDiagLines(MeloLibraryPolicy.LibraryDiag(42_734, 0, 27, 1_700_000_000_000L), utc)
        assertEquals("42.734 bài", lines.first { it.first == "Tổng bài trong thư viện" }.second)
        assertEquals("0", lines.first { it.first == "Kênh có bài (số đã lưu)" }.second)
        assertEquals("27", lines.first { it.first == "Kênh có bài (đếm thật)" }.second)
        assertEquals("14/11/2023 22:13", lines.first { it.first == "Đồng bộ hoàn tất lần cuối" }.second)
        assertEquals("chưa từng hoàn tất", MeloLibraryPolicy.lastSyncText(0L, utc))
        val unknown = MeloLibraryPolicy.libraryDiagLines(null)
        assertEquals(1, unknown.size)
        assertTrue(unknown.single().second.startsWith("chưa có số"))
        assertEquals(1, MeloLibraryPolicy.libraryDiagLines(MeloLibraryPolicy.LibraryDiag(-1, -1, -1, 0L)).size)
    }
}
