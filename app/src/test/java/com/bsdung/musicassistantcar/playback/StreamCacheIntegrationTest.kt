package com.bsdung.musicassistantcar.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * E6 Integration Test Suite using an in-process ServerSocket HTTP server to verify:
 * 1. Cache-hit (first request is miss, second request is hit with 0 network calls).
 * 2. HTTP Range requests (206 Partial Content, Content-Range slicing).
 * 3. Quality separation (Original vs Mobile quality cache keys never collide).
 * 4. Preload next 2 tracks (StreamCachePolicy.nextIndices prefetches bounded intro).
 * 5. Prefetch cancellation on rapid skip (thread interrupt halts prefetch cleanly).
 * 6. Resilient playback when cache fails (FLAG_IGNORE_CACHE_ON_ERROR falls back to upstream).
 */
class StreamCacheIntegrationTest {

    private lateinit var tempCacheDir: File
    private lateinit var localServer: LocalAudioServer
    private lateinit var httpFactory: DataSource.Factory

    @Before
    fun setUp() {
        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "melovault_e6_integ_" + System.nanoTime())
        tempCacheDir.mkdirs()

        StreamCacheManager.initForTesting(tempCacheDir, capGiB = 10)

        localServer = LocalAudioServer()
        httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
    }

    @After
    fun tearDown() {
        StreamCacheManager.releaseForTesting()
        localServer.close()
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testCacheHit_firstMissSecondHit() {
        val trackPath = "/v1/tracks/11200/stream"
        val payload = ByteArray(128 * 1024) { (it % 127).toByte() }
        localServer.registerPayload(trackPath, payload)

        val uri = Uri.parse(localServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory)

        // Request 1: Cache MISS -> must fetch from local HTTP server
        val source1 = cacheFactory.createDataSource()
        val readBuffer1 = ByteArray(payload.size)
        val dataSpec1 = DataSpec.Builder().setUri(uri).setPosition(0).setLength(payload.size.toLong()).build()
        source1.open(dataSpec1)
        var totalRead1 = 0
        while (totalRead1 < payload.size) {
            val n = source1.read(readBuffer1, totalRead1, payload.size - totalRead1)
            if (n <= 0) break
            totalRead1 += n
        }
        source1.close()

        assertEquals(payload.size, totalRead1)
        assertArrayEquals("First read data must match server payload", payload, readBuffer1)
        assertEquals("First read must make exactly 1 HTTP request (cache MISS)", 1, localServer.getRequestCount(trackPath))

        // Request 2: Cache HIT -> must read from SimpleCache with 0 upstream HTTP calls
        val source2 = cacheFactory.createDataSource()
        val readBuffer2 = ByteArray(payload.size)
        val dataSpec2 = DataSpec.Builder().setUri(uri).setPosition(0).setLength(payload.size.toLong()).build()
        source2.open(dataSpec2)
        var totalRead2 = 0
        while (totalRead2 < payload.size) {
            val n = source2.read(readBuffer2, totalRead2, payload.size - totalRead2)
            if (n <= 0) break
            totalRead2 += n
        }
        source2.close()

        assertEquals(payload.size, totalRead2)
        assertArrayEquals("Second read data from cache must match original payload byte-for-byte", payload, readBuffer2)
        assertEquals("Second read must NOT make another HTTP request (cache HIT: request count remains 1)", 1, localServer.getRequestCount(trackPath))
    }

    @Test
    fun testHttpRangeHeaderHandling() {
        val trackPath = "/v1/tracks/11201/stream"
        val payload = ByteArray(64 * 1024) { (it and 0xFF).toByte() }
        localServer.registerPayload(trackPath, payload)

        val uri = Uri.parse(localServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory)

        // Read slice [1024 .. 4095] (length = 3072 bytes)
        val rangeOffset = 1024L
        val rangeLength = 3072L
        val source = cacheFactory.createDataSource()
        val sliceBuffer = ByteArray(rangeLength.toInt())
        val dataSpec = DataSpec.Builder().setUri(uri).setPosition(rangeOffset).setLength(rangeLength).build()
        source.open(dataSpec)
        var totalRead = 0
        while (totalRead < rangeLength.toInt()) {
            val n = source.read(sliceBuffer, totalRead, rangeLength.toInt() - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        source.close()

        assertEquals(rangeLength.toInt(), totalRead)
        val expectedSlice = payload.copyOfRange(rangeOffset.toInt(), (rangeOffset + rangeLength).toInt())
        assertArrayEquals("Received slice must match exact byte range from payload", expectedSlice, sliceBuffer)

        // Verify server received an HTTP Range header
        val rangesReceived = localServer.getRanges(trackPath)
        assertTrue("Server must have received Range header", rangesReceived.isNotEmpty())
        assertTrue("Range header must request byte offset 1024", rangesReceived.first().contains("1024"))
    }

    @Test
    fun testQualityOriginalAndMobileCacheSeparation() {
        val basePath = "/v1/tracks/11202/stream"
        val originalPayload = "ORIGINAL_FLAC_24BIT_96KHZ_".repeat(2048).toByteArray()
        val mobilePayload = "MOBILE_AAC_16BIT_44KHZ_".repeat(1024).toByteArray()

        localServer.registerPayload("$basePath?quality=original", originalPayload)
        localServer.registerPayload("$basePath?quality=mobile", mobilePayload)

        val originalUri = Uri.parse(localServer.baseUrl() + "$basePath?quality=original")
        val mobileUri = Uri.parse(localServer.baseUrl() + "$basePath?quality=mobile")

        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory)

        // 1. Read Original
        val srcOrig1 = cacheFactory.createDataSource()
        val bufOrig1 = ByteArray(originalPayload.size)
        srcOrig1.open(DataSpec.Builder().setUri(originalUri).setPosition(0).setLength(originalPayload.size.toLong()).build())
        var readOrig1 = 0
        while (readOrig1 < originalPayload.size) {
            val n = srcOrig1.read(bufOrig1, readOrig1, originalPayload.size - readOrig1)
            if (n <= 0) break
            readOrig1 += n
        }
        srcOrig1.close()
        assertArrayEquals(originalPayload, bufOrig1)
        assertEquals(1, localServer.getRequestCount("$basePath?quality=original"))
        assertEquals(0, localServer.getRequestCount("$basePath?quality=mobile"))

        // 2. Read Mobile -> must NOT use Original cache; must fetch Mobile stream
        val srcMob1 = cacheFactory.createDataSource()
        val bufMob1 = ByteArray(mobilePayload.size)
        srcMob1.open(DataSpec.Builder().setUri(mobileUri).setPosition(0).setLength(mobilePayload.size.toLong()).build())
        var readMob1 = 0
        while (readMob1 < mobilePayload.size) {
            val n = srcMob1.read(bufMob1, readMob1, mobilePayload.size - readMob1)
            if (n <= 0) break
            readMob1 += n
        }
        srcMob1.close()
        assertArrayEquals(mobilePayload, bufMob1)
        assertEquals(1, localServer.getRequestCount("$basePath?quality=original"))
        assertEquals(1, localServer.getRequestCount("$basePath?quality=mobile"))

        // 3. Second read for Original -> must hit Original cache (request count stays 1)
        val srcOrig2 = cacheFactory.createDataSource()
        val bufOrig2 = ByteArray(originalPayload.size)
        srcOrig2.open(DataSpec.Builder().setUri(originalUri).setPosition(0).setLength(originalPayload.size.toLong()).build())
        var readOrig2 = 0
        while (readOrig2 < originalPayload.size) {
            val n = srcOrig2.read(bufOrig2, readOrig2, originalPayload.size - readOrig2)
            if (n <= 0) break
            readOrig2 += n
        }
        srcOrig2.close()
        assertArrayEquals(originalPayload, bufOrig2)
        assertEquals("Original quality must hit cache", 1, localServer.getRequestCount("$basePath?quality=original"))

        // 4. Second read for Mobile -> must hit Mobile cache (request count stays 1)
        val srcMob2 = cacheFactory.createDataSource()
        val bufMob2 = ByteArray(mobilePayload.size)
        srcMob2.open(DataSpec.Builder().setUri(mobileUri).setPosition(0).setLength(mobilePayload.size.toLong()).build())
        var readMob2 = 0
        while (readMob2 < mobilePayload.size) {
            val n = srcMob2.read(bufMob2, readMob2, mobilePayload.size - readMob2)
            if (n <= 0) break
            readMob2 += n
        }
        srcMob2.close()
        assertArrayEquals(mobilePayload, bufMob2)
        assertEquals("Mobile quality must hit cache", 1, localServer.getRequestCount("$basePath?quality=mobile"))
    }

    @Test
    fun testPreloadNextTwoTracks() {
        // Setup a queue of 4 tracks: index 0 (current), 1, 2, 3
        val queueTracks = (0..3).map { "/v1/tracks/${11210 + it}/stream" }
        queueTracks.forEach { path ->
            localServer.registerPayload(path, ByteArray(256 * 1024) { (it % 256).toByte() })
        }

        val currentIndex = 0
        val nextIndices = StreamCachePolicy.nextIndices(currentIndex, queueTracks.size)
        assertEquals("Policy must select exactly next 2 tracks [1, 2]", listOf(1, 2), nextIndices)

        val prefetchBudget = 64 * 1024L // 64 KB intro budget
        for (idx in nextIndices) {
            val uri = Uri.parse(localServer.baseUrl() + queueTracks[idx])
            val prefetchedBytes = StreamCacheManager.prefetchIntroDirect(uri, httpFactory, prefetchBudget)
            assertEquals("Prefetch must fetch intro budget", prefetchBudget, prefetchedBytes)
        }

        // Verify tracks 1 and 2 received requests, but track 3 did NOT
        assertEquals("Track 1 must have been prefetched", 1, localServer.getRequestCount(queueTracks[1]))
        assertEquals("Track 2 must have been prefetched", 1, localServer.getRequestCount(queueTracks[2]))
        assertEquals("Track 3 must NOT have been prefetched", 0, localServer.getRequestCount(queueTracks[3]))

        // Now simulate user skipping to Track 1: opening intro should hit cache without new HTTP request
        val track1Uri = Uri.parse(localServer.baseUrl() + queueTracks[1])
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory)
        val source = cacheFactory.createDataSource()
        val buf = ByteArray(prefetchBudget.toInt())
        source.open(DataSpec.Builder().setUri(track1Uri).setPosition(0).setLength(prefetchBudget).build())
        var read = 0
        while (read < prefetchBudget.toInt()) {
            val n = source.read(buf, read, prefetchBudget.toInt() - read)
            if (n <= 0) break
            read += n
        }
        source.close()

        assertEquals(prefetchBudget.toInt(), read)
        assertEquals("Reading prefetched Track 1 intro must NOT make additional HTTP request", 1, localServer.getRequestCount(queueTracks[1]))
    }

    @Test
    fun testCancelPrefetchOnTrackSkip() {
        val trackPath = "/v1/tracks/11220/stream"
        // 10 MB payload with throttled delivery to simulate active prefetch in progress
        val largePayload = ByteArray(10 * 1024 * 1024) { (it % 128).toByte() }
        localServer.registerPayload(trackPath, largePayload)
        localServer.throttle(trackPath, 50) // 50ms per 8KB chunk ensures prolonged transfer

        val uri = Uri.parse(localServer.baseUrl() + trackPath)

        val executor = Executors.newSingleThreadExecutor()
        assertFalse("Cache should be idle before prefetch", StreamCacheManager.isCacheInUse())

        val future = executor.submit {
            StreamCacheManager.prefetchIntroDirect(uri, httpFactory, 5 * 1024 * 1024L)
        }

        // Wait up to 2 seconds for prefetch loop to actively report in-use
        var inUse = false
        val waitStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - waitStart < 2000) {
            if (StreamCacheManager.isCacheInUse()) {
                inUse = true
                break
            }
            Thread.sleep(10)
        }
        assertTrue("Cache must report in-use while prefetch is running", inUse)

        // User skips track: cancel prefetch immediately
        future.cancel(true)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)

        // After cancellation, prefetch count must restore and cache is idle
        assertFalse("Cache must return to idle after prefetch cancellation", StreamCacheManager.isCacheInUse())
    }

    @Test
    fun testResilientPlaybackWhenCacheFails_fallsBackToUpstream() {
        val trackPath = "/v1/tracks/11230/stream"
        val payload = ByteArray(64 * 1024) { (it % 250).toByte() }
        localServer.registerPayload(trackPath, payload)

        val uri = Uri.parse(localServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory)

        // 1. Initial read populates cache
        val src1 = cacheFactory.createDataSource()
        val buf1 = ByteArray(payload.size)
        src1.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(payload.size.toLong()).build())
        var read1 = 0
        while (read1 < payload.size) {
            val n = src1.read(buf1, read1, payload.size - read1)
            if (n <= 0) break
            read1 += n
        }
        src1.close()
        assertEquals(1, localServer.getRequestCount(trackPath))

        // 2. Delete all cache span files on disk to simulate cache corruption / I/O error
        tempCacheDir.walkTopDown().filter { it.isFile && it.extension != "exi" }.forEach { file ->
            file.delete()
        }

        // 3. With FLAG_IGNORE_CACHE_ON_ERROR, reading again must NOT crash; it must fall back to upstream HTTP
        val src2 = cacheFactory.createDataSource()
        val buf2 = ByteArray(payload.size)
        src2.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(payload.size.toLong()).build())
        var read2 = 0
        while (read2 < payload.size) {
            val n = src2.read(buf2, read2, payload.size - read2)
            if (n <= 0) break
            read2 += n
        }
        src2.close()

        assertEquals(payload.size, read2)
        assertArrayEquals("Fallback data from upstream must match original payload", payload, buf2)
        assertEquals("Upstream must have received a second request due to cache error fallback", 2, localServer.getRequestCount(trackPath))
    }

    /**
     * In-process HTTP Server backed by standard java.net.ServerSocket.
     */
    private class LocalAudioServer : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port: Int = serverSocket.localPort
        private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val requestedRanges = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
        private val payloads = ConcurrentHashMap<String, ByteArray>()
        private val throttles = ConcurrentHashMap<String, Long>()
        @Volatile private var running = true
        private val executor = Executors.newCachedThreadPool()

        init {
            executor.submit {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        executor.submit { handleClient(socket) }
                    } catch (e: Exception) {
                        if (!running) break
                    }
                }
            }
        }

        fun baseUrl(): String = "http://127.0.0.1:$port"

        fun registerPayload(pathWithQuery: String, data: ByteArray) {
            payloads[pathWithQuery] = data
        }

        fun throttle(pathWithQuery: String, delayMsPerChunk: Long) {
            throttles[pathWithQuery] = delayMsPerChunk
        }

        fun getRequestCount(pathWithQuery: String): Int =
            requestCounts[pathWithQuery]?.get() ?: 0

        fun getRanges(pathWithQuery: String): List<String> =
            requestedRanges[pathWithQuery] ?: emptyList()

        private fun writeThrottled(out: java.io.OutputStream, data: ByteArray, start: Int, length: Int, delayMs: Long) {
            if (delayMs <= 0) {
                out.write(data, start, length)
                return
            }
            val chunkSize = 8192
            var written = 0
            while (written < length && running) {
                val toWrite = minOf(chunkSize, length - written)
                out.write(data, start + written, toWrite)
                out.flush()
                written += toWrite
                try {
                    Thread.sleep(delayMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }

        private fun handleClient(socket: Socket) {
            socket.use { s ->
                val input = s.getInputStream().bufferedReader()
                val output = s.getOutputStream()

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val rawPath = parts[1]

                requestCounts.computeIfAbsent(rawPath) { AtomicInteger(0) }.incrementAndGet()
                val basePath = rawPath.substringBefore('?')
                if (basePath != rawPath) {
                    requestCounts.computeIfAbsent(basePath) { AtomicInteger(0) }
                }

                var rangeHeader: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(':').trim()
                    }
                }

                if (rangeHeader != null) {
                    requestedRanges.computeIfAbsent(rawPath) { CopyOnWriteArrayList() }.add(rangeHeader)
                    requestedRanges.computeIfAbsent(basePath) { CopyOnWriteArrayList() }.add(rangeHeader)
                }

                val payload = payloads[rawPath] ?: payloads[basePath] ?: ByteArray(64 * 1024)
                val totalLen = payload.size.toLong()
                val throttleDelay = throttles[rawPath] ?: throttles[basePath] ?: 0L

                if (rangeHeader != null && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
                    val rangeVal = rangeHeader.removePrefix("bytes=").trim()
                    val rParts = rangeVal.split("-")
                    val start = rParts[0].toLongOrNull() ?: 0L
                    val end = if (rParts.size > 1 && rParts[1].isNotEmpty()) {
                        rParts[1].toLongOrNull() ?: (totalLen - 1)
                    } else {
                        totalLen - 1
                    }
                    val clampedEnd = minOf(end, totalLen - 1)
                    val contentLen = (clampedEnd - start + 1).coerceAtLeast(0L)

                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                        "Content-Type: audio/flac\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Content-Range: bytes $start-$clampedEnd/$totalLen\r\n" +
                        "Content-Length: $contentLen\r\n" +
                        "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.US_ASCII))
                    if (contentLen > 0) {
                        writeThrottled(output, payload, start.toInt(), contentLen.toInt(), throttleDelay)
                    }
                    output.flush()
                } else {
                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: audio/flac\r\n" +
                        "Accept-Ranges: bytes\r\n" +
                        "Content-Length: $totalLen\r\n" +
                        "Connection: close\r\n\r\n"
                    output.write(header.toByteArray(Charsets.US_ASCII))
                    writeThrottled(output, payload, 0, payload.size, throttleDelay)
                    output.flush()
                }
            }
        }

        override fun close() {
            running = false
            runCatching { serverSocket.close() }
            executor.shutdownNow()
        }
    }
}
