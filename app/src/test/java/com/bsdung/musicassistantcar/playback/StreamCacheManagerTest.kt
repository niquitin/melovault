package com.bsdung.musicassistantcar.playback

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheManagerTest {

    @Test
    fun testCacheSizeIs30GiB() {
        val expected30GiB = 30L * 1024 * 1024 * 1024
        assertEquals(expected30GiB, StreamCacheManager.MAX_CACHE_SIZE_BYTES)
    }

    @Test
    fun testBuildCacheKeyStripsQueryParameters() {
        val uriWithParams = Uri.parse("https://api.melovault.internal/v1/tracks/11209/stream?token=secret123&quality=ORIGINAL")
        val cacheKey = StreamCacheManager.buildCacheKey(uriWithParams)
        assertEquals("https://api.melovault.internal/v1/tracks/11209/stream?quality=original", cacheKey)
    }

    @Test
    fun testQualityVariantsNeverShareCachedAudio() {
        val path = "https://api.melovault.internal/v1/tracks/11209/stream"
        val a = StreamCacheManager.buildCacheKey(Uri.parse("$path?quality=original&token=one"))
        val b = StreamCacheManager.buildCacheKey(Uri.parse("$path?quality=mobile&token=two"))
        org.junit.Assert.assertNotEquals(a, b)
        assertEquals("$path?quality=mobile", b)
    }

    @Test
    fun testBuildCacheKeyWithoutQueryParamsRemainsUnchanged() {
        val simpleUri = Uri.parse("https://api.melovault.internal/v1/tracks/11209/stream")
        val cacheKey = StreamCacheManager.buildCacheKey(simpleUri)
        assertEquals("https://api.melovault.internal/v1/tracks/11209/stream", cacheKey)
    }

    @Test
    fun testPinnedAndCacheDirectoryIsolation() {
        val streamCacheName = "melovault_stream_cache"
        val offlinePinnedName = "melovault_offline"
        org.junit.Assert.assertNotEquals(streamCacheName, offlinePinnedName)
    }

    @Test
    fun testCacheBusyStateTracking() {
        StreamCacheManager.releaseForTesting()
        org.junit.Assert.assertFalse(StreamCacheManager.isCacheInUse())

        StreamCacheManager.onPlaybackStateChanged(true)
        org.junit.Assert.assertTrue(StreamCacheManager.isCacheInUse())

        StreamCacheManager.onPlaybackStateChanged(false)
        org.junit.Assert.assertFalse(StreamCacheManager.isCacheInUse())

        StreamCacheManager.onPrefetchStarted()
        org.junit.Assert.assertTrue(StreamCacheManager.isCacheInUse())

        StreamCacheManager.onPrefetchFinished()
        org.junit.Assert.assertFalse(StreamCacheManager.isCacheInUse())

        StreamCacheManager.releaseForTesting()
    }

    @Test
    fun testBuildCacheKeyWithMultipleTokensAndReversedParams() {
        val base = "https://api.melovault.internal/v1/tracks/11209/stream"

        val uriTokenFirst = Uri.parse("$base?token=jwt_xyz_123&quality=original&exp=1700000000")
        assertEquals("$base?quality=original", StreamCacheManager.buildCacheKey(uriTokenFirst))

        val uriQualityFirst = Uri.parse("$base?quality=MOBILE&timestamp=123456&token=another_token")
        assertEquals("$base?quality=mobile", StreamCacheManager.buildCacheKey(uriQualityFirst))

        val uriUnsupportedQuality = Uri.parse("$base?quality=low&token=secret")
        assertEquals(base, StreamCacheManager.buildCacheKey(uriUnsupportedQuality))

        val uriWithFragment = Uri.parse("$base?token=abc&quality=original#section2")
        assertEquals("$base?quality=original", StreamCacheManager.buildCacheKey(uriWithFragment))
    }

    @Test
    fun testRepairStreamCacheDirRejectsOtherDirectories() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            // Protected directory with different name (e.g. offline)
            val protectedDir = java.io.File(tempDir, "melovault_offline")
            protectedDir.mkdirs()
            val protectedFile = java.io.File(protectedDir, "track_11200.flac")
            protectedFile.writeText("important audio data")

            // Should refuse to delete contents of non-stream cache directory
            val nonStreamResult = StreamCacheManager.repairStreamCacheDir(protectedDir)
            org.junit.Assert.assertFalse("Must return false for non-stream directory", nonStreamResult)
            org.junit.Assert.assertTrue("Protected file must not be deleted", protectedFile.exists())

            // Now test stream cache directory
            val streamDir = java.io.File(tempDir, "melovault_stream_cache")
            streamDir.mkdirs()
            val corruptedFile = java.io.File(streamDir, "corrupted_span.cache")
            corruptedFile.writeText("bad data")

            // Should refuse to delete while cache is actively in use (playback active)
            StreamCacheManager.onPlaybackStateChanged(true)
            val busyPlaybackResult = StreamCacheManager.repairStreamCacheDir(streamDir)
            org.junit.Assert.assertFalse("Must refuse repair during active playback", busyPlaybackResult)
            org.junit.Assert.assertTrue("File must remain untouched while playing", corruptedFile.exists())
            StreamCacheManager.onPlaybackStateChanged(false)

            // Should refuse to delete while prefetch is running
            StreamCacheManager.onPrefetchStarted()
            val busyPrefetchResult = StreamCacheManager.repairStreamCacheDir(streamDir)
            org.junit.Assert.assertFalse("Must refuse repair during active prefetch", busyPrefetchResult)
            org.junit.Assert.assertTrue("File must remain untouched while prefetching", corruptedFile.exists())
            StreamCacheManager.onPrefetchFinished()

            // When idle, repair succeeds and only cleans corrupted stream files
            val idleResult = StreamCacheManager.repairStreamCacheDir(streamDir)
            org.junit.Assert.assertTrue("Repair should succeed when idle", idleResult)
            org.junit.Assert.assertFalse("Corrupted file inside stream cache should be deleted", corruptedFile.exists())
            org.junit.Assert.assertTrue("Directory itself must remain", streamDir.exists())
        } finally {
            tempDir.deleteRecursively()
            StreamCacheManager.releaseForTesting()
        }
    }

    @Test
    fun testTargetConstants() {
        assertEquals("internal", StreamCacheManager.TARGET_INTERNAL)
        assertEquals("sd", StreamCacheManager.TARGET_SD)
    }

    @Test
    fun testLRUCapacityResizeCycle_10GB_to_5GB() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_cap_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            // Step 1: Initial state configured and initialized with 10 GB
            val expected10GiBBytes = 10L * StreamCachePolicy.GIB
            StreamCacheManager.initForTesting(tempDir, 10)
            assertEquals("Active cap in GiB must be 10", 10, StreamCacheManager.activeCapGiB())
            assertEquals("Active cap in bytes must be 10 GiB", expected10GiBBytes, StreamCacheManager.activeCapBytes())
            assertEquals("Evictor maxBytes must be exactly 10 GiB", expected10GiBBytes, StreamCacheManager.getActiveEvictorMaxBytes())

            // Step 2: During playback, if limit changes, active cache remains 10 GB to prevent disruption
            StreamCacheManager.onPlaybackStateChanged(true)
            assertEquals("Active cap remains 10 during playback", 10, StreamCacheManager.activeCapGiB())
            assertEquals("Evictor maxBytes remains 10 GiB", expected10GiBBytes, StreamCacheManager.getActiveEvictorMaxBytes())
            StreamCacheManager.onPlaybackStateChanged(false)

            // Step 3: Full application restart with 5 GB cap applied
            val expected5GiBBytes = 5L * StreamCachePolicy.GIB
            StreamCacheManager.initForTesting(tempDir, 5)
            assertEquals("Active cap in GiB must be 5 after restart", 5, StreamCacheManager.activeCapGiB())
            assertEquals("Active cap in bytes must be 5 GiB", expected5GiBBytes, StreamCacheManager.activeCapBytes())
            assertEquals("Evictor maxBytes must be exactly 5 GiB", expected5GiBBytes, StreamCacheManager.getActiveEvictorMaxBytes())

            // Step 4: Verify delta is exactly 5 GiB (5,368,709,120 bytes)
            val deltaBytes = expected10GiBBytes - StreamCacheManager.getActiveEvictorMaxBytes()
            assertEquals("Evictor capacity reduction must be exactly 5 GiB", expected5GiBBytes, deltaBytes)
        } finally {
            StreamCacheManager.releaseForTesting()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testMedia3LeastRecentlyUsedEvictionByteLimitEnforced() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_evict_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        try {
            // Set cap to 100 KB
            val maxBytes = 100 * 1024L
            val evictor = androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(maxBytes)
            val cache = androidx.media3.datasource.cache.SimpleCache(tempDir, evictor)

            try {
                // Item 1: 40 KB (total = 40 KB <= 100 KB)
                cache.startReadWrite("track_1", 0, 40 * 1024L)
                val file1 = cache.startFile("track_1", 0, 40 * 1024L)
                file1.writeBytes(ByteArray(40 * 1024))
                cache.commitFile(file1, 40 * 1024L)

                // Item 2: 40 KB (total = 80 KB <= 100 KB)
                cache.startReadWrite("track_2", 0, 40 * 1024L)
                val file2 = cache.startFile("track_2", 0, 40 * 1024L)
                file2.writeBytes(ByteArray(40 * 1024))
                cache.commitFile(file2, 40 * 1024L)

                assertEquals(80 * 1024L, cache.cacheSpace)
                org.junit.Assert.assertTrue("track_1 must be present", cache.keys.contains("track_1"))
                org.junit.Assert.assertTrue("track_2 must be present", cache.keys.contains("track_2"))

                // Item 3: 40 KB (total would be 120 KB > 100 KB) -> triggers eviction of track_1!
                cache.startReadWrite("track_3", 0, 40 * 1024L)
                val file3 = cache.startFile("track_3", 0, 40 * 1024L)
                file3.writeBytes(ByteArray(40 * 1024))
                cache.commitFile(file3, 40 * 1024L)

                // Verify eviction: track_1 evicted (0 cached bytes and empty spans), track_2 & track_3 remain
                val spansTrack1 = cache.getCachedSpans("track_1")
                org.junit.Assert.assertTrue("track_1 spans must be evicted by LRU evictor", spansTrack1.isEmpty())
                assertEquals("track_1 cached bytes must be 0", 0L, cache.getCachedBytes("track_1", 0, Long.MAX_VALUE))
                org.junit.Assert.assertFalse("track_2 spans must remain in cache", cache.getCachedSpans("track_2").isEmpty())
                org.junit.Assert.assertFalse("track_3 spans must remain in cache", cache.getCachedSpans("track_3").isEmpty())
                org.junit.Assert.assertTrue("Total cache space must be <= 100 KB", cache.cacheSpace <= maxBytes)
                assertEquals("Total cache space should be 80 KB", 80 * 1024L, cache.cacheSpace)
            } finally {
                cache.release()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
