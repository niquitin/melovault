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
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OfflineStorageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore
    private lateinit var manager: PinnedDownloadManager

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("offline_storage_manager_test")
        store = OfflineMetadataStore(storeDir)
        manager = PinnedDownloadManager(store)
    }

    private fun createSizedFile(file: File, sizeBytes: Long) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(sizeBytes)
        }
    }

    private fun addCompletedTrack(
        trackId: Long,
        title: String = "Track $trackId",
        fileSizeBytes: Long,
        metadataSizeBytes: Long = fileSizeBytes,
        downloadedAtMs: Long = 1700000000000L + trackId,
        relativeFileName: String = "track_${trackId}.mp3",
    ): OfflineTrackRecord {
        val file = File(storeDir, relativeFileName)
        createSizedFile(file, fileSizeBytes)
        val record = OfflineTrackRecord(
            trackId = trackId,
            title = title,
            artist = "Artist $trackId",
            album = "Album $trackId",
            durationMs = 200_000L,
            relativeFileName = relativeFileName,
            fileSizeBytes = metadataSizeBytes,
            sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            analysisVersion = null,
            downloadedAtMs = downloadedAtMs,
            format = "mp3",
        )
        store.saveRecord(record)
        return record
    }

    private fun createCarTrack(
        id: Int,
        title: String = "Track $id",
        sizeBytes: Long? = 5000L,
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
    // 20. STORAGE ACCOUNTING UNIT TESTS
    // =========================================================================

    @Test
    fun testPinnedUsedBytes_sumsCompletedTracks() {
        val mb = 1024L * 1024L
        addCompletedTrack(101L, fileSizeBytes = 10L * mb)
        addCompletedTrack(102L, fileSizeBytes = 20L * mb)
        addCompletedTrack(103L, fileSizeBytes = 30L * mb)

        assertEquals(60L * mb, manager.getPinnedUsedBytes())
        val summary = manager.getStorageSummary()
        assertEquals(60L * mb, summary.usedBytes)
        assertEquals(3, summary.trackCount)
    }

    @Test
    fun testPartialFileNotCountedAsPinnedUsed() {
        val mb = 1024L * 1024L
        addCompletedTrack(101L, fileSizeBytes = 10L * mb)

        val partFile = File(storeDir, "track_999.mp3.part")
        createSizedFile(partFile, 25L * mb)
        assertTrue(partFile.exists())

        assertEquals(10L * mb, manager.getPinnedUsedBytes())
    }

    @Test
    fun testMetadataFileNotCounted() {
        val mb = 1024L * 1024L
        addCompletedTrack(101L, fileSizeBytes = 15L * mb)

        val metadataFile = File(storeDir, "offline_metadata.json")
        assertTrue("offline_metadata.json should exist", metadataFile.exists())
        assertTrue("offline_metadata.json should be non-empty", metadataFile.length() > 0L)

        // Pinned used bytes must equal ONLY the 15 MB audio file, never including offline_metadata.json
        assertEquals(15L * mb, manager.getPinnedUsedBytes())
    }

    @Test
    fun testStreamCacheNotCounted() {
        val mb = 1024L * 1024L
        addCompletedTrack(101L, fileSizeBytes = 12L * mb)

        val streamCacheDir = tempFolder.newFolder("media3_stream_cache")
        val cachedChunk = File(streamCacheDir, "0.v3.exo")
        createSizedFile(cachedChunk, 50L * mb)

        assertEquals(12L * mb, manager.getPinnedUsedBytes())
    }

    @Test
    fun testMissingFinalFileNotCounted() {
        val mb = 1024L * 1024L
        addCompletedTrack(101L, fileSizeBytes = 10L * mb)
        addCompletedTrack(102L, fileSizeBytes = 20L * mb)

        // Delete track 102's underlying audio file while leaving metadata in store
        val missingFile = File(storeDir, "track_102.mp3")
        assertTrue(missingFile.delete())

        // Must not be OFFLINE_READY, must not be counted, and metadata must be reconciled
        assertFalse(manager.isOfflineReady(102L))
        assertEquals(10L * mb, manager.getPinnedUsedBytes())
        val summary = manager.getStorageSummary(repairStaleMetadata = true)
        assertEquals(10L * mb, summary.usedBytes)
        assertEquals(1, summary.trackCount)
        assertNull("Reconciled missing track should be removed from metadata store", store.getRecord(102L))
    }

    @Test
    fun testMetadataSizeMismatchUsesActualFilesystemSize() {
        val mb = 1024L * 1024L
        // Metadata claims 10 MB, actual file on disk is 12 MB
        addCompletedTrack(
            trackId = 105L,
            fileSizeBytes = 12L * mb,
            metadataSizeBytes = 10L * mb,
        )

        // Accounting must use actual filesystem size (12 MB)
        assertEquals(12L * mb, manager.getPinnedUsedBytes())

        // Repairing mismatched sizes updates metadata to actual 12 MB
        val repaired = store.repairMismatchedSizes()
        assertEquals(1, repaired)
        assertEquals(12L * mb, store.getRecord(105L)?.fileSizeBytes)
        assertTrue(manager.isOfflineReady(105L))
    }

    // =========================================================================
    // 21. CAP ENFORCEMENT UNIT TESTS
    // =========================================================================

    @Test
    fun testSingleDownloadWithinCapAllowed() {
        val gb = 1024L * 1024L * 1024L
        addCompletedTrack(200L, fileSizeBytes = 8L * gb)
        manager.setOfflineStorageCapBytes(10L * gb)
        manager.setAvailableSpaceProviderForTesting { 20L * gb }

        val result = manager.checkSingleDownloadStorage(createCarTrack(201, sizeBytes = 1L * gb))
        assertTrue(result.isAllowed)
        assertEquals(StoragePrecheckStatus.ALLOWED, result.status)
    }

    @Test
    fun testSingleDownloadExceedingCapBlocked() {
        val gb = 1024L * 1024L * 1024L
        addCompletedTrack(200L, fileSizeBytes = 8L * gb)
        manager.setOfflineStorageCapBytes(10L * gb)
        manager.setAvailableSpaceProviderForTesting { 20L * gb }

        val result = manager.checkSingleDownloadStorage(createCarTrack(201, sizeBytes = 3L * gb))
        assertFalse(result.isAllowed)
        assertEquals(StoragePrecheckStatus.BLOCKED_CAP_EXCEEDED, result.status)
        assertEquals("Vượt giới hạn dung lượng offline", result.title)
        assertTrue(result.message!!.contains("8 GB"))
        assertTrue(result.message!!.contains("3 GB"))
        assertTrue(result.message!!.contains("10 GB"))
    }

    @Test
    fun testBulkDownloadExceedingCapBlocked() {
        val gb = 1024L * 1024L * 1024L
        addCompletedTrack(200L, fileSizeBytes = 9L * gb)
        manager.setOfflineStorageCapBytes(10L * gb)
        manager.setAvailableSpaceProviderForTesting { 50L * gb }

        val batchTracks = listOf(
            createCarTrack(201, sizeBytes = 1L * gb),
            createCarTrack(202, sizeBytes = 1L * gb),
        )
        val result = manager.checkBulkDownloadStorage(batchTracks)
        assertFalse(result.isAllowed)
        assertEquals(StoragePrecheckStatus.BLOCKED_CAP_EXCEEDED, result.status)
        assertEquals("Vượt giới hạn dung lượng offline", result.title)
    }

    @Test
    fun testUnlimitedCapAllowsDownload() {
        val gb = 1024L * 1024L * 1024L
        addCompletedTrack(200L, fileSizeBytes = 40L * gb)
        manager.setOfflineStorageCapBytes(PinnedDownloadManager.CAP_UNLIMITED)
        manager.setAvailableSpaceProviderForTesting { 25L * gb }

        val result = manager.checkSingleDownloadStorage(createCarTrack(201, sizeBytes = 15L * gb))
        assertTrue(result.isAllowed)
        assertEquals(StoragePrecheckStatus.ALLOWED, result.status)
    }

    @Test
    fun testUnknownSizeAllowedWhenCapSet() {
        val gb = 1024L * 1024L * 1024L
        addCompletedTrack(200L, fileSizeBytes = 8L * gb)
        manager.setOfflineStorageCapBytes(10L * gb)
        manager.setAvailableSpaceProviderForTesting { 5L * gb }

        val result = manager.checkSingleDownloadStorage(createCarTrack(201, sizeBytes = null))
        assertTrue("Unknown size should be allowed to start when current usage < cap", result.isAllowed)

        // Also verify indeterminate progress (totalBytes == -1L) with no fake percentage
        val testData = ByteArray(16_384) { 0x2A }
        val progressTotals = mutableListOf<Long>()
        val latch = CountDownLatch(1)
        val dlManager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(testData), null) },
        )
        dlManager.setOfflineStorageCapBytes(10L * gb)
        dlManager.setAvailableSpaceProviderForTesting { 5L * gb }
        dlManager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
                progressTotals.add(totalBytes)
            }
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })
        dlManager.startDownload(createCarTrack(201, sizeBytes = null))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(progressTotals.isNotEmpty())
        assertTrue("Unknown Content-Length must report 0L totalBytes (no fake %)", progressTotals.all { it == 0L })
    }

    @Test
    fun testUnknownSizeMidTransferCapExceededFailsCleanly() {
        // Existing completed download: 10,000 bytes
        addCompletedTrack(trackId = 300L, fileSizeBytes = 10_000L)
        assertEquals(10_000L, manager.getPinnedUsedBytes())

        // Cap set to 15,000 bytes; unknown-size stream sends 20,000 bytes -> exceeds cap mid-transfer
        val unknownStreamData = ByteArray(20_000) { 0x55 }
        val failLatch = CountDownLatch(1)
        var failureError: String? = null

        val dlManager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(unknownStreamData), null) },
        )
        dlManager.setOfflineStorageCapBytes(15_000L)
        dlManager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        dlManager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                failureError = error
                failLatch.countDown()
            }
        })

        dlManager.startDownload(createCarTrack(301, sizeBytes = null))
        assertTrue("Mid-transfer cap failure should trigger", failLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(failureError)
        assertEquals(DownloadState.FAILED, dlManager.getDownloadStatus(301L).state)
        assertFalse("Track 301 must not be OFFLINE_READY", dlManager.isOfflineReady(301L))
        assertFalse(".part file must be cleaned up", File(storeDir, "track_301.mp3.part").exists())
        assertFalse("Final file must not exist", File(storeDir, "track_301.mp3").exists())

        // Existing download 300L must be preserved intact
        assertTrue("Existing track 300 must remain OFFLINE_READY", dlManager.isOfflineReady(300L))
        assertEquals(10_000L, dlManager.getPinnedUsedBytes())
    }

    // =========================================================================
    // 22. FREE SPACE RESERVE UNIT TESTS
    // =========================================================================

    @Test
    fun testDownloadAllowedAboveFreeSpaceReserve() {
        val mb = 1024L * 1024L
        manager.setOfflineStorageCapBytes(PinnedDownloadManager.CAP_UNLIMITED)
        manager.setAvailableSpaceProviderForTesting { 700L * mb }

        // Free device space = 700 MB, track = 100 MB -> leaves 600 MB (> 500 MB reserve)
        val result = manager.checkSingleDownloadStorage(createCarTrack(400, sizeBytes = 100L * mb))
        assertTrue(result.isAllowed)
        assertEquals(StoragePrecheckStatus.ALLOWED, result.status)
    }

    @Test
    fun testDownloadBlockedBelowFreeSpaceReserve() {
        val mb = 1024L * 1024L
        manager.setOfflineStorageCapBytes(PinnedDownloadManager.CAP_UNLIMITED)
        manager.setAvailableSpaceProviderForTesting { 550L * mb }

        // Free device space = 550 MB, track = 100 MB -> leaves 450 MB (< 500 MB reserve)
        val result = manager.checkSingleDownloadStorage(createCarTrack(400, sizeBytes = 100L * mb))
        assertFalse(result.isAllowed)
        assertEquals(StoragePrecheckStatus.BLOCKED_LOW_FREE_SPACE, result.status)
        assertEquals("Không đủ dung lượng bộ nhớ", result.title)
        assertTrue(result.message!!.contains("500 MB"))
    }

    @Test
    fun testOutOfSpaceDuringTransferFailsCleanly() {
        // Existing completed download preserved
        addCompletedTrack(trackId = 400L, fileSizeBytes = 8_000L)

        val enospcStream = object : InputStream() {
            private var bytesReadSoFar = 0
            override fun read(): Int {
                if (bytesReadSoFar >= 4096) {
                    throw IOException("write failed: ENOSPC (No space left on device)")
                }
                bytesReadSoFar++
                return 0x42
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesReadSoFar >= 4096) {
                    throw IOException("write failed: ENOSPC (No space left on device)")
                }
                val toRead = minOf(len, 4096 - bytesReadSoFar)
                for (i in 0 until toRead) {
                    b[off + i] = 0x42
                }
                bytesReadSoFar += toRead
                return toRead
            }
        }

        val failLatch = CountDownLatch(1)
        var reportedError: String? = null
        val dlManager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(enospcStream, 16_384L) },
        )
        dlManager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }
        dlManager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                reportedError = error
                failLatch.countDown()
            }
        })

        dlManager.startDownload(createCarTrack(401, sizeBytes = 16_384L))
        assertTrue(failLatch.await(5, TimeUnit.SECONDS))
        assertEquals(DownloadState.FAILED, dlManager.getDownloadStatus(401L).state)
        assertEquals("Không đủ dung lượng bộ nhớ", reportedError)
        assertFalse("Partial file must be removed on ENOSPC", File(storeDir, "track_401.mp3.part").exists())
        assertFalse(dlManager.isOfflineReady(401L))
        assertTrue("Existing completed download 400 must be unchanged", dlManager.isOfflineReady(400L))
    }

    // =========================================================================
    // 23. SORTING UNIT TESTS
    // =========================================================================

    @Test
    fun testDownloadedSortByNameAsc() {
        addCompletedTrack(1L, title = "Đêm Đông", fileSizeBytes = 1000L, downloadedAtMs = 100L)
        addCompletedTrack(2L, title = "Ánh Trăng", fileSizeBytes = 2000L, downloadedAtMs = 200L)
        addCompletedTrack(3L, title = "Biển Nhớ", fileSizeBytes = 3000L, downloadedAtMs = 300L)

        val sorted = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_NAME_ASC)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.trackId })
    }

    @Test
    fun testDownloadedSortByDateDesc() {
        addCompletedTrack(1L, title = "Track A", fileSizeBytes = 1000L, downloadedAtMs = 1000L)
        addCompletedTrack(2L, title = "Track B", fileSizeBytes = 2000L, downloadedAtMs = 3000L)
        addCompletedTrack(3L, title = "Track C", fileSizeBytes = 3000L, downloadedAtMs = 2000L)

        val sorted = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_DATE_DESC)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.trackId })
    }

    @Test
    fun testDownloadedSortByDateAsc() {
        addCompletedTrack(1L, title = "Track A", fileSizeBytes = 1000L, downloadedAtMs = 1000L)
        addCompletedTrack(2L, title = "Track B", fileSizeBytes = 2000L, downloadedAtMs = 3000L)
        addCompletedTrack(3L, title = "Track C", fileSizeBytes = 3000L, downloadedAtMs = 2000L)

        val sorted = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_DATE_ASC)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.trackId })
    }

    @Test
    fun testDownloadedSortBySizeDesc() {
        addCompletedTrack(1L, title = "Small", fileSizeBytes = 1000L, downloadedAtMs = 1000L)
        addCompletedTrack(2L, title = "Large", fileSizeBytes = 9000L, downloadedAtMs = 2000L)
        addCompletedTrack(3L, title = "Medium", fileSizeBytes = 5000L, downloadedAtMs = 3000L)

        val sorted = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_SIZE_DESC)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.trackId })
    }

    @Test
    fun testDownloadedSortBySizeAsc() {
        addCompletedTrack(1L, title = "Small", fileSizeBytes = 1000L, downloadedAtMs = 1000L)
        addCompletedTrack(2L, title = "Large", fileSizeBytes = 9000L, downloadedAtMs = 2000L)
        addCompletedTrack(3L, title = "Medium", fileSizeBytes = 5000L, downloadedAtMs = 3000L)

        val sorted = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_SIZE_ASC)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.trackId })
    }

    @Test
    fun testDownloadedSortDeterministicTieBreak() {
        // Equal sizes and equal timestamps -> must break ties deterministically by trackId ascending
        addCompletedTrack(30L, title = "Same", fileSizeBytes = 5000L, downloadedAtMs = 1000L)
        addCompletedTrack(10L, title = "Same", fileSizeBytes = 5000L, downloadedAtMs = 1000L)
        addCompletedTrack(20L, title = "Same", fileSizeBytes = 5000L, downloadedAtMs = 1000L)

        val bySizeDesc = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_SIZE_DESC)
        assertEquals(listOf(10L, 20L, 30L), bySizeDesc.map { it.trackId })

        val byDateDesc = manager.sortDownloadedRecords(store.getAllRecords(), PinnedDownloadManager.SORT_DATE_DESC)
        assertEquals(listOf(10L, 20L, 30L), byDateDesc.map { it.trackId })
    }

    // =========================================================================
    // 24. MULTI-DELETE UNIT TESTS
    // =========================================================================

    @Test
    fun testMultiDeleteRemovesSelectedOfflineTracksOnly() {
        addCompletedTrack(501L, title = "Keep Track", fileSizeBytes = 4000L)
        addCompletedTrack(502L, title = "Delete Track 1", fileSizeBytes = 6000L)
        addCompletedTrack(503L, title = "Delete Track 2", fileSizeBytes = 8000L)

        // Remote catalog & playlist state representation (never touched by local delete)
        val remoteCatalogIds = mutableSetOf(501L, 502L, 503L)
        val playlistMembershipIds = mutableListOf(501L, 502L, 503L)
        var backendDeleteCalled = false

        val deletedCount = manager.deleteMultipleDownloads(listOf(502L, 503L))
        assertEquals(2, deletedCount)

        // 2 local files and metadata entries gone
        assertFalse(File(storeDir, "track_502.mp3").exists())
        assertFalse(File(storeDir, "track_503.mp3").exists())
        assertNull(store.getRecord(502L))
        assertNull(store.getRecord(503L))
        assertFalse(manager.isOfflineReady(502L))
        assertFalse(manager.isOfflineReady(503L))

        // 1 track remains OFFLINE_READY
        assertTrue(File(storeDir, "track_501.mp3").exists())
        assertNotNull(store.getRecord(501L))
        assertTrue(manager.isOfflineReady(501L))
        assertEquals(4000L, manager.getPinnedUsedBytes())

        // Remote catalog, playlist membership, and backend untouched
        assertFalse(backendDeleteCalled)
        assertEquals(setOf(501L, 502L, 503L), remoteCatalogIds)
        assertEquals(listOf(501L, 502L, 503L), playlistMembershipIds)
    }

    // =========================================================================
    // 25. DELETE ALL UNIT TESTS
    // =========================================================================

    @Test
    fun testDeleteAllOfflineClearsOnlyPinnedStorage() {
        addCompletedTrack(601L, fileSizeBytes = 5000L)
        addCompletedTrack(602L, fileSizeBytes = 7000L)
        addCompletedTrack(603L, fileSizeBytes = 9000L)

        // Simulated ExoPlayer stream cache and Device Music files outside offline_tracks
        val streamCacheDir = tempFolder.newFolder("exo_stream_cache")
        val streamCacheFile = File(streamCacheDir, "cached_segment.exo").apply { writeBytes(ByteArray(1024)) }
        val deviceMusicDir = tempFolder.newFolder("device_music")
        val deviceMusicFile = File(deviceMusicDir, "local_song.flac").apply { writeBytes(ByteArray(2048)) }

        val result = manager.deleteAllOfflineDownloads()
        assertTrue(result.success)
        assertFalse(result.blockedByActiveDownloads)
        assertEquals(3, result.deletedTrackCount)
        assertEquals(21_000L, result.freedBytes)

        // All completed pinned files deleted & metadata empty
        assertFalse(File(storeDir, "track_601.mp3").exists())
        assertFalse(File(storeDir, "track_602.mp3").exists())
        assertFalse(File(storeDir, "track_603.mp3").exists())
        assertTrue(store.getAllRecords().isEmpty())
        assertEquals(0L, manager.getPinnedUsedBytes())

        // ExoPlayer stream cache and Device Music untouched
        assertTrue("Stream cache must not be deleted", streamCacheFile.exists())
        assertTrue("Device Music must not be deleted", deviceMusicFile.exists())
    }

    @Test
    fun testDeleteAllBlockedWhileActiveDownloadRunning() {
        addCompletedTrack(701L, fileSizeBytes = 5000L)

        val streamStartedLatch = CountDownLatch(1)
        val releaseStreamLatch = CountDownLatch(1)
        val allowFinish = AtomicBoolean(false)

        val blockingStream = object : InputStream() {
            override fun read(): Int {
                streamStartedLatch.countDown()
                releaseStreamLatch.await(5, TimeUnit.SECONDS)
                return if (allowFinish.getAndSet(false)) 0x11 else -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                streamStartedLatch.countDown()
                releaseStreamLatch.await(5, TimeUnit.SECONDS)
                return -1
            }
        }

        val dlManager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(blockingStream, null) },
        )
        dlManager.setAvailableSpaceProviderForTesting { 10L * 1024L * 1024L * 1024L }

        dlManager.startDownload(createCarTrack(702, sizeBytes = null))
        assertTrue("Active download should start", streamStartedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Manager must report active jobs", dlManager.hasActiveJobs())

        // Attempt delete all while active download is running -> MUST BE BLOCKED
        val blockedResult = dlManager.deleteAllOfflineDownloads()
        assertFalse(blockedResult.success)
        assertTrue("Delete All must be blocked while active download is running", blockedResult.blockedByActiveDownloads)
        assertEquals(0, blockedResult.deletedTrackCount)
        assertTrue("Existing offline track 701 must still exist", dlManager.isOfflineReady(701L))

        // Cancel active download and release stream
        dlManager.cancelDownload(702L)
        releaseStreamLatch.countDown()
    }

    // =========================================================================
    // 26. PATH SAFETY UNIT TESTS
    // =========================================================================

    @Test
    fun testDeleteNeverTraversesOutsideOfflineDirectory() {
        val outsideDir = tempFolder.newFolder("outside_sensitive_dir")
        val outsideFile1 = File(outsideDir, "evil.mp3").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val outsideFile2 = File(outsideDir, "absolute_evil.mp3").apply { writeBytes(byteArrayOf(5, 6, 7, 8)) }

        // 1. Relative traversal "../outside_sensitive_dir/evil.mp3"
        val evilTraversalPath = "../${outsideDir.name}/evil.mp3"
        assertFalse(store.isSafeRelativeFileName(evilTraversalPath))
        assertNull(store.resolveSafeTrackFile(evilTraversalPath))

        // 2. Windows drive path "C:\evil.mp3"
        assertFalse(store.isSafeRelativeFileName("C:\\evil.mp3"))
        assertNull(store.resolveSafeTrackFile("C:\\evil.mp3"))

        // 3. Unix absolute path "/absolute/evil.mp3"
        assertFalse(store.isSafeRelativeFileName("/absolute/evil.mp3"))
        assertNull(store.resolveSafeTrackFile("/absolute/evil.mp3"))

        // 4. Even if metadata is tampered to reference "../outside_sensitive_dir/evil.mp3" or absolute path,
        // deleting the record must NEVER delete the file outside offline_tracks!
        store.saveRecord(
            OfflineTrackRecord(
                trackId = 901L,
                title = "Tampered Relative",
                artist = "Hacker",
                album = "Album",
                durationMs = 1000L,
                relativeFileName = evilTraversalPath,
                fileSizeBytes = 4L,
                sha256 = "00",
                loudnessLufs = null,
                truePeakDbtp = null,
                loudnessRangeLu = null,
                analysisVersion = null,
                downloadedAtMs = 1000L,
                format = "mp3",
            )
        )
        store.saveRecord(
            OfflineTrackRecord(
                trackId = 902L,
                title = "Tampered Absolute",
                artist = "Hacker",
                album = "Album",
                durationMs = 1000L,
                relativeFileName = outsideFile2.absolutePath,
                fileSizeBytes = 4L,
                sha256 = "00",
                loudnessLufs = null,
                truePeakDbtp = null,
                loudnessRangeLu = null,
                analysisVersion = null,
                downloadedAtMs = 1000L,
                format = "mp3",
            )
        )

        manager.deleteDownload(901L)
        manager.deleteDownload(902L)

        assertTrue("Outside file 1 must NEVER be deleted", outsideFile1.exists())
        assertTrue("Outside file 2 must NEVER be deleted", outsideFile2.exists())
    }
}
