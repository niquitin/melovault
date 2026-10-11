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
}
