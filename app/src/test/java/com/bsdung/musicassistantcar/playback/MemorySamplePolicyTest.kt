package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class MemorySamplePolicyTest {
    @Test fun samplesOnFirstPlayingTickAndOncePerFiveMinutes() {
        val p = MemorySamplePolicy()
        assertTrue(p.shouldSample(10_000, true))
        assertFalse(p.shouldSample(19_000, true))
        assertFalse(p.shouldSample(309_999, true))
        assertTrue(p.shouldSample(310_000, true))
        assertFalse(p.shouldSample(310_001, true))
        assertTrue(p.shouldSample(610_000, true))
    }

    @Test fun neverSamplesPausedBufferingOrNegativeTimes() {
        val p = MemorySamplePolicy()
        assertFalse(p.shouldSample(0, false))
        assertFalse(p.shouldSample(-1, true))
        assertTrue(p.shouldSample(0, true))
        assertFalse(p.shouldSample(200_000, false))
        assertFalse(p.shouldSample(200_000, true))
        assertTrue(p.shouldSample(300_000, true))
    }

    @Test fun monotonicClockResetProducesOneNewBaseline() {
        val p = MemorySamplePolicy()
        assertTrue(p.shouldSample(700_000, true))
        assertTrue(p.shouldSample(5_000, true))
        assertFalse(p.shouldSample(5_001, true))
        assertTrue(p.shouldSample(305_000, true))
    }

    @Test fun resetAllowsNewServiceSessionSample() {
        val p = MemorySamplePolicy()
        assertTrue(p.shouldSample(1_000, true))
        p.reset()
        assertTrue(p.shouldSample(1_001, true))
    }

    @Test fun customIntervalIsHonored() {
        val p = MemorySamplePolicy(intervalMs = 60_000)
        assertTrue(p.shouldSample(1, true))
        assertFalse(p.shouldSample(60_000, true))
        assertTrue(p.shouldSample(60_001, true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroInterval() {
        MemorySamplePolicy(0L)
    }
}
