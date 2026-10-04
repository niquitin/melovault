package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object StreamCacheManager {
    private const val TAG = "StreamCacheManager"
    const val MAX_CACHE_SIZE_BYTES = 10L * 1024 * 1024 * 1024 // 10 GiB LRU
    private const val CACHE_DIR_NAME = "melovault_stream_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val appCtx = context.applicationContext
            val cacheDir = File(appCtx.externalCacheDir ?: appCtx.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val dbProvider = StandaloneDatabaseProvider(appCtx)
            databaseProvider = dbProvider
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
            val cache = SimpleCache(cacheDir, evictor, dbProvider)
            simpleCache = cache
            Log.i(TAG, "Initialized SimpleCache at ${cacheDir.absolutePath} with max 10 GiB")
            cache
        }
    }

    fun buildCacheKey(uri: android.net.Uri): String {
        // Strip query parameters so cache keys are uniform and contain no transient parameters or tokens
        return uri.buildUpon().clearQuery().build().toString()
    }

    fun createDataSourceFactory(
        context: Context,
        upstreamHttpFactory: DataSource.Factory,
    ): DataSource.Factory {
        val cache = getCache(context)
        val cacheKeyFactory = CacheKeyFactory { dataSpec ->
            buildCacheKey(dataSpec.uri)
        }
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamHttpFactory)
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // DefaultDataSource routes file:// to FileDataSource, content:// to ContentDataSource,
        // and http(s):// to cacheDataSourceFactory
        return DefaultDataSource.Factory(context, cacheDataSourceFactory)
    }

    @Synchronized
    fun clearCache(context: Context) {
        val cache = simpleCache ?: return
        try {
            val keys = cache.keys.toList()
            for (key in keys) {
                cache.removeResource(key)
            }
            Log.i(TAG, "Cleared ${keys.size} resources from StreamCache")
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing cache: ${e.message}", e)
        }
    }

    fun getCurrentCacheSizeBytes(context: Context): Long {
        return runCatching { getCache(context).cacheSpace }.getOrDefault(0L)
    }

    @Synchronized
    fun releaseForTesting() {
        try {
            simpleCache?.release()
        } catch (_: Exception) {}
        simpleCache = null
        databaseProvider = null
    }
}
