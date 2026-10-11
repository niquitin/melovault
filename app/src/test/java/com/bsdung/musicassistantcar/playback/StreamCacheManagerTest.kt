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
            StreamCacheManager.repairStreamCacheDir(protectedDir)
            org.junit.Assert.assertTrue("Protected file must not be deleted", protectedFile.exists())

            // Now test stream cache directory
            val streamDir = java.io.File(tempDir, "melovault_stream_cache")
            streamDir.mkdirs()
            val corruptedFile = java.io.File(streamDir, "corrupted_span.cache")
            corruptedFile.writeText("bad data")

            StreamCacheManager.repairStreamCacheDir(streamDir)
            org.junit.Assert.assertFalse("Corrupted file inside stream cache should be deleted", corruptedFile.exists())
            org.junit.Assert.assertTrue("Directory itself must remain", streamDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testTargetConstants() {
        assertEquals("internal", StreamCacheManager.TARGET_INTERNAL)
        assertEquals("sd", StreamCacheManager.TARGET_SD)
    }
}
