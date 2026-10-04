package com.bsdung.musicassistantcar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryTrackerTest {

    @Test
    fun rapidSkipUnder10SecDoesNotReport() {
        val reported = mutableListOf<String>()
        val tracker = PlayHistoryTracker { reported.add(it) }

        // Start track 1 at t=1000
        tracker.onMediaItemTransition("track:1", isPlaying = true, nowRealtimeMs = 1_000L)

        // After 2.5 seconds, user skips to track 2
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 3_500L)
        assertEquals(0, reported.size)

        tracker.onMediaItemTransition("track:2", isPlaying = true, nowRealtimeMs = 3_500L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 4_000L)

        assertEquals(0, reported.size)
        assertFalse(tracker.isReported())
    }

    @Test
    fun playingFor10SecReportsExactlyOnce() {
        val reported = mutableListOf<String>()
        val tracker = PlayHistoryTracker { reported.add(it) }

        tracker.onMediaItemTransition("drive-file:123", isPlaying = true, nowRealtimeMs = 10_000L)

        // Ticks before 10s
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 15_000L)
        assertEquals(0, reported.size)
        assertFalse(tracker.isReported())

        // Reaches 10s (nowRealtimeMs = 20_000L, exactly 10s of play)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 20_000L)
        assertEquals(1, reported.size)
        assertEquals("drive-file:123", reported[0])
        assertTrue(tracker.isReported())

        // Subsequent ticks do NOT duplicate report
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 25_000L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 30_000L)
        assertEquals(1, reported.size)
    }

    @Test
    fun pauseStopsAccumulation() {
        val reported = mutableListOf<String>()
        val tracker = PlayHistoryTracker { reported.add(it) }

        // Play for 6 seconds
        tracker.onMediaItemTransition("track:1", isPlaying = true, nowRealtimeMs = 1_000L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 7_000L)
        assertEquals(0, reported.size)

        // Pause at t=7000
        tracker.onIsPlayingChanged(isPlaying = false, nowRealtimeMs = 7_000L)
        assertEquals(6_000L, tracker.getAccumulatedMs(isPlaying = false, nowRealtimeMs = 7_000L))

        // Idle while paused for 30 seconds
        tracker.onPeriodicTick(isPlaying = false, nowRealtimeMs = 37_000L)
        assertEquals(0, reported.size)
        assertEquals(6_000L, tracker.getAccumulatedMs(isPlaying = false, nowRealtimeMs = 37_000L))

        // Resume at t=37000
        tracker.onIsPlayingChanged(isPlaying = true, nowRealtimeMs = 37_000L)

        // Play for 3 more seconds (total 9s) -> not yet
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 40_000L)
        assertEquals(0, reported.size)

        // Play for 1.5 more seconds (total 10.5s) -> report!
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 41_500L)
        assertEquals(1, reported.size)
        assertEquals("track:1", reported[0])
    }

    @Test
    fun trackTransitionResetsAccumulation() {
        val reported = mutableListOf<String>()
        val tracker = PlayHistoryTracker { reported.add(it) }

        // Play track 1 for 8s
        tracker.onMediaItemTransition("track:1", isPlaying = true, nowRealtimeMs = 1_000L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 9_000L)
        assertEquals(0, reported.size)

        // Switch to track 2
        tracker.onMediaItemTransition("track:2", isPlaying = true, nowRealtimeMs = 9_000L)
        assertEquals(0L, tracker.getAccumulatedMs(isPlaying = true, nowRealtimeMs = 9_000L))

        // Play track 2 for 10s
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 19_000L)
        assertEquals(1, reported.size)
        assertEquals("track:2", reported[0])
    }

    @Test
    fun sameTrackMetadataUpdateDoesNotReset() {
        val reported = mutableListOf<String>()
        val tracker = PlayHistoryTracker { reported.add(it) }

        tracker.onMediaItemTransition("drive-file:99", isPlaying = true, nowRealtimeMs = 5_000L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 10_000L) // 5s

        // Replaced metadata for the SAME track
        tracker.onMediaItemTransition("drive-file:99", isPlaying = true, nowRealtimeMs = 10_000L)

        // 5 more seconds (now 15_000L)
        tracker.onPeriodicTick(isPlaying = true, nowRealtimeMs = 15_000L)
        assertEquals(1, reported.size)
        assertEquals("drive-file:99", reported[0])
    }
}
