package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
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

    enum class FallbackReason {
        NONE,
        SD_NOT_REQUESTED,
        SD_UNMOUNTED_OR_REMOVED,
        SD_BASE_INVALID_OR_READONLY,
        SD_CACHE_DIR_CREATE_FAILED,
        SD_CACHE_DIR_NOT_WRITABLE,
        SD_INSUFFICIENT_USABLE_SPACE,
        SD_SIMPLE_CACHE_INIT_FAILED,
    }

    data class StorageResolution(
        val target: String,
        val cacheDir: File,
        val capGiB: Int,
        val evictorBytes: Long,
        val usableBytes: Long,
        val totalBytes: Long,
        val existingBytes: Long,
        val fallbackReason: FallbackReason = FallbackReason.NONE,
        val fallbackDetail: String? = null,
    )

    @Volatile private var simpleCache: SimpleCache? = null
    @Volatile private var databaseProvider: StandaloneDatabaseProvider? = null
    @Volatile private var activeBase: File? = null
    @Volatile private var activeTarget: String? = null
    @Volatile private var activeCap: Int = -1
    @Volatile private var activeCapBytes: Long = -1L
    @Volatile private var lastFallback: FallbackReason = FallbackReason.NONE
    @Volatile private var lastFallbackMsg: String? = null
    @Volatile private var isPlaybackActive: Boolean = false
    @Volatile private var isPlaybackBuffering: Boolean = false
    private val activePrefetchCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val cachedBytesReadTotal = java.util.concurrent.atomic.AtomicLong(0L)
    private val upstreamBytesReadTotal = java.util.concurrent.atomic.AtomicLong(0L)
    private val cacheIgnoredTotal = java.util.concurrent.atomic.AtomicInteger(0)

    fun activeCapGiB(): Int = activeCap
    fun activeCapBytes(): Long = activeCapBytes
    fun activeTargetName(): String = activeTarget ?: "uninitialized"
    fun activeCacheDir(): File? = activeBase
    fun activeCacheUsedBytes(): Long = runCatching { simpleCache?.cacheSpace ?: 0L }.getOrDefault(0L)
    fun lastFallbackReason(): FallbackReason = lastFallback
    fun lastFallbackDetail(): String? = lastFallbackMsg
    fun totalCachedBytesRead(): Long = cachedBytesReadTotal.get()
    fun totalUpstreamBytesRead(): Long = upstreamBytesReadTotal.get()
    fun totalCacheIgnoredCount(): Int = cacheIgnoredTotal.get()

    fun resetTelemetryCounters() {
        cachedBytesReadTotal.set(0L)
        upstreamBytesReadTotal.set(0L)
        cacheIgnoredTotal.set(0)
    }

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

    fun onPlaybackBufferingChanged(isBuffering: Boolean) {
        isPlaybackBuffering = isBuffering
    }

    fun isPlaybackActivelyBuffering(): Boolean = isPlaybackBuffering

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
        val cacheSubdir = File(selectedVolume, CACHE_DIR_NAME)
        val existingBytes = if (cacheSubdir.exists() && cacheSubdir.isDirectory) {
            runCatching { cacheSubdir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
        } else 0L
        val base = StreamCachePolicy.defaultCapGiB(
            totalBytes = selectedVolume.totalSpace,
            usableBytes = selectedVolume.usableSpace,
            existingBytes = existingBytes,
        )
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

    /**
     * Verifies that a stream cache directory exists, is a directory, has write permission,
     * and can actually create and remove a zero-byte probe file inside itself.
     * Never touches melovault_offline or any parent directory.
     */
    internal fun isDirectoryWritable(dir: File): Boolean {
        if (dir.name != CACHE_DIR_NAME) return false
        if (!dir.exists() || !dir.isDirectory || !dir.canWrite()) return false
        val probe = File(dir, ".melovault_write_probe")
        return runCatching {
            if (probe.exists()) probe.delete()
            val created = probe.createNewFile() || probe.exists()
            probe.delete()
            created
        }.getOrDefault(false)
    }

    /**
     * Safely resolves and validates the stream cache directory.
     *
     * Key E6 R7 fix:
     * On Android 10 (Shanling M3 Ultra), calling `sdRoot.usableSpace` or `sdRoot.totalSpace`
     * BEFORE `sdRoot.mkdirs()` causes `statvfs` to fail with `ENOENT` and return `0L`,
     * triggering a false fallback to internal storage.
     * This method verifies `sdBase` first, creates the isolated `melovault_stream_cache`
     * directory, verifies writeability, and ONLY THEN queries `usableSpace` / `totalSpace`.
     */
    internal fun resolveCacheDirectory(
        requestedTarget: String,
        sdBase: File?,
        intBase: File,
        fallbackInternalCacheDir: File = intBase,
        explicitCapGiB: Int? = null,
        usableSpaceProvider: (File) -> Long = { it.usableSpace },
        totalSpaceProvider: (File) -> Long = { it.totalSpace },
        mkdirsProvider: (File) -> Boolean = { it.mkdirs() },
        writableChecker: (File) -> Boolean = { isDirectoryWritable(it) },
    ): StorageResolution {
        var fallbackReason = FallbackReason.NONE
        var fallbackDetail: String? = null

        if (requestedTarget == TARGET_SD) {
            if (sdBase == null) {
                fallbackReason = FallbackReason.SD_UNMOUNTED_OR_REMOVED
                fallbackDetail = "SD card not mounted or removable base is null"
                Log.w(TAG, "$fallbackDetail. Falling back to internal storage.")
            } else if (!sdBase.exists() || !sdBase.isDirectory || !sdBase.canWrite()) {
                fallbackReason = FallbackReason.SD_BASE_INVALID_OR_READONLY
                fallbackDetail = "SD base invalid or read-only: ${sdBase.absolutePath}"
                Log.w(TAG, "$fallbackDetail. Falling back to internal storage.")
            } else {
                val sdRoot = File(sdBase, CACHE_DIR_NAME)
                val dirReady = (sdRoot.exists() && sdRoot.isDirectory) || mkdirsProvider(sdRoot)
                if (!dirReady || !sdRoot.exists() || !sdRoot.isDirectory) {
                    fallbackReason = FallbackReason.SD_CACHE_DIR_CREATE_FAILED
                    fallbackDetail = "Failed to create SD stream cache dir: ${sdRoot.absolutePath}"
                    Log.w(TAG, "$fallbackDetail. Falling back to internal storage.")
                } else if (!writableChecker(sdRoot)) {
                    fallbackReason = FallbackReason.SD_CACHE_DIR_NOT_WRITABLE
                    fallbackDetail = "SD stream cache dir is not writable: ${sdRoot.absolutePath}"
                    Log.w(TAG, "$fallbackDetail. Falling back to internal storage.")
                } else {
                    val existingBytes = runCatching {
                        sdRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    }.getOrDefault(0L)
                    // Query usableSpace & totalSpace AFTER sdRoot exists, falling back to sdBase if needed
                    val usableBytes = usableSpaceProvider(sdRoot).takeIf { it > 0L } ?: usableSpaceProvider(sdBase)
                    val totalBytes = totalSpaceProvider(sdRoot).takeIf { it > 0L } ?: totalSpaceProvider(sdBase)
                    val capGiB = explicitCapGiB ?: StreamCachePolicy.defaultCapGiB(
                        totalBytes = totalBytes,
                        usableBytes = usableBytes,
                        existingBytes = existingBytes,
                    )
                    val desiredBytes = capGiB * StreamCachePolicy.GIB
                    val safeCap = StreamCachePolicy.safeCapBytes(
                        desiredBytes,
                        usableBytes,
                        existingBytes,
                        totalBytes,
                    )
                    if (usableBytes >= StreamCachePolicy.MIN_SD_USABLE_BYTES && safeCap > 0L) {
                        val evictorBytes = safeCap.coerceAtLeast(1024L * 1024L)
                        return StorageResolution(
                            target = TARGET_SD,
                            cacheDir = sdRoot,
                            capGiB = capGiB,
                            evictorBytes = evictorBytes,
                            usableBytes = usableBytes,
                            totalBytes = totalBytes,
                            existingBytes = existingBytes,
                            fallbackReason = FallbackReason.NONE,
                            fallbackDetail = null,
                        )
                    } else {
                        fallbackReason = FallbackReason.SD_INSUFFICIENT_USABLE_SPACE
                        fallbackDetail = "SD card has insufficient capacity after reserve (usable=$usableBytes, total=$totalBytes, safeCap=$safeCap)"
                        Log.w(TAG, "$fallbackDetail. Falling back to internal storage.")
                    }
                }
            }
        } else {
            fallbackReason = FallbackReason.SD_NOT_REQUESTED
        }

        // Internal storage (default or safe fallback)
        var intRoot = File(intBase, CACHE_DIR_NAME)
        if (!(intRoot.exists() && intRoot.isDirectory) && !mkdirsProvider(intRoot)) {
            intRoot = File(fallbackInternalCacheDir, CACHE_DIR_NAME)
            if (!(intRoot.exists() && intRoot.isDirectory) && !mkdirsProvider(intRoot)) {
                throw IllegalStateException("Internal cache storage unavailable")
            }
        }
        val existingBytes = runCatching {
            intRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
        val usableBytes = usableSpaceProvider(intRoot).takeIf { it > 0L } ?: usableSpaceProvider(intRoot.parentFile ?: intBase)
        val totalBytes = totalSpaceProvider(intRoot).takeIf { it > 0L } ?: totalSpaceProvider(intRoot.parentFile ?: intBase)
        val capGiB = explicitCapGiB ?: StreamCachePolicy.defaultCapGiB(
            totalBytes = totalBytes,
            usableBytes = usableBytes,
            existingBytes = existingBytes,
        )
        val desiredBytes = capGiB * StreamCachePolicy.GIB
        var safeCap = StreamCachePolicy.safeCapBytes(
            desiredBytes,
            usableBytes,
            existingBytes,
            totalBytes,
        )
        if (safeCap <= 0L) {
            safeCap = minOf(StreamCachePolicy.MIN_STREAM_CACHE_BYTES, usableBytes.coerceAtLeast(1024L * 1024L))
        }
        val evictorBytes = safeCap.coerceAtLeast(1024L * 1024L)
        return StorageResolution(
            target = TARGET_INTERNAL,
            cacheDir = intRoot,
            capGiB = capGiB,
            evictorBytes = evictorBytes,
            usableBytes = usableBytes,
            totalBytes = totalBytes,
            existingBytes = existingBytes,
            fallbackReason = fallbackReason,
            fallbackDetail = fallbackDetail,
        )
    }

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
        val savedCap = prefs(app).getInt(PREF_CAP, -1).takeIf { it in StreamCachePolicy.allowedCapsGiB }

        val resolution = resolveCacheDirectory(
            requestedTarget = requestedTarget,
            sdBase = if (requestedTarget == TARGET_SD) removableBase(app) else null,
            intBase = internalBase(app),
            fallbackInternalCacheDir = app.cacheDir,
            explicitCapGiB = savedCap,
        )

        if (resolution.target == TARGET_SD) {
            try {
                val db = runCatching { StandaloneDatabaseProvider(app) }.getOrNull()
                val result = if (db != null) {
                    SimpleCache(resolution.cacheDir, LeastRecentlyUsedCacheEvictor(resolution.evictorBytes), db)
                } else {
                    SimpleCache(resolution.cacheDir, LeastRecentlyUsedCacheEvictor(resolution.evictorBytes))
                }
                simpleCache = result
                databaseProvider = db
                activeBase = resolution.cacheDir
                activeTarget = TARGET_SD
                activeCap = resolution.capGiB
                activeCapBytes = resolution.evictorBytes
                lastFallback = FallbackReason.NONE
                lastFallbackMsg = null
                Log.i(
                    TAG,
                    "E6 init target=sd dir=${resolution.cacheDir.absolutePath} " +
                        "capGiB=${resolution.capGiB} capBytes=${resolution.evictorBytes} " +
                        "usable=${resolution.usableBytes} total=${resolution.totalBytes}",
                )
                return result
            } catch (e: Exception) {
                val msg = "SimpleCache init on SD failed: ${e.message}"
                Log.w(TAG, "$msg. Falling back to internal storage.", e)
                val intResolution = resolveCacheDirectory(
                    requestedTarget = TARGET_INTERNAL,
                    sdBase = null,
                    intBase = internalBase(app),
                    fallbackInternalCacheDir = app.cacheDir,
                    explicitCapGiB = savedCap,
                ).copy(
                    fallbackReason = FallbackReason.SD_SIMPLE_CACHE_INIT_FAILED,
                    fallbackDetail = msg,
                )
                return initInternalSimpleCache(app, intResolution)
            }
        }

        return initInternalSimpleCache(app, resolution)
    }

    private fun initInternalSimpleCache(app: Context, resolution: StorageResolution): SimpleCache {
        val intRoot = resolution.cacheDir
        val evictorBytes = resolution.evictorBytes
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
            activeCap = resolution.capGiB
            activeCapBytes = evictorBytes
            lastFallback = resolution.fallbackReason
            lastFallbackMsg = resolution.fallbackDetail
            Log.i(
                TAG,
                "E6 init target=internal dir=${intRoot.absolutePath} " +
                    "capGiB=${resolution.capGiB} capBytes=$evictorBytes " +
                    "fallbackReason=${resolution.fallbackReason}",
            )
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
            activeCap = resolution.capGiB
            activeCapBytes = evictorBytes
            lastFallback = resolution.fallbackReason
            lastFallbackMsg = resolution.fallbackDetail
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

    private class UpstreamCountingDataSource(
        private val delegate: DataSource,
        private val isPrefetch: Boolean = false,
    ) : DataSource {
        private var openTimeMs: Long = 0L
        private var hasLoggedFirstByte: Boolean = false

        override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
            delegate.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            openTimeMs = SystemClock.elapsedRealtime()
            hasLoggedFirstByte = false
            (delegate as? androidx.media3.datasource.HttpDataSource)?.let { httpDs ->
                val token = com.bsdung.musicassistantcar.data.CarApiClient.activeApiToken()
                if (token.isNotBlank()) {
                    httpDs.setRequestProperty("Authorization", "Bearer $token")
                } else {
                    httpDs.clearRequestProperty("Authorization")
                }
            }
            if (!isPrefetch) {
                Log.i(TAG, "[SHUFFLE_BENCHMARK] stage=HTTP_CONNECT_START timestamp_monotonic_ms=$openTimeMs uri=${dataSpec.uri}")
            }
            return try {
                val res = delegate.open(dataSpec)
                val connectedTimeMs = SystemClock.elapsedRealtime()
                if (!isPrefetch) {
                    val latency = connectedTimeMs - openTimeMs
                    Log.i(TAG, "[SHUFFLE_BENCHMARK] stage=HTTP_CONNECTED timestamp_monotonic_ms=$connectedTimeMs (latency=${latency}ms, contentLength=$res)")
                }
                res
            } catch (e: Exception) {
                val key = buildCacheKey(dataSpec.uri)
                if (isPrefetch) {
                    PlaybackDiagnosticTracker.onPrefetchError(key, e)
                } else {
                    PlaybackDiagnosticTracker.onPlaybackError(
                        mediaId = key,
                        error = e,
                    )
                }
                throw e
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val n = try {
                delegate.read(buffer, offset, length)
            } catch (e: Exception) {
                val key = delegate.uri?.let { buildCacheKey(it) }
                if (isPrefetch) {
                    PlaybackDiagnosticTracker.onPrefetchError(key, e)
                } else {
                    PlaybackDiagnosticTracker.onPlaybackError(
                        mediaId = key,
                        error = e,
                    )
                }
                throw e
            }
            if (n > 0) {
                upstreamBytesReadTotal.addAndGet(n.toLong())
                if (!isPrefetch && !hasLoggedFirstByte && openTimeMs > 0L) {
                    hasLoggedFirstByte = true
                    val t = SystemClock.elapsedRealtime()
                    Log.i(TAG, "[SHUFFLE_BENCHMARK] stage=FIRST_BYTE timestamp_monotonic_ms=$t (delta_from_open=${t - openTimeMs}ms, bytes=$n)")
                }
            }
            return n
        }

        override fun getUri(): Uri? = delegate.uri

        override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

        override fun close() {
            delegate.close()
        }
    }

    fun createCacheDataSourceFactory(http: DataSource.Factory, isPrefetch: Boolean = false): CacheDataSource.Factory {
        val cache = simpleCache ?: throw IllegalStateException("Cache not initialized")
        val countingUpstreamFactory = DataSource.Factory {
            UpstreamCountingDataSource(http.createDataSource(), isPrefetch = isPrefetch)
        }
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(countingUpstreamFactory)
            .setCacheKeyFactory(CacheKeyFactory { buildCacheKey(it.uri) })
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    if (cachedBytesRead > 0L) {
                        cachedBytesReadTotal.addAndGet(cachedBytesRead)
                        if (!isPrefetch) {
                            Log.i(TAG, "[SHUFFLE_BENCHMARK] stage=CACHE_HIT timestamp_monotonic_ms=${SystemClock.elapsedRealtime()} (cachedBytesRead=$cachedBytesRead, totalCached=$cacheSizeBytes)")
                        }
                    }
                }

                override fun onCacheIgnored(reason: Int) {
                    cacheIgnoredTotal.incrementAndGet()
                    if (!isPrefetch) {
                        Log.i(TAG, "[SHUFFLE_BENCHMARK] stage=CACHE_MISS timestamp_monotonic_ms=${SystemClock.elapsedRealtime()} (reason=$reason)")
                    }
                }
            })
    }

    private fun cachedFactory(ctx: Context, upstream: DataSource.Factory): DataSource.Factory {
        getCache(ctx)
        return createCacheDataSourceFactory(upstream)
    }

    fun createDataSourceFactory(ctx: Context, http: DataSource.Factory): DataSource.Factory =
        DefaultDataSource.Factory(ctx, cachedFactory(ctx, http))

    fun prefetchIntroDirect(uri: Uri, http: DataSource.Factory, maxBytes: Long): Long {
        if (uri.scheme !in listOf("https", "http") || maxBytes <= 0) return 0
        if (isPlaybackBuffering) return 0
        val cache = simpleCache ?: return 0
        onPrefetchStarted()
        try {
            val key = buildCacheKey(uri)
            if (cache.getCachedBytes(key, 0, maxBytes) >= maxBytes) return 0
            val source = createCacheDataSourceFactory(http, isPrefetch = true).createDataSource()
            var bytes = 0L
            try {
                source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(maxBytes).build())
                val buf = ByteArray(65536)
                while (bytes < maxBytes && !Thread.currentThread().isInterrupted && !isPlaybackBuffering) {
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
        if (isPlaybackBuffering) return 0
        val cache = getCache(ctx)
        val base = activeBase ?: return 0
        val usable = base.usableSpace.takeIf { it > 0L } ?: base.parentFile?.usableSpace ?: 0L
        val total = base.totalSpace.takeIf { it > 0L } ?: base.parentFile?.totalSpace ?: 0L
        val reserve = minOf(StreamCachePolicy.RESERVE_GIB * StreamCachePolicy.GIB, total / 4)
        if (usable <= reserve) return 0
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
        lastFallback = FallbackReason.NONE
        lastFallbackMsg = null
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
        lastFallback = FallbackReason.NONE
        lastFallbackMsg = null
        isPlaybackActive = false
        isPlaybackBuffering = false
        activePrefetchCount.set(0)
        resetTelemetryCounters()
    }
}
