package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.DeckLayout
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.NowPlayingStyle
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.SleepOption
import org.junit.Assert.assertArrayEquals
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
    fun peaksAreNormalisedAndNeverInvented() {
        val peaks = MeloNowPlayingPolicy.bucketPeaks(floatArrayOf(0.1f, -0.5f, 0.2f, 0.25f), 2)
        assertArrayEquals(floatArrayOf(1f, 0.5f), peaks, 0.0001f)
        assertEquals(3, MeloNowPlayingPolicy.bucketPeaks(floatArrayOf(0.1f, 0.2f, 0.4f), 120).size)
        assertEquals(0, MeloNowPlayingPolicy.bucketPeaks(FloatArray(0), 120).size)
        assertArrayEquals(floatArrayOf(0f, 0f), MeloNowPlayingPolicy.bucketPeaks(floatArrayOf(0f, 0f), 2), 0f)
        val many = MeloNowPlayingPolicy.bucketPeaks(FloatArray(1000) { it / 1000f }, 120)
        assertEquals(120, many.size)
        assertEquals(1f, many.last(), 0.0001f)
        assertTrue(many.all { it in 0f..1f })
    }

    @Test
    fun seekFractionIsClamped() {
        assertEquals(0.5f, MeloNowPlayingPolicy.seekFraction(50f, 100f), 0.0001f)
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(-5f, 100f), 0.0001f)
        assertEquals(1f, MeloNowPlayingPolicy.seekFraction(150f, 100f), 0.0001f)
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(5f, 0f), 0.0001f)
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
    fun seekFractionWithInsetMapsTheBarNotTheView() {
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(0f, 100f, 10f), 0.0001f)
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(10f, 100f, 10f), 0.0001f)
        assertEquals(1f, MeloNowPlayingPolicy.seekFraction(90f, 100f, 10f), 0.0001f)
        assertEquals(1f, MeloNowPlayingPolicy.seekFraction(100f, 100f, 10f), 0.0001f)
        assertEquals(0.5f, MeloNowPlayingPolicy.seekFraction(50f, 100f, 10f), 0.0001f)
        assertEquals(0.25f, MeloNowPlayingPolicy.seekFraction(30f, 100f, 10f), 0.0001f)
    }

    @Test
    fun seekFractionNeverDividesByZero() {
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(5f, 20f, 10f), 0.0001f)
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(5f, 10f, 10f), 0.0001f)
        assertEquals(0f, MeloNowPlayingPolicy.seekFraction(5f, 0f, 3f), 0.0001f)
        assertEquals(0.5f, MeloNowPlayingPolicy.seekFraction(50f, 100f, -4f), 0.0001f)
    }

    @Test
    fun knobStaysInsideTheViewAtBothEnds() {
        assertEquals(10f, MeloNowPlayingPolicy.knobCenter(0f, 100f, 10f), 0.0001f)
        assertEquals(90f, MeloNowPlayingPolicy.knobCenter(1f, 100f, 10f), 0.0001f)
        assertEquals(50f, MeloNowPlayingPolicy.knobCenter(0.5f, 100f, 10f), 0.0001f)
        assertEquals(10f, MeloNowPlayingPolicy.knobCenter(-1f, 100f, 10f), 0.0001f)
        assertEquals(90f, MeloNowPlayingPolicy.knobCenter(2f, 100f, 10f), 0.0001f)
        // a view narrower than the knob keeps the knob at the inset instead of going negative
        assertEquals(10f, MeloNowPlayingPolicy.knobCenter(0.7f, 15f, 10f), 0.0001f)
    }

    @Test
    fun touchAndKnobAgree() {
        for (f in listOf(0f, 0.2f, 0.5f, 0.9f, 1f)) {
            val x = MeloNowPlayingPolicy.knobCenter(f, 240f, 12f)
            assertEquals(f, MeloNowPlayingPolicy.seekFraction(x, 240f, 12f), 0.0001f)
        }
    }

    @Test
    fun waveformCacheIsAccountScoped() {
        val a = MeloNowPlayingPolicy.waveformCacheName(1, 42, 9_000_000)
        val b = MeloNowPlayingPolicy.waveformCacheName(2, 42, 9_000_000)
        assertTrue(a != b)
        assertEquals("wf_1_42_9000000.bin", a)
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
        assertEquals(Status.DOWNLOADING, MeloNowPlayingPolicy.queueStatus(false, false, 30))
        assertEquals(Status.DOWNLOADED, MeloNowPlayingPolicy.queueStatus(false, true, null))
        assertEquals(Status.TELEGRAM, MeloNowPlayingPolicy.queueStatus(false, false, null))
    }
}
