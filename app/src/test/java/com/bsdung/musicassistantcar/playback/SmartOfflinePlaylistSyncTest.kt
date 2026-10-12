package com.bsdung.musicassistantcar.playback

import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarPlaylist
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.CollectionPlaybackResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SmartOfflinePlaylistSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

    class ControllableNetworkPolicyResolver(
        @Volatile var transports: List<NetworkTransportInfo> = listOf(
            NetworkTransportInfo(
                hasWifiTransport = true,
                hasCellularTransport = false,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
    ) : NetworkPolicyResolver {
        constructor(singleTransport: NetworkTransportInfo) : this(listOf(singleTransport))

        override fun getActiveTransports(): List<NetworkTransportInfo> = transports
    }

    class FakeWakeLock : DownloadWakeLock {
        val acquireCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)
        @Volatile override var isHeld: Boolean = false

        override fun acquire() {
            acquireCount.incrementAndGet()
            isHeld = true
        }

        override fun release() {
            if (isHeld) {
                releaseCount.incrementAndGet()
                isHeld = false
            }
        }
    }

    class FakeServiceController : DownloadServiceController {
        val activeCalls = AtomicInteger(0)
        val idleCalls = AtomicInteger(0)
        @Volatile private var active: Boolean = false

        override fun onQueueActive() {
            activeCalls.incrementAndGet()
            active = true
        }

        override fun onQueueIdle() {
            idleCalls.incrementAndGet()
            active = false
        }

        override val isServiceActive: Boolean
            get() = active
    }

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("smart_playlist_sync_test")
        store = OfflineMetadataStore(storeDir)
    }

    private fun sampleTrack(
        id: Int,
        title: String = "Track $id",
        sizeBytes: Long = 8_000L,
        status: String = "ready",
        sourceType: String? = null,
    ): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            status = status,
            duration = 200.0,
            sourceFormat = "flac",
            sourceSizeBytes = sizeBytes,
            sourceType = sourceType,
        )
    }

    private fun seedOfflineReadyTrack(trackId: Long, sizeBytes: Long = 8_000L, targetStore: OfflineMetadataStore = store) {
        val payload = ByteArray(sizeBytes.toInt()) { (it % 251).toByte() }
        val file = File(targetStore.getStorageDirectory(), "track_${trackId}.flac")
        file.writeBytes(payload)
        val sha = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        targetStore.saveRecord(
            OfflineTrackRecord(
                trackId = trackId,
                title = "Track $trackId",
                artist = "Artist $trackId",
                album = "Album $trackId",
                durationMs = 200_000L,
                relativeFileName = "track_${trackId}.flac",
                fileSizeBytes = sizeBytes,
                sha256 = sha,
                loudnessLufs = null,
                truePeakDbtp = null,
                loudnessRangeLu = null,
                analysisVersion = null,
                downloadedAtMs = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Section 40: Basic Enable & Initial Sync
     * Playlist with 3 tracks: 1 already OFFLINE_READY, 2 NOT_DOWNLOADED.
     * Enable "Luôn giữ offline" -> only 2 tracks enqueued, already-offline track skipped,
     * after completion playlist sync status = SYNCED.
     */
    @Test
    fun test40_basicEnableAndInitialSync() {
        seedOfflineReadyTrack(1L)
        val downloadedTrackIds = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val latch = CountDownLatch(2)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                downloadedTrackIds.add(track.id)
                val bytes = ByteArray(8_000) { 1 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))

        val preClassification = coordinator.classifyPlaylist(10, "Bolero Hay", tracks)
        assertEquals(3, preClassification.eligibleCount)
        assertEquals(1, preClassification.alreadyOfflineCount)
        assertEquals(2, preClassification.eligibleNewCount)

        val prompt = coordinator.buildEnableConfirmationPrompt("Bolero Hay", preClassification)
        assertEquals("Luôn giữ playlist này offline?", prompt.title)
        assertTrue(prompt.message.contains("Playlist: Bolero Hay"))
        assertTrue(prompt.message.contains("Tổng: 3 bài"))
        assertTrue(prompt.message.contains("Đã offline: 1 bài"))
        assertTrue(prompt.message.contains("Cần tải thêm: 2 bài"))

        val result = coordinator.enableWatchAndSync(10, "Bolero Hay", tracks)
        assertTrue(coordinator.isPlaylistWatched(10))
        assertEquals(2, result.enqueuedCount)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(2, 3), downloadedTrackIds)
        assertFalse("Track 1 must not be re-downloaded", downloadedTrackIds.contains(1))

        val finalStatus = coordinator.getPlaylistSyncStatus(10, "Bolero Hay", tracks)
        assertEquals(PlaylistSyncState.SYNCED, finalStatus.state)
        assertEquals("Luôn giữ offline: Bật", finalStatus.toggleLabel)
        assertEquals("✓ Đã đồng bộ offline", finalStatus.statusText)
    }

    /**
     * Section 41: Enable When Playlist Already 100% Offline
     * All 3 tracks already OFFLINE_READY.
     * Enable "Luôn giữ offline":
     * - 0 tracks enqueued
     * - service not started
     * - status immediately SYNCED
     */
    @Test
    fun test41_enableWhenPlaylistAlready100PercentOffline() {
        seedOfflineReadyTrack(1L)
        seedOfflineReadyTrack(2L)
        seedOfflineReadyTrack(3L)

        val serviceController = FakeServiceController()
        val openerCalls = AtomicInteger(0)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            serviceController = serviceController,
            streamOpener = { _, _ ->
                openerCalls.incrementAndGet()
                Pair(ByteArrayInputStream(ByteArray(100)), 100L)
            }
        )

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))

        val classification = coordinator.classifyPlaylist(11, "All Offline Playlist", tracks)
        assertTrue(classification.isAllEligibleOffline)
        assertEquals(0, classification.eligibleNewCount)

        val res = coordinator.enableWatchAndSync(11, "All Offline Playlist", tracks)
        assertEquals(0, res.enqueuedCount)
        assertEquals(0, serviceController.activeCalls.get())
        assertEquals(0, openerCalls.get())
        assertEquals(PlaylistSyncState.SYNCED, res.status.state)
        assertEquals("✓ Đã đồng bộ offline", res.status.statusText)
    }

    /**
     * Section 42: Remote Track Addition Sync
     * Watched playlist initially has [1, 2, 3] all OFFLINE_READY.
     * Later remote playlist becomes [1, 2, 3, 4, 5].
     * Run reconciliation:
     * - only 4 and 5 enqueued in order [4, 5]
     * - 1, 2, 3 not re-downloaded
     * - after completion status = SYNCED
     */
    @Test
    fun test42_remoteTrackAdditionSync() {
        seedOfflineReadyTrack(1L)
        seedOfflineReadyTrack(2L)
        seedOfflineReadyTrack(3L)

        val downloadedOrder = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val latch = CountDownLatch(2)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                downloadedOrder.add(track.id)
                val bytes = ByteArray(8_000) { 2 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val initialTracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))
        coordinator.enableWatchAndSync(20, "Roadtrip", initialTracks)
        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(20, "Roadtrip", initialTracks).state)

        // Remote playlist now has tracks 4 and 5 added
        val updatedTracks = listOf(
            sampleTrack(1),
            sampleTrack(2),
            sampleTrack(3),
            sampleTrack(4),
            sampleTrack(5),
        )
        val recResult = coordinator.reconcilePlaylist(20, "Roadtrip", updatedTracks)
        assertEquals(2, recResult.enqueuedCount)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(4, 5), downloadedOrder)

        val finalStatus = coordinator.getPlaylistSyncStatus(20, "Roadtrip", updatedTracks)
        assertEquals(PlaylistSyncState.SYNCED, finalStatus.state)
        assertEquals("✓ Đã đồng bộ offline", finalStatus.statusText)
    }

    /**
     * Section 43: Track Removed From Playlist Does NOT Delete Local File
     * Watched playlist [1, 2, 3] all OFFLINE_READY.
     * Remote playlist later becomes [1, 2].
     * Run reconciliation:
     * - track 3 remains OFFLINE_READY
     * - file and metadata for 3 still exist
     * - playlist status = SYNCED
     */
    @Test
    fun test43_trackRemovedFromPlaylistDoesNotDeleteLocalFile() {
        seedOfflineReadyTrack(1L)
        seedOfflineReadyTrack(2L)
        seedOfflineReadyTrack(3L)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
        )
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatchAndSync(30, "Acoustic", listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3)))

        // Track 3 removed from remote playlist
        val reducedTracks = listOf(sampleTrack(1), sampleTrack(2))
        val res = coordinator.reconcilePlaylist(30, "Acoustic", reducedTracks)

        assertEquals(0, res.enqueuedCount)
        assertTrue("Track 3 must remain OFFLINE_READY", manager.isOfflineReady(3L))
        assertNotNull("Metadata for track 3 must still exist", store.getRecord(3L))
        assertTrue("Local file for track 3 must still exist", File(storeDir, "track_3.flac").exists())
        assertEquals(PlaylistSyncState.SYNCED, res.status.state)
    }

    /**
     * Section 44: Playlist Rename Preserves Watch
     * Watch playlist id = 42, name "Old Name".
     * Remote playlist list now returns id = 42, name "New Name".
     * Run reconciliation:
     * - playlist 42 remains watched
     * - stored display name updated to "New Name"
     */
    @Test
    fun test44_playlistRenamePreservesWatch() {
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)

        coordinator.enableWatch(42, "Old Name")
        assertTrue(coordinator.isPlaylistWatched(42))
        assertEquals("Old Name", coordinator.getWatchedPlaylistRecord(42)?.displayName)

        val removed = coordinator.reconcileAuthoritativePlaylistList(
            listOf(CarPlaylist(id = 42, name = "New Name", description = "", trackCount = 3))
        )
        assertTrue(removed.isEmpty())
        assertTrue(coordinator.isPlaylistWatched(42))
        assertEquals("New Name", coordinator.getWatchedPlaylistRecord(42)?.displayName)
    }

    /**
     * Section 45: Deleted Playlist Cleanup vs Transient Error
     * Case A: Watched playlist 42; /v1/playlists succeeds and returns [10, 20] -> 42 removed from watched set, local files untouched.
     * Case B: Watched playlist 42; /v1/playlists fails with IOException -> 42 remains watched, no local files touched.
     */
    @Test
    fun test45_deletedPlaylistCleanupVsTransientError() {
        seedOfflineReadyTrack(501L)
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(42, "My Watched Playlist")

        // Case B first: transient IOException from /v1/playlists
        val failingApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> {
                throw IOException("Backend timeout / 503")
            }
        }
        val failSummary = coordinator.syncAllWatchedPlaylistsFromRemote(failingApi)
        assertFalse(failSummary.authoritativeListFetched)
        assertTrue("Playlist 42 must remain watched on transient error", coordinator.isPlaylistWatched(42))
        assertTrue("Local file 501 must remain intact", manager.isOfflineReady(501L))

        // Case A: authoritative /v1/playlists succeeds and returns [10, 20] without 42
        val authoritativeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> {
                return listOf(
                    CarPlaylist(id = 10, name = "Playlist 10", description = "", trackCount = 2),
                    CarPlaylist(id = 20, name = "Playlist 20", description = "", trackCount = 5),
                )
            }
        }
        val okSummary = coordinator.syncAllWatchedPlaylistsFromRemote(authoritativeApi)
        assertTrue(okSummary.authoritativeListFetched)
        assertEquals(listOf(42), okSummary.removedDeletedPlaylistIds)
        assertFalse("Playlist 42 must be removed after authoritative deletion", coordinator.isPlaylistWatched(42))
        assertTrue("Local file 501 must remain untouched after playlist deletion", manager.isOfflineReady(501L))
    }

    /**
     * Section 46: Multiple Watched Playlists Share Deduped Track Jobs
     * Playlist A: [101, 102, 103]
     * Playlist B: [102, 103, 104]
     * Both watched and reconciled together.
     * Verify:
     * - total enqueued jobs = 4 (101, 102, 103, 104)
     * - 102 and 103 are not duplicated
     * - when all 4 complete, both playlists report SYNCED
     */
    @Test
    fun test46_multipleWatchedPlaylistsShareDedupedTrackJobs() {
        val downloadedTracks = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val latch = CountDownLatch(4)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                downloadedTracks.add(track.id)
                val bytes = ByteArray(4_000) { 3 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracksA = listOf(sampleTrack(101), sampleTrack(102), sampleTrack(103))
        val tracksB = listOf(sampleTrack(102), sampleTrack(103), sampleTrack(104))

        coordinator.enableWatch(1, "Playlist A", nowMs = 1000L)
        coordinator.enableWatch(2, "Playlist B", nowMs = 2000L)

        val results = coordinator.reconcileMultiplePlaylists(
            listOf(
                Triple(1, "Playlist A", tracksA),
                Triple(2, "Playlist B", tracksB),
            )
        )
        val totalEnqueued = results.sumOf { it.enqueuedCount }
        assertEquals("Total enqueued across both playlists must be 4", 4, totalEnqueued)
        assertEquals(3, results[0].enqueuedCount)
        assertEquals(1, results[1].enqueuedCount)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(101, 102, 103, 104), downloadedTracks)

        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(1, "Playlist A", tracksA).state)
        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(2, "Playlist B", tracksB).state)
    }

    /**
     * Section 47: Idempotent Reconcile Under Repeated Triggers
     * Run reconciliation 3 times back-to-back while one track is DOWNLOADING and one is QUEUED.
     * Verify:
     * - no duplicate jobs
     * - queueSequence unchanged
     * - activeBatch identity preserved
     * - active download not restarted
     */
    @Test
    fun test47_idempotentReconcileUnderRepeatedTriggers() {
        val startedLatch = CountDownLatch(1)
        val holdDownloadLatch = CountDownLatch(1)
        val completedLatch = CountDownLatch(2)
        val openerCallsForTrack1 = AtomicInteger(0)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 1) {
                    openerCallsForTrack1.incrementAndGet()
                    startedLatch.countDown()
                    holdDownloadLatch.await(5, TimeUnit.SECONDS)
                }
                val bytes = ByteArray(4_000) { 4 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(1), sampleTrack(2))

        val firstRes = coordinator.enableWatchAndSync(50, "Idempotent Playlist", tracks)
        assertEquals(2, firstRes.enqueuedCount)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val initialBatch = manager.getActiveBatch()
        assertNotNull(initialBatch)
        val seq1Before = manager.getJobQueueSequence(1L)
        val seq2Before = manager.getJobQueueSequence(2L)
        assertNotNull(seq1Before)
        assertNotNull(seq2Before)

        // Run reconciliation 3 times back-to-back while track 1 is DOWNLOADING and track 2 is QUEUED
        repeat(3) {
            val repeatRes = coordinator.reconcilePlaylist(50, "Idempotent Playlist", tracks)
            assertEquals(0, repeatRes.enqueuedCount)
            assertSame(initialBatch, manager.getActiveBatch())
            assertEquals(seq1Before, manager.getJobQueueSequence(1L))
            assertEquals(seq2Before, manager.getJobQueueSequence(2L))
        }

        assertEquals(1, openerCallsForTrack1.get())
        holdDownloadLatch.countDown()
        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, openerCallsForTrack1.get())
    }

    /**
     * Section 48: WIFI_ONLY Policy Integration
     * Policy = WIFI_ONLY, network = cellular only.
     * Reconcile watched playlist with 2 missing tracks:
     * - tracks enter WAITING_FOR_WIFI
     * - 0 HTTP download requests opened
     * - wake lock not held
     * - playlist sync status = WAITING_FOR_WIFI
     * Switch network to validated Wi-Fi -> downloads automatically resume and complete, status becomes SYNCED.
     */
    @Test
    fun test48_wifiOnlyPolicyIntegration() {
        val resolver = ControllableNetworkPolicyResolver(
            NetworkTransportInfo(
                hasWifiTransport = false,
                hasCellularTransport = true,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        val wakeLock = FakeWakeLock()
        val openerCalls = AtomicInteger(0)
        val completedLatch = CountDownLatch(2)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
            streamOpener = { _, _ ->
                openerCalls.incrementAndGet()
                val bytes = ByteArray(4_000) { 5 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(301), sampleTrack(302))

        val res = coordinator.enableWatchAndSync(60, "Wi-Fi Only Playlist", tracks)
        assertEquals(2, res.enqueuedCount)
        assertEquals(0, openerCalls.get())
        assertFalse(wakeLock.isHeld)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(301L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(302L).state)

        val waitingStatus = coordinator.getPlaylistSyncStatus(60, "Wi-Fi Only Playlist", tracks)
        assertEquals(PlaylistSyncState.WAITING_FOR_WIFI, waitingStatus.state)
        assertEquals("Đang chờ Wi-Fi · 0/2 bài", waitingStatus.statusText)

        // Switch network to validated Wi-Fi
        resolver.transports = listOf(
            NetworkTransportInfo(
                hasWifiTransport = true,
                hasCellularTransport = false,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        manager.onNetworkStateChanged()

        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(2, openerCalls.get())
        assertFalse(wakeLock.isHeld)

        val syncedStatus = coordinator.getPlaylistSyncStatus(60, "Wi-Fi Only Playlist", tracks)
        assertEquals(PlaylistSyncState.SYNCED, syncedStatus.state)
        assertEquals("✓ Đã đồng bộ offline", syncedStatus.statusText)
    }

    /**
     * Section 49: Storage Cap / Reserve Integration
     * Configure storage cap or free space so missing playlist tracks exceed allowed storage.
     * Run reconciliation:
     * - 0 tracks enqueued
     * - no partial .part file created
     * - playlist remains watched
     * - playlist sync status = STORAGE_BLOCKED ("Thiếu dung lượng · X/Y bài")
     */
    @Test
    fun test49_storageCapAndReserveIntegration() {
        seedOfflineReadyTrack(401L, sizeBytes = 10_000L)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(1000)), 1000L) }
        )
        // Simulate low available space (< 500 MB reserve after download)
        manager.setAvailableSpaceProviderForTesting { PinnedDownloadManager.MIN_FREE_RESERVE_BYTES + 1_000_000L }

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(
            sampleTrack(401, sizeBytes = 10_000L),
            sampleTrack(402, sizeBytes = 5_000_000L), // 5 MB exceeds 1 MB margin above 500 MB reserve
        )

        val res = coordinator.enableWatchAndSync(70, "Large Playlist", tracks)
        assertEquals(0, res.enqueuedCount)
        assertNotNull(res.storagePrecheck)
        assertFalse(res.storagePrecheck!!.isAllowed)
        assertTrue(coordinator.isPlaylistWatched(70))
        assertFalse(File(storeDir, "track_402.part").exists())
        assertEquals(PlaylistSyncState.STORAGE_BLOCKED, res.status.state)
        assertEquals("Thiếu dung lượng · 1/2 bài", res.status.statusText)
    }

    /**
     * Section 50: Partial Failure Does Not Loop
     * Watched playlist [1, 2, 3]. Track 2 fails with IOException, tracks 1 and 3 succeed.
     * Verify:
     * - 1 and 3 -> OFFLINE_READY
     * - 2 -> FAILED
     * - 2 is not retried automatically in a loop
     * - playlist status reports 1 failed track
     * - manual "Đồng bộ ngay" later can re-enqueue track 2
     */
    @Test
    fun test50_partialFailureDoesNotLoop() {
        val track2Attempts = AtomicInteger(0)
        val failTrack2 = java.util.concurrent.atomic.AtomicBoolean(true)
        val firstPassLatch = CountDownLatch(3)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 2) {
                    track2Attempts.incrementAndGet()
                    if (failTrack2.get()) {
                        throw IOException("Simulated stream error on track 2")
                    }
                }
                val bytes = ByteArray(4_000) { 6 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                firstPassLatch.countDown()
            }
            override fun onDownloadFailed(trackId: Long, error: String) {
                firstPassLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(1), sampleTrack(2), sampleTrack(3))

        val res = coordinator.enableWatchAndSync(80, "Partial Failure Playlist", tracks)
        assertEquals(3, res.enqueuedCount)
        assertTrue(firstPassLatch.await(5, TimeUnit.SECONDS))

        assertTrue(manager.isOfflineReady(1L))
        assertTrue(manager.isOfflineReady(3L))
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(2L).state)
        assertEquals("Track 2 must only be attempted once (no auto retry loop)", 1, track2Attempts.get())

        val statusAfterFailure = coordinator.getPlaylistSyncStatus(80, "Partial Failure Playlist", tracks)
        assertEquals(PlaylistSyncState.HAS_FAILURES, statusAfterFailure.state)
        assertEquals("Có 1 bài tải lỗi", statusAfterFailure.statusText)
        assertEquals("2/3 offline · 1 bài tải lỗi", statusAfterFailure.secondaryStatusText)

        // Manual "Đồng bộ ngay" later when network issue is resolved re-enqueues only track 2
        failTrack2.set(false)
        val retryLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 2L) retryLatch.countDown()
            }
        })

        val manualSyncRes = coordinator.reconcilePlaylist(80, "Partial Failure Playlist", tracks)
        assertEquals(1, manualSyncRes.enqueuedCount)
        assertTrue(retryLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(2L))
        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(80, "Partial Failure Playlist", tracks).state)
    }

    /**
     * Section 51: Cancel Semantics
     * Watched playlist actively syncing.
     * User calls cancelPendingQueue() or cancelAllJobs().
     * Verify:
     * - canceled tracks become CANCELLED
     * - coordinator does not immediately re-enqueue them
     * - playlist remains watched
     * - later explicit "Đồng bộ ngay" re-enqueues remaining tracks
     */
    @Test
    fun test51_cancelSemantics() {
        val startedLatch = CountDownLatch(1)
        val holdLatch = CountDownLatch(1)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 1) {
                    startedLatch.countDown()
                    holdLatch.await(5, TimeUnit.SECONDS)
                }
                val bytes = ByteArray(4_000) { 7 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(1), sampleTrack(2))

        coordinator.enableWatchAndSync(90, "Cancelable Playlist", tracks)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val cancelledCount = manager.cancelAllJobs()
        holdLatch.countDown()
        assertEquals(2, cancelledCount)

        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(1L).state)
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(2L).state)
        assertTrue("Playlist must remain watched after user cancel", coordinator.isPlaylistWatched(90))
        assertFalse("Coordinator must not auto-reenqueue on cancel", manager.hasActiveJobs())

        // Later explicit "Đồng bộ ngay" re-enqueues the 2 cancelled tracks
        val doneLatch = CountDownLatch(2)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                doneLatch.countDown()
            }
        })
        val res = coordinator.reconcilePlaylist(90, "Cancelable Playlist", tracks)
        assertEquals(2, res.enqueuedCount)
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(90, "Cancelable Playlist", tracks).state)
    }

    /**
     * Section 52: Disable Watch Preserves Local Files
     * Watched playlist with downloaded tracks.
     * Disable "Luôn giữ offline":
     * - playlist ID removed from watched set
     * - all OFFLINE_READY files and metadata remain intact
     */
    @Test
    fun test52_disableWatchPreservesLocalFiles() {
        seedOfflineReadyTrack(601L)
        seedOfflineReadyTrack(602L)
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(601), sampleTrack(602))

        coordinator.enableWatchAndSync(95, "Keep Offline Files", tracks)
        assertTrue(coordinator.isPlaylistWatched(95))

        val disabled = coordinator.disableWatch(95)
        assertTrue(disabled)
        assertFalse(coordinator.isPlaylistWatched(95))
        assertTrue(manager.isOfflineReady(601L))
        assertTrue(manager.isOfflineReady(602L))
        assertTrue(File(storeDir, "track_601.flac").exists())
        assertTrue(File(storeDir, "track_602.flac").exists())

        val status = coordinator.getPlaylistSyncStatus(95, "Keep Offline Files", tracks)
        assertEquals(PlaylistSyncState.OFF, status.state)
        assertEquals("Luôn giữ offline: Tắt", status.toggleLabel)
    }

    /**
     * Section 53: Process Restart Recovery
     * Enable watch on playlist 77.
     * Start syncing 3 tracks; complete track 1, leave 2 and 3 pending.
     * Simulate process restart:
     * - watched playlist 77 still persisted
     * - track 1 remains OFFLINE_READY
     * - tracks 2 and 3 recover/continue cleanly without re-downloading track 1
     */
    @Test
    fun test53_processRestartRecovery() {
        val track1DoneLatch = CountDownLatch(1)
        val track2StartedLatch = CountDownLatch(1)
        val holdTrack2Latch = CountDownLatch(1)

        val manager1 = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 702) {
                    track2StartedLatch.countDown()
                    holdTrack2Latch.await(5, TimeUnit.SECONDS)
                }
                val bytes = ByteArray(4_000) { 8 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager1.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 701L) track1DoneLatch.countDown()
            }
        })

        val coordinator1 = OfflinePlaylistSyncCoordinator(downloadManager = manager1)
        val tracks = listOf(sampleTrack(701), sampleTrack(702), sampleTrack(703))
        coordinator1.enableWatchAndSync(77, "Persisted Playlist 77", tracks)
        assertTrue(track1DoneLatch.await(5, TimeUnit.SECONDS))
        assertTrue(track2StartedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager1.isOfflineReady(701L))

        // Simulate process restart with new Store, new PinnedDownloadManager, new Coordinator
        val restartedDownloads = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val remainingLatch = CountDownLatch(2)
        val startRecoveryLatch = CountDownLatch(1)
        val store2 = OfflineMetadataStore(storeDir)
        val manager2 = PinnedDownloadManager(
            store = store2,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                startRecoveryLatch.await(5, TimeUnit.SECONDS)
                restartedDownloads.add(track.id)
                val bytes = ByteArray(4_000) { 9 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager2.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                remainingLatch.countDown()
            }
        })
        startRecoveryLatch.countDown()

        val coordinator2 = OfflinePlaylistSyncCoordinator(downloadManager = manager2)
        assertTrue("Playlist 77 must remain watched across process restart", coordinator2.isPlaylistWatched(77))
        assertEquals("Persisted Playlist 77", coordinator2.getWatchedPlaylistRecord(77)?.displayName)
        assertTrue("Track 701 must remain OFFLINE_READY", manager2.isOfflineReady(701L))

        // Restore queue or reconcile playlist on startup
        manager2.restoreQueueState()
        coordinator2.reconcilePlaylist(77, "Persisted Playlist 77", tracks)

        assertTrue(remainingLatch.await(5, TimeUnit.SECONDS))
        assertFalse("Track 701 must NOT be re-downloaded", restartedDownloads.contains(701))
        assertEquals(listOf(702, 703), restartedDownloads)
        assertEquals(PlaylistSyncState.SYNCED, coordinator2.getPlaylistSyncStatus(77, "Persisted Playlist 77", tracks).state)

        holdTrack2Latch.countDown()
    }

    /**
     * Section 54 & 55: Remote Streaming Regression & v0.6.25 Network Policy Regression
     * - While playlist sync is WIFI_ONLY and waiting for Wi-Fi on cellular, normal remote streaming URI is unaffected.
     * - Wi-Fi + Tailscale/VPN allowed under WIFI_ONLY; cellular + VPN blocked under WIFI_ONLY.
     */
    @Test
    fun test54_and_55_remoteStreamingAndNetworkPolicyRegression() {
        val resolver = ControllableNetworkPolicyResolver(
            listOf(
                NetworkTransportInfo(
                    hasWifiTransport = false,
                    hasCellularTransport = true,
                    hasVpnTransport = true, // Cellular + VPN must still be blocked under WIFI_ONLY
                    hasInternetCapability = true,
                    isValidated = true,
                )
            )
        )
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
        )
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        val tracks = listOf(sampleTrack(801))

        coordinator.enableWatchAndSync(88, "Tailscale Test", tracks)
        assertFalse("Cellular + VPN must be blocked under WIFI_ONLY", manager.isNetworkAllowedForDownload())
        assertEquals(PlaylistSyncState.WAITING_FOR_WIFI, coordinator.getPlaylistSyncStatus(88, "Tailscale Test", tracks).state)

        // Verify remote streaming URI is completely unaffected by WIFI_ONLY download state
        val api = object : CarApiClient() {
            override fun probe(): String = "http://127.0.0.1:8790"
        }
        val streamUri = api.streamUri(801)
        assertTrue(streamUri.toString().contains("/v1/tracks/801/stream"))

        // Switch to Wi-Fi + Tailscale VPN -> must be ALLOWED under WIFI_ONLY
        resolver.transports = listOf(
            NetworkTransportInfo(
                hasWifiTransport = true,
                hasCellularTransport = false,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            ),
            NetworkTransportInfo(
                hasWifiTransport = false,
                hasCellularTransport = false,
                hasVpnTransport = true,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        assertTrue("Wi-Fi + Tailscale VPN must be allowed under WIFI_ONLY", manager.isNetworkAllowedForDownload())
    }

    /**
     * Section 14: Unsupported / Non-Downloadable Playlist Items
     * Playlist has 2 downloadable tracks and 1 unsupported track (e.g. status="unsupported" or device_local).
     * Verify:
     * - only 2 downloadable tracks enqueued
     * - unsupported track reported in status ("2/3 bài có thể tải đã offline" and "1 bài không thể tải offline")
     */
    @Test
    fun testUnsupportedTrackHandlingInPlaylistSync() {
        seedOfflineReadyTrack(901L)
        seedOfflineReadyTrack(902L)
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)

        val tracks = listOf(
            sampleTrack(901),
            sampleTrack(902),
            sampleTrack(903, status = "unsupported"),
        )

        val res = coordinator.enableWatchAndSync(99, "Mixed Playlist", tracks)
        assertEquals(0, res.enqueuedCount)
        assertEquals(2, res.classification.eligibleCount)
        assertEquals(1, res.classification.unsupportedCount)
        assertEquals(PlaylistSyncState.PARTIAL_UNSUPPORTED, res.status.state)
        assertEquals("2/3 bài có thể tải đã offline", res.status.statusText)
        assertEquals("1 bài không thể tải offline", res.status.secondaryStatusText)
    }
}
