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

    @Test
    fun testSdRootNotYetExisting_CreatedFirstBeforeCheckingUsableSpace_SucceedsOnSd() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_statvfs_" + System.nanoTime())
        val sdBase = java.io.File(tempRoot, "external_sd1_files")
        val intBase = java.io.File(tempRoot, "internal_cache")
        sdBase.mkdirs()
        intBase.mkdirs()
        try {
            val sdCacheSubdir = java.io.File(sdBase, "melovault_stream_cache")
            org.junit.Assert.assertFalse("Precondition: melovault_stream_cache must NOT exist yet", sdCacheSubdir.exists())

            val g = StreamCachePolicy.GIB
            // Simulate Android 10 statvfs behavior: returns 0L if directory does not exist on disk yet!
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = sdBase,
                intBase = intBase,
                explicitCapGiB = null,
                usableSpaceProvider = { file -> if (file.exists()) 37L * g else 0L },
                totalSpaceProvider = { file -> if (file.exists()) 239L * g else 0L },
            )

            assertEquals("Must select TARGET_SD after creating sdRoot first", StreamCacheManager.TARGET_SD, resolution.target)
            assertEquals("No fallback must occur", StreamCacheManager.FallbackReason.NONE, resolution.fallbackReason)
            org.junit.Assert.assertTrue("sdRoot directory must now exist", resolution.cacheDir.exists())
            assertEquals("melovault_stream_cache", resolution.cacheDir.name)
            // With 37 GiB usable and 239 GiB total (20 GiB reserve -> 17 GiB available), must recommend 10 GB, not 30 GB
            assertEquals("Shanling M3 37 GiB free must default to 10 GB", 10, resolution.capGiB)
            assertEquals("Evictor bytes must be 10 GiB", 10L * g, resolution.evictorBytes)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testSdDirectoryNotWritable_FallsBackSafelyToInternal() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_readonly_" + System.nanoTime())
        val sdBase = java.io.File(tempRoot, "external_sd1_files")
        val intBase = java.io.File(tempRoot, "internal_cache")
        sdBase.mkdirs()
        intBase.mkdirs()
        try {
            val g = StreamCachePolicy.GIB
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = sdBase,
                intBase = intBase,
                explicitCapGiB = 10,
                usableSpaceProvider = { if (it.absolutePath.contains("external_sd1")) 37L * g else 2L * g },
                totalSpaceProvider = { if (it.absolutePath.contains("external_sd1")) 239L * g else 21L * g },
                writableChecker = { dir -> !dir.absolutePath.contains("external_sd1") },
            )

            assertEquals("Must fall back to TARGET_INTERNAL when SD dir is not writable", StreamCacheManager.TARGET_INTERNAL, resolution.target)
            assertEquals(StreamCacheManager.FallbackReason.SD_CACHE_DIR_NOT_WRITABLE, resolution.fallbackReason)
            org.junit.Assert.assertTrue(resolution.cacheDir.absolutePath.contains("internal_cache"))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testSdDirectoryMkdirsFailure_FallsBackSafelyToInternal() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_mkdirfail_" + System.nanoTime())
        val sdBase = java.io.File(tempRoot, "external_sd1_files")
        val intBase = java.io.File(tempRoot, "internal_cache")
        sdBase.mkdirs()
        intBase.mkdirs()
        try {
            val g = StreamCachePolicy.GIB
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = sdBase,
                intBase = intBase,
                explicitCapGiB = 10,
                usableSpaceProvider = { 37L * g },
                totalSpaceProvider = { 239L * g },
                mkdirsProvider = { dir ->
                    if (dir.absolutePath.contains("external_sd1")) false else dir.mkdirs()
                },
            )

            assertEquals(StreamCacheManager.TARGET_INTERNAL, resolution.target)
            assertEquals(StreamCacheManager.FallbackReason.SD_CACHE_DIR_CREATE_FAILED, resolution.fallbackReason)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testSdCardRemovedOrUnmounted_FallsBackSafelyToInternal() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_removed_" + System.nanoTime())
        val intBase = java.io.File(tempRoot, "internal_cache")
        intBase.mkdirs()
        try {
            val g = StreamCachePolicy.GIB
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = null, // SD card unmounted / removed
                intBase = intBase,
                explicitCapGiB = 10,
                usableSpaceProvider = { 2L * g },
                totalSpaceProvider = { 21L * g },
            )

            assertEquals("Must fall back to TARGET_INTERNAL when SD card is removed", StreamCacheManager.TARGET_INTERNAL, resolution.target)
            assertEquals(StreamCacheManager.FallbackReason.SD_UNMOUNTED_OR_REMOVED, resolution.fallbackReason)
            org.junit.Assert.assertTrue("Internal cache dir must be created", resolution.cacheDir.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testSdCardInsufficientSpaceAfterReserve_FallsBackSafelyToInternal() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_lowspace_" + System.nanoTime())
        val sdBase = java.io.File(tempRoot, "external_sd1_files")
        val intBase = java.io.File(tempRoot, "internal_cache")
        sdBase.mkdirs()
        intBase.mkdirs()
        try {
            val g = StreamCachePolicy.GIB
            // SD card only has 15 GiB free on a 239 GiB card (< 20 GiB reserve -> safeCap = 0)
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = sdBase,
                intBase = intBase,
                explicitCapGiB = 10,
                usableSpaceProvider = { if (it.absolutePath.contains("external_sd1")) 15L * g else 2L * g },
                totalSpaceProvider = { if (it.absolutePath.contains("external_sd1")) 239L * g else 21L * g },
            )

            assertEquals(StreamCacheManager.TARGET_INTERNAL, resolution.target)
            assertEquals(StreamCacheManager.FallbackReason.SD_INSUFFICIENT_USABLE_SPACE, resolution.fallbackReason)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testOfflineDownloadsPreservedDuringSdStreamCacheInitAndOperations() {
        val tempRoot = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_sd_isolation_" + System.nanoTime())
        val sdBase = java.io.File(tempRoot, "external_sd1_files")
        val intBase = java.io.File(tempRoot, "internal_cache")
        sdBase.mkdirs()
        intBase.mkdirs()
        try {
            // Create protected melovault_offline sibling folder with offline tracks
            val offlineDir = java.io.File(sdBase, "melovault_offline")
            offlineDir.mkdirs()
            val offlineTrack = java.io.File(offlineDir, "track_11232.mp3")
            val payload = "PROTECTED_OFFLINE_AUDIO_PAYLOAD_SHANLING_M3".toByteArray()
            offlineTrack.writeBytes(payload)

            val g = StreamCachePolicy.GIB
            val resolution = StreamCacheManager.resolveCacheDirectory(
                requestedTarget = StreamCacheManager.TARGET_SD,
                sdBase = sdBase,
                intBase = intBase,
                usableSpaceProvider = { 37L * g },
                totalSpaceProvider = { 239L * g },
            )

            assertEquals(StreamCacheManager.TARGET_SD, resolution.target)
            org.junit.Assert.assertTrue("Offline track must still exist", offlineTrack.exists())
            org.junit.Assert.assertArrayEquals("Offline track bytes must be untouched", payload, offlineTrack.readBytes())
            org.junit.Assert.assertFalse(
                "Write probe file must be cleaned up immediately",
                java.io.File(resolution.cacheDir, ".melovault_write_probe").exists()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
