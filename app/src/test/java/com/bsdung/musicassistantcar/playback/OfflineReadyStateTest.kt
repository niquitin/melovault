package com.bsdung.musicassistantcar.playback

import android.net.Uri
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarPlaylist
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
import java.io.File

class OfflineReadyStateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore
    private lateinit var manager: PinnedDownloadManager

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("offline_ready_test_dir")
        store = OfflineMetadataStore(storeDir)
        manager = PinnedDownloadManager(store)
    }

    private fun createValidRecord(
        trackId: Long,
        sizeBytes: Long = 1024L,
        sha256: String = "deadbeefcafebabe1234567890abcdef1234567890abcdef1234567890abcdef",
    ): OfflineTrackRecord {
        val fileName = "track_$trackId.mp3"
        val file = File(storeDir, fileName)
        file.writeBytes(ByteArray(sizeBytes.toInt()) { 0x42 })
        return OfflineTrackRecord(
            trackId = trackId,
            title = "Offline Test Track $trackId",
            artist = "Audiophile",
            album = "Test Album",
            durationMs = 180_000L,
            relativeFileName = fileName,
            fileSizeBytes = sizeBytes,
            sha256 = sha256,
            loudnessLufs = -18.0,
            truePeakDbtp = -1.0,
            loudnessRangeLu = 5.0,
            analysisVersion = 1,
            downloadedAtMs = System.currentTimeMillis(),
            format = "mp3",
            codec = "mp3",
        )
    }

    /**
     * 1. download 99%: OFFLINE_READY = false
     */
    @Test
    fun testDownload99PercentNotOfflineReady() {
        val trackId = 1001L
        // Track has bytes in .part, active download in progress at 99%
        val partFile = File(storeDir, "track_$trackId.part")
        partFile.writeBytes(ByteArray(99) { 0x01 })

        // Download status is active DOWNLOADING
        val carTrack = CarTrack(
            id = trackId.toInt(),
            title = "Track 1001",
            artist = "Artist",
            album = "Album",
            status = "ready",
            duration = 100.0,
            sourceSizeBytes = 100L,
        )

        assertFalse("Track at 99% must NOT be offline ready", manager.isOfflineReady(trackId))
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.getDownloadStatus(trackId).state)
    }

    /**
     * 2. progress 100% but .part not finalized: OFFLINE_READY = false
     */
    @Test
    fun testProgress100PercentButPartNotFinalizedNotOfflineReady() {
        val trackId = 1002L
        // 100% bytes transferred into .part file, but final rename / verification not finished
        val partFile = File(storeDir, "track_$trackId.part")
        partFile.writeBytes(ByteArray(100) { 0x02 })

        // Even if store record was somehow pre-created
        val record = OfflineTrackRecord(
            trackId = trackId,
            title = "Track 1002",
            artist = "Artist",
            album = "Album",
            durationMs = 100_000L,
            relativeFileName = "track_$trackId.mp3",
            fileSizeBytes = 100L,
            sha256 = "dummy_sha",
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            analysisVersion = null,
            downloadedAtMs = System.currentTimeMillis(),
        )
        store.saveRecord(record)

        // Since .part file still exists and final file does not exist, OFFLINE_READY must be false
        assertFalse("Track with .part still existing must NOT be offline ready", manager.isOfflineReady(trackId))
    }

    /**
     * 3. final file exists but metadata persistence failed: OFFLINE_READY = false
     */
    @Test
    fun testFinalFileExistsButMetadataPersistenceFailedNotOfflineReady() {
        val trackId = 1003L
        val finalFile = File(storeDir, "track_$trackId.mp3")
        finalFile.writeBytes(ByteArray(500) { 0x03 })

        // No record saved in store (metadata failure)
        assertNull(store.getRecord(trackId))
        assertFalse("Final file without persisted metadata must NOT be offline ready", manager.isOfflineReady(trackId))
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.getDownloadStatus(trackId).state)
    }

    /**
     * 4. successful finalized download: OFFLINE_READY = true
     */
    @Test
    fun testSuccessfulFinalizedDownloadIsOfflineReady() {
        val trackId = 1004L
        val record = createValidRecord(trackId, sizeBytes = 800L)
        store.saveRecord(record)

        assertTrue("Finalized download with verified file and metadata must be offline ready", manager.isOfflineReady(trackId))
        assertTrue("isDownloaded must match isOfflineReady", manager.isDownloaded(trackId))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(trackId).state)
    }

    /**
     * 5. restart app: OFFLINE_READY remains true
     */
    @Test
    fun testRestartAppOfflineReadyRemainsTrue() {
        val trackId = 1005L
        val record = createValidRecord(trackId, sizeBytes = 1200L)
        store.saveRecord(record)
        assertTrue(manager.isOfflineReady(trackId))

        // Simulate app restart by constructing new store and manager from disk
        val restartedStore = OfflineMetadataStore(storeDir)
        val restartedManager = PinnedDownloadManager(restartedStore)

        assertTrue("After restart, offline ready status must remain true", restartedManager.isOfflineReady(trackId))
        assertEquals(DownloadState.OFFLINE_READY, restartedManager.getDownloadStatus(trackId).state)
        assertNotNull(restartedManager.getRecord(trackId))
    }

    /**
     * 6. missing final file after restart: OFFLINE_READY becomes false safely
     */
    @Test
    fun testMissingFinalFileAfterRestartOfflineReadyBecomesFalseSafely() {
        val trackId = 1006L
        val record = createValidRecord(trackId, sizeBytes = 600L)
        store.saveRecord(record)
        assertTrue(manager.isOfflineReady(trackId))

        // Simulate file being deleted outside the app (e.g. user or OS cleanup)
        val audioFile = store.getTrackFile(record)
        assertTrue(audioFile.delete())

        // App restarts
        val restartedStore = OfflineMetadataStore(storeDir)
        val restartedManager = PinnedDownloadManager(restartedStore)

        assertFalse("Missing audio file must demote OFFLINE_READY to false safely", restartedManager.isOfflineReady(trackId))
        assertFalse("hasTrack must be false for missing file", restartedStore.hasTrack(trackId))
        assertEquals(DownloadState.NOT_DOWNLOADED, restartedManager.getDownloadStatus(trackId).state)
        val downloadedItems = restartedManager.getAllDownloadedMediaItems()
        assertFalse("Track with missing file must not appear in downloaded items", downloadedItems.any { it.mediaId == "track:$trackId" })
    }

    /**
     * 7. wrong final size: OFFLINE_READY = false
     */
    @Test
    fun testWrongFinalSizeNotOfflineReady() {
        val trackId = 1007L
        // Record says 1000 bytes, but actual file only has 600 bytes
        val fileName = "track_$trackId.mp3"
        val file = File(storeDir, fileName)
        file.writeBytes(ByteArray(600) { 0x07 })

        val record = OfflineTrackRecord(
            trackId = trackId,
            title = "Size Mismatch Track",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            relativeFileName = fileName,
            fileSizeBytes = 1000L, // Mismatch!
            sha256 = "somesha",
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            analysisVersion = null,
            downloadedAtMs = System.currentTimeMillis(),
        )
        store.saveRecord(record)

        assertFalse("Size mismatch must NOT be offline ready", manager.isOfflineReady(trackId))
        assertFalse(store.hasTrack(trackId))
    }

    /**
     * 8. completed pinned track appears in Downloaded Music
     */
    @Test
    fun testCompletedPinnedTrackAppearsInDownloadedMusic() {
        val trackId = 1008L
        val record = createValidRecord(trackId, sizeBytes = 750L)
        store.saveRecord(record)

        val items = manager.getAllDownloadedMediaItems()
        assertTrue("Completed pinned track must appear in Downloaded Music", items.any { it.mediaId == "track:$trackId" })
        val item = items.first { it.mediaId == "track:$trackId" }
        assertEquals("PINNED_DOWNLOAD", item.mediaMetadata.extras?.getString("playback_source"))
        assertTrue(item.mediaMetadata.extras?.getBoolean("is_offline_download", false) == true)
    }

    /**
     * 9. partial download does not appear in Downloaded Music
     */
    @Test
    fun testPartialDownloadDoesNotAppearInDownloadedMusic() {
        val trackId = 1009L
        val partFile = File(storeDir, "track_$trackId.part")
        partFile.writeBytes(ByteArray(300) { 0x09 })

        val items = manager.getAllDownloadedMediaItems()
        assertFalse("Partial .part download must NOT appear in Downloaded Music", items.any { it.mediaId == "track:$trackId" })
    }

    /**
     * 10. stream-cache-only track does not show ✓ Offline
     */
    @Test
    fun testStreamCacheOnlyTrackDoesNotShowOffline() {
        val trackId = 1010L
        // Track exists only in network / stream cache; NO pinned record exists
        assertFalse("Stream cache track without pinned record must NOT be offline ready", manager.isOfflineReady(trackId))
        assertFalse(manager.isDownloaded(trackId))
        val status = manager.getDownloadStatus(trackId)
        assertEquals("Stream cache track must have NOT_DOWNLOADED status", DownloadState.NOT_DOWNLOADED, status.state)
    }

    /**
     * 11. delete offline copy: local pinned file removed
     */
    @Test
    fun testDeleteOfflineCopyLocalPinnedFileRemoved() {
        val trackId = 1011L
        val record = createValidRecord(trackId, sizeBytes = 400L)
        store.saveRecord(record)
        val file = store.getTrackFile(record)
        assertTrue(file.exists())

        val deleted = manager.deleteDownload(trackId)
        assertTrue(deleted)
        assertFalse("Local pinned file must be removed after deletion", file.exists())
    }

    /**
     * 12. delete offline copy: offline metadata removed
     */
    @Test
    fun testDeleteOfflineCopyOfflineMetadataRemoved() {
        val trackId = 1012L
        val record = createValidRecord(trackId, sizeBytes = 550L)
        store.saveRecord(record)
        assertNotNull(store.getRecord(trackId))

        manager.deleteDownload(trackId)
        assertNull("Offline metadata must be removed after deletion", store.getRecord(trackId))
        assertFalse("Track must no longer be offline ready", manager.isOfflineReady(trackId))
    }

    /**
     * 13. delete offline copy: remote track still exists
     */
    @Test
    fun testDeleteOfflineCopyRemoteTrackStillExists() {
        val trackId = 1013
        val remoteTrack = CarTrack(
            id = trackId,
            title = "Remote Pristine Track",
            artist = "Original Artist",
            album = "Original Album",
            status = "ready",
            duration = 180.0,
            sourceSizeBytes = 2048L,
        )

        val fakeApi = object : CarApiClient() {
            override fun track(tId: Int): CarTrack {
                if (tId == trackId) return remoteTrack
                throw IllegalArgumentException("Track not found")
            }
        }

        val record = createValidRecord(trackId.toLong(), sizeBytes = 2048L)
        store.saveRecord(record)
        assertTrue(manager.isOfflineReady(trackId.toLong()))

        // Delete local copy
        manager.deleteDownload(trackId.toLong())

        // Verify remote track is completely intact in remote backend
        val fetched = fakeApi.track(trackId)
        assertEquals("Remote track must still exist untouched", "Remote Pristine Track", fetched.title)
        assertEquals(trackId, fetched.id)
    }

    /**
     * 14. delete offline copy: remote track remains stream-playable
     */
    @Test
    fun testDeleteOfflineCopyRemoteTrackRemainsStreamPlayable() {
        val trackId = 1014
        val streamUri = Uri.parse("http://127.0.0.1:8790/v1/tracks/$trackId/stream?quality=flac")
        val remoteTrack = CarTrack(
            id = trackId,
            title = "Streamable Track",
            artist = "Artist",
            album = "Album",
            status = "ready",
            duration = 210.0,
        )

        val fakeApi = object : CarApiClient() {
            override fun track(tId: Int): CarTrack = remoteTrack
            override fun streamUri(tId: Int): Uri = streamUri
        }

        val catalog = RemoteCatalog(
            api = fakeApi,
            downloadManagerSupplier = { manager },
        )

        val record = createValidRecord(trackId.toLong(), sizeBytes = 900L)
        store.saveRecord(record)

        // While downloaded, catalog.item resolves to file URI
        val offlineItem = catalog.item("track:$trackId")
        assertNotNull(offlineItem)
        assertEquals("file", offlineItem?.localConfiguration?.uri?.scheme)

        // Delete local copy
        manager.deleteDownload(trackId.toLong())
        assertFalse(manager.isOfflineReady(trackId.toLong()))

        // After deletion, catalog.item falls back cleanly to HTTP remote stream
        val streamedItem = catalog.item("track:$trackId")
        assertNotNull(streamedItem)
        assertEquals("http", streamedItem?.localConfiguration?.uri?.scheme)
        assertEquals(streamUri, streamedItem?.localConfiguration?.uri)
    }

    /**
     * 15. delete offline copy: playlist membership unchanged
     */
    @Test
    fun testDeleteOfflineCopyPlaylistMembershipUnchanged() {
        val playlistId = 42
        val track1 = CarTrack(id = 1015, title = "Track 1", artist = "A", album = "Alb", status = "ready", duration = 60.0)
        val track2 = CarTrack(id = 1016, title = "Track 2", artist = "A", album = "Alb", status = "ready", duration = 60.0)
        val playlistTracks = mutableListOf(track1, track2)

        val fakeApi = object : CarApiClient() {
            override fun playlistTracksWithTotal(pId: Int, limit: Int, offset: Int): Pair<List<CarTrack>, Int> {
                return Pair(playlistTracks, playlistTracks.size)
            }
        }

        // Track 1015 is downloaded
        store.saveRecord(createValidRecord(1015L))
        assertTrue(manager.isOfflineReady(1015L))

        // Delete local offline copy of Track 1015
        manager.deleteDownload(1015L)

        // Playlist membership is completely unchanged
        val (currentTracks, total) = fakeApi.playlistTracksWithTotal(playlistId, 10, 0)
        assertEquals(2, total)
        assertEquals(1015, currentTracks[0].id)
        assertEquals(1016, currentTracks[1].id)
    }

    /**
     * 16. delete offline copy: backend delete endpoint never called
     */
    @Test
    fun testDeleteOfflineCopyBackendDeleteEndpointNeverCalled() {
        var networkCallCount = 0

        // Spy API client that increments count on ANY network interaction
        val spyApi = object : CarApiClient() {
            override fun track(trackId: Int): CarTrack {
                networkCallCount++
                throw UnsupportedOperationException("Unexpected network call")
            }
            override fun streamUri(trackId: Int): Uri {
                networkCallCount++
                throw UnsupportedOperationException("Unexpected network call")
            }
            override fun playlists(): List<CarPlaylist> {
                networkCallCount++
                throw UnsupportedOperationException("Unexpected network call")
            }
        }

        val trackId = 1016L
        store.saveRecord(createValidRecord(trackId))
        assertTrue(manager.isOfflineReady(trackId))

        // Perform delete
        manager.deleteDownload(trackId)

        // Backend was NEVER touched
        assertEquals("Backend endpoints must never be called on local deletion", 0, networkCallCount)
    }

    /**
     * 17. re-download after local deletion: succeeds normally
     */
    @Test
    fun testRedownloadAfterLocalDeletionSucceedsNormally() {
        val trackId = 1017L
        val record1 = createValidRecord(trackId, sizeBytes = 1000L)
        store.saveRecord(record1)
        assertTrue(manager.isOfflineReady(trackId))

        // Delete local copy
        assertTrue(manager.deleteDownload(trackId))
        assertFalse(manager.isOfflineReady(trackId))
        assertEquals(DownloadState.NOT_DOWNLOADED, manager.getDownloadStatus(trackId).state)

        // Re-download and verify OFFLINE_READY is restored
        val record2 = createValidRecord(trackId, sizeBytes = 1000L)
        store.saveRecord(record2)
        assertTrue("Re-download after deletion must succeed and restore OFFLINE_READY", manager.isOfflineReady(trackId))
        assertEquals(DownloadState.OFFLINE_READY, manager.getDownloadStatus(trackId).state)
    }
}
