package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * E6: audio streaming LRU cache, isolated from protected melovault_offline downloads.
 * Directory changes take effect on a fresh service start, not during active playback.
 */
object StreamCacheManager {
    private const val TAG = "StreamCacheManager"
    const val MAX_CACHE_SIZE_BYTES = 30L * 1024 * 1024 * 1024
    private const val CACHE_DIR_NAME = "melovault_stream_cache"
    private const val PREFS_NAME = "music_assistant_car"
    private const val PREF_TARGET = "stream_cache_target_e6"
    private const val PREF_CAP = "stream_cache_cap_gib_e6"
    private const val PREF_PRELOAD = "stream_preload_e6"
    const val TARGET_INTERNAL = "internal"
    const val TARGET_SD = "sd"

    @Volatile private var simpleCache: SimpleCache? = null
    @Volatile private var databaseProvider: StandaloneDatabaseProvider? = null
    @Volatile private var activeBase: File? = null
    @Volatile private var activeTarget: String? = null
    @Volatile private var activeCap: Int = -1

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun preferredTarget(ctx: Context): String {
        val saved = prefs(ctx).getString(PREF_TARGET, null)
        if (saved == TARGET_SD || saved == TARGET_INTERNAL) return saved
        // Shanling M3 Ultra with a mounted writable microSD defaults to card cache;
        // other devices, including FYT, retain the original internal-cache default.
        val shanling = Build.MANUFACTURER.contains("shanling", ignoreCase = true) ||
            Build.MODEL.contains("M3 Ultra", ignoreCase = true)
        return if (shanling && sdAvailable(ctx)) TARGET_SD else TARGET_INTERNAL
    }

    fun removableBase(ctx: Context): File? =
        ctx.applicationContext.getExternalFilesDirs(null).filterNotNull().firstOrNull { f ->
            runCatching {
                Environment.getExternalStorageState(f) == Environment.MEDIA_MOUNTED &&
                    Environment.isExternalStorageRemovable(f) && f.isDirectory && f.canWrite()
            }.getOrDefault(false)
        }

    fun sdAvailable(ctx: Context): Boolean = removableBase(ctx) != null

    fun effectiveTarget(ctx: Context): String =
        if (preferredTarget(ctx) == TARGET_SD && sdAvailable(ctx)) TARGET_SD else TARGET_INTERNAL

    fun setPreferredTarget(ctx: Context, target: String): Boolean {
        if (target !in setOf(TARGET_INTERNAL, TARGET_SD)) return false
        if (target == TARGET_SD && !sdAvailable(ctx)) return false
        prefs(ctx).edit().putString(PREF_TARGET, target).apply()
        return true
    }

    fun configuredCapGiB(ctx: Context): Int {
        val selectedVolume = if (effectiveTarget(ctx) == TARGET_SD)
            removableBase(ctx) ?: internalBase(ctx) else internalBase(ctx)
        val base = StreamCachePolicy.defaultCapGiB(selectedVolume.totalSpace)
        return prefs(ctx).getInt(PREF_CAP, base).takeIf { it in StreamCachePolicy.allowedCapsGiB } ?: base
    }

    fun setCapGiB(ctx: Context, value: Int) {
        require(value in StreamCachePolicy.allowedCapsGiB)
        prefs(ctx).edit().putInt(PREF_CAP, value).apply()
    }

    fun isPreloadEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(PREF_PRELOAD, true)

