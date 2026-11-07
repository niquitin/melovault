package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.DeckLayout
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.NowPlayingStyle
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.SleepOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloNowPlayingPolicyTest {

    @Test
    fun studioDeckIsDefaultAndKeysRoundTrip() {
        assertEquals(NowPlayingStyle.STUDIO_DECK, NowPlayingStyle.fromKey(null))
        assertEquals(NowPlayingStyle.STUDIO_DECK, NowPlayingStyle.fromKey("bogus"))
        for (s in NowPlayingStyle.entries) assertEquals(s, NowPlayingStyle.fromKey(s.key))
    }

    @Test
    fun layoutFollowsMeasuredS25() {
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(384, 832))
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(832, 384))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(640, 1024))
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(1024, 640))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(384, 640))
    }

    @Test
    fun lowLandscapeWindowGetsTheCompactDeck() {
        assertTrue(MeloNowPlayingPolicy.landscapeCompact(DeckLayout.LANDSCAPE, 411))
        assertTrue(MeloNowPlayingPolicy.landscapeCompact(DeckLayout.LANDSCAPE, 499))
        assertFalse(MeloNowPlayingPolicy.landscapeCompact(DeckLayout.LANDSCAPE, 500))
        assertFalse(MeloNowPlayingPolicy.landscapeCompact(DeckLayout.LANDSCAPE, 642))
        assertFalse(MeloNowPlayingPolicy.landscapeCompact(DeckLayout.PORTRAIT, 411))
        // The sideways Sony window is compact; the head unit and tablet windows are not.
        assertTrue(MeloNowPlayingPolicy.landscapeCompact(MeloNowPlayingPolicy.deckLayout(912, 411), 411))
        assertFalse(MeloNowPlayingPolicy.landscapeCompact(MeloNowPlayingPolicy.deckLayout(782, 642), 642))
    }

    @Test
    fun deckIsTwoColumnsWhenWiderThanTallAndAtLeast600dp() {
        // FYT 7870 windows (owner video 2026-10-19) and the Sony sideways window.
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(1179, 642))
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(782, 642))
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(912, 411))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(592, 642))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(388, 642))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(411, 860))
        // Boundaries: 600dp wide is the first two-column width, a square window is one column.
        assertEquals(DeckLayout.LANDSCAPE, MeloNowPlayingPolicy.deckLayout(600, 599))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(599, 400))
        assertEquals(DeckLayout.PORTRAIT, MeloNowPlayingPolicy.deckLayout(700, 700))
    }

    @Test
    fun sleepTimerDeadlineAndLabel() {
        assertNull(MeloNowPlayingPolicy.sleepDeadline(SleepOption.OFF, 1_000))
        assertNull(MeloNowPlayingPolicy.sleepDeadline(SleepOption.END_OF_TRACK, 1_000))
        assertEquals(1_000L + 15 * 60_000L, MeloNowPlayingPolicy.sleepDeadline(SleepOption.M15, 1_000))
        assertEquals("Hẹn giờ 15:00", MeloNowPlayingPolicy.sleepLabel(15 * 60_000L, false, 0))
        assertEquals("Hẹn giờ 0:01", MeloNowPlayingPolicy.sleepLabel(500, false, 0))
        assertEquals("Hẹn giờ", MeloNowPlayingPolicy.sleepLabel(100, false, 200))
        assertEquals("Hẹn giờ", MeloNowPlayingPolicy.sleepLabel(null, false, 0))
        assertEquals("Dừng hết bài", MeloNowPlayingPolicy.sleepLabel(null, true, 0))
    }

    @Test
    fun seekIsClamped() {
        assertEquals(0L, MeloNowPlayingPolicy.seekTarget(10_000, 200_000, MeloNowPlayingPolicy.SEEK_BACK_MS))
        assertEquals(200_000L, MeloNowPlayingPolicy.seekTarget(190_000, 200_000, MeloNowPlayingPolicy.SEEK_FORWARD_MS))
    }

    @Test
    fun transportButtonsGrowOnlyInTallWindows() {
        // Head unit windows are 642dp tall: 56dp buttons; a low sideways phone keeps the 48dp minimum.
        assertEquals(56, MeloNowPlayingPolicy.transportHeightDp(642))
        assertEquals(56, MeloNowPlayingPolicy.transportHeightDp(600))
        assertEquals(48, MeloNowPlayingPolicy.transportHeightDp(599))
        assertEquals(48, MeloNowPlayingPolicy.transportHeightDp(384))
    }

    @Test
    fun bitDepthIsProbedForLosslessFilesOnly() {
        assertTrue(MeloNowPlayingPolicy.shouldProbeBitDepth("Biển Gọi Tên Gió.flac"))
        assertTrue(MeloNowPlayingPolicy.shouldProbeBitDepth("track.WAV"))
        assertFalse(MeloNowPlayingPolicy.shouldProbeBitDepth("track.mp3"))
        assertFalse(MeloNowPlayingPolicy.shouldProbeBitDepth("track.m4a"))
        assertFalse(MeloNowPlayingPolicy.shouldProbeBitDepth("noextension"))
        assertFalse(MeloNowPlayingPolicy.shouldProbeBitDepth(null))
    }

    @Test
    fun seekStepsStayThirtyBackSixtyForward() {
        // The waveform scrubber is gone; these steps (Media3 seek through the host) are the only seek on the deck.
        assertEquals(-30_000L, MeloNowPlayingPolicy.SEEK_BACK_MS)
        assertEquals(60_000L, MeloNowPlayingPolicy.SEEK_FORWARD_MS)
        assertEquals(70_000L, MeloNowPlayingPolicy.seekTarget(10_000, 300_000, MeloNowPlayingPolicy.SEEK_FORWARD_MS))
        assertEquals(0L, MeloNowPlayingPolicy.seekTarget(20_000, 300_000, MeloNowPlayingPolicy.SEEK_BACK_MS))
    }

    @Test
    fun artTakesWhatTheOtherRowsLeave() {
        // Y700-like column: 800 wide, 750 high, 360 of text and buttons -> 390 cover
        assertEquals(390, MeloNowPlayingPolicy.artSide(800, 750, 360, 56))
        // Sony landscape: 330 wide, 380 high, 340 rest -> 40 would be too small -> minimum
        assertEquals(56, MeloNowPlayingPolicy.artSide(330, 380, 340, 56))
    }

    @Test
    fun playerGoesCompactOnlyWhenTheSmallestCoverDoesNotFit() {
        assertTrue(MeloNowPlayingPolicy.playerTight(380, 340, 56))   // 40 left < 56
        assertFalse(MeloNowPlayingPolicy.playerTight(400, 340, 56))  // exactly 60 left
        assertFalse(MeloNowPlayingPolicy.playerTight(750, 360, 56))
        assertFalse(MeloNowPlayingPolicy.playerTight(0, 360, 56))    // not laid out yet
    }

    @Test
    fun repeatChipLabelStaysShort() {
        assertEquals("↻ Lặp", MeloNowPlayingPolicy.repeatChipLabel("tắt"))
        assertEquals("↻ Lặp", MeloNowPlayingPolicy.repeatChipLabel("tất cả"))
        assertEquals("↻ Lặp 1", MeloNowPlayingPolicy.repeatChipLabel("một bài"))
    }

    @Test
    fun extrasStackOnlyWhenTheWidestLabelDoesNotFitAQuarter() {
        val widths = listOf(100, 110, 100, 140)
        // four equal cells of (available - 3 gaps) / 4 must hold the widest label (140)
        assertTrue(MeloNowPlayingPolicy.extrasStacked(400, widths, 8))   // cell 94
        assertTrue(MeloNowPlayingPolicy.extrasStacked(583, widths, 8))   // cell 139
        assertFalse(MeloNowPlayingPolicy.extrasStacked(584, widths, 8))  // cell 140 fits
        assertFalse(MeloNowPlayingPolicy.extrasStacked(800, widths, 8))
        assertFalse(MeloNowPlayingPolicy.extrasStacked(0, emptyList(), 8))
    }

    @Test
    fun artIsNeverWiderThanTheColumn() {
        assertEquals(300, MeloNowPlayingPolicy.artSide(300, 1200, 100, 56))
    }

    @Test
    fun artFallsBackToMinimumOnNonsense() {
        assertEquals(56, MeloNowPlayingPolicy.artSide(300, 0, 0, 56))
        assertEquals(56, MeloNowPlayingPolicy.artSide(300, -50, 20, 56))
        assertEquals(56, MeloNowPlayingPolicy.artSide(0, 800, 100, 56))
        // a negative rest is treated as 0
        assertEquals(300, MeloNowPlayingPolicy.artSide(300, 800, -40, 56))
        assertEquals(0, MeloNowPlayingPolicy.artSide(300, 10, 20, -5))
    }

    @Test
    fun qualityLabelFromRealFormat() {
        assertEquals("MP3 · 44.1 kHz · 320 kbps", MeloNowPlayingPolicy.qualityLabel("audio/mpeg", "x.mp3", 44_100, 320_000, null))
        // Decoded PCM falls back to the file's own format; unknown bitrate is omitted.
        assertEquals("FLAC · 24bit · 96 kHz · Hi-Res", MeloNowPlayingPolicy.qualityLabel("audio/raw", "a.flac", 96_000, -1, 24))
        assertEquals("FLAC · 44.1 kHz", MeloNowPlayingPolicy.qualityLabel("audio/flac", null, 44_100, -1, null))
        // Lossy files are never labelled Hi-Res, whatever the sample rate.
        assertTrue(MeloNowPlayingPolicy.qualityLabel("audio/mpeg", null, 96_000, 0, 24)?.contains("Hi-Res") == false)
        assertNull(MeloNowPlayingPolicy.qualityLabel(null, null, 0, 0, null))
    }

    @Test
    fun queueStatusPriority() {
        assertEquals(Status.PLAYING, MeloNowPlayingPolicy.queueStatus(true, true, null))
        assertEquals(Status.PAUSED, MeloNowPlayingPolicy.queueStatus(true, true, null, paused = true))
        assertEquals(Status.OPENING, MeloNowPlayingPolicy.queueStatus(true, false, 25, buffering = true))
        assertEquals(Status.PAUSED, MeloNowPlayingPolicy.queueStatus(true, false, 25, paused = true, buffering = true))
        assertEquals(Status.DOWNLOADING, MeloNowPlayingPolicy.queueStatus(false, false, 30))
        assertEquals(Status.DOWNLOADED, MeloNowPlayingPolicy.queueStatus(false, true, null))
        assertEquals(Status.TELEGRAM, MeloNowPlayingPolicy.queueStatus(false, false, null))
    }

    // ---- Source line under the title (E95 F08) ------------------------------------------------

    @Test
    fun telegramSongNamesTheChannelNeverAnArtist() {
        assertEquals("Kênh: Nhạc Lạnh - Chillout (EMI)", MeloNowPlayingPolicy.sourceLine("Nhạc Lạnh - Chillout (EMI)", local = false, sourceIsFolder = false))
        assertEquals("Kênh: LakHouse", MeloNowPlayingPolicy.sourceLine("  LakHouse ", local = false, sourceIsFolder = false))
        // The folder flag only matters for local songs; a channel is never labelled "Nghệ sĩ".
        assertEquals("Kênh: LakHouse", MeloNowPlayingPolicy.sourceLine("LakHouse", local = false, sourceIsFolder = true))
    }

    @Test
    fun localSongSaysArtistWhenItHasOneAndFolderOtherwise() {
        assertEquals("Nghệ sĩ: Sơn Tùng M-TP", MeloNowPlayingPolicy.sourceLine("Sơn Tùng M-TP", local = true, sourceIsFolder = false))
        assertEquals("Thư mục: Download", MeloNowPlayingPolicy.sourceLine("Download", local = true, sourceIsFolder = true))
    }

    @Test
    fun noNameMeansNoLine() {
        assertNull(MeloNowPlayingPolicy.sourceLine(null, local = false, sourceIsFolder = false))
        assertNull(MeloNowPlayingPolicy.sourceLine("   ", local = true, sourceIsFolder = false))
        assertNull(MeloNowPlayingPolicy.sourceLine("", local = true, sourceIsFolder = true))
    }

    @Test
    fun localNameEqualToTheFolderIsTheFolder() {
        // MeloLocalSourcePolicy.displayArtist falls back to the folder when there is no real artist.
        assertTrue(MeloNowPlayingPolicy.localSourceIsFolder("Vietnam", "Vietnam"))
        assertTrue(MeloNowPlayingPolicy.localSourceIsFolder(" vietnam ", "Vietnam"))
        assertFalse(MeloNowPlayingPolicy.localSourceIsFolder("Hà Anh Tuấn", "Vietnam"))
        assertFalse(MeloNowPlayingPolicy.localSourceIsFolder(null, "Vietnam"))
        assertFalse(MeloNowPlayingPolicy.localSourceIsFolder("Hà Anh Tuấn", null))
        assertFalse(MeloNowPlayingPolicy.localSourceIsFolder("", ""))
    }

    // ---- E98 teal theme labels --------------------------------------------------------------

    @Test
    fun themedShuffleLabelNamesTheState() {
        assertEquals("Đảo: Tắt", MeloNowPlayingPolicy.themedShuffleLabel(false))
        assertEquals("Đảo: Bật", MeloNowPlayingPolicy.themedShuffleLabel(true))
    }

    @Test
    fun themedRepeatLabelCoversEveryHostWord() {
        assertEquals("Không lặp", MeloNowPlayingPolicy.themedRepeatLabel("tắt"))
        assertEquals("Lặp tất cả", MeloNowPlayingPolicy.themedRepeatLabel("tất cả"))
        assertEquals("Lặp 1 bài", MeloNowPlayingPolicy.themedRepeatLabel("một bài"))
        // An unexpected word never shows as if repeat were on.
        assertEquals("Không lặp", MeloNowPlayingPolicy.themedRepeatLabel(""))
        assertEquals("Không lặp", MeloNowPlayingPolicy.themedRepeatLabel("lạ"))
    }

    @Test
    fun themedFavoriteAndQueueLabels() {
        assertEquals("Lưu bài", MeloNowPlayingPolicy.themedFavoriteLabel(false))
        assertEquals("Đã lưu", MeloNowPlayingPolicy.themedFavoriteLabel(true))
        assertEquals("Hàng đợi", MeloNowPlayingPolicy.THEMED_QUEUE_LABEL)
    }

    @Test
    fun qualityPillTextUsesBulletsAndSkipsBlanks() {
        assertEquals("MP3 • 44.1 kHz • 320 kbps", MeloNowPlayingPolicy.qualityPillText(listOf("MP3 · 44.1 kHz · 320 kbps")))
        assertEquals("MP3 • 320 kbps", MeloNowPlayingPolicy.qualityPillText(listOf("MP3", null, " ", "320 kbps")))
        assertEquals("", MeloNowPlayingPolicy.qualityPillText(listOf(null, "")))
        assertEquals("", MeloNowPlayingPolicy.qualityPillText(emptyList()))
    }

    @Test
    fun seekBarEnabledNeedsAKnownDuration() {
        assertFalse(MeloNowPlayingPolicy.seekBarEnabled(0L))
        assertFalse(MeloNowPlayingPolicy.seekBarEnabled(-1L))
        // C.TIME_UNSET
        assertFalse(MeloNowPlayingPolicy.seekBarEnabled(Long.MIN_VALUE + 1))
        assertTrue(MeloNowPlayingPolicy.seekBarEnabled(1L))
    }

    @Test
    fun seekBarProgressMapsPositionToThousandths() {
        val max = MeloNowPlayingPolicy.SEEK_BAR_MAX
        assertEquals(0, MeloNowPlayingPolicy.seekBarProgress(50_000, 0))
        assertEquals(0, MeloNowPlayingPolicy.seekBarProgress(50_000, Long.MIN_VALUE + 1))
        assertEquals(0, MeloNowPlayingPolicy.seekBarProgress(-5_000, 200_000))
        assertEquals(500, MeloNowPlayingPolicy.seekBarProgress(100_000, 200_000))
        assertEquals(max, MeloNowPlayingPolicy.seekBarProgress(250_000, 200_000))
        assertEquals(max, MeloNowPlayingPolicy.seekBarProgress(200_000, 200_000))
        // A 10 hour song times 1000 stays inside Long.
        val tenHours = 36_000_000L
        assertEquals(250, MeloNowPlayingPolicy.seekBarProgress(tenHours / 4, tenHours))
    }

    @Test
    fun seekBarTargetIsNullWithoutDurationAndClampsInput() {
        assertNull(MeloNowPlayingPolicy.seekBarTarget(500, 0))
        assertNull(MeloNowPlayingPolicy.seekBarTarget(500, -1))
        assertNull(MeloNowPlayingPolicy.seekBarTarget(500, Long.MIN_VALUE + 1))
        assertEquals(0L, MeloNowPlayingPolicy.seekBarTarget(0, 200_000))
        assertEquals(100_000L, MeloNowPlayingPolicy.seekBarTarget(500, 200_000))
        // Out of range progress is clamped, never a negative or past-the-end position.
        assertEquals(0L, MeloNowPlayingPolicy.seekBarTarget(-5, 200_000))
        assertEquals(199_000L, MeloNowPlayingPolicy.seekBarTarget(1200, 200_000))
    }

    @Test
    fun seekBarTargetStopsOneSecondBeforeTheEnd() {
        val max = MeloNowPlayingPolicy.SEEK_BAR_MAX
        assertEquals(199_000L, MeloNowPlayingPolicy.seekBarTarget(max, 200_000))
        // A track shorter than the guard can only seek to its start.
        assertEquals(0L, MeloNowPlayingPolicy.seekBarTarget(max, 600))
        assertEquals(0L, MeloNowPlayingPolicy.seekBarTarget(500, 600))
        assertEquals(0L, MeloNowPlayingPolicy.seekBarTarget(max, 1_000))
    }

    @Test
    fun seekBarRoundTripStaysWithinOneThousandth() {
        val duration = 240_000L
        val tolerance = duration / MeloNowPlayingPolicy.SEEK_BAR_MAX
        var pos = 0L
        while (pos <= duration - MeloNowPlayingPolicy.SEEK_BAR_END_GUARD_MS) {
            val back = MeloNowPlayingPolicy.seekBarTarget(MeloNowPlayingPolicy.seekBarProgress(pos, duration), duration)!!
            assertTrue("pos=$pos back=$back", kotlin.math.abs(back - pos) <= tolerance)
            pos += 7_919L
        }
    }
}
