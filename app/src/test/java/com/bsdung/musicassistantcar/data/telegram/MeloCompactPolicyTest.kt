package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.CompactSetting
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloCompactPolicyTest {

    /** The PANEL (not the window) decides: panel short/long side in dp and whether the app shares the screen. */
    private fun panelCompact(shortDp: Int, longDp: Int, inMultiWindow: Boolean = false) =
        MeloScreenPolicy.panelCompact(shortDp, longDp, inMultiWindow)

    @Test
    fun layerKeyWithoutCoversIsTheOldKey() {
        assertEquals("compact:auto:768x1280:320:1.0", MeloCompactPolicy.layerKey("auto", 768, 1280, 320, 1.0f, false))
    }

    @Test
    fun layerKeyChangesWithCoversAndFontScale() {
        val plain = MeloCompactPolicy.layerKey("auto", 768, 1280, 320, 1.0f, false)
        val art = MeloCompactPolicy.layerKey("auto", 768, 1280, 320, 1.0f, true)
        assertEquals("$plain:art", art)
        assertTrue(plain != art)
        assertTrue(art != MeloCompactPolicy.layerKey("auto", 768, 1280, 320, 1.3f, true))
    }

    @Test
    fun autoTurnsOnForShanlingPanel() {
        // Shanling M3 Ultra: 768 × 1280 px at 320 dpi = 384 × 640dp, either orientation.
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(384, 640), authorized = true))
    }

    @Test
    fun autoStaysOffOnPhonesAndTablets() {
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(411, 914), authorized = true)) // phone
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(640, 1024), authorized = true)) // Y700-like
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(1200, 1920), authorized = true)) // FYT 160
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(756, 1209), authorized = true)) // FYT 7870 real panel
    }

    @Test
    fun autoUsesPanelNotSplitWindow() {
        // The head unit's panel is 1209 × 756dp; its 1/3 window (388 × 642dp) must not turn into Gọn.
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(756, 1209), authorized = true))
        // Even if firmware reported only the card as the panel, a shared screen never counts as Gọn.
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, panelCompact(388, 642, inMultiWindow = true), authorized = true))
    }

    @Test
    fun manualSettingsOverrideAuto() {
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.ON, panelCompact(640, 1024), authorized = true))
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.EMULATE_SHANLING, panelCompact(640, 1024), authorized = true))
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.OFF, panelCompact(384, 640), authorized = true))
    }

    @Test
    fun neverBeforeLogin() {
        for (s in CompactSetting.entries) {
            assertFalse(MeloCompactPolicy.shouldUseCompact(s, panelCompact(384, 640), authorized = false))
        }
    }

    @Test
    fun settingKeysRoundTripAndDefaultToAuto() {
        for (s in CompactSetting.entries) assertEquals(s, CompactSetting.fromKey(s.key))
        assertEquals(CompactSetting.AUTO, CompactSetting.fromKey(null))
        assertEquals(CompactSetting.AUTO, CompactSetting.fromKey("bogus"))
    }

    @Test
    fun downloadedDefaultAppliesOnlyOnce() {
        assertTrue(MeloCompactPolicy.needsDefaultDownloadedFilter(alreadyApplied = false))
        assertFalse(MeloCompactPolicy.needsDefaultDownloadedFilter(alreadyApplied = true))
    }

    @Test
    fun headerTitleUsesRealCounts() {
        assertEquals("Đã tải · 76 bài", MeloCompactPolicy.headerTitle(Mode.DOWNLOADED, null, 76))
        assertEquals("Tất cả · 25.085 bài", MeloCompactPolicy.headerTitle(Mode.ALL, null, 25_085))
        assertEquals("Playlist · 12 bài", MeloCompactPolicy.headerTitle(Mode.PLAYLIST, "Kênh A", 12))
        assertEquals("Nhạc Lạnh · 77 bài", MeloCompactPolicy.headerTitle(Mode.ALL, " Nhạc Lạnh ", 77))
        assertEquals("Đã tải · Nhạc Lạnh · 3 bài", MeloCompactPolicy.headerTitle(Mode.DOWNLOADED, "Nhạc Lạnh", 3))
        assertEquals("Tất cả", MeloCompactPolicy.headerTitle(Mode.ALL, null, null))
    }

    @Test
    fun rowsRebuildWhenSongOrPauseStateChanges() {
        assertFalse(MeloCompactPolicy.rowsStale(7L, false, 7L, false))
        assertTrue(MeloCompactPolicy.rowsStale(7L, false, 7L, true))
        assertTrue(MeloCompactPolicy.rowsStale(7L, true, 7L, false))
        assertTrue(MeloCompactPolicy.rowsStale(7L, false, 8L, false))
        assertTrue(MeloCompactPolicy.rowsStale(null, false, 8L, false))
        assertTrue(MeloCompactPolicy.rowsStale(7L, false, null, false))
        // No current song: the pause flag alone is meaningless.
        assertFalse(MeloCompactPolicy.rowsStale(null, false, null, true))
    }

    @Test
    fun rowStatusPriority() {
        assertEquals(Status.PLAYING, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = 40, playing = true))
        assertEquals(Status.PAUSED, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = null, playing = true, paused = true))
        // Paused only changes the current song; other rows are unaffected.
        assertEquals(Status.DOWNLOADED, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = null, playing = false, paused = true))
        assertEquals(Status.DOWNLOADING, MeloCompactPolicy.rowStatus(downloaded = false, downloadingPercent = 40, playing = false))
        assertEquals(Status.DOWNLOADED, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = null, playing = false))
        assertEquals(Status.TELEGRAM, MeloCompactPolicy.rowStatus(downloaded = false, downloadingPercent = null, playing = false))
    }

    @Test
    fun progressPercentNeverInvented() {
        assertEquals(45, MeloCompactPolicy.progressPercent(45, 100))
        assertEquals(100, MeloCompactPolicy.progressPercent(150, 100))
        assertNull(MeloCompactPolicy.progressPercent(10, 0))
        assertNull(MeloCompactPolicy.progressPercent(10, -1))
    }

    @Test
    fun loadMoreNearEnd() {
        assertTrue(MeloCompactPolicy.shouldLoadMore(180, 10, 200))
        assertFalse(MeloCompactPolicy.shouldLoadMore(0, 10, 200))
        assertFalse(MeloCompactPolicy.shouldLoadMore(0, 0, 0))
    }

    @Test
    fun downloadedLineHiddenWhileCountUnknown() {
        assertNull(MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = false, groups = null))
        assertNull(MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = true, groups = null))
        assertNull(MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = false, groups = -1))
    }

    @Test
    fun downloadedLineShowsStoredCountLabelledAsPreviousCheck() {
        val line = MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = false, groups = 3)
        assertEquals("Đã tải: 3 nhóm · lần kiểm trước", line)
        // Same wording on the source list (the "Tất cả nguồn · 29.607 bài" screen of the Shanling).
        assertEquals(line, MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = true, groups = 3))
    }

    @Test
    fun downloadedLineFollowsTheCountAfterABackgroundScan() {
        val stored = MeloCompactPolicy.downloadedLine(Mode.ALL, "", showingSources = false, groups = 3)
        val scanned = MeloCompactPolicy.downloadedLine(Mode.ALL, "", showingSources = false, groups = 12)
        assertEquals("Đã tải: 3 nhóm · lần kiểm trước", stored)
        assertEquals("Đã tải: 12 nhóm · lần kiểm trước", scanned)
    }

    @Test
    fun downloadedLineTreatsZeroAsAValidCount() {
        assertEquals(
            "Đã tải: 0 nhóm · lần kiểm trước",
            MeloCompactPolicy.downloadedLine(Mode.ALL, null, showingSources = false, groups = 0)
        )
    }

    @Test
    fun downloadedLineOnlyOnTheWholeLibraryViews() {
        // Downloaded mode already titles the count; playlist and one channel are not the whole library.
        assertNull(MeloCompactPolicy.downloadedLine(Mode.DOWNLOADED, null, showingSources = false, groups = 3))
        assertNull(MeloCompactPolicy.downloadedLine(Mode.PLAYLIST, null, showingSources = false, groups = 3))
        assertNull(MeloCompactPolicy.downloadedLine(Mode.ALL, "BOX", showingSources = false, groups = 3))
    }

    @Test
    fun downloadedLineUsesTheTealAccentAndFitsTheHeaderRow() {
        assertEquals(0xFF3DDC97.toInt(), MeloCompactPolicy.DOWNLOADED_LINE_COLOR)
        // Title 16sp + hint 13sp stacked (1.25 line height) stay inside the 48dp header row up to
        // fontScale 1.3, so the list keeps its height and the 72dp bar is not pushed down.
        val titleSp = 16f
        val hintSp = 13f
        for (fontScale in listOf(1.0f, 1.15f, 1.3f)) {
            assertTrue((titleSp + hintSp) * 1.25f * fontScale <= MeloDesignTokens.MIN_TOUCH_DP)
        }
    }
}
