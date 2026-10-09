package com.bsdung.musicassistantcar.playback

import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class NetworkDownloadPolicyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

    private class FakeWakeLock : DownloadWakeLock {
        @Volatile
        override var isHeld: Boolean = false
            private set
        val acquireCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        override fun acquire() {
            isHeld = true
            acquireCount.incrementAndGet()
        }

        override fun release() {
            if (isHeld) {
                isHeld = false
                releaseCount.incrementAndGet()
            }
        }
    }

    private class MutableNetworkPolicyResolver(
        initialTransports: List<NetworkTransportInfo> = listOf(
            NetworkTransportInfo(
                hasWifiTransport = true,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
    ) : NetworkPolicyResolver {
        private val current = AtomicReference(initialTransports)

        constructor(singleTransport: NetworkTransportInfo) : this(listOf(singleTransport))

        fun setTransports(transports: List<NetworkTransportInfo>) {
            current.set(transports)
        }

        fun setTransportInfo(info: NetworkTransportInfo) {
            current.set(listOf(info))
        }

        override fun getActiveTransports(): List<NetworkTransportInfo> = current.get()
    }

    companion object {
        val WIFI_VALIDATED = NetworkTransportInfo(
            hasWifiTransport = true,
            hasCellularTransport = false,
            hasVpnTransport = false,
            hasInternetCapability = true,
            isValidated = true,
        )
        val CELLULAR_VALIDATED = NetworkTransportInfo(
            hasWifiTransport = false,
            hasCellularTransport = true,
            hasVpnTransport = false,
            hasInternetCapability = true,
            isValidated = true,
        )
        val WIFI_UNVALIDATED = NetworkTransportInfo(
            hasWifiTransport = true,
            hasCellularTransport = false,
            hasVpnTransport = false,
            hasInternetCapability = true,
            isValidated = false,
        )
        val VPN_TUNNEL = NetworkTransportInfo(
            hasWifiTransport = false,
            hasCellularTransport = false,
            hasVpnTransport = true,
            hasInternetCapability = true,
            isValidated = true,
        )
    }

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("network_policy_test")
        store = OfflineMetadataStore(storeDir)
    }

    private fun createCarTrack(
        id: Int,
        title: String = "Track $id",
        sizeBytes: Long? = 4096L,
    ): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            status = "ready",
            duration = 180.0,
            sourceSizeBytes = sizeBytes,
            sourceFormat = "mp3",
        )
    }

    // =========================================================================
    // 39. NETWORK RESOLVER UNIT TESTS
    // =========================================================================

    @Test
    fun testWifiValidatedAllowsWifiOnly() {
        val resolver = MutableNetworkPolicyResolver(WIFI_VALIDATED)
        assertTrue(resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertTrue(resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testCellularValidatedBlocksWifiOnly() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        assertFalse("Cellular must NOT satisfy WIFI_ONLY", resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertTrue("Cellular with validated internet satisfies ANY_NETWORK", resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testNoNetworkBlocksAllPolicies() {
        val resolver = MutableNetworkPolicyResolver(emptyList())
        assertFalse(resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertFalse(resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testCaptiveUnvalidatedWifiBlocksWifiOnly() {
        // TRANSPORT_WIFI = true, NET_CAPABILITY_INTERNET = true, NET_CAPABILITY_VALIDATED = false
        val resolver = MutableNetworkPolicyResolver(WIFI_UNVALIDATED)
        assertFalse("Unvalidated captive Wi-Fi must NOT satisfy WIFI_ONLY", resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertFalse("Unvalidated captive Wi-Fi must NOT satisfy ANY_NETWORK", resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testVpnOverValidatedWifiAllowsWifiOnly() {
        // Active network has TRANSPORT_VPN, underlying/concurrent network has TRANSPORT_WIFI + INTERNET + VALIDATED
        val resolver = MutableNetworkPolicyResolver(listOf(VPN_TUNNEL, WIFI_VALIDATED))
        assertTrue("VPN/Tailscale over validated Wi-Fi must satisfy WIFI_ONLY", resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertTrue(resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testVpnOverCellularBlocksWifiOnly() {
        // Active network has TRANSPORT_VPN, underlying network has TRANSPORT_CELLULAR + INTERNET + VALIDATED, no Wi-Fi
        val resolver = MutableNetworkPolicyResolver(listOf(VPN_TUNNEL, CELLULAR_VALIDATED))
        assertFalse("VPN over cellular without validated Wi-Fi must NOT satisfy WIFI_ONLY", resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
        assertTrue("VPN over cellular satisfies ANY_NETWORK", resolver.isDownloadAllowed(DownloadNetworkPolicy.ANY_NETWORK))
    }

    @Test
    fun testMeteredValidatedWifiStillAllowsWifiOnly() {
        // Do NOT require NET_CAPABILITY_NOT_METERED: a validated Wi-Fi marked metered by Android still has isValidatedWifi = true
        val resolver = MutableNetworkPolicyResolver(WIFI_VALIDATED)
        assertTrue("Validated Wi-Fi must pass WIFI_ONLY even if metered", resolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY))
    }

    // =========================================================================
    // 40. POLICY PERSISTENCE UNIT TESTS
    // =========================================================================

    @Test
    fun testDefaultDownloadPolicyIsAnyNetwork() {
        val manager = PinnedDownloadManager(store)
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, manager.getDownloadNetworkPolicy())
        assertEquals("Mọi mạng", manager.formatNetworkPolicyLabel())
    }

    @Test
    fun testSetDownloadPolicyPersistsAcrossManagerInstances() {
        val manager1 = PinnedDownloadManager(store)
        manager1.setDownloadNetworkPolicy(DownloadNetworkPolicy.WIFI_ONLY)
        assertEquals(DownloadNetworkPolicy.WIFI_ONLY, manager1.getDownloadNetworkPolicy())
        assertEquals("Chỉ Wi-Fi", manager1.formatNetworkPolicyLabel())

        // Recreate manager on same store directory
        val manager2 = PinnedDownloadManager(store)
        assertEquals(DownloadNetworkPolicy.WIFI_ONLY, manager2.getDownloadNetworkPolicy())
        assertEquals("Chỉ Wi-Fi", manager2.formatNetworkPolicyLabel())

        // Switch back to ANY_NETWORK and verify persistence
        manager2.setDownloadNetworkPolicy(DownloadNetworkPolicy.ANY_NETWORK)
        val manager3 = PinnedDownloadManager(store)
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, manager3.getDownloadNetworkPolicy())
        assertEquals("Mọi mạng", manager3.formatNetworkPolicyLabel())
    }

    @Test
    fun testInvalidStoredPolicyFallsBackToAnyNetwork() {
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, DownloadNetworkPolicy.fromString(null))
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, DownloadNetworkPolicy.fromString(""))
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, DownloadNetworkPolicy.fromString("INVALID_POLICY"))
        assertEquals(DownloadNetworkPolicy.WIFI_ONLY, DownloadNetworkPolicy.fromString("WIFI_ONLY"))
        assertEquals(DownloadNetworkPolicy.ANY_NETWORK, DownloadNetworkPolicy.fromString("ANY_NETWORK"))
    }

    // =========================================================================
    // 41. SINGLE DOWNLOAD UNDER WIFI_ONLY UNIT TESTS
    // =========================================================================

    @Test
    fun testSingleDownloadUnderWifiOnlyWhenOnCellularEntersWaitingForWifi() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val wakeLock = FakeWakeLock()
        val streamOpenedCount = AtomicInteger(0)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ ->
                streamOpenedCount.incrementAndGet()
                Pair(ByteArrayInputStream(ByteArray(1024) { 0x11 }), 1024L)
            },
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }

        manager.startDownload(createCarTrack(101))

        val status = manager.getDownloadStatus(101L)
        assertEquals(DownloadState.WAITING_FOR_WIFI, status.state)
        assertTrue(manager.isQueueWaitingForWifi())
        assertEquals("No HTTP stream may be opened while WAITING_FOR_WIFI", 0, streamOpenedCount.get())
        assertFalse("No .part file may exist while WAITING_FOR_WIFI", File(storeDir, "track_101.part").exists())
        assertFalse("WakeLock must not be held while WAITING_FOR_WIFI", wakeLock.isHeld)
        assertEquals(0, wakeLock.acquireCount.get())
    }

    @Test
    fun testSingleDownloadUnderWifiOnlyWhenOnWifiStartsImmediately() {
        val resolver = MutableNetworkPolicyResolver(WIFI_VALIDATED)
        val wakeLock = FakeWakeLock()
        val completedLatch = CountDownLatch(1)
        val data = ByteArray(4096) { 0x33 }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(data), data.size.toLong()) },
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        manager.startDownload(createCarTrack(102, sizeBytes = data.size.toLong()))
        assertTrue("Download should complete on Wi-Fi", completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(102L))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(102L).state)
        assertFalse("WakeLock must be released after completion", wakeLock.isHeld)
    }

    // =========================================================================
    // 42. BULK DOWNLOAD UNDER WIFI_ONLY UNIT TESTS
    // =========================================================================

    @Test
    fun testBulkDownloadUnderWifiOnlyWhenOnCellularWaitsAndResumesFifoOnWifi() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val streamOrder = Collections.synchronizedList(mutableListOf<Int>())
        val activeConcurrency = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)
        val allCompletedLatch = CountDownLatch(3)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                val cur = activeConcurrency.incrementAndGet()
                maxObservedConcurrency.updateAndGet { maxOf(it, cur) }
                streamOrder.add(track.id)
                val payload = ByteArray(2048) { track.id.toByte() }
                val wrapped = object : ByteArrayInputStream(payload) {
                    override fun close() {
                        activeConcurrency.decrementAndGet()
                        super.close()
                    }
                }
                Pair(wrapped, payload.size.toLong())
            },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                allCompletedLatch.countDown()
            }
        })

        val tracks = listOf(
            createCarTrack(201, "Track A", 2048L),
            createCarTrack(202, "Track B", 2048L),
            createCarTrack(203, "Track C", 2048L),
        )
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "1",
            sourceDisplayName = "QA Playlist",
            trackIds = listOf(201L, 202L, 203L),
            initialEligibleCount = 3,
            knownTotalBytes = 6144L,
            hasUnknownSizes = false,
        )

        val enqueued = manager.enqueueBatch(batch, tracks)
        assertEquals(3, enqueued)

        // All 3 tracks must be WAITING_FOR_WIFI and 0 streams opened
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(201L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(202L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(203L).state)
        assertTrue(manager.isQueueWaitingForWifi())
        assertTrue(streamOrder.isEmpty())

        // Now validated Wi-Fi becomes available
        resolver.setTransportInfo(WIFI_VALIDATED)
        manager.onNetworkStateChanged()

        assertTrue("All 3 batch tracks should complete after Wi-Fi arrives", allCompletedLatch.await(5, TimeUnit.SECONDS))
        assertEquals("Sequential FIFO order must be preserved", listOf(201, 202, 203), streamOrder)
        assertEquals("Max concurrency must never exceed 1", 1, maxObservedConcurrency.get())
        assertTrue(manager.isOfflineReady(201L))
        assertTrue(manager.isOfflineReady(202L))
        assertTrue(manager.isOfflineReady(203L))
    }

    // =========================================================================
    // 43. MID-TRANSFER WI-FI LOSS UNIT TESTS
    // =========================================================================

    @Test
    fun testMidTransferWifiLossPausesHeadJobAndPreservesQueueOrder() {
        val resolver = MutableNetworkPolicyResolver(WIFI_VALIDATED)
        val wakeLock = FakeWakeLock()

        val trackBFirstAttemptMidwayLatch = CountDownLatch(1)
        val trackBWaitingLatch = CountDownLatch(1)
        val allCompletedLatch = CountDownLatch(3)

        val trackBAttempts = AtomicInteger(0)
        val completionOrder = Collections.synchronizedList(mutableListOf<Long>())
        val streamStartOrder = Collections.synchronizedList(mutableListOf<String>())

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                when (track.id) {
                    301 -> {
                        streamStartOrder.add("A")
                        val bytes = ByteArray(4096) { 0x41 }
                        Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
                    }
                    302 -> {
                        val attempt = trackBAttempts.incrementAndGet()
                        streamStartOrder.add("B#$attempt")
                        if (attempt == 1) {
                            // First attempt on Wi-Fi: sends 2048 bytes, then Wi-Fi drops mid-transfer
                            val stream = object : InputStream() {
                                private var sent = 0
                                override fun read(): Int = -1
                                override fun read(b: ByteArray, off: Int, len: Int): Int {
                                    if (sent == 0) {
                                        val chunk = minOf(len, 2048)
                                        for (i in 0 until chunk) b[off + i] = 0x42
                                        sent += chunk
                                        return chunk
                                    }
                                    trackBFirstAttemptMidwayLatch.countDown()
                                    // Wait until network state change aborts/closes this stream
                                    Thread.sleep(2000)
                                    return -1
                                }
                            }
                            Pair(stream, 8192L)
                        } else {
                            // Second attempt after Wi-Fi returns: full clean download from byte 0
                            val bytes = ByteArray(8192) { 0x42 }
                            Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
                        }
                    }
                    303 -> {
                        streamStartOrder.add("C")
                        val bytes = ByteArray(4096) { 0x43 }
                        Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
                    }
                    else -> throw IllegalArgumentException("Unexpected track ${track.id}")
                }
            },
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadWaitingForWifi(trackId: Long) {
                if (trackId == 302L) {
                    trackBWaitingLatch.countDown()
                }
            }
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionOrder.add(trackId)
                allCompletedLatch.countDown()
            }
        })

        val tracks = listOf(
            createCarTrack(301, "Track A", 4096L),
            createCarTrack(302, "Track B", 8192L),
            createCarTrack(303, "Track C", 4096L),
        )
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "3",
            sourceDisplayName = "Playlist 3",
            trackIds = listOf(301L, 302L, 303L),
            initialEligibleCount = 3,
            knownTotalBytes = 16384L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)

        // Wait until Track A completes and Track B is mid-transfer
        assertTrue("Track B should reach mid-transfer", trackBFirstAttemptMidwayLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Track A must already be OFFLINE_READY", manager.isOfflineReady(301L))

        // Simulate Wi-Fi loss (device falls back to cellular)
        resolver.setTransportInfo(CELLULAR_VALIDATED)
        manager.onNetworkStateChanged()

        assertTrue("Track B should transition to WAITING_FOR_WIFI", trackBWaitingLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(302L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(303L).state)
        assertFalse("Track B must NOT be OFFLINE_READY", manager.isOfflineReady(302L))
        assertFalse("Track B .part file must be deleted on Wi-Fi loss", File(storeDir, "track_302.part").exists())
        assertTrue("Track A must remain OFFLINE_READY", manager.isOfflineReady(301L))
        assertFalse("WakeLock must be released while waiting for Wi-Fi", wakeLock.isHeld)

        // Now Wi-Fi returns: Track B must restart from byte 0 BEFORE Track C starts!
        resolver.setTransportInfo(WIFI_VALIDATED)
        manager.onNetworkStateChanged()

        assertTrue("All 3 tracks should complete after Wi-Fi returns", allCompletedLatch.await(5, TimeUnit.SECONDS))
        assertEquals("Completion order must be A -> B -> C", listOf(301L, 302L, 303L), completionOrder)
        assertEquals("Stream start order must be A -> B#1 -> B#2 -> C", listOf("A", "B#1", "B#2", "C"), streamStartOrder)
        assertTrue(manager.isOfflineReady(301L))
        assertTrue(manager.isOfflineReady(302L))
        assertTrue(manager.isOfflineReady(303L))
        assertEquals(8192L, store.getRecord(302L)?.fileSizeBytes)
        assertFalse(File(storeDir, "track_302.part").exists())
    }

    // =========================================================================
    // 44. POLICY SWITCH UNIT TESTS
    // =========================================================================

    @Test
    fun testSwitchAnyNetworkToWifiOnlyWhileOnCellularAbortsActiveAndWaitsForWifi() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val wakeLock = FakeWakeLock()
        val midwayLatch = CountDownLatch(1)
        val waitingLatch = CountDownLatch(1)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ ->
                val s = object : InputStream() {
                    private var count = 0
                    override fun read(): Int = -1
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (count == 0) {
                            val n = minOf(len, 1024)
                            for (i in 0 until n) b[off + i] = 0x55
                            count += n
                            return n
                        }
                        midwayLatch.countDown()
                        Thread.sleep(2000)
                        return -1
                    }
                }
                Pair(s, 4096L)
            },
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.ANY_NETWORK,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadWaitingForWifi(trackId: Long) {
                if (trackId == 401L) waitingLatch.countDown()
            }
        })

        // Start download on cellular under ANY_NETWORK -> begins downloading
        manager.startDownload(createCarTrack(401, sizeBytes = 4096L))
        assertTrue(midwayLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.DOWNLOADING, manager.getDownloadStatus(401L).state)

        // User switches policy to WIFI_ONLY while on cellular
        manager.setDownloadNetworkPolicy(DownloadNetworkPolicy.WIFI_ONLY)

        assertTrue("Active cellular transfer must abort and enter WAITING_FOR_WIFI", waitingLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(401L).state)
        assertFalse("Partial file must be cleaned up", File(storeDir, "track_401.part").exists())
        assertFalse("Track must not be OFFLINE_READY", manager.isOfflineReady(401L))
        assertFalse("WakeLock must be released", wakeLock.isHeld)
    }

    @Test
    fun testSwitchWifiOnlyToAnyNetworkWhileWaitingOnCellularStartsImmediately() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val completedLatch = CountDownLatch(1)
        val payload = ByteArray(4096) { 0x66 }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(payload), payload.size.toLong()) },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        // Enqueue under WIFI_ONLY while on cellular -> waits for Wi-Fi
        manager.startDownload(createCarTrack(402, sizeBytes = payload.size.toLong()))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(402L).state)

        // User switches policy to ANY_NETWORK -> starts immediately over cellular
        manager.setDownloadNetworkPolicy(DownloadNetworkPolicy.ANY_NETWORK)
        assertTrue("Download should start and complete after switching to ANY_NETWORK", completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(402L))
    }

    // =========================================================================
    // 45. CANCEL WHILE WAITING UNIT TESTS
    // =========================================================================

    @Test
    fun testSingleCancelWhileWaitingForWifi() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val streamOpened = AtomicBoolean(false)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ ->
                streamOpened.set(true)
                Pair(ByteArrayInputStream(ByteArray(1024)), 1024L)
            },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )

        manager.startDownload(createCarTrack(501))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(501L).state)

        assertTrue(manager.cancelDownload(501L))
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(501L).state)
        assertFalse(manager.hasActiveJobs())

        // Even if Wi-Fi becomes available later, cancelled track must NOT start
        resolver.setTransportInfo(WIFI_VALIDATED)
        manager.onNetworkStateChanged()
        Thread.sleep(100)
        assertFalse("Cancelled track must not download when Wi-Fi returns", streamOpened.get())
        assertFalse(manager.isOfflineReady(501L))
    }

    @Test
    fun testCancelPendingQueueAndCancelAllWhileWaitingForWifi() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(1024)), 1024L) },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )

        val tracks = listOf(createCarTrack(511), createCarTrack(512), createCarTrack(513))
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "5",
            sourceDisplayName = "Batch 5",
            trackIds = listOf(511L, 512L, 513L),
            initialEligibleCount = 3,
            knownTotalBytes = 3072L,
            hasUnknownSizes = false,
        )
        manager.enqueueBatch(batch, tracks)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(511L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(512L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(513L).state)

        // Hủy hàng đợi (cancelPendingQueue) must cancel waiting jobs
        val cancelledPending = manager.cancelPendingQueue()
        assertEquals(3, cancelledPending)
        assertFalse(manager.hasActiveJobs())
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(511L).state)

        // Re-enqueue and test Hủy tất cả (cancelAllJobs)
        manager.enqueueBatch(batch, tracks)
        assertTrue(manager.hasActiveJobs())
        val cancelledAll = manager.cancelAllJobs()
        assertEquals(3, cancelledAll)
        assertFalse(manager.hasActiveJobs())
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(512L).state)
    }

    // =========================================================================
    // 46. STORAGE RE-CHECK ON RESUME UNIT TESTS
    // =========================================================================

    @Test
    fun testStorageCapExceededWhenWifiReturnsFailsCleanly() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val failLatch = CountDownLatch(1)
        var reportedError: String? = null

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(4096)), 4096L) },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.setOfflineStorageCapBytes(10_000L)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                if (trackId == 601L) {
                    reportedError = error
                    failLatch.countDown()
                }
            }
        })

        // Enqueue 4096-byte track while cap is 10,000 bytes and usage is 0 -> waits for Wi-Fi
        manager.startDownload(createCarTrack(601, sizeBytes = 4096L))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(601L).state)

        // While waiting for Wi-Fi, cap is lowered (or storage fills up) so 4096 bytes exceeds cap
        manager.setOfflineStorageCapBytes(2000L)

        // Wi-Fi becomes available -> must re-check cap before starting transfer and fail cleanly
        resolver.setTransportInfo(WIFI_VALIDATED)
        manager.onNetworkStateChanged()

        assertTrue("Job should fail storage cap recheck on resume", failLatch.await(5, TimeUnit.SECONDS))
        assertEquals("Vượt giới hạn dung lượng offline", reportedError)
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(601L).state)
        assertFalse(manager.isOfflineReady(601L))
    }

    @Test
    fun testFreeSpaceReserveViolatedWhenWifiReturnsFailsCleanly() {
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val failLatch = CountDownLatch(1)
        var reportedError: String? = null
        val availableSpace = AtomicReference(2L * 1024L * 1024L * 1024L) // 2 GB initially

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(4096)), 4096L) },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { availableSpace.get() }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                if (trackId == 602L) {
                    reportedError = error
                    failLatch.countDown()
                }
            }
        })

        // Enqueue while 2 GB free -> enters WAITING_FOR_WIFI
        manager.startDownload(createCarTrack(602, sizeBytes = 4096L))
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(602L).state)

        // While waiting, free space drops below 500 MB reserve (e.g. 400 MB)
        availableSpace.set(400L * 1024L * 1024L)

        // Wi-Fi returns -> must re-check 500 MB reserve and fail cleanly
        resolver.setTransportInfo(WIFI_VALIDATED)
        manager.onNetworkStateChanged()

        assertTrue("Job should fail 500 MB reserve recheck on resume", failLatch.await(5, TimeUnit.SECONDS))
        assertEquals("Không đủ dung lượng bộ nhớ", reportedError)
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(602L).state)
        assertFalse(manager.isOfflineReady(602L))
    }

    // =========================================================================
    // 47. FINALIZATION RACE SAFETY & STREAMING ISOLATION UNIT TESTS
    // =========================================================================

    @Test
    fun testCancelDuringFinalizingRemovesFileAndRecordEvenUnderWifiOnly() {
        val resolver = MutableNetworkPolicyResolver(WIFI_VALIDATED)
        val finalizingEnteredLatch = CountDownLatch(1)
        val cancelledLatch = CountDownLatch(1)
        val payload = ByteArray(4096) { 0x7A }

        lateinit var manager: PinnedDownloadManager
        manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(payload), payload.size.toLong()) },
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        manager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFinalizing(trackId: Long) {
                if (trackId == 701L) {
                    finalizingEnteredLatch.countDown()
                    // Cancel right as FINALIZING is entered
                    manager.cancelDownload(701L)
                }
            }
            override fun onDownloadCancelled(trackId: Long) {
                if (trackId == 701L) {
                    cancelledLatch.countDown()
                }
            }
        })

        manager.startDownload(createCarTrack(701, sizeBytes = payload.size.toLong()))
        assertTrue(finalizingEnteredLatch.await(5, TimeUnit.SECONDS))
        assertTrue(cancelledLatch.await(5, TimeUnit.SECONDS))

        assertFalse("Cancelled track must NOT be OFFLINE_READY", manager.isOfflineReady(701L))
        assertNull("Cancelled track must NOT have metadata record", store.getRecord(701L))
        assertFalse("Final file must not remain on disk", File(storeDir, "track_701.mp3").exists())
        assertFalse("Part file must not remain on disk", File(storeDir, "track_701.part").exists())
    }

    @Test
    fun testWifiOnlyPolicyDoesNotBlockRemoteStreamingOrApiRequests() {
        // Verify that RemoteCatalog playback item resolution and CarApiClient stream URL resolution
        // have zero dependency on PinnedDownloadManager's WIFI_ONLY network policy.
        val resolver = MutableNetworkPolicyResolver(CELLULAR_VALIDATED)
        val manager = PinnedDownloadManager(
            store = store,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        assertFalse("Pinned download is blocked on cellular under WIFI_ONLY", manager.isNetworkAllowedForDownload())

        val trackId = 11209
        val expectedStreamUri = android.net.Uri.parse("http://127.0.0.1:8790/v1/tracks/$trackId/stream?quality=flac")
        val remoteTrack = createCarTrack(trackId, "Remote Stream Track", 252182L)
        val fakeApi = object : CarApiClient() {
            override fun track(tId: Int): CarTrack = remoteTrack
            override fun streamUri(tId: Int): android.net.Uri = expectedStreamUri
            override fun trackCoverUri(tId: Int): android.net.Uri =
                android.net.Uri.parse("http://127.0.0.1:8790/v1/tracks/$tId/cover")
        }
        val catalog = com.bsdung.musicassistantcar.data.RemoteCatalog(
            api = fakeApi,
            downloadManagerSupplier = { manager },
        )

        // Remote stream MediaItem and API lookup succeed unconditionally even when WIFI_ONLY is active on cellular
        val fetchedTrack = fakeApi.track(trackId)
        assertEquals(trackId, fetchedTrack.id)
        val streamedItem = catalog.item("track:$trackId")
        assertNotNull(streamedItem)
        assertEquals("http", streamedItem?.localConfiguration?.uri?.scheme)
        assertEquals(expectedStreamUri, streamedItem?.localConfiguration?.uri)
    }
}
