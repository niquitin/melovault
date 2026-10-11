package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test

class StreamCachePolicyTest {
    @Test fun storageDefaults() {
        assertEquals(10, StreamCachePolicy.defaultCapGiB(64L * StreamCachePolicy.GIB))
        assertEquals(30, StreamCachePolicy.defaultCapGiB(256L * StreamCachePolicy.GIB))
    }
    @Test fun nextTwoTracks() {
        assertEquals(listOf(1, 2), StreamCachePolicy.nextIndices(0, 75))
        assertEquals(listOf(74), StreamCachePolicy.nextIndices(73, 75))
        assertTrue(StreamCachePolicy.nextIndices(74, 75).isEmpty())
    }
    @Test fun reservedFreeSpace() {
        val g = StreamCachePolicy.GIB
        assertEquals(30L * g, StreamCachePolicy.safeCapBytes(30 * g, 100 * g, 0, 256 * g))
        assertEquals(5L * g, StreamCachePolicy.safeCapBytes(30 * g, 25 * g, 0, 256 * g))
        assertEquals(0L, StreamCachePolicy.safeCapBytes(30 * g, 5 * g, 0, 256 * g))
    }

    @Test fun spaceChecksAndConstants() {
        val g = StreamCachePolicy.GIB
        assertTrue(StreamCachePolicy.hasSufficientSpace(50 * g, 256 * g))
        assertFalse(StreamCachePolicy.hasSufficientSpace(10 * g, 256 * g))
        assertEquals(256L * 1024 * 1024, StreamCachePolicy.MIN_SD_USABLE_BYTES)
        assertEquals(64L * 1024 * 1024, StreamCachePolicy.MIN_STREAM_CACHE_BYTES)
        assertEquals(listOf(5, 10, 20, 30, 50), StreamCachePolicy.allowedCapsGiB)
        assertEquals(3L * 1024 * 1024, StreamCachePolicy.PREFETCH_UNMETERED_BYTES)
        assertEquals(512L * 1024, StreamCachePolicy.PREFETCH_METERED_BYTES)
    }

    @Test fun testShanlingM3UltraMicroSdRecommends10GiBNot30GiB() {
        val g = StreamCachePolicy.GIB
        // Shanling M3 Ultra real metrics: 239 GiB total microSD, 37 GiB usable free space.
        // With 20 GiB minimum reserve, available budget above reserve is 17 GiB (< 30 GiB).
        // Must recommend 10 GB (DEFAULT_SMALL_GIB), never auto-granting 30 GB!
        val recommended = StreamCachePolicy.recommendedCapGiB(
            totalBytes = 239L * g,
            usableBytes = 37L * g,
            existingBytes = 0L,
        )
        assertEquals("With 37 GiB free and 20 GiB reserve, must recommend 10 GB, not 30 GB", 10, recommended)
        assertEquals(
            10,
            StreamCachePolicy.defaultCapGiB(totalBytes = 239L * g, usableBytes = 37L * g)
        )

        // When free space is 60 GiB (40 GiB above 20 GiB reserve >= 30 GiB), 30 GB is permitted
        assertEquals(
            30,
            StreamCachePolicy.defaultCapGiB(totalBytes = 239L * g, usableBytes = 60L * g)
        )
    }
}
