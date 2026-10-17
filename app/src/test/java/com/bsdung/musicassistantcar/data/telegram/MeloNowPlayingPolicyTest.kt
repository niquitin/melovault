package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.DeckLayout
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.NowPlayingStyle
import com.bsdung.musicassistantcar.data.telegram.MeloNowPlayingPolicy.SleepOption
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        assertEquals("⏾ 15:00", MeloNowPlayingPolicy.sleepLabel(15 * 60_000L, false, 0))
        assertEquals("⏾ 0:01", MeloNowPlayingPolicy.sleepLabel(500, false, 0))
        assertEquals("⏾ Hẹn giờ", MeloNowPlayingPolicy.sleepLabel(100, false, 200))
        assertEquals("⏾ Hẹn giờ", MeloNowPlayingPolicy.sleepLabel(null, false, 0))
        assertEquals("⏾ Hết bài", MeloNowPlayingPolicy.sleepLabel(null, true, 0))
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
