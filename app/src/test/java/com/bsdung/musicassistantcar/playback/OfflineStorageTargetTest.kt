package com.bsdung.musicassistantcar.playback

import android.content.SharedPreferences
import android.os.Environment
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OfflineStorageTargetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var metadataDir: File
    private lateinit var internalOfflineDir: File
    private lateinit var sdOfflineDir: File
    private lateinit var volumeProvider: ConfigurableOfflineStorageVolumeProvider
    private lateinit var store: OfflineMetadataStore
    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setup() {
        metadataDir = tempFolder.newFolder("metadata_root")
        internalOfflineDir = File(tempFolder.newFolder("internal_root"), "melovault_offline")
        sdOfflineDir = File(tempFolder.newFolder("sd_root"), "melovault_offline")

        volumeProvider = ConfigurableOfflineStorageVolumeProvider(
            internalOfflineDir = internalOfflineDir,
            removableSdOfflineDir = sdOfflineDir,
            removableSdMounted = true,
            removableSdWritable = true,
            removableSdUuid = "1234-ABCD",
            internalAvailableBytesOverride = 20L * 1024L * 1024L * 1024L,
            removableSdAvailableBytesOverride = 32L * 1024L * 1024L * 1024L,
        )
        store = OfflineMetadataStore(
            storageDir = metadataDir,
            volumeProvider = volumeProvider,
        )
        prefs = FakeSharedPreferences()
    }

    private fun createTrack(id: Int, title: String = "Track $id", sizeBytes: Long = 1024L): CarTrack {
        return CarTrack(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            status = "ready",
            duration = 200.0,
            sourceFormat = "mp3",
            sourceSizeBytes = sizeBytes,
        )
    }

    // =========================================================================
    // 23. UNIT TESTS — VOLUME CLASSIFICATION
    // =========================================================================

    @Test
    fun testVolumeClassification_internalOnly() {
        val intRoot = tempFolder.newFolder("vol_int_only")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot),
            isRemovableResolver = { false },
            storageStateResolver = { Environment.MEDIA_MOUNTED },
            isWritableResolver = { true },
        )
        assertTrue(res.isInternalAvailable)
        assertEquals(File(intRoot, "melovault_offline").canonicalFile, res.internalVolume?.offlineDir?.canonicalFile)
        assertFalse(res.isRemovableSdAvailable)
        assertNull(res.removableSdVolume)
    }

    @Test
    fun testVolumeClassification_internalPlusMountedWritableSd() {
        val intRoot = tempFolder.newFolder("vol_int")
        val sdRoot = tempFolder.newFolder("vol_sd")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot, sdRoot),
            isRemovableResolver = { it == sdRoot },
            storageStateResolver = { Environment.MEDIA_MOUNTED },
            isWritableResolver = { true },
            volumeUuidResolver = { if (it == sdRoot) "ABCD-9999" else null },
        )
        assertTrue(res.isInternalAvailable)
        assertTrue(res.isRemovableSdAvailable)
        assertEquals(File(sdRoot, "melovault_offline").canonicalFile, res.removableSdVolume?.offlineDir?.canonicalFile)
        assertEquals("ABCD-9999", res.removableSdVolume?.volumeUuid)
    }

    @Test
    fun testVolumeClassification_internalPlusNullEntry() {
        val intRoot = tempFolder.newFolder("vol_int_null")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot, null),
            isRemovableResolver = { false },
            storageStateResolver = { Environment.MEDIA_MOUNTED },
            isWritableResolver = { true },
        )
        assertTrue(res.isInternalAvailable)
        assertFalse(res.isRemovableSdAvailable)
        assertNull(res.removableSdVolume)
    }

    @Test
    fun testVolumeClassification_internalPlusUnmountedSd() {
        val intRoot = tempFolder.newFolder("vol_int_unmounted")
        val sdRoot = tempFolder.newFolder("vol_sd_unmounted")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot, sdRoot),
            isRemovableResolver = { it == sdRoot },
            storageStateResolver = { if (it == sdRoot) Environment.MEDIA_UNMOUNTED else Environment.MEDIA_MOUNTED },
            isWritableResolver = { true },
        )
        assertTrue(res.isInternalAvailable)
        assertFalse(res.isRemovableSdAvailable)
        assertNull(res.removableSdVolume)
    }

    @Test
    fun testVolumeClassification_internalPlusReadOnlySd() {
        val intRoot = tempFolder.newFolder("vol_int_ro")
        val sdRoot = tempFolder.newFolder("vol_sd_ro")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot, sdRoot),
            isRemovableResolver = { it == sdRoot },
            storageStateResolver = { if (it == sdRoot) Environment.MEDIA_MOUNTED_READ_ONLY else Environment.MEDIA_MOUNTED },
            isWritableResolver = { it != sdRoot },
        )
        assertTrue(res.isInternalAvailable)
        assertFalse("Read-only SD must not be available for new downloads", res.isRemovableSdAvailable)
        assertNull(res.removableSdVolume)
    }

    @Test
    fun testVolumeClassification_multipleRemovableVolumesSelectsFirstMountedWritableDeterministically() {
        val intRoot = tempFolder.newFolder("vol_int_multi")
        val sdRoot1 = tempFolder.newFolder("vol_sd_1")
        val sdRoot2 = tempFolder.newFolder("vol_sd_2")
        val res = OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = arrayOf(intRoot, sdRoot1, sdRoot2),
            isRemovableResolver = { it == sdRoot1 || it == sdRoot2 },
            storageStateResolver = { Environment.MEDIA_MOUNTED },
            isWritableResolver = { true },
            volumeUuidResolver = {
                when (it) {
                    sdRoot1 -> "SD-FIRST"
                    sdRoot2 -> "SD-SECOND"
                    else -> null
                }
            },
        )
        assertTrue(res.isRemovableSdAvailable)
        assertEquals(File(sdRoot1, "melovault_offline").canonicalFile, res.removableSdVolume?.offlineDir?.canonicalFile)
        assertEquals("SD-FIRST", res.removableSdVolume?.volumeUuid)
    }

    // =========================================================================
    // 24. UNIT TESTS — STORAGE TARGET PREFERENCE
    // =========================================================================

    @Test
    fun testStorageTargetPreference_defaultAndPersistenceAndUpgradeCompatibility() {
        // Default target = INTERNAL when no preference file exists
        val manager = PinnedDownloadManager(
            store = store,
            volumeProvider = volumeProvider,
        )
        assertEquals(OfflineStorageTarget.INTERNAL, manager.getOfflineStorageTarget())

        // Set REMOVABLE_SD -> persisted across manager instances
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)
        assertEquals(OfflineStorageTarget.REMOVABLE_SD, manager.getOfflineStorageTarget())

        val reloadedManager1 = PinnedDownloadManager(
            store = store,
            volumeProvider = volumeProvider,
        )
        assertEquals(OfflineStorageTarget.REMOVABLE_SD, reloadedManager1.getOfflineStorageTarget())

        // Set INTERNAL -> persisted across manager instances
        reloadedManager1.setOfflineStorageTarget(OfflineStorageTarget.INTERNAL)
        assertEquals(OfflineStorageTarget.INTERNAL, reloadedManager1.getOfflineStorageTarget())

        val reloadedManager2 = PinnedDownloadManager(
            store = store,
            volumeProvider = volumeProvider,
        )
        assertEquals(OfflineStorageTarget.INTERNAL, reloadedManager2.getOfflineStorageTarget())

        // Legacy metadata entry without storage_target field defaults to INTERNAL
        File(internalOfflineDir, "track_11200.mp3").writeBytes(ByteArray(128) { 0x55 })
        val legacyJsonFile = File(metadataDir, "offline_metadata.json")
        legacyJsonFile.writeText(
            """
            {
              "version": 1,
              "tracks": [
                {
                  "track_id": 11200,
                  "title": "Legacy Track",
                  "artist": "Legacy Artist",
                  "album": "Legacy Album",
                  "duration_ms": 180000,
                  "relative_file_name": "track_11200.mp3",
                  "file_size_bytes": 128,
                  "sha256": "00112233",
                  "downloaded_at_ms": 1700000000000
                }
              ]
            }
            """.trimIndent()
        )
        val reloadedStore = OfflineMetadataStore(metadataDir, volumeProvider)
        val legacyRec = reloadedStore.getRecord(11200L)
        assertNotNull(legacyRec)
        assertEquals(OfflineStorageTarget.INTERNAL, legacyRec!!.storageTarget)
    }

    // =========================================================================
    // 25. UNIT TESTS — MULTI-VOLUME DOWNLOAD & PLAYBACK
    // =========================================================================

    @Test
    fun testMultiVolumeDownloadAndPlayback_internalAndSdCoexistAndPlay() {
        val bytesA = ByteArray(2048) { 0x41 }
        val bytesB = ByteArray(4096) { 0x42 }

        val manager = PinnedDownloadManager(
            store = store,
            streamOpener = { track, _ ->
                val data = if (track.id == 2001) bytesA else bytesB
                Pair(ByteArrayInputStream(data), data.size.toLong())
            },
            volumeProvider = volumeProvider,
        )

        // 1. Target = INTERNAL -> download Track A (2001)
        assertEquals(OfflineStorageTarget.INTERNAL, manager.getOfflineStorageTarget())
        manager.startDownload(createTrack(2001, "Internal Song", bytesA.size.toLong()))
        assertTrue(waitForOfflineReady(manager, 2001L))

        val recA = manager.getRecord(2001L)
        assertNotNull(recA)
        assertEquals(OfflineStorageTarget.INTERNAL, recA!!.storageTarget)
        val fileA = File(internalOfflineDir, recA.relativeFileName)
        assertTrue("Track A must exist in internal melovault_offline", fileA.exists())
        assertFalse("Track A must NOT exist in SD melovault_offline", File(sdOfflineDir, recA.relativeFileName).exists())

        // 2. Switch target to REMOVABLE_SD -> download Track B (2002)
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)
        manager.startDownload(createTrack(2002, "SD Song", bytesB.size.toLong()))
        assertTrue(waitForOfflineReady(manager, 2002L))

        val recB = manager.getRecord(2002L)
        assertNotNull(recB)
        assertEquals(OfflineStorageTarget.REMOVABLE_SD, recB!!.storageTarget)
        assertEquals("1234-ABCD", recB.storageVolumeUuid)
        val fileB = File(sdOfflineDir, recB.relativeFileName)
        assertTrue("Track B must exist in SD melovault_offline", fileB.exists())
        assertFalse("Track B must NOT exist in internal melovault_offline", File(internalOfflineDir, recB.relativeFileName).exists())

        // 3. Track A remains on internal untouched; both resolve and play from local files
        assertTrue("Track A must remain on internal", fileA.exists())
        assertTrue(manager.isOfflineReady(2001L))
        assertTrue(manager.isOfflineReady(2002L))

        val resolvedA = manager.resolveLocalPlaybackFile(2001L)
        val resolvedB = manager.resolveLocalPlaybackFile(2002L)
        assertNotNull(resolvedA)
        assertNotNull(resolvedB)
        assertEquals(fileA.canonicalPath, resolvedA!!.canonicalPath)
        assertEquals(fileB.canonicalPath, resolvedB!!.canonicalPath)

        val summary = manager.getStorageSummary()
        assertEquals(2, summary.trackCount)
        assertEquals(1, summary.internalTrackCount)
        assertEquals(1, summary.sdTrackCount)
        assertEquals(bytesA.size.toLong(), summary.internalUsedBytes)
        assertEquals(bytesB.size.toLong(), summary.sdUsedBytes)
        assertEquals((bytesA.size + bytesB.size).toLong(), summary.usedBytes)
    }

    // =========================================================================
    // 26. UNIT TESTS — SD UNAVAILABLE BEFORE DOWNLOAD
    // =========================================================================

    @Test
    fun testSdUnavailableBeforeDownload_zeroHttpZeroPartNoFallbackToInternal() {
        volumeProvider.setRemovableSdState(mounted = false, writable = false)

        val httpCalls = AtomicInteger(0)
        val manager = PinnedDownloadManager(
            store = store,
            streamOpener = { _, _ ->
                httpCalls.incrementAndGet()
                throw AssertionError("HTTP transfer must NOT start when target SD is unavailable")
            },
            volumeProvider = volumeProvider,
        )
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)

        val track = createTrack(3001, "Blocked SD Track", 4096L)
        val precheck = manager.checkSingleDownloadStorage(track)
        assertFalse(precheck.isAllowed)
        assertEquals(StoragePrecheckStatus.STORAGE_TARGET_UNAVAILABLE, precheck.status)
        assertEquals("Thẻ nhớ SD không khả dụng", precheck.title)

        // Attempt startDownload directly
        manager.startDownload(track)
        assertEquals(0, httpCalls.get())
        assertFalse(manager.isOfflineReady(3001L))

        val status = manager.getDownloadStatus(3001L)
        assertEquals(DownloadState.STORAGE_TARGET_UNAVAILABLE, status.state)
        assertEquals("Thẻ nhớ SD không khả dụng", status.errorMessage)

        // Verify 0 .part files and 0 files written to internal or SD
        val internalFiles = internalOfflineDir.listFiles()?.filter { it.name != "offline_metadata.json" }.orEmpty()
        val sdFiles = sdOfflineDir.listFiles().orEmpty()
        assertTrue("No file or .part may be written to internal", internalFiles.isEmpty())
        assertTrue("No file or .part may be written to SD", sdFiles.isEmpty())
    }

    // =========================================================================
    // 27. UNIT TESTS — SD REMOVED MID-DOWNLOAD
    // =========================================================================

    @Test
    fun testSdRemovedMidDownload_abortsCleanlyReleasesWakeLockAndNeverContinuesOnInternal() {
        val streamClosed = AtomicBoolean(false)
        val fakeWakeLock = object : DownloadWakeLock {
            @Volatile
            override var isHeld: Boolean = false
            override fun acquire() {
                isHeld = true
            }
            override fun release() {
                isHeld = false
            }
        }

        val midUnmountStream = object : InputStream() {
            private var bytesReadCount = 0
            override fun read(): Int {
                val b = ByteArray(1)
                val n = read(b, 0, 1)
                return if (n == -1) -1 else b[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesReadCount >= 16384) {
                    // Simulate SD card pulled out mid-transfer
                    volumeProvider.setRemovableSdState(mounted = false, writable = false)
                    throw IOException("EIO: I/O error on removable media")
                }
                val chunk = minOf(len, 4096)
                for (i in 0 until chunk) {
                    b[off + i] = 0x55
                }
                bytesReadCount += chunk
                return chunk
            }

            override fun close() {
                streamClosed.set(true)
                super.close()
            }
        }

        val failLatch = CountDownLatch(1)
        val manager = PinnedDownloadManager(
            store = store,
            streamOpener = { _, _ -> Pair(midUnmountStream, 65536L) },
            wakeLock = fakeWakeLock,
            volumeProvider = volumeProvider,
        )
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadFailed(trackId: Long, error: String) {
                failLatch.countDown()
            }
        })

        manager.startDownload(createTrack(4001, "Mid Unmount Track", 65536L))
        assertTrue("Download should fail promptly on mid-stream SD removal", failLatch.await(3, TimeUnit.SECONDS))

        assertTrue("InputStream must be closed", streamClosed.get())
        assertFalse("Track must NOT be marked OFFLINE_READY", manager.isOfflineReady(4001L))
        assertEquals(DownloadState.STORAGE_TARGET_UNAVAILABLE, manager.getDownloadStatus(4001L).state)
        assertFalse("WakeLock must be released", fakeWakeLock.isHeld)

        // Verify .part file on SD was cleaned up and nothing was written to internal storage
        val partFilesOnSd = sdOfflineDir.listFiles()?.filter { it.name.endsWith(".part") }.orEmpty()
        assertTrue("Partial file on SD must be cleaned", partFilesOnSd.isEmpty())
        val internalFiles = internalOfflineDir.listFiles()?.filter { it.name != "offline_metadata.json" }.orEmpty()
        assertTrue("Must NEVER continue or write onto internal storage", internalFiles.isEmpty())
    }

    // =========================================================================
    // 28. UNIT TESTS — SD UNMOUNTED AFTER COMPLETION
    // =========================================================================

    @Test
    fun testSdUnmountedAfterCompletion_retainsMetadataFallsBackToStreamAndRestoresOnRemount() {
        val bytes = ByteArray(3072) { 0x33 }
        val httpStreamRequests = AtomicInteger(0)

        val manager = PinnedDownloadManager(
            store = store,
            streamOpener = { _, _ ->
                httpStreamRequests.incrementAndGet()
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            },
            volumeProvider = volumeProvider,
        )
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)

        val trackB = createTrack(5001, "SD Persistent Track", bytes.size.toLong())
        manager.startDownload(trackB)
        assertTrue(waitForOfflineReady(manager, 5001L))
        assertEquals(1, httpStreamRequests.get())

        // Simulate SD unmount after completion
        volumeProvider.setRemovableSdState(mounted = false, writable = false)

        // 1. Metadata must NOT be deleted when reconcile() or reconcileMissingAndUnsafe() runs
        val removedIds = store.reconcile()
        assertFalse("SD record must NOT be purged while SD is unmounted", removedIds.contains(5001L))
        store.reconcileMissingAndUnsafe()
        assertNotNull("SD record must remain in metadata store", store.getRecord(5001L))

        // 2. Status is OFFLINE_MEDIA_UNAVAILABLE (not OFFLINE_READY)
        assertFalse(manager.isOfflineReady(5001L))
        assertTrue(manager.isOfflineMediaUnavailable(5001L))
        assertEquals(DownloadState.OFFLINE_MEDIA_UNAVAILABLE, manager.getDownloadStatus(5001L).state)

        // 3. Playback resolver skips local file and falls back to remote stream
        assertNull("Local playback file must be null while SD unmounted", manager.resolveLocalPlaybackFile(5001L))
        val fallbackItem = manager.resolvePlaybackMediaItem(trackB)
        assertEquals("REMOTE_STREAM", fallbackItem.mediaMetadata.extras?.getString("playback_source"))
        assertEquals("http", fallbackItem.localConfiguration?.uri?.scheme)

        // 4. Bulk download & Smart Playlist Sync do NOT redownload Track B while SD is unmounted
        val bulkClassification = manager.classifyTracksForDownload(listOf(trackB))
        assertEquals(1, bulkClassification.alreadyOfflineTracks.size)
        assertTrue("Must NOT enqueue Track B for redownload while SD is unmounted", bulkClassification.eligibleNewTracks.isEmpty())

        val syncCoordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            prefs = prefs,
        )
        val playlistClassification = syncCoordinator.classifyPlaylist(
            playlistId = 10,
            playlistName = "SD Playlist",
            tracks = listOf(trackB),
        )
        assertEquals(1, playlistClassification.alreadyOfflineTracks.size)
        assertTrue("Smart Sync must NOT redownload Track B while SD is unmounted", playlistClassification.eligibleNewTracks.isEmpty())
        assertEquals(1, httpStreamRequests.get())

        // 5. Simulate SD remount: Track B automatically returns to OFFLINE_READY and plays locally
        volumeProvider.setRemovableSdState(mounted = true, writable = true)

        assertTrue("Track B must return to OFFLINE_READY on SD remount", manager.isOfflineReady(5001L))
        assertFalse(manager.isOfflineMediaUnavailable(5001L))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(5001L).state)
        val remountedLocalFile = manager.resolveLocalPlaybackFile(5001L)
        assertNotNull(remountedLocalFile)
        assertTrue(remountedLocalFile!!.exists())
        val remountedMediaItem = manager.resolvePlaybackMediaItem(trackB)
        assertNotNull(remountedMediaItem)
        assertEquals("PINNED_DOWNLOAD", remountedMediaItem?.mediaMetadata?.extras?.getString("playback_source"))
        assertEquals("file", remountedMediaItem?.localConfiguration?.uri?.scheme)
    }

    // =========================================================================
    // 29. UNIT TESTS — SELECTIVE DELETE
    // =========================================================================

    @Test
    fun testSelectiveDelete_deletesOnlyTargetVolumeCopy() {
        val bytesA = ByteArray(1500) { 0x11 }
        val bytesB = ByteArray(2500) { 0x22 }

        val manager = PinnedDownloadManager(
            store = store,
            streamOpener = { track, _ ->
                val b = if (track.id == 6001) bytesA else bytesB
                Pair(ByteArrayInputStream(b), b.size.toLong())
            },
            volumeProvider = volumeProvider,
        )

        // Download Track A (6001) to INTERNAL
        manager.setOfflineStorageTarget(OfflineStorageTarget.INTERNAL)
        manager.startDownload(createTrack(6001, "Internal Delete Test", bytesA.size.toLong()))
        assertTrue(waitForOfflineReady(manager, 6001L))

        // Download Track B (6002) to REMOVABLE_SD
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)
        manager.startDownload(createTrack(6002, "SD Delete Test", bytesB.size.toLong()))
        assertTrue(waitForOfflineReady(manager, 6002L))

        val fileA = File(internalOfflineDir, manager.getRecord(6001L)!!.relativeFileName)
        val fileB = File(sdOfflineDir, manager.getRecord(6002L)!!.relativeFileName)
        assertTrue(fileA.exists())
        assertTrue(fileB.exists())

        // Delete Track B -> B removed from SD, A untouched on internal
        assertTrue(manager.deleteDownload(6002L))
        assertFalse("Track B file must be deleted from SD", fileB.exists())
        assertNull(manager.getRecord(6002L))
        assertTrue("Track A file on internal must be untouched", fileA.exists())
        assertTrue(manager.isOfflineReady(6001L))

        // Re-download Track B to SD, then delete Track A -> A removed from internal, B untouched on SD
        manager.startDownload(createTrack(6002, "SD Delete Test", bytesB.size.toLong()))
        assertTrue(waitForOfflineReady(manager, 6002L))
        val fileB2 = File(sdOfflineDir, manager.getRecord(6002L)!!.relativeFileName)
        assertTrue(fileB2.exists())

        assertTrue(manager.deleteDownload(6001L))
        assertFalse("Track A file must be deleted from internal", fileA.exists())
        assertNull(manager.getRecord(6001L))
        assertTrue("Track B file on SD must be untouched", fileB2.exists())
        assertTrue(manager.isOfflineReady(6002L))
    }

    // =========================================================================
    // 30. UNIT TESTS — TARGET-VOLUME FREE SPACE RESERVE
    // =========================================================================

    @Test
    fun testTargetVolumeFreeSpaceReserve_checksSelectedTargetVolumeNotInternalWhenSdSelected() {
        val mb = 1024L * 1024L
        val gb = 1024L * mb

        val manager = PinnedDownloadManager(
            store = store,
            volumeProvider = volumeProvider,
        )
        manager.setOfflineStorageTarget(OfflineStorageTarget.REMOVABLE_SD)

        // Case 1: internal free = 20 GB, SD free = 400 MB, target = REMOVABLE_SD, required = 50 MB -> BLOCKED
        volumeProvider.setInternalAvailableBytes(20L * gb)
        volumeProvider.setRemovableSdAvailableBytes(400L * mb)

        val track50Mb = createTrack(7001, "50MB Track", 50L * mb)
        val res1 = manager.checkSingleDownloadStorage(track50Mb)
        assertFalse("Must block when SD free space < required + 500 MB reserve", res1.isAllowed)
        assertEquals(StoragePrecheckStatus.BLOCKED_LOW_FREE_SPACE, res1.status)
        assertEquals("Không đủ dung lượng bộ nhớ", res1.title)

        // Case 2: internal free = 400 MB, SD free = 20 GB, target = REMOVABLE_SD, required = 50 MB -> ALLOWED
        volumeProvider.setInternalAvailableBytes(400L * mb)
        volumeProvider.setRemovableSdAvailableBytes(20L * gb)

        val res2 = manager.checkSingleDownloadStorage(track50Mb)
        assertTrue("Must allow SD download when SD has 20 GB free even if internal has 400 MB free", res2.isAllowed)
        assertEquals(StoragePrecheckStatus.ALLOWED, res2.status)

        // Case 3: When target = INTERNAL, internal free = 400 MB -> BLOCKED
        manager.setOfflineStorageTarget(OfflineStorageTarget.INTERNAL)
        val res3 = manager.checkSingleDownloadStorage(track50Mb)
        assertFalse("Must block internal download when internal has 400 MB free", res3.isAllowed)
        assertEquals(StoragePrecheckStatus.BLOCKED_LOW_FREE_SPACE, res3.status)
        assertEquals("Không đủ dung lượng bộ nhớ", res3.title)
    }

    private fun waitForOfflineReady(manager: PinnedDownloadManager, trackId: Long, timeoutMs: Long = 3000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (manager.isOfflineReady(trackId)) return true
            Thread.sleep(20)
        }
        return manager.isOfflineReady(trackId)
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val map = HashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toMutableSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearFlag = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearFlag) map.clear()
                for ((k, v) in pending) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }
}
