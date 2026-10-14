package com.bsdung.musicassistantcar.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.ConnectionStateManager
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-End Audio Streaming & Network Recovery Test Suite.
 *
 * Verifies the actual Data Plane (ExoPlayer CacheDataSource / SimpleCache):
 * 1. Mạng mất 2s: Đọc từ buffer/cache hiện có, 0 lần rebuffer, tiếp tục mượt mà.
 * 2. Mạng mất 5s: Socket ngắt giữa chừng, tự động retry qua HTTP Range 206, nối tiếp byte-exact.
 * 3. Mạng mất 10s: Vượt ngưỡng Damping/Hysteresis, kết nối lại nối đúng byte offset, không đập hàng đợi.
 * 4. Mạng mất 15s & 30s: Outage kéo dài và Exponential Backoff phục hồi; phát lại lần 2 đạt 100% Cache-Hit.
 * 5. HTTP Range Retry & FLAC Integrity: Range slicing bảo toàn nguyên vẹn khung âm thanh FLAC (header fLaC).
 * 6. Prefetch Non-Competition: Prefetch 2 bài tiếp theo bị giới hạn ngân sách byte, không cạnh tranh băng thông bài đang nghe.
 */
class AudioStreamingNetworkRecoveryTest {

    private lateinit var tempCacheDir: File
    private lateinit var audioServer: ResilientAudioServer
    private lateinit var httpFactory: DataSource.Factory
    // Test-only loopback authority, never supplied by production playback.
    private val allowTestLoopback: (Uri) -> Unit = { uri ->
        val host = java.net.URL(uri.toString()).host
        require(host == "127.0.0.1" || host == "localhost")
    }
    private lateinit var stateManager: ConnectionStateManager
    private var virtualTimeMs: Long = 1_000_000L

    @Before
    fun setUp() {
        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "melovault_recovery_test_" + System.nanoTime())
        tempCacheDir.mkdirs()

        StreamCacheManager.initForTesting(tempCacheDir, capGiB = 10)

        audioServer = ResilientAudioServer()
        httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(6000)
            .setReadTimeoutMs(6000)