    fun setPreloadEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(PREF_PRELOAD, enabled).apply()
    }

    fun isRestartRequired(ctx: Context): Boolean =
        simpleCache != null && (activeTarget != effectiveTarget(ctx) || activeCap != configuredCapGiB(ctx))

    fun status(ctx: Context): String {
        val where = if (preferredTarget(ctx) == TARGET_SD) "microSD" else "Bộ nhớ trong"
        val sdWarning = if (preferredTarget(ctx) == TARGET_SD && !sdAvailable(ctx))
            " (thẻ không khả dụng: fallback bộ nhớ trong)" else ""
        val restart = if (isRestartRequired(ctx)) " • Cần khởi động lại dịch vụ phát nhạc" else ""
        return where + sdWarning + " • " + configuredCapGiB(ctx) + " GB • Đã lưu " +
            (getCurrentCacheSizeBytes(ctx) / 1048576L) + " MB" + restart
    }

    private fun internalBase(ctx: Context): File = ctx.externalCacheDir ?: ctx.cacheDir

    @Synchronized
    fun getCache(ctx: Context): SimpleCache {
        simpleCache?.let { return it }
        val app = ctx.applicationContext
        val requestedTarget = effectiveTarget(app)
        val selectedBase = if (requestedTarget == TARGET_SD) removableBase(app) else internalBase(app)
        var root = File(selectedBase ?: internalBase(app), CACHE_DIR_NAME)
        var actualTarget = requestedTarget
        if (!root.exists() && !root.mkdirs()) {
            if (actualTarget != TARGET_SD) throw IllegalStateException("Internal cache unavailable")
            root = File(internalBase(app), CACHE_DIR_NAME)
            actualTarget = TARGET_INTERNAL
            if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Cache unavailable")
        }
        // Reuse existing internal cache path from Stable v0.6.39, avoiding needless redownload.
        // Include nested Media3 cache-span folders when honoring the free-space reserve.
        val existingBytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val capGiB = configuredCapGiB(app)
        val safeCap = StreamCachePolicy.safeCapBytes(capGiB * StreamCachePolicy.GIB,
            root.usableSpace, existingBytes, root.totalSpace)
        val db = StandaloneDatabaseProvider(app)
        val result = SimpleCache(root, LeastRecentlyUsedCacheEvictor(safeCap), db)
        simpleCache = result
        databaseProvider = db
        activeBase = root
        activeTarget = actualTarget
        activeCap = capGiB
        Log.i(TAG, "E6 init target=" + actualTarget + " capBytes=" + safeCap)
        return result
    }

    /** Quality contributes to cache identity; tokens and transient queries never do. */
    fun buildCacheKey(uri: Uri): String {
        // The JVM Android URI mock lacks getQueryParameter(); use the canonical
        // URL text to keep this security-critical policy testable without Android.
        val raw = uri.toString()
        val base = raw.substringBefore('?').substringBefore('#')
        val query = raw.substringAfter('?', "").substringBefore('#')
        val quality = query.split('&')
            .mapNotNull { field ->
                val parts = field.split('=', limit = 2)
                if (parts.firstOrNull() == "quality") parts.getOrNull(1)?.lowercase()
                else null
            }
            .firstOrNull { it == "original" || it == "mobile" }
        return if (quality == null) base else base + "?quality=" + quality
    }

    private fun cachedFactory(ctx: Context, upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(getCache(ctx))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(CacheKeyFactory { buildCacheKey(it.uri) })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun createDataSourceFactory(ctx: Context, http: DataSource.Factory): DataSource.Factory =
        DefaultDataSource.Factory(ctx, cachedFactory(ctx, http))

    /** Prefetches only a bounded prefix; never a full album, nor any pinned download. */
    fun prefetchIntro(ctx: Context, uri: Uri, http: DataSource.Factory, maxBytes: Long): Long {
        if (uri.scheme !in listOf("https", "http") || maxBytes <= 0 || !isPreloadEnabled(ctx)) return 0
        val cache = getCache(ctx)
        val base = activeBase ?: return 0
        val reserve = minOf(StreamCachePolicy.RESERVE_GIB * StreamCachePolicy.GIB, base.totalSpace / 4)
        if (base.usableSpace <= reserve) return 0
        if (cache.getCachedBytes(buildCacheKey(uri), 0, maxBytes) >= maxBytes) return 0
        val source = cachedFactory(ctx, http).createDataSource()
        var bytes = 0L
        try {
            source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(maxBytes).build())
            val buf = ByteArray(65536)
            while (bytes < maxBytes && !Thread.currentThread().isInterrupted) {
                val n = source.read(buf, 0, minOf(65536L, maxBytes - bytes).toInt())
                if (n <= 0) break
                bytes += n
            }
        } catch (e: Exception) {
            Log.d(TAG, "Prefetch unavailable " + e.javaClass.simpleName)
        } finally {
            runCatching { source.close() }
        }
        return bytes
    }

    @Synchronized fun clearCache(ctx: Context) {
        val c = simpleCache ?: return
        try {
            c.keys.toList().forEach { c.removeResource(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Streaming cache clear failed", e)
        }
    }

    fun getCurrentCacheSizeBytes(ctx: Context): Long =
        runCatching { getCache(ctx).cacheSpace }.getOrDefault(0L)

    @Synchronized fun releaseForTesting() {
        runCatching { simpleCache?.release() }
        simpleCache = null
        databaseProvider = null
        activeBase = null
        activeTarget = null
        activeCap = -1
    }
}
