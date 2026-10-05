package com.bsdung.musicassistantcar.playback

import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DownloadProgressManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("dl_progress_test_dir")
        store = OfflineMetadataStore(storeDir)
    }

    private fun createCarTrack(
        id: Int,
        title: String = "Test Track $id",
        size: Long? = 5000L,
    ): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            status = "ready",
            duration = 180.0,
            sourceSizeBytes = size,
            sourceFormat = "mp3",
        )
    }

    /**
     * 1. Deterministic fake-source test A: known total size
     * Monotonic progress, 0 <= progress <= 100, final 100 only after full bytes received
     */
    @Test
    fun testKnownLengthProgress_monotonic_0to100() {
        val totalBytes = 10_000L
        val testData = ByteArray(totalBytes.toInt()) { (it % 256).toByte() }
        val progressList = mutableListOf<Pair<Long, Long>>()
        val completionLatch = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(testData), totalBytes)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
                progressList.add(Pair(bytesDownloaded, totalBytes))
            }

            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        val track = createCarTrack(1001, size = totalBytes)
        manager.startDownload(track)

        assertTrue("Download should complete within timeout", completionLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Should have recorded progress events", progressList.isNotEmpty())

        var lastBytes = 0L
        for ((bytes, total) in progressList) {
            assertEquals("Reported total bytes must match", totalBytes, total)
            assertTrue("Progress must be monotonic non-decreasing: $lastBytes <= $bytes", bytes >= lastBytes)
            assertTrue("Progress must be <= totalBytes", bytes <= totalBytes)
            lastBytes = bytes
        }

        assertTrue("Track must be OFFLINE_READY after 100% full transfer", manager.isOfflineReady(1001L))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(1001L).state)
        assertEquals(totalBytes, manager.getDownloadStatus(1001L).bytesDownloaded)
    }

    /**
     * 2. Deterministic fake-source test B: unknown Content-Length
     * Indeterminate progress, downloaded byte counter increases, no fake %
     */
    @Test
    fun testUnknownLengthProgress_indeterminate_noFakePercent() {
        val totalBytes = 8_000L
        val testData = ByteArray(totalBytes.toInt()) { (it % 256).toByte() }
        val progressList = mutableListOf<Pair<Long, Long>>()
        val completionLatch = CountDownLatch(1)

        // Stream opener reports null content length and track has null sourceSizeBytes
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(testData), null)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
                progressList.add(Pair(bytesDownloaded, totalBytes))
            }

            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        val track = createCarTrack(1002, size = null)
        manager.startDownload(track)

        assertTrue("Download should complete", completionLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Progress events recorded", progressList.isNotEmpty())

        for ((bytes, total) in progressList) {
            assertEquals("Total bytes must be 0 for unknown Content-Length (no fake total)", 0L, total)
            assertTrue("Downloaded bytes counter must be non-negative", bytes >= 0L)
        }

        assertTrue("Should be offline ready after completion", manager.isOfflineReady(1002L))
        val status = manager.getDownloadStatus(1002L)
        assertEquals(DownloadState.OFFLINE_READY, status.state)
        assertEquals(totalBytes, status.bytesDownloaded)
    }

    /**
     * 3. Queue serialization and concurrency = 1
     * Additional downloads remain QUEUED. Deterministic FIFO ordering.
     */
    @Test
    fun testQueueSerialization_andConcurrencyOne() {
        val startLatchA = CountDownLatch(1)
        val proceedLatchA = CountDownLatch(1)
        val completeLatchA = CountDownLatch(1)
        val completeLatchB = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            if (track.id == 2001) {
                startLatchA.countDown()
                proceedLatchA.await(5, TimeUnit.SECONDS)
            }
            Pair(ByteArrayInputStream(ByteArray(1000) { 0x11 }), 1000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 2001L) completeLatchA.countDown()
                if (trackId == 2002L) completeLatchB.countDown()
            }
        })

        val trackA = createCarTrack(2001)
        val trackB = createCarTrack(2002)

        manager.startDownload(trackA)
        assertTrue("Track A should start execution", startLatchA.await(5, TimeUnit.SECONDS))

        // Now enqueue Track B while Track A is still downloading
        manager.startDownload(trackB)

        // Track B MUST be QUEUED because concurrency = 1
        assertEquals("Track A must be DOWNLOADING", DownloadState.DOWNLOADING, manager.getDownloadStatus(2001L).state)
        assertEquals("Track B must be QUEUED", DownloadState.QUEUED, manager.getDownloadStatus(2002L).state)
        assertFalse("Track B must not be marked downloading while A is active", manager.getDownloadStatus(2002L).state == DownloadState.DOWNLOADING)

        // Release Track A
        proceedLatchA.countDown()

        assertTrue("Track A completes", completeLatchA.await(5, TimeUnit.SECONDS))
        assertTrue("Track B subsequently completes", completeLatchB.await(5, TimeUnit.SECONDS))

        assertTrue("Track A offline ready", manager.isOfflineReady(2001L))
        assertTrue("Track B offline ready", manager.isOfflineReady(2002L))
    }

    /**
     * 4. Cancel active download:
     * Stops network transfer, cleans .part, no metadata created, OFFLINE_READY remains false.
     */
    @Test
    fun testCancelActiveDownload() {
        val startLatch = CountDownLatch(1)
        val cancelLatch = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            val infiniteStream = object : InputStream() {
                @Volatile var isClosed = false
                override fun read(): Int {
                    startLatch.countDown()
                    if (isClosed) throw IOException("Stream closed")
                    Thread.sleep(10)
                    return 0x42
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    startLatch.countDown()
                    if (isClosed) throw IOException("Stream closed")
                    Thread.sleep(10)
                    b[off] = 0x42
                    return 1
                }
                override fun close() {
                    isClosed = true
                }
            }
            Pair(infiniteStream, 100_000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCancelled(trackId: Long) {
                cancelLatch.countDown()
            }
        })

        val track = createCarTrack(3001)
        manager.startDownload(track)

        assertTrue("Download should start", startLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.DOWNLOADING, manager.getDownloadStatus(3001L).state)

        // Verify .part file exists during download
        val partFile = File(storeDir, "track_3001.part")
        assertTrue("Part file must exist while downloading", partFile.exists())

        // Cancel
        val cancelled = manager.cancelDownload(3001L)
        assertTrue("cancelDownload should return true", cancelled)

        assertTrue("onDownloadCancelled should fire", cancelLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(3001L).state)
        assertFalse(".part file must be cleaned upon cancellation", partFile.exists())
        assertNull("No metadata record should be saved", store.getRecord(3001L))
        assertFalse("OFFLINE_READY must be false", manager.isOfflineReady(3001L))
    }

    /**
     * 5. Cancel queued download:
     * Transitions queued job to CANCELLED without starting.
     */
    @Test
    fun testCancelQueuedDownload() {
        val blockLatch = CountDownLatch(1)
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            if (track.id == 4001) {
                blockLatch.await(5, TimeUnit.SECONDS)
            }
            Pair(ByteArrayInputStream(ByteArray(500)), 500L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        val trackA = createCarTrack(4001)
        val trackB = createCarTrack(4002)

        manager.startDownload(trackA)
        manager.startDownload(trackB)

        assertEquals(DownloadState.QUEUED, manager.getDownloadStatus(4002L).state)

        manager.cancelDownload(4002L)
        assertEquals(DownloadState.CANCELLED, manager.getDownloadStatus(4002L).state)

        blockLatch.countDown()
        assertFalse("Track B was cancelled so must never become offline ready", manager.isOfflineReady(4002L))
    }

    /**
     * 6. Retry: FAILED/CANCELLED -> QUEUED -> DOWNLOADING -> OFFLINE_READY
     */
    @Test
    fun testRetryFailedOrCancelledJob() {
        var shouldFail = true
        val retryLatch = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            if (shouldFail) {
                throw IOException("Simulated network drop")
            }
            Pair(ByteArrayInputStream(ByteArray(1000) { 0x55 }), 1000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                retryLatch.countDown()
            }
        })

        val track = createCarTrack(5001)
        manager.startDownload(track)

        Thread.sleep(200)
        assertEquals(DownloadState.FAILED, manager.getDownloadStatus(5001L).state)
        assertFalse(manager.isOfflineReady(5001L))

        // Now fix network and retry
        shouldFail = false
        val retried = manager.retryDownload(5001L)
        assertTrue("retryDownload should return true", retried)

        assertTrue("Retried download must complete", retryLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Track must be offline ready after successful retry", manager.isOfflineReady(5001L))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(5001L).state)
    }

    /**
     * 7. Orphan .part files cleaned on startup
     */
    @Test
    fun testOrphanPartCleanedOnStartup() {
        // Simulate orphan .part files from previous crash
        val orphan1 = File(storeDir, "track_9001.part")
        val orphan2 = File(storeDir, "track_9002.part")
        orphan1.writeBytes(ByteArray(100) { 0x11 })
        orphan2.writeBytes(ByteArray(200) { 0x22 })

        assertTrue(orphan1.exists())
        assertTrue(orphan2.exists())

        // Initializing manager cleans orphan parts
        val manager = PinnedDownloadManager(store)

        assertFalse("Orphan 1 must be deleted on startup", orphan1.exists())
        assertFalse("Orphan 2 must be deleted on startup", orphan2.exists())
        assertFalse(manager.isOfflineReady(9001L))
        assertFalse(manager.isOfflineReady(9002L))
    }

    /**
     * 8. Restart recovery: completed pinned downloads survive restart
     */
    @Test
    fun testRestartRecovery_completedDownloadsPersist() {
        val totalBytes = 2048L
        val testData = ByteArray(totalBytes.toInt()) { 0x33 }
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(testData), totalBytes)
        }

        val manager1 = PinnedDownloadManager(store = store, streamOpener = streamOpener)
        val completionLatch = CountDownLatch(1)
        manager1.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        manager1.startDownload(createCarTrack(6001, size = totalBytes))
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager1.isOfflineReady(6001L))

        // Simulate complete app process restart with new store and manager instances
        val store2 = OfflineMetadataStore(storeDir)
        val manager2 = PinnedDownloadManager(store2)

        assertTrue("Completed download must survive process restart", manager2.isOfflineReady(6001L))
        val record = manager2.getRecord(6001L)
        assertNotNull(record)
        assertEquals(totalBytes, record?.fileSizeBytes)
        assertEquals("Test Track 6001", record?.title)
    }

    /**
     * 9. Downloaded Music separation from active jobs
     */
    @Test
    fun testDownloadedMusicSeparation_fromActiveJobs() {
        val completedTrack = createCarTrack(7001, size = 1000L)
        val activeTrack = createCarTrack(7002, size = 2000L)

        val blockLatch = CountDownLatch(1)
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { track, _ ->
            if (track.id == 7002) {
                blockLatch.await(5, TimeUnit.SECONDS)
            }
            Pair(ByteArrayInputStream(ByteArray(1000) { 0x01 }), 1000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        val latchA = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                if (trackId == 7001L) latchA.countDown()
            }
        })

        // Download 7001 to completion
        manager.startDownload(completedTrack)
        assertTrue(latchA.await(5, TimeUnit.SECONDS))

        // Start 7002 in background
        manager.startDownload(activeTrack)

        val downloadedItems = manager.getAllDownloadedMediaItems()
        val activeJobItems = manager.getActiveJobMediaItems()

        // 7001 must be in downloadedItems, NOT in activeJobItems
        assertTrue("Downloaded list contains completed track", downloadedItems.any { it.mediaId == "track:7001" })
        assertFalse("Downloaded list must NOT contain active job", downloadedItems.any { it.mediaId == "track:7002" || it.mediaId == "download_job:7002" })

        // 7002 must be in activeJobItems, NOT in downloadedItems
        assertTrue("Active jobs contains active track", activeJobItems.any { it.mediaId == "download_job:7002" })
        assertFalse("Active jobs must NOT contain completed track", activeJobItems.any { it.mediaId == "download_job:7001" })

        blockLatch.countDown()
    }

    /**
     * 10. Downloads screen state model:
     * MediaItem has correct title, artist, status, byte counts
     */
    @Test
    fun testDownloadsScreenStateModel() {
        val track = createCarTrack(8001, title = "Job Track", size = 15000L)
        val blockLatch = CountDownLatch(1)

        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            blockLatch.await(5, TimeUnit.SECONDS)
            Pair(ByteArrayInputStream(ByteArray(15000)), 15000L)
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = streamOpener,
        )

        manager.startDownload(track)
        Thread.sleep(50)

        val activeJobs = manager.getActiveJobMediaItems()
        assertEquals(1, activeJobs.size)
        val jobItem = activeJobs[0]

        assertEquals("download_job:8001", jobItem.mediaId)
        assertEquals("Job Track", jobItem.mediaMetadata.title.toString())
        assertEquals("Artist 8001", jobItem.mediaMetadata.artist.toString())
        val extras = jobItem.mediaMetadata.extras
        assertNotNull(extras)
        assertEquals(8001L, extras?.getLong("track_id_long"))
        assertEquals(15000L, extras?.getLong("total_bytes"))
        assertTrue(extras?.getBoolean("is_download_job") == true)

        blockLatch.countDown()
    }

    /**
     * 11. Stream cache isolation: stream cache never enters OFFLINE_READY
     */
    @Test
    fun testStreamCacheIsolation_neverOfflineReady() {
        // Stream cache stores files in a separate directory or ExoPlayer SimpleCache
        val streamCacheDir = tempFolder.newFolder("stream_cache")
        val streamCacheFile = File(streamCacheDir, "stream_9999.cache")
        streamCacheFile.writeBytes(ByteArray(5000) { 0x99.toByte() })

        val manager = PinnedDownloadManager(store)
        assertFalse("Stream cache must NEVER claim OFFLINE_READY", manager.isOfflineReady(9999L))
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.getDownloadStatus(9999L).state)
    }

    /**
     * 12. Safe local delete:
     * Removes only pinned file, metadata, and .part. Never backend delete.
     */
    @Test
    fun testSafeLocalDelete() {
        val totalBytes = 1000L
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(ByteArray(totalBytes.toInt()) { 0x77 }), totalBytes)
        }

        val manager = PinnedDownloadManager(store = store, streamOpener = streamOpener)
        val latch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        manager.startDownload(createCarTrack(9999, size = totalBytes))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(9999L))

        val finalFile = File(storeDir, "track_9999.mp3")
        assertTrue("Final file exists before delete", finalFile.exists())

        // Perform safe delete
        val deleted = manager.deleteDownload(9999L)
        assertTrue("deleteDownload returns true", deleted)

        assertFalse("Final file must be deleted", finalFile.exists())
        assertNull("Metadata must be removed", store.getRecord(9999L))
        assertFalse("Must no longer be offline ready", manager.isOfflineReady(9999L))
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.getDownloadStatus(9999L).state)
    }

    /**
     * 13. No secrets persisted in offline metadata or MediaItem extras
     */
    @Test
    fun testNoSecretsPersistedInMetadataOrExtras() {
        val totalBytes = 500L
        val streamOpener: (CarTrack, CarApiClient) -> Pair<InputStream, Long?> = { _, _ ->
            Pair(ByteArrayInputStream(ByteArray(totalBytes.toInt()) { 0x12 }), totalBytes)
        }

        val manager = PinnedDownloadManager(store = store, streamOpener = streamOpener)
        val latch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        manager.startDownload(createCarTrack(1111, size = totalBytes))
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // Check offline_metadata.json on disk
        val metaFile = File(storeDir, "offline_metadata.json")
        assertTrue(metaFile.exists())
        val metaContent = metaFile.readText(Charsets.UTF_8).lowercase()

        assertFalse("Metadata must not contain 'bearer'", metaContent.contains("bearer"))
        assertFalse("Metadata must not contain 'token'", metaContent.contains("token"))
        assertFalse("Metadata must not contain 'authorization'", metaContent.contains("authorization"))

        // Check MediaItem extras
        val record = store.getRecord(1111L)!!
        val mediaItem = manager.toMediaItem(record)
        val extras = mediaItem.mediaMetadata.extras!!
        for (key in extras.keySet()) {
            val lowerKey = key.lowercase()
            assertFalse("Extras key must not contain secret tokens: $key", lowerKey.contains("token") || lowerKey.contains("auth"))
        }
    }
}