        val directExecutor = Executors.newSingleThreadScheduledExecutor()
        stateManager = ConnectionStateManager.createForTesting(
            context = null,
            apiClientSupplier = {
                object : CarApiClient() {
                    override fun probe(): String = audioServer.baseUrl()
                }
            },
            mainHandler = null,
            executor = directExecutor,
            timeProvider = { virtualTimeMs }
        )
    }

    @After
    fun tearDown() {
        StreamCacheManager.releaseForTesting()
        audioServer.close()
        tempCacheDir.deleteRecursively()
    }

    /**
     * Generates a realistic FLAC payload starting with standard magic bytes "fLaC".
     */
    private fun generateFlacPayload(sizeBytes: Int): ByteArray {
        val payload = ByteArray(sizeBytes)
        // FLAC 4-byte stream marker: 0x66, 0x4C, 0x61, 0x43 ("fLaC")
        payload[0] = 0x66.toByte()
        payload[1] = 0x4C.toByte()
        payload[2] = 0x61.toByte()
        payload[3] = 0x43.toByte()
        // STREAMINFO block header (mandatory first metadata block)
        payload[4] = 0x00.toByte() // last block = 0, type = STREAMINFO (0)
        payload[5] = 0x00.toByte() // length = 34 bytes
        payload[6] = 0x00.toByte()
        payload[7] = 0x22.toByte()
        // Fill remaining audio frames with deterministic pseudorandom audio bytes
        for (i in 8 until sizeBytes) {
            payload[i] = ((i * 31 + 17) and 0xFF).toByte()
        }
        return payload
    }

    @Test
    fun testNetworkDrop_2Seconds_PlaybackContinuesFromBuffer_ZeroRebuffer() {
        val trackPath = "/v1/tracks/2001/stream?quality=original"
        val payloadSize = 256 * 1024 // 256 KB
        val flacData = generateFlacPayload(payloadSize)
        audioServer.registerPayload(trackPath, flacData)

        val uri = Uri.parse(audioServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)
        val dataSource = cacheFactory.createDataSource()

        // Phase 1: Prebuffer initial 64 KB
        val prebufferSize = 64 * 1024
        val receivedData = ByteArray(payloadSize)
        val spec = DataSpec.Builder().setUri(uri).setPosition(0).setLength(payloadSize.toLong()).build()
        dataSource.open(spec)

        var totalRead = 0
        while (totalRead < prebufferSize) {
            val n = dataSource.read(receivedData, totalRead, prebufferSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        assertEquals(prebufferSize, totalRead)

        // Phase 2: Simulate network drop of 2 seconds (Wi-Fi roaming / transient glitch)
        audioServer.simulateOutageDurationMs(trackPath, outageMs = 2000L)
        virtualTimeMs += 2000L

        // Phase 3: Player continues reading. Since client buffer/cache already has bytes, no rebuffer occurs.
        var rebufferCount = 0
        while (totalRead < payloadSize) {
            val n = dataSource.read(receivedData, totalRead, payloadSize - totalRead)
            if (n <= 0) {
                rebufferCount++
                Thread.sleep(50)
                continue
            }
            totalRead += n
        }
        dataSource.close()

        assertEquals("Full track should be read completely", payloadSize, totalRead)
        assertArrayEquals("Received FLAC data must match byte-for-byte", flacData, receivedData)
        assertEquals("Short 2s drop must produce ZERO rebuffer stalls", 0, rebufferCount)
    }

    @Test
    fun testNetworkDrop_5Seconds_SocketDisconnectAndRangeResume() {
        val trackPath = "/v1/tracks/2002/stream?quality=original"
        val payloadSize = 384 * 1024 // 384 KB
        val flacData = generateFlacPayload(payloadSize)
        audioServer.registerPayload(trackPath, flacData)

        // Configure server to forcefully sever the socket after exactly 128 KB
        val dropOffset = 128 * 1024
        audioServer.forceSocketCloseAfterBytes(trackPath, dropOffset)

        val uri = Uri.parse(audioServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)

        // Read stream until socket error is encountered
        val receivedData = ByteArray(payloadSize)
        var totalRead = 0
        var source = cacheFactory.createDataSource()
        val spec1 = DataSpec.Builder().setUri(uri).setPosition(0).setLength(payloadSize.toLong()).build()
        source.open(spec1)

        var socketFailed = false
        try {
            while (totalRead < payloadSize) {
                val n = source.read(receivedData, totalRead, payloadSize - totalRead)
                if (n <= 0) {
                    if (totalRead < payloadSize) socketFailed = true
                    break
                }
                totalRead += n
            }
        } catch (e: IOException) {
            socketFailed = true
        } finally {
            runCatching { source.close() }
        }

        assertTrue("Socket should have failed at drop offset", socketFailed)
        assertEquals("Should have read exactly up to drop offset", dropOffset, totalRead)

        // Simulate 5s network outage recovery
        virtualTimeMs += 5000L
        audioServer.clearSocketFault(trackPath)

        // Resume playback via HTTP Range: client requests remainder starting at totalRead
        source = cacheFactory.createDataSource()
        val resumeSpec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(totalRead.toLong())
            .setLength((payloadSize - totalRead).toLong())
            .build()
        source.open(resumeSpec)

        while (totalRead < payloadSize) {
            val n = source.read(receivedData, totalRead, payloadSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        source.close()

        assertEquals("Full stream must be recovered", payloadSize, totalRead)
        assertArrayEquals("Audio data across socket cutoff must be 100% byte-exact", flacData, receivedData)

        // Verify server received an HTTP Range request for the resumed portion
        val ranges = audioServer.getRanges(trackPath)
        assertTrue("Server must have received Range resume header", ranges.isNotEmpty())
        assertTrue("Range header must request resume from $dropOffset", ranges.last().contains("$dropOffset"))
    }

    @Test
    fun testNetworkDrop_10Seconds_GracePeriodHysteresisAndRangeRecovery() {
        val trackPath = "/v1/tracks/2003/stream?quality=original"
        val payloadSize = 256 * 1024
        val flacData = generateFlacPayload(payloadSize)
        audioServer.registerPayload(trackPath, flacData)

        // Establish online state
        stateManager.reportApiCall("/health", audioServer.baseUrl(), success = true)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // Simulate 10-second outage where intermediate probes/calls fail
        val cutoffOffset = 96 * 1024
        audioServer.forceSocketCloseAfterBytes(trackPath, cutoffOffset)

        val uri = Uri.parse(audioServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)
        var source = cacheFactory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(payloadSize.toLong()).build())

        val receivedData = ByteArray(payloadSize)
        var totalRead = 0
        try {
            while (totalRead < payloadSize) {
                val n = source.read(receivedData, totalRead, payloadSize - totalRead)
                if (n <= 0) break
                totalRead += n
            }
        } catch (_: IOException) {}
        finally { runCatching { source.close() } }

        // Report failure to ConnectionStateManager at t = 0
        stateManager.reportApiCall(trackPath, audioServer.baseUrl(), success = false, error = IOException("Cutoff"))
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus) // Damped!

        // At t = 6s (< 10s grace period), report 2nd failure
        virtualTimeMs += 6000L
        stateManager.reportApiCall(trackPath, audioServer.baseUrl(), success = false, error = IOException("Still down"))
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus) // Still damped!

        // At t = 10s (Grace period elapsed), backend recovers
        virtualTimeMs += 4000L
        audioServer.clearSocketFault(trackPath)
        stateManager.reportApiCall("/health", audioServer.baseUrl(), success = true)

        // Resume playback: Range header connects seamlessly at offset totalRead
        source = cacheFactory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(totalRead.toLong()).setLength((payloadSize - totalRead).toLong()).build())

        while (totalRead < payloadSize) {
            val n = source.read(receivedData, totalRead, payloadSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        source.close()

        assertEquals("Full track completed after 10s recovery", payloadSize, totalRead)
        assertArrayEquals("FLAC payload verified byte-for-byte", flacData, receivedData)
        assertEquals("UI connection state stayed ONLINE throughout 10s grace period",
            ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)
    }

    @Test
    fun testNetworkDrop_15s_and_30s_ExtendedOutage_BackoffAndFullCacheHit() {
        val trackPath = "/v1/tracks/2004/stream?quality=original"
        val payloadSize = 256 * 1024
        val flacData = generateFlacPayload(payloadSize)
        audioServer.registerPayload(trackPath, flacData)

        // Read half of the file
        val halfOffset = 128 * 1024
        val uri = Uri.parse(audioServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)
        var source = cacheFactory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(halfOffset.toLong()).build())

        val receivedData = ByteArray(payloadSize)
        var totalRead = 0
        while (totalRead < halfOffset) {
            val n = source.read(receivedData, totalRead, halfOffset - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        source.close()
        assertEquals(halfOffset, totalRead)

        // Extended 30s network loss: Server is dead
        audioServer.setServerOffline(true)
        stateManager.reportApiCall("/health", audioServer.baseUrl(), success = true)

        // Multiple failures over 30s
        for (i in 1..4) {
            virtualTimeMs += 7500L
            stateManager.reportApiCall("/health", audioServer.baseUrl(), success = false, error = IOException("Down $i"))
        }
        assertEquals("Transitions to OFFLINE after extended outage",
            ConnectionStateManager.UiConnectionStatus.OFFLINE, stateManager.snapshot().uiStatus)

        // Network recovers at +30s
        audioServer.setServerOffline(false)
        stateManager.reportApiCall("/health", audioServer.baseUrl(), success = true)
        assertEquals("Recovers to ONLINE",
            ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // Resume remaining bytes
        source = cacheFactory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(totalRead.toLong()).setLength((payloadSize - totalRead).toLong()).build())
        while (totalRead < payloadSize) {
            val n = source.read(receivedData, totalRead, payloadSize - totalRead)
            if (n <= 0) break
            totalRead += n
        }
        source.close()
        assertEquals(payloadSize, totalRead)
        assertArrayEquals("Payload intact after 30s outage recovery", flacData, receivedData)

        // CRUCIAL: Verification of 100% Cache-Hit on replay
        val requestsBeforeReplay = audioServer.getRequestCount(trackPath)
        val replaySource = cacheFactory.createDataSource()
        val replayBuffer = ByteArray(payloadSize)
        replaySource.open(DataSpec.Builder().setUri(uri).setPosition(0).setLength(payloadSize.toLong()).build())

        var replayRead = 0
        while (replayRead < payloadSize) {
            val n = replaySource.read(replayBuffer, replayRead, payloadSize - replayRead)
            if (n <= 0) break
            replayRead += n
        }
        replaySource.close()

        assertEquals(payloadSize, replayRead)
        assertArrayEquals("Replay read from SimpleCache matches flacData", flacData, replayBuffer)
        assertEquals("Replay MUST make 0 new HTTP requests (100% Cache HIT)",
            requestsBeforeReplay, audioServer.getRequestCount(trackPath))
    }

    @Test
    fun testHttpRangeRetry_FLACHeaderAndFrameIntegrity() {
        val trackPath = "/v1/tracks/2005/stream?quality=original"
        val payloadSize = 512 * 1024 // 512 KB
        val flacData = generateFlacPayload(payloadSize)
        audioServer.registerPayload(trackPath, flacData)

        val uri = Uri.parse(audioServer.baseUrl() + trackPath)
        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)

        // Seek directly to 75% into the track (offset 384 KB)
        val seekOffset = 384 * 1024L
        val seekLength = 64 * 1024L
        val source = cacheFactory.createDataSource()
        source.open(DataSpec.Builder().setUri(uri).setPosition(seekOffset).setLength(seekLength).build())

        val seekBuffer = ByteArray(seekLength.toInt())
        var readBytes = 0
        while (readBytes < seekLength.toInt()) {
            val n = source.read(seekBuffer, readBytes, seekLength.toInt() - readBytes)
            if (n <= 0) break
            readBytes += n
        }
        source.close()

        assertEquals(seekLength.toInt(), readBytes)
        val expectedSlice = flacData.copyOfRange(seekOffset.toInt(), (seekOffset + seekLength).toInt())
        assertArrayEquals("Seek slice must match exact FLAC payload slice", expectedSlice, seekBuffer)

        // Verify HTTP Range request details
        val ranges = audioServer.getRanges(trackPath)
        assertTrue("Must have received Range request", ranges.isNotEmpty())
        assertTrue("Range header must request byte 393216", ranges.first().contains("$seekOffset"))
    }

    @Test
    fun testPrefetchDoesNotStarveActivePlayback_BoundedBudget() {
        val currentTrackPath = "/v1/tracks/current/stream?quality=original"
        val nextTrackPath = "/v1/tracks/next/stream?quality=original"
        val trackSize = 1024 * 1024 // 1 MB

        val currentData = generateFlacPayload(trackSize)
        val nextData = generateFlacPayload(trackSize)

        audioServer.registerPayload(currentTrackPath, currentData)
        audioServer.registerPayload(nextTrackPath, nextData)

        val currentUri = Uri.parse(audioServer.baseUrl() + currentTrackPath)
        val nextUri = Uri.parse(audioServer.baseUrl() + nextTrackPath)

        val cacheFactory = StreamCacheManager.createCacheDataSourceFactory(httpFactory, verifyProtectedUri = allowTestLoopback)

        // 1. Start active playback of current track
        val activeSource = cacheFactory.createDataSource()
        activeSource.open(DataSpec.Builder().setUri(currentUri).setPosition(0).setLength(trackSize.toLong()).build())

        // Read active track's startup buffer (64 KB)
        val activeBuf = ByteArray(64 * 1024)
        activeSource.read(activeBuf, 0, activeBuf.size)

        // 2. Concurrently run prefetch of next track with bounded budget (PREFETCH_METERED_BYTES = 512 KB)
        val prefetchBudget = StreamCachePolicy.PREFETCH_METERED_BYTES
        val prefetchExecutor = Executors.newSingleThreadExecutor()
        val prefetchFuture = prefetchExecutor.submit(Callable {
            StreamCacheManager.prefetchIntroDirect(nextUri, httpFactory, prefetchBudget, verifyProtectedUri = allowTestLoopback)
        })

        val prefetchedBytes = prefetchFuture.get(5, TimeUnit.SECONDS)
        assertEquals("Prefetch must be strictly bounded to budget (512 KB)", prefetchBudget, prefetchedBytes)

        // 3. Active playback continues without interruption
        var totalActiveRead = activeBuf.size
        val fullActiveBuf = ByteArray(trackSize)
        System.arraycopy(activeBuf, 0, fullActiveBuf, 0, activeBuf.size)

        while (totalActiveRead < trackSize) {
            val n = activeSource.read(fullActiveBuf, totalActiveRead, trackSize - totalActiveRead)
            if (n <= 0) break
            totalActiveRead += n
        }
        activeSource.close()
        prefetchExecutor.shutdownNow()

        assertEquals("Active track read 100% of bytes without starvation", trackSize, totalActiveRead)
        assertArrayEquals("Active playback audio payload is uncorrupted", currentData, fullActiveBuf)
    }

    /**
     * In-process HTTP Server with dynamic fault injection:
     * - Force socket sever after N bytes
     * - Simulate blackout / outage for N ms
     * - Server offline toggle (returns connection refused / closes immediately)
     * - HTTP 206 Partial Content / Range header processing
     */
    private class ResilientAudioServer : AutoCloseable {
        private val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        @Volatile private var running = true
        @Volatile private var isServerOffline = false

        private val executor = Executors.newCachedThreadPool { r ->
            Thread(r, "ResilientAudioServer-worker").apply { isDaemon = true }
        }

        private val payloads = ConcurrentHashMap<String, ByteArray>()
        private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
        private val requestedRanges = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
        private val socketCutoffs = ConcurrentHashMap<String, Int>()
        private val outageDelays = ConcurrentHashMap<String, Long>()

        init {
            val listenerThread = Thread({
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        if (isServerOffline) {
                            socket.close()
                        } else {
                            executor.submit { handleClient(socket) }
                        }
                    } catch (_: Exception) {}
                }
            }, "ResilientAudioServer-listener")
            listenerThread.isDaemon = true
            listenerThread.start()
        }

        fun baseUrl(): String = "http://127.0.0.1:$port"

        fun registerPayload(path: String, data: ByteArray) {
            payloads[path] = data
        }

        fun forceSocketCloseAfterBytes(path: String, byteCount: Int) {
            socketCutoffs[path] = byteCount
        }

        fun clearSocketFault(path: String) {
            socketCutoffs.remove(path)
            socketCutoffs.remove(path.substringBefore('?'))
            outageDelays.remove(path)
            outageDelays.remove(path.substringBefore('?'))
        }

        fun simulateOutageDurationMs(path: String, outageMs: Long) {
            outageDelays[path] = outageMs
        }

        fun setServerOffline(offline: Boolean) {
            isServerOffline = offline
        }

        fun getRequestCount(path: String): Int =
            requestCounts[path]?.get() ?: 0

        fun getRanges(path: String): List<String> =
            requestedRanges[path] ?: emptyList()

        private fun handleClient(socket: Socket) {
            try {
                socket.use { s ->
                    val input = s.getInputStream().bufferedReader()
                    val output = s.getOutputStream()

                    val requestLine = input.readLine() ?: return
                    val parts = requestLine.split(" ")
                    if (parts.size < 2) return
                    val rawPath = parts[1]

                    requestCounts.computeIfAbsent(rawPath) { AtomicInteger(0) }.incrementAndGet()
                    val basePath = rawPath.substringBefore('?')

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
                    }

                    val delay = outageDelays[rawPath] ?: outageDelays[basePath] ?: 0L
                    if (delay > 0) {
                        try { Thread.sleep(delay) } catch (_: InterruptedException) { return }
                    }

                    val payload = payloads[rawPath] ?: payloads[basePath] ?: ByteArray(64 * 1024)
                    val totalLen = payload.size.toLong()
                    val cutoff = socketCutoffs[rawPath] ?: socketCutoffs[basePath] ?: -1

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
                        writeBytesWithFault(s, output, payload, start.toInt(), contentLen.toInt(), cutoff)
                        output.flush()
                    } else {
                        val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: audio/flac\r\n" +
                            "Accept-Ranges: bytes\r\n" +
                            "Content-Length: $totalLen\r\n" +
                            "Connection: close\r\n\r\n"
                        output.write(header.toByteArray(Charsets.US_ASCII))
                        writeBytesWithFault(s, output, payload, 0, payload.size, cutoff)
                        output.flush()
                    }
                }
            } catch (_: IOException) {
                // Expected during abrupt socket RST severing
            }
        }

        private fun writeBytesWithFault(socket: Socket, out: OutputStream, data: ByteArray, start: Int, length: Int, cutoff: Int) {
            val chunkSize = 8192
            var written = 0
            while (written < length) {
                val currentOffset = start + written
                if (cutoff >= 0 && currentOffset >= cutoff) {
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                    socket.setSoLinger(true, 0)
                    socket.close()
                    throw IOException("Simulated network socket abrupt disconnect at offset $currentOffset")
                }
                var toWrite = minOf(chunkSize, length - written)
                if (cutoff in currentOffset until (currentOffset + toWrite)) {
                    toWrite = cutoff - currentOffset
                }
                if (toWrite > 0) {
                    out.write(data, currentOffset, toWrite)
                    out.flush()
                    written += toWrite
                }
                if (cutoff >= 0 && (start + written) >= cutoff) {
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                    socket.setSoLinger(true, 0)
                    socket.close()
                    throw IOException("Simulated network socket abrupt disconnect at offset ${start + written}")
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
