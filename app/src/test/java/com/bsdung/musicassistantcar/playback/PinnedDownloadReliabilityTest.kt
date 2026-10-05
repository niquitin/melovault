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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PinnedDownloadReliabilityTest {

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
        storeDir = tempFolder.newFolder("reliability_test_offline")
        store = OfflineMetadataStore(storeDir)
    }

    private fun sampleTrack(
        id: Int = 5001,
        title: String = "Reliability Test Track",
        size: Long = 10_000L,
    ): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Reliability Artist",
            album = "Reliability Album",
            status = "ready",
            duration = 180.0,
            sourceSizeBytes = size,
            sourceFormat = "flac",
        )
    }

    @Test
    fun testForegroundServiceActiveAndStopsWhenQueueIdle() {
        val totalBytes = 5_000L
        val data = ByteArray(totalBytes.toInt()) { 42 }
        val fakeService = FakeServiceController()
        val fakeWakeLock = FakeWakeLock()

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(data), totalBytes) },
            wakeLock = fakeWakeLock,
            serviceController = fakeService,
        )

        val completionLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(5001, size = totalBytes))
        assertTrue("Download must complete within timeout", completionLatch.await(5, TimeUnit.SECONDS))

        assertTrue("Foreground service must have been triggered on active queue", fakeService.activeCalls.get() >= 1)
        assertTrue("Foreground service must have been notified on idle queue", fakeService.idleCalls.get() >= 1)
        assertFalse("Foreground service must not be active when idle", fakeService.isServiceActive)
    }

    @Test
    fun testWakeLockAcquiredDuringTransferAndReleasedOnSuccess() {
        val totalBytes = 6_000L
        val data = ByteArray(totalBytes.toInt()) { 7 }
        val fakeWakeLock = FakeWakeLock()
        val readStartedLatch = CountDownLatch(1)
        val allowFinishLatch = CountDownLatch(1)
        val wasWakeLockHeldDuringRead = AtomicBoolean(false)

        val blockingStream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            override fun read(): Int {
                wasWakeLockHeldDuringRead.set(fakeWakeLock.isHeld)
                readStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                wasWakeLockHeldDuringRead.set(fakeWakeLock.isHeld)
                readStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read(b, off, len)
            }
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(blockingStream, totalBytes) },
            wakeLock = fakeWakeLock,
        )

        val completionLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        assertFalse("WakeLock must not be held before start", fakeWakeLock.isHeld)
        manager.startDownload(sampleTrack(5002, size = totalBytes))

        assertTrue("Read must have started", readStartedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("WakeLock must be held during active stream transfer", wasWakeLockHeldDuringRead.get())
        assertTrue("WakeLock must be actively held while transfer in progress", fakeWakeLock.isHeld)

        allowFinishLatch.countDown()
        assertTrue("Download must complete", completionLatch.await(5, TimeUnit.SECONDS))

        assertFalse("WakeLock must be released on success", fakeWakeLock.isHeld)
        assertEquals("WakeLock acquire count", 1, fakeWakeLock.acquireCount.get())
        assertEquals("WakeLock release count", 1, fakeWakeLock.releaseCount.get())
    }

    @Test
    fun testWakeLockReleasedOnFailure() {
        val fakeWakeLock = FakeWakeLock()
        val fakeService = FakeServiceController()

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> throw IOException("Simulated network failure") },
            wakeLock = fakeWakeLock,
            serviceController = fakeService,
        )

        val failLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                failLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(5003))
        assertTrue("Download must fail", failLatch.await(5, TimeUnit.SECONDS))

        val status = manager.getDownloadStatus(5003L)
        assertEquals(DownloadState.FAILED, status.state)
        assertFalse("WakeLock must be released on failure", fakeWakeLock.isHeld)
        assertEquals("WakeLock release count must match acquire count", fakeWakeLock.acquireCount.get(), fakeWakeLock.releaseCount.get())
        assertTrue("Service controller notified idle on failure", fakeService.idleCalls.get() >= 1)
    }

    @Test
    fun testWakeLockReleasedOnCancel() {
        val totalBytes = 8_000L
        val data = ByteArray(totalBytes.toInt()) { 1 }
        val fakeWakeLock = FakeWakeLock()
        val fakeService = FakeServiceController()
        val readStartedLatch = CountDownLatch(1)

        val blockingStream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            @Volatile private var isClosed = false
            override fun read(): Int {
                if (isClosed) throw IOException("Stream closed")
                readStartedLatch.countDown()
                Thread.sleep(50)
                return delegate.read()
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (isClosed) throw IOException("Stream closed")
                readStartedLatch.countDown()
                Thread.sleep(50)
                return delegate.read(b, off, len)
            }
            override fun close() {
                isClosed = true
                super.close()
            }
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(blockingStream, totalBytes) },
            wakeLock = fakeWakeLock,
            serviceController = fakeService,
        )

        val cancelLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCancelled(trackId: Long) {
                cancelLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(5004, size = totalBytes))
        assertTrue("Read must have started", readStartedLatch.await(5, TimeUnit.SECONDS))

        val cancelled = manager.cancelDownload(5004L)
        assertTrue("Cancel must return true", cancelled)
        assertTrue("Cancel notification must be received", cancelLatch.await(5, TimeUnit.SECONDS))

        val status = manager.getDownloadStatus(5004L)
        assertEquals(DownloadState.CANCELLED, status.state)
        assertFalse("WakeLock must be released on cancel", fakeWakeLock.isHeld)
        assertEquals("WakeLock acquire count equals release count", fakeWakeLock.acquireCount.get(), fakeWakeLock.releaseCount.get())
    }

    @Test
    fun testActivityStopWhileActiveDownloadContinues() {
        val totalBytes = 10_000L
        val data = ByteArray(totalBytes.toInt()) { 3 }
        val allowFinishLatch = CountDownLatch(1)
        val readStartedLatch = CountDownLatch(1)

        val stream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                readStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read(b, off, len)
            }
            override fun read(): Int {
                readStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read()
            }
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(stream, totalBytes) },
        )

        val activityListener = object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadStarted(trackId: Long) {}
        }
        manager.addListener(activityListener)

        manager.startDownload(sampleTrack(5005, size = totalBytes))
        assertTrue("Transfer must start", readStartedLatch.await(5, TimeUnit.SECONDS))

        // Activity stops / destroyed
        manager.removeListener(activityListener)

        // Transfer continues in background
        allowFinishLatch.countDown()
        Thread.sleep(500)

        // Wait until offline ready
        var isReady = false
        for (i in 1..20) {
            if (manager.isOfflineReady(5005L)) {
                isReady = true
                break
            }
            Thread.sleep(100)
        }
        assertTrue("Download must successfully complete in background after Activity stops", isReady)
    }

    @Test
    fun testActivityRecreatedDoesNotDuplicateJob() {
        val totalBytes = 5_000L
        val data = ByteArray(totalBytes.toInt()) { 9 }
        val streamStartedLatch = CountDownLatch(1)
        val allowFinishLatch = CountDownLatch(1)

        val stream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                streamStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read(b, off, len)
            }
            override fun read(): Int {
                streamStartedLatch.countDown()
                allowFinishLatch.await(5, TimeUnit.SECONDS)
                return delegate.read()
            }
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(stream, totalBytes) },
        )

        val track = sampleTrack(5006, size = totalBytes)
        manager.startDownload(track)
        assertTrue("Stream must start", streamStartedLatch.await(5, TimeUnit.SECONDS))

        // Simulate Activity recreation calling startDownload on the same track again
        manager.startDownload(track)

        val activeJobs = manager.getActiveJobMediaItems()
        assertEquals("Must NOT duplicate job worker; exactly 1 job exists", 1, activeJobs.size)

        allowFinishLatch.countDown()
        Thread.sleep(300)
    }

    @Test
    fun testUiReattachesToExistingProgress() {
        val totalBytes = 8_000L
        val data = ByteArray(totalBytes.toInt()) { 4 }
        val halfReadLatch = CountDownLatch(1)
        val allowFinishLatch = CountDownLatch(1)

        val stream = object : InputStream() {
            private val delegate = ByteArrayInputStream(data)
            private var readsCount = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (readsCount == 0) {
                    readsCount++
                    val chunkSize = 4000.coerceAtMost(len)
                    return delegate.read(b, off, chunkSize)
                } else if (readsCount == 1) {
                    readsCount++
                    halfReadLatch.countDown()
                    allowFinishLatch.await(5, TimeUnit.SECONDS)
                    return delegate.read(b, off, len)
                } else {
                    return delegate.read(b, off, len)
                }
            }
            override fun read(): Int {
                return delegate.read()
            }
        }

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(stream, totalBytes) },
        )

        val track = sampleTrack(5007, size = totalBytes)
        manager.startDownload(track)

        assertTrue("Must reach midway transfer", halfReadLatch.await(5, TimeUnit.SECONDS))

        // UI attaches afresh and queries download state
        val status = manager.getDownloadStatus(5007L)
        assertEquals(DownloadState.DOWNLOADING, status.state)
        assertTrue("Bytes downloaded must reflect actual progress", status.bytesDownloaded >= 4000L)
        assertEquals(totalBytes, status.totalBytes)

        val completedLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        allowFinishLatch.countDown()
        assertTrue("Download completes after UI reattached", completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Track is OFFLINE_READY", manager.isOfflineReady(5007L))
    }

    @Test
    fun testScreenOffLifecycleEventNotMappedToFailed() {
        val totalBytes = 4_000L
        val data = ByteArray(totalBytes.toInt()) { 2 }
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(data), totalBytes) },
        )

        val failOccurred = AtomicBoolean(false)
        val completeLatch = CountDownLatch(1)

        val activityListener = object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                failOccurred.set(true)
            }
        }
        val backgroundServiceListener = object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completeLatch.countDown()
            }
        }
        manager.addListener(activityListener)
        manager.addListener(backgroundServiceListener)

        // Start download
        manager.startDownload(sampleTrack(5008, size = totalBytes))

        // Simulate screen off / Activity paused & stopped
        manager.removeListener(activityListener)

        // Wait for completion
        assertTrue("Download completes through screen-off simulation", completeLatch.await(5, TimeUnit.SECONDS))
        assertFalse("Screen-off/lifecycle change must NOT be mapped to FAILED", failOccurred.get())
        assertTrue(manager.isOfflineReady(5008L))
    }

    @Test
    fun testProcessRecoveryNeverMarksPartOfflineReady() {
        val partFile = File(storeDir, "track_5009.part")
        partFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(partFile.exists())

        // Simulate process recovery: new PinnedDownloadManager instance
        val manager = PinnedDownloadManager(store)
        assertFalse("Orphan part file must never be marked OFFLINE_READY", manager.isOfflineReady(5009L))
        assertFalse("Store must not have orphan track", store.hasTrack(5009L))
        assertFalse("Orphan .part file must be cleaned on startup", partFile.exists())
    }

    @Test
    fun testCompletedPinnedDownloadSurvivesProcessRecreation() {
        val audioFile = File(storeDir, "track_5010.flac")
        val payload = ByteArray(2048) { 0x5A }
        audioFile.writeBytes(payload)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(payload).joinToString("") { "%02x".format(it) }

        val record = OfflineTrackRecord(
            trackId = 5010L,
            title = "Surviving Track",
            artist = "Surviving Artist",
            album = "Surviving Album",
            durationMs = 120_000L,
            relativeFileName = "track_5010.flac",
            fileSizeBytes = 2048L,
            sha256 = sha256,
            loudnessLufs = -14.0,
            truePeakDbtp = -1.0,
            loudnessRangeLu = 5.0,
            analysisVersion = 1,
            downloadedAtMs = System.currentTimeMillis(),
        )
        store.saveRecord(record)

        // Simulate brand new process
        val recoveredStore = OfflineMetadataStore(storeDir)
        val recoveredManager = PinnedDownloadManager(recoveredStore)

        assertTrue("Completed pinned download survives process recreation", recoveredManager.isOfflineReady(5010L))
        val loaded = recoveredManager.getRecord(5010L)
        assertNotNull(loaded)
        assertEquals("Surviving Track", loaded?.title)
        assertEquals(2048L, loaded?.fileSizeBytes)
    }

    @Test
    fun testSafeDeleteUnchanged() {
        val audioFile = File(storeDir, "track_5011.flac")
        val payload = ByteArray(1024) { 0x33 }
        audioFile.writeBytes(payload)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(payload).joinToString("") { "%02x".format(it) }

        val record = OfflineTrackRecord(
            trackId = 5011L,
            title = "Delete Track",
            artist = "Delete Artist",
            album = "Delete Album",
            durationMs = 90_000L,
            relativeFileName = "track_5011.flac",
            fileSizeBytes = 1024L,
            sha256 = sha256,
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            analysisVersion = null,
            downloadedAtMs = System.currentTimeMillis(),
        )
        store.saveRecord(record)
        val manager = PinnedDownloadManager(store)
        assertTrue(manager.isOfflineReady(5011L))

        val deleted = manager.deleteDownload(5011L)
        assertTrue("Safe delete must return true", deleted)
        assertFalse("Must no longer be offline ready", manager.isOfflineReady(5011L))
        assertFalse("Local file must be deleted", audioFile.exists())
        assertNull("Metadata record must be deleted", store.getRecord(5011L))
    }

    @Test
    fun testStreamCacheNeverShowsOfflineBadge() {
        val manager = PinnedDownloadManager(store)
        assertFalse(manager.isOfflineReady(99999L))
        assertFalse(manager.isDownloaded(99999L))

        // Create a non-pinned cache file
        val cacheFile = File(storeDir, "cache_stream_99999.exo")
        cacheFile.writeBytes(ByteArray(4096) { 0x11 })

        assertFalse("Stream cache file is never equivalent to pinned offline", manager.isOfflineReady(99999L))
        assertFalse("Stream cache file is never equivalent to pinned offline", manager.isDownloaded(99999L))
    }

    @Test
    fun testNoAuthTokenPersisted() {
        val totalBytes = 2_000L
        val data = ByteArray(totalBytes.toInt()) { 0x55 }
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(data), totalBytes) },
        )

        val completionLatch = CountDownLatch(1)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completionLatch.countDown()
            }
        })

        manager.startDownload(sampleTrack(5012, size = totalBytes))
        assertTrue(completionLatch.await(5, TimeUnit.SECONDS))

        // Inspect offline_metadata.json directly
        val recordsFile = File(storeDir, "offline_metadata.json")
        assertTrue(recordsFile.exists())
        val recordsJson = recordsFile.readText(Charsets.UTF_8).lowercase()

        assertFalse("offline_metadata.json must NOT contain token", recordsJson.contains("token"))
        assertFalse("offline_metadata.json must NOT contain bearer", recordsJson.contains("bearer"))
        assertFalse("offline_metadata.json must NOT contain authorization", recordsJson.contains("authorization"))
    }
}
