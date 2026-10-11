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
 * Directory changes take effect on a fresh application restart, not during active playback.
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
    @Volatile private var activeCapBytes: Long = -1L
    @Volatile private var isPlaybackActive: Boolean = false
    private val activePrefetchCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun activeCapGiB(): Int = activeCap
    fun activeCapBytes(): Long = activeCapBytes

    fun getActiveEvictorMaxBytes(): Long {
        val cache = simpleCache ?: return -1L
        return runCatching {
            val evictorField = SimpleCache::class.java.getDeclaredField("evictor").apply { isAccessible = true }
            val evictor = evictorField.get(cache)
            val maxBytesField = evictor.javaClass.getDeclaredField("maxBytes").apply { isAccessible = true }
            maxBytesField.getLong(evictor)
        }.getOrDefault(activeCapBytes)
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        isPlaybackActive = isPlaying
    }

    fun onPrefetchStarted() {
        activePrefetchCount.incrementAndGet()
    }

    fun onPrefetchFinished() {
        activePrefetchCount.decrementAndGet()
    }

    fun isCacheInUse(): Boolean = isPlaybackActive || activePrefetchCount.get() > 0

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
        val restart = if (isRestartRequired(ctx)) " • Cần khởi động lại ứng dụng" else ""
        return where + sdWarning + " • " + configuredCapGiB(ctx) + " GB • Đã lưu " +
            (getCurrentCacheSizeBytes(ctx) / 1048576L) + " MB" + restart
    }

    private fun internalBase(ctx: Context): File = ctx.externalCacheDir ?: ctx.cacheDir

    fun repairStreamCacheDir(dir: File): Boolean {
        if (dir.name != CACHE_DIR_NAME) {
            Log.e(TAG, "Refusing to clean non-stream cache directory: ${dir.name}")
            return false
        }
        if (isCacheInUse()) {
            Log.w(TAG, "Refusing to repair stream cache directory while cache is actively in use")
            return false
        }
        return runCatching {
            dir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
            true
        }.getOrDefault(false)
    }


    @Synchronized
    fun getCache(ctx: Context): SimpleCache {
        simpleCache?.let { return it }
        val app = ctx.applicationContext
        val requestedTarget = effectiveTarget(app)
        val capGiB = configuredCapGiB(app)
        val desiredBytes = capGiB * StreamCachePolicy.GIB

        // Attempt 1: SD card target (when requested and mounted)
        if (requestedTarget == TARGET_SD) {
            val sdBase = removableBase(app)
            if (sdBase != null) {
                val sdRoot = File(sdBase, CACHE_DIR_NAME)
                val existingBytes = runCatching {
                    sdRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }.getOrDefault(0L)
                val safeCap = StreamCachePolicy.safeCapBytes(
                    desiredBytes,
                    sdRoot.usableSpace,
                    existingBytes,
                    sdRoot.totalSpace
                )
                if (sdRoot.usableSpace >= StreamCachePolicy.MIN_SD_USABLE_BYTES && safeCap > 0L) {
                    if (sdRoot.exists() || sdRoot.mkdirs()) {
                        val evictorBytes = safeCap.coerceAtLeast(1024L * 1024L)
                        try {
                            val db = runCatching { StandaloneDatabaseProvider(app) }.getOrNull()
                            val result = if (db != null) {
                                SimpleCache(sdRoot, LeastRecentlyUsedCacheEvictor(evictorBytes), db)
                            } else {
                                SimpleCache(sdRoot, LeastRecentlyUsedCacheEvictor(evictorBytes))
                            }
                            simpleCache = result
                            databaseProvider = db
                            activeBase = sdRoot
                            activeTarget = TARGET_SD
                            activeCap = capGiB
                            activeCapBytes = evictorBytes
                            Log.i(TAG, "E6 init target=sd capBytes=$evictorBytes")
                            return result
                        } catch (e: Exception) {
                            Log.w(TAG, "SimpleCache init on SD failed: ${e.message}. Falling back to internal storage.", e)
                        }
                    } else {
                        Log.w(TAG, "Cannot create SD cache directory. Falling back to internal storage.")
                    }
                } else {
                    Log.w(TAG, "SD card has insufficient capacity (usable=${sdRoot.usableSpace}, safeCap=$safeCap). Falling back to internal storage.")
                }
            } else {
                Log.w(TAG, "SD card not mounted or removable. Falling back to internal storage.")
            }
        }

        // Attempt 2: Internal storage (default or fallback)
        val intBase = internalBase(app)
        var intRoot = File(intBase, CACHE_DIR_NAME)
        if (!intRoot.exists() && !intRoot.mkdirs()) {
            intRoot = File(app.cacheDir, CACHE_DIR_NAME)
            if (!intRoot.exists() && !intRoot.mkdirs()) {
                throw IllegalStateException("Internal cache storage unavailable")
            }
        }

        val existingBytes = runCatching {
            intRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
        var safeCap = StreamCachePolicy.safeCapBytes(
            desiredBytes,
            intRoot.usableSpace,
            existingBytes,
            intRoot.totalSpace
        )
        if (safeCap <= 0L) {
            safeCap = minOf(StreamCachePolicy.MIN_STREAM_CACHE_BYTES, intRoot.usableSpace.coerceAtLeast(1024L * 1024L))
        }

        val evictorBytes = safeCap.coerceAtLeast(1024L * 1024L)
        try {
            val db = runCatching { StandaloneDatabaseProvider(app) }.getOrNull()
            val result = if (db != null) {
                SimpleCache(intRoot, LeastRecentlyUsedCacheEvictor(evictorBytes), db)
            } else {
                SimpleCache(intRoot, LeastRecentlyUsedCacheEvictor(evictorBytes))
            }
            simpleCache = result
            databaseProvider = db
            activeBase = intRoot
            activeTarget = TARGET_INTERNAL
            activeCap = capGiB
            activeCapBytes = evictorBytes
            Log.i(TAG, "E6 init target=internal capBytes=$evictorBytes")
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Internal stream cache corrupted: ${e.message}. Attempting self-repair of stream cache dir.", e)
            repairStreamCacheDir(intRoot)
            val db = runCatching { StandaloneDatabaseProvider(app) }.getOrNull()
            val result = if (db != null) {
                SimpleCache(intRoot, LeastRecentlyUsedCacheEvictor(evictorBytes), db)
            } else {
                SimpleCache(intRoot, LeastRecentlyUsedCacheEvictor(evictorBytes))
            }
            simpleCache = result
            databaseProvider = db
            activeBase = intRoot
            activeTarget = TARGET_INTERNAL
            activeCap = capGiB
            activeCapBytes = evictorBytes
            Log.i(TAG, "E6 self-repair success: target=internal capBytes=$evictorBytes")
            return result
        }
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

    fun createCacheDataSourceFactory(http: DataSource.Factory): CacheDataSource.Factory {
        val cache = simpleCache ?: throw IllegalStateException("Cache not initialized")
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(http)
            .setCacheKeyFactory(CacheKeyFactory { buildCacheKey(it.uri) })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun cachedFactory(ctx: Context, upstream: DataSource.Factory): DataSource.Factory {
        getCache(ctx)
        return createCacheDataSourceFactory(upstream)
    }

    fun createDataSourceFactory(ctx: Context, http: DataSource.Factory): DataSource.Factory =
        DefaultDataSource.Factory(ctx, cachedFactory(ctx, http))

    fun prefetchIntroDirect(uri: Uri, http: DataSource.Factory, maxBytes: Long): Long {
        if (uri.scheme !in listOf("https", "http") || maxBytes <= 0) return 0
        val cache = simpleCache ?: return 0
        onPrefetchStarted()
        try {
            val key = buildCacheKey(uri)
            if (cache.getCachedBytes(key, 0, maxBytes) >= maxBytes) return 0
            val source = createCacheDataSourceFactory(http).createDataSource()
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
        } finally {
            onPrefetchFinished()
        }
    }

    /** Prefetches only a bounded prefix; never a full album, nor any pinned download. */
    fun prefetchIntro(ctx: Context, uri: Uri, http: DataSource.Factory, maxBytes: Long): Long {
        if (uri.scheme !in listOf("https", "http") || maxBytes <= 0 || !isPreloadEnabled(ctx)) return 0
        val cache = getCache(ctx)
        val base = activeBase ?: return 0
        val reserve = minOf(StreamCachePolicy.RESERVE_GIB * StreamCachePolicy.GIB, base.totalSpace / 4)
        if (base.usableSpace <= reserve) return 0
        return prefetchIntroDirect(uri, http, maxBytes)
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

    @Synchronized
    fun initForTesting(cacheDir: File, capGiB: Int, isSd: Boolean = false): SimpleCache {
        releaseForTesting()
        val capBytes = capGiB * StreamCachePolicy.GIB
        val evictor = LeastRecentlyUsedCacheEvictor(capBytes)
        val cache = SimpleCache(cacheDir, evictor)
        simpleCache = cache
        activeBase = cacheDir
        activeTarget = if (isSd) TARGET_SD else TARGET_INTERNAL
        activeCap = capGiB
        activeCapBytes = capBytes
        return cache
    }

    @Synchronized fun releaseForTesting() {
        runCatching { simpleCache?.release() }
        simpleCache = null
        databaseProvider = null
        activeBase = null
        activeTarget = null
        activeCap = -1
        activeCapBytes = -1L
        isPlaybackActive = false
        activePrefetchCount.set(0)
    }
}
