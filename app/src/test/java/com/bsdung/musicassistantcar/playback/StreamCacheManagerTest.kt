package com.bsdung.musicassistantcar.playback

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCacheManagerTest {

    @Test
    fun testCacheSizeIs10GiB() {
        val expected10GiB = 10L * 1024 * 1024 * 1024
        assertEquals(expected10GiB, StreamCacheManager.MAX_CACHE_SIZE_BYTES)
    }

    @Test
    fun testBuildCacheKeyStripsQueryParameters() {
        val uriWithParams = Uri.parse("https://api.melovault.internal/v1/tracks/11209/stream?token=secret123&quality=ORIGINAL")
        val cacheKey = StreamCacheManager.buildCacheKey(uriWithParams)
        assertEquals("https://api.melovault.internal/v1/tracks/11209/stream", cacheKey)
    }

    @Test
    fun testBuildCacheKeyWithoutQueryParamsRemainsUnchanged() {
        val simpleUri = Uri.parse("https://api.melovault.internal/v1/tracks/11209/stream")
        val cacheKey = StreamCacheManager.buildCacheKey(simpleUri)
        assertEquals("https://api.melovault.internal/v1/tracks/11209/stream", cacheKey)
    }
}
