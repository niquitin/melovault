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
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BulkDownloadTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

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
        storeDir = tempFolder.newFolder("bulk_download_test_offline")
        store = OfflineMetadataStore(storeDir)
    }

    private fun sampleTrack(
        id: Int,
        title: String = "Track $id",
        size: Long = 10_000L,
    ): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            status = "ready",
            duration = 180.0,
            sourceSizeBytes = size,
            sourceFormat = "flac",
        )
    }

    private fun createPreExistingOfflineTrack(trackId: Long, sizeBytes: Long = 5000L) {
        val finalFile = File(storeDir, "track_${trackId}.flac")
        finalFile.writeBytes(ByteArray(sizeBytes.toInt()) { 7 })
        val record = OfflineTrackRecord(
            trackId = trackId,
            title = "Offline Track $trackId",
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            relativeFileName = "track_${trackId}.flac",
            fileSizeBytes = sizeBytes,
            sha256 = "dummy_sha256_${trackId}",
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            analysisVersion = null,
            downloadedAtMs = System.currentTimeMillis(),
        )
        store.saveRecord(record)
    }

    /**
     * 1. Classification rules:
     * 10 tracks: 3 offline, 2 queued, 1 downloading, 4 new -> 4 enqueued.
     */
    @Test
    fun testClassificationRules() {
        // Create 3 already offline tracks
        createPreExistingOfflineTrack(1L)
        createPreExistingOfflineTrack(2L)
        createPreExistingOfflineTrack(3L)

        val trackHoldingLatch = CountDownLatch(1)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 4) {
                    trackHoldingLatch.await(5, TimeUnit.SECONDS)
                }
                Pair(ByteArrayInputStream(ByteArray(1000)), 1000L)
            }
        )

        // Track 4 starts downloading, 5 and 6 queued
        manager.startDownload(sampleTrack(4))
        Thread.sleep(50) // Allow worker thread to pick up track 4
        manager.startDownload(sampleTrack(5))
        manager.startDownload(sampleTrack(6))

        // Tracks 1..10
        val all10Tracks = (1..10).map { sampleTrack(it) }
        val result = manager.classifyTracksForDownload(all10Tracks)

        assertEquals(10, result.totalTracks)
        assertEquals(3, result.alreadyOfflineTracks.size)
        assertTrue(result.alreadyOfflineTracks.any { it.id == 1 })
        assertTrue(result.alreadyOfflineTracks.any { it.id == 2 })
        assertTrue(result.alreadyOfflineTracks.any { it.id == 3 })

        assertEquals(3, result.inProgressTracks.size)
        assertTrue(result.inProgressTracks.any { it.id == 4 })
        assertTrue(result.inProgressTracks.any { it.id == 5 })
        assertTrue(result.inProgressTracks.any { it.id == 6 })

        assertEquals(4, result.eligibleNewTracks.size)
        assertTrue(result.eligibleNewTracks.any { it.id == 7 })
        assertTrue(result.eligibleNewTracks.any { it.id == 8 })
        assertTrue(result.eligibleNewTracks.any { it.id == 9 })
        assertTrue(result.eligibleNewTracks.any { it.id == 10 })

        trackHoldingLatch.countDown()
    }

    /**
     * 2. Duplicate track IDs in input list are deduplicated.
     */
    @Test
    fun testDuplicateTrackIdsDeduplicated() {
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(1000)), 1000L) }
        )

        val tracksWithDuplicates = listOf(
            sampleTrack(101),
            sampleTrack(102),
            sampleTrack(101),
            sampleTrack(103),
            sampleTrack(102),
        )

        val classification = manager.classifyTracksForDownload(tracksWithDuplicates)
        assertEquals(3, classification.totalTracks)
        assertEquals(3, classification.eligibleNewTracks.size)

        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "test_playlist",
            sourceDisplayName = "Test Playlist",
            trackIds = tracksWithDuplicates.map { it.id.toLong() },
            initialEligibleCount = classification.eligibleNewTracks.size,
            knownTotalBytes = classification.knownTotalBytes,
            hasUnknownSizes = classification.unknownSizeCount > 0,
        )

        val enqueued = manager.enqueueBatch(batch, classification.eligibleNewTracks)
        assertEquals(3, enqueued)
    }

    /**
     * 3. Retry eligibility for FAILED and CANCELLED tracks.
     */
    @Test
    fun testRetryEligibilityForFailedAndCancelled() {
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 201) {
                    throw IOException("Network error")
                }
                Pair(ByteArrayInputStream(ByteArray(1000)), 1000L)
            }
        )

        val completedLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                if (trackId == 201L) completedLatch.countDown()
            }
        })

        // Track 201 will fail
        manager.startDownload(sampleTrack(201))
        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(201L).state)

        // Track 202 is cancelled
        manager.startDownload(sampleTrack(202))
        manager.cancelDownload(202L)
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(202L).state)

        // Classify tracks 201, 202, and 203
        val tracks = listOf(sampleTrack(201), sampleTrack(202), sampleTrack(203))
        val classification = manager.classifyTracksForDownload(tracks)

        assertEquals(3, classification.totalTracks)
        assertEquals(0, classification.alreadyOfflineTracks.size)
        assertEquals(0, classification.inProgressTracks.size)
        // Both FAILED and CANCELLED are re-enqueue eligible!
        assertEquals(3, classification.eligibleNewTracks.size)
        assertTrue(classification.eligibleNewTracks.any { it.id == 201 })
        assertTrue(classification.eligibleNewTracks.any { it.id == 202 })
        assertTrue(classification.eligibleNewTracks.any { it.id == 203 })
    }

    /**
     * 4. Sequential execution and strict concurrency = 1.
     */
    @Test
    fun testSequentialExecutionAndStrictConcurrencyOne() {
        val activeExecutions = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)
        val executionOrder = Collections.synchronizedList(mutableListOf<Int>())
        val allDoneLatch = CountDownLatch(4)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            val cur = activeExecutions.incrementAndGet()
            var prevMax = maxConcurrent.get()
            while (cur > prevMax) {
                if (maxConcurrent.compareAndSet(prevMax, cur)) break
                prevMax = maxConcurrent.get()
            }

            Thread.sleep(50)
            executionOrder.add(track.id)
            activeExecutions.decrementAndGet()
            Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                allDoneLatch.countDown()
            }
        })

        val tracks = listOf(sampleTrack(301), sampleTrack(302), sampleTrack(303), sampleTrack(304))
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "p1",
            sourceDisplayName = "Playlist",
            trackIds = tracks.map { it.id.toLong() },
            initialEligibleCount = 4,
            knownTotalBytes = 8000L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)
        assertTrue(allDoneLatch.await(10, TimeUnit.SECONDS))

        assertEquals(1, maxConcurrent.get())
        assertEquals(listOf(301, 302, 303, 304), executionOrder)
    }

    /**
     * 5. Failure continuation: when a track fails, the queue continues with next track.
     */
    @Test
    fun testFailureContinuation() {
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            if (track.id == 402) {
                throw IOException("Simulated network timeout for track 402")
            }
            Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        val latch401 = CountDownLatch(1)
        val latch402 = CountDownLatch(1)
        val latch403 = CountDownLatch(1)

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 401L) latch401.countDown()
                if (trackId == 403L) latch403.countDown()
            }
            override fun onDownloadFailed(trackId: Long, error: String) {
                if (trackId == 402L) latch402.countDown()
            }
        })

        val tracks = listOf(sampleTrack(401), sampleTrack(402), sampleTrack(403))
        val batch = DownloadBatch(
            sourceType = "ALBUM",
            sourceId = "a1",
            sourceDisplayName = "Album 1",
            trackIds = tracks.map { it.id.toLong() },
            initialEligibleCount = 3,
            knownTotalBytes = 6000L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)

        assertTrue("Track 401 should complete", latch401.await(5, TimeUnit.SECONDS))
        assertTrue("Track 402 should fail", latch402.await(5, TimeUnit.SECONDS))
        assertTrue("Track 403 should complete despite 402 failure", latch403.await(5, TimeUnit.SECONDS))

        assertTrue(manager.isOfflineReady(401L))
        assertFalse(manager.isOfflineReady(402L))
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(402L).state)
        assertTrue(manager.isOfflineReady(403L))
    }

    /**
     * 6. Cancel pending queue: cancels only QUEUED jobs; active transfer continues.
     */
    @Test
    fun testCancelPendingQueueLeavesActiveDownloading() {
        val holdLatch = CountDownLatch(1)
        val track501StartedLatch = CountDownLatch(1)
        val track501DoneLatch = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            if (track.id == 501) {
                track501StartedLatch.countDown()
                holdLatch.await(5, TimeUnit.SECONDS)
            }
            Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 501L) track501DoneLatch.countDown()
            }
        })

        val tracks = listOf(sampleTrack(501), sampleTrack(502), sampleTrack(503))
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "p5",
            sourceDisplayName = "Playlist 5",
            trackIds = tracks.map { it.id.toLong() },
            initialEligibleCount = 3,
            knownTotalBytes = 6000L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)
        assertTrue("Track 501 should enter downloading", track501StartedLatch.await(5, TimeUnit.SECONDS))

        val cancelledCount = manager.cancelPendingQueue()
        assertEquals(2, cancelledCount) // 502 and 503 cancelled

        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(502L).state)
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(503L).state)

        // Release track 501 to finish
        holdLatch.countDown()
        assertTrue("Track 501 must complete successfully", track501DoneLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(501L))
    }

    /**
     * 7. Cancel all jobs: cancels active + queued jobs; completed tracks are never deleted.
     */
    @Test
    fun testCancelAllJobsDoesNotDeleteCompletedTracks() {
        // First track 601 is completed
        val track601DoneLatch = CountDownLatch(1)
        val holdLatch = CountDownLatch(1)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 602) {
                    holdLatch.await(5, TimeUnit.SECONDS)
                }
                Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
            }
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 601L) track601DoneLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(601))
        assertTrue(track601DoneLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(601L))

        // Now enqueue 602 and 603
        val tracks = listOf(sampleTrack(602), sampleTrack(603))
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "p6",
            sourceDisplayName = "Playlist 6",
            trackIds = listOf(601L, 602L, 603L),
            initialEligibleCount = 2,
            knownTotalBytes = 4000L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)
        Thread.sleep(50) // Track 602 is DOWNLOADING

        val cancelledCount = manager.cancelAllJobs()
        assertTrue(cancelledCount >= 2)

        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(602L).state)
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(603L).state)

        // Completed track 601 MUST REMAIN intact
        assertTrue("Completed track 601 must not be deleted on cancelAllJobs", manager.isOfflineReady(601L))
        assertNotNull(manager.getRecord(601L))
    }

    /**
     * 8. Batch progress tracking.
     */
    @Test
    fun testBatchProgressTracking() {
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            Pair(ByteArrayInputStream(ByteArray(5000)), 5000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        val doneLatch = CountDownLatch(3)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                doneLatch.countDown()
            }
        })

        val tracks = listOf(sampleTrack(701, size = 5000L), sampleTrack(702, size = 5000L), sampleTrack(703, size = 5000L))
        val batch = DownloadBatch(
            sourceType = "PLAYLIST",
            sourceId = "p7",
            sourceDisplayName = "Batch 7",
            trackIds = tracks.map { it.id.toLong() },
            initialEligibleCount = 3,
            knownTotalBytes = 15000L,
            hasUnknownSizes = false,
        )

        manager.enqueueBatch(batch, tracks)
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))

        val progress = manager.getActiveBatchProgress()
        assertNotNull(progress)
        assertEquals(3, progress?.totalTracks)
        assertEquals(3, progress?.completedTracks)
        assertEquals(0, progress?.inProgressTracks)
        assertEquals(0, progress?.failedTracks)
        assertEquals(0, progress?.cancelledTracks)
        assertEquals(15000L, progress?.downloadedBytes)
    }

    /**
     * 9. Process restart queue restoration and orphan part cleanup.
     */
    @Test
    fun testProcessRestartQueueRestoration() {
        // Create an orphan .part file in storage
        val orphanPart = File(storeDir, "track_899.part")
        orphanPart.writeBytes(ByteArray(100) { 1 })
        assertTrue(orphanPart.exists())

        // Create manager instance
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
        }

        val managerA = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        // Orphan part file should be cleaned on startup
        assertFalse("Orphan part file must be cleaned on init", orphanPart.exists())

        // Save a mock download_queue_state.json simulating process killed with jobs queued
        val stateFile = File(storeDir, "download_queue_state.json")
        val json = """
            {
              "batch": {
                "batch_id": "download_batch:restore_test",
                "source_type": "PLAYLIST",
                "source_id": "p8",
                "source_display_name": "Restored Playlist",
                "created_at": 1720000000000,
                "track_ids": [801,802],
                "initial_eligible_count": 2,
                "known_total_bytes": 4000,
                "has_unknown_sizes": false
              },
              "jobs": [
                {
                  "track_id": 801,
                  "title": "Track 801",
                  "artist": "Artist 801",
                  "album": "Album 801",
                  "duration": 180.0,
                  "source_size_bytes": 2000,
                  "source_format": "flac",
                  "batch_id": "download_batch:restore_test",
                  "enqueued_at_ms": 1720000000000
                },
                {
                  "track_id": 802,
                  "title": "Track 802",
                  "artist": "Artist 802",
                  "album": "Album 802",
                  "duration": 180.0,
                  "source_size_bytes": 2000,
                  "source_format": "flac",
                  "batch_id": "download_batch:restore_test",
                  "enqueued_at_ms": 1720000000000
                }
              ]
            }
        """.trimIndent()
        stateFile.writeText(json)

        val streamGate = CountDownLatch(1)
        val managerBStreamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            streamGate.await(5, TimeUnit.SECONDS)
            Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
        }

        val restoredLatch = CountDownLatch(2)
        val managerB = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = managerBStreamOpener,
        )

        managerB.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                restoredLatch.countDown()
            }
        })

        // Release the stream opener gate so jobs can download
        streamGate.countDown()

        // On init, managerB restored the 2 jobs from JSON and executed them
        assertTrue("Restored jobs should complete", restoredLatch.await(5, TimeUnit.SECONDS))
        assertTrue(managerB.isOfflineReady(801L))
        assertTrue(managerB.isOfflineReady(802L))
    }

    /**
     * 10. Storage precheck and runtime ENOSPC handling.
     */
    @Test
    fun testStoragePrecheckAndENOSPC() {
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 902) {
                    throw IOException("write failed: ENOSPC (No space left on device)")
                }
                Pair(ByteArrayInputStream(ByteArray(2000)), 2000L)
            }
        )

        // Storage precheck
        val available = manager.getAvailableStorageBytes()
        assertTrue(available > 0L)
        assertTrue(manager.hasSufficientStorage(1000L))
        assertFalse(manager.hasSufficientStorage(Long.MAX_VALUE))

        // Runtime ENOSPC
        val failedLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                if (trackId == 902L) failedLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(902))
        assertTrue(failedLatch.await(5, TimeUnit.SECONDS))

        val status = manager.getDownloadStatus(902L)
        assertEquals(DownloadState.FAILED, status.state)
        assertEquals("Không đủ dung lượng bộ nhớ", status.errorMessage)

        // Part file must be cleaned up
        val partFile = File(storeDir, "track_902.part")
        assertFalse(partFile.exists())
    }

    /**
     * 11. Safe playlist offline delete: removes local pinned files only.
     */
    @Test
    fun testDeleteOfflinePlaylist() {
        // Create 3 offline tracks
        createPreExistingOfflineTrack(1001L)
        createPreExistingOfflineTrack(1002L)
        createPreExistingOfflineTrack(1003L)
        createPreExistingOfflineTrack(1004L) // Belongs to different playlist

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
        )

        assertTrue(manager.isOfflineReady(1001L))
        assertTrue(manager.isOfflineReady(1002L))
        assertTrue(manager.isOfflineReady(1003L))
        assertTrue(manager.isOfflineReady(1004L))

        // Delete playlist containing 1001, 1002, 1003
        val deleted = manager.deleteOfflinePlaylist(listOf(1001L, 1002L, 1003L))
        assertEquals(3, deleted)

        assertFalse(manager.isOfflineReady(1001L))
        assertFalse(manager.isOfflineReady(1002L))
        assertFalse(manager.isOfflineReady(1003L))
        assertTrue("Track 1004 from different playlist must remain offline", manager.isOfflineReady(1004L))
    }
}
