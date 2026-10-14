package com.bsdung.musicassistantcar.playback

import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

/**
 * Unit tests for MeloVault v0.6.27 — Background Offline Playlist Sync / WorkManager (Sections 29–47).
 */
class BackgroundOfflinePlaylistSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val edits = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { if (key != null) edits[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { if (key != null) edits[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) edits[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) edits[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) edits[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) edits[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) edits[key] = null }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                for ((k, v) in edits) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Before
    fun setup() {
        // This suite checks the owner's server-backed playlist sync, absent from Shareable.
        // Shareable's account-owned Telegram sync requires its own tests when implemented.
        org.junit.Assume.assumeTrue(com.bsdung.musicassistantcar.BuildConfig.PERSONAL_BACKEND_ENABLED)
        storeDir = tempFolder.newFolder("bg_playlist_sync_test")
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
     * Section 29: Scheduler Zero-Watch Test
     * No playlists watched -> no periodic work scheduled, cancel path invoked safely.
     */
    @Test
    fun test29_schedulerZeroWatches() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        assertTrue(coordinator.getWatchedPlaylistIds().isEmpty())
        assertFalse(coordinator.scheduler.isScheduled())
        assertTrue(backend.cancelCount >= 1)
        assertEquals("Tự đồng bộ nền: chưa bật", coordinator.formatBackgroundSyncInfoLine())

        val scheduledNow = coordinator.ensureBackgroundSyncScheduled()
        assertFalse(scheduledNow)
        assertEquals(0, backend.getScheduledWorkCount())
    }

    /**
     * Section 30: Scheduler First-Watch Test
     * Enable "Luôn giữ offline" on playlist 10 -> unique periodic work melovault_offline_playlist_sync
     * scheduled with interval = 6 hours, policy = KEEP, network constraint = CONNECTED.
     */
    @Test
    fun test30_firstWatchSchedules6HourConnectedWork() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        coordinator.enableWatch(10, "Bolero")
        assertTrue(coordinator.scheduler.isScheduled())
        assertEquals(1, backend.getScheduledWorkCount())
        assertEquals("Tự đồng bộ nền: 6 giờ/lần", coordinator.formatBackgroundSyncInfoLine())

        val spec = coordinator.scheduler.getScheduledSpec()
        assertNotNull(spec)
        assertEquals("melovault_offline_playlist_sync", spec!!.uniqueWorkName)
        assertEquals(6L * 60L * 60L * 1000L, spec.repeatIntervalMs)
        assertEquals(NetworkType.CONNECTED, spec.requiredNetworkType)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, spec.existingWorkPolicy)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
    }

    /**
     * Section 31: Scheduler Multi-Watch Single-Worker Test
     * Enable "Luôn giữ offline" on playlists 10, 20, 30 -> still only 1 unique periodic work.
     */
    @Test
    fun test31_multipleWatchesSingleWorker() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        coordinator.enableWatch(10, "Playlist 10")
        coordinator.enableWatch(20, "Playlist 20")
        coordinator.enableWatch(30, "Playlist 30")

        assertEquals(setOf(10, 20, 30), coordinator.getWatchedPlaylistIds())
        assertEquals("Must have exactly 1 global periodic worker across all watched playlists", 1, backend.getScheduledWorkCount())
        assertTrue(coordinator.scheduler.isScheduled())
        assertEquals(0, backend.replacedExistingCount)
    }

    /**
     * Section 32: Scheduler Disable / Last-Watch Cancel Test
     * Playlists 10 and 20 watched. Disable 10 -> periodic work remains scheduled.
     * Disable 20 -> watched set becomes empty -> periodic work is cancelled.
     */
    @Test
    fun test32_disableWatchesKeepsUntilLastThenCancels() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        coordinator.enableWatch(10, "Playlist 10")
        coordinator.enableWatch(20, "Playlist 20")
        assertTrue(coordinator.scheduler.isScheduled())

        coordinator.disableWatch(10)
        assertEquals(setOf(20), coordinator.getWatchedPlaylistIds())
        assertTrue("Periodic work must remain scheduled while playlist 20 is still watched", coordinator.scheduler.isScheduled())
        assertEquals(1, backend.getScheduledWorkCount())

        coordinator.disableWatch(20)
        assertTrue(coordinator.getWatchedPlaylistIds().isEmpty())
        assertFalse("Periodic work must be cancelled when last watched playlist is disabled", coordinator.scheduler.isScheduled())
        assertEquals(0, backend.getScheduledWorkCount())
        assertEquals("Tự đồng bộ nền: chưa bật", coordinator.formatBackgroundSyncInfoLine())
    }

    /**
     * Section 33: Scheduler Idempotence Test
     * Call scheduler.ensureScheduled() 5 times while 1 playlist is watched -> still 1 unique periodic work,
     * existing schedule kept (KEEP), timer not reset.
     */
    @Test
    fun test33_schedulerIdempotence() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        coordinator.enableWatch(10, "Playlist 10")
        val initialSpec = coordinator.scheduler.getScheduledSpec()
        assertNotNull(initialSpec)

        repeat(5) {
            val active = coordinator.scheduler.ensureScheduled()
            assertTrue(active)
        }

        assertEquals(1, backend.getScheduledWorkCount())
        assertSame("KEEP policy must preserve existing scheduled work without resetting timer", initialSpec, coordinator.scheduler.getScheduledSpec())
        assertEquals(0, backend.replacedExistingCount)
    }

    /**
     * Section 34: Background Discovery Enqueues Only New Tracks
     * Playlist 10 watched; tracks [1, 2, 3] already OFFLINE_READY.
     * Remote playlist later returns [1, 2, 3, 4, 5].
     * Run OfflinePlaylistSyncWorker once:
     * - only 4 and 5 enqueued in order [4, 5]
     * - 1, 2, 3 not re-downloaded
     * - worker returns Result.success()
     */
    @Test
    fun test34_backgroundDiscoveryEnqueuesOnlyNewTracks() {
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
                val bytes = ByteArray(8_000) { 4 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(10, "Watched 10")

        val remoteTracks = listOf(
            sampleTrack(1),
            sampleTrack(2),
            sampleTrack(3),
            sampleTrack(4),
            sampleTrack(5),
        )
        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 10, name = "Watched 10", description = "", trackCount = 5)
            )
            override fun fetchCollectionPlayback(
                kind: String,
                artist: String?,
                album: String?,
                root: Int?,
                path: String?,
                playlistId: Int?,
                sort: String,
                recursive: Boolean?,
            ): CollectionPlaybackResult {
                return CollectionPlaybackResult(
                    items = remoteTracks,
                    total = 5,
                    playable = 5,
                    skipped = 0,
                )
            }
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(
            coordinator = coordinator,
            apiClient = fakeApi,
            runAttemptCount = 0,
            nowMs = 123_456L,
        )

        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(2, workerRes.enqueuedTrackCount)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(4, 5), downloadedOrder)
        assertFalse(downloadedOrder.contains(1))
        assertFalse(downloadedOrder.contains(2))
        assertFalse(downloadedOrder.contains(3))
        assertEquals(123_456L, coordinator.getLastBackgroundSyncAttemptAtMs())
        assertEquals(123_456L, coordinator.getLastBackgroundSyncSuccessAtMs())
    }

    /**
     * Section 35: Repeated Worker Run Does Not Duplicate
     * Run worker while track 4 is DOWNLOADING and 5 is QUEUED, and again after both are OFFLINE_READY.
     * Verify:
     * - 0 duplicate jobs
     * - queueSequence preserved
     * - activeBatch preserved
     * - no duplicate .flac or .part
     */
    @Test
    fun test35_repeatedWorkerRunNoDuplicates() {
        val startedLatch = CountDownLatch(1)
        val holdLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val openerCallsFor4 = AtomicInteger(0)

        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                if (track.id == 4) {
                    openerCallsFor4.incrementAndGet()
                    startedLatch.countDown()
                    holdLatch.await(5, TimeUnit.SECONDS)
                }
                val bytes = ByteArray(4_000) { 5 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                doneLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(10, "Watched 10")

        val remoteTracks = listOf(sampleTrack(4), sampleTrack(5))
        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 10, name = "Watched 10", description = "", trackCount = 2)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(remoteTracks, 2, 2, 0)
        }

        val firstRun = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(2, firstRun.enqueuedTrackCount)
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS))

        val batchBefore = manager.getActiveBatch()
        val seq4Before = manager.getJobQueueSequence(4L)
        val seq5Before = manager.getJobQueueSequence(5L)

        // Second run while 4 is DOWNLOADING and 5 is QUEUED
        val secondRun = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(0, secondRun.enqueuedTrackCount)
        assertSame(batchBefore, manager.getActiveBatch())
        assertEquals(seq4Before, manager.getJobQueueSequence(4L))
        assertEquals(seq5Before, manager.getJobQueueSequence(5L))

        holdLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))

        // Third run after both are OFFLINE_READY
        val thirdRun = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(0, thirdRun.enqueuedTrackCount)
        assertEquals(1, openerCallsFor4.get())
        assertTrue(storeDir.listFiles()?.none { it.name.endsWith(".part") } == true)
    }

    /**
     * Section 36: Multiple Watched Playlists Dedup in Worker
     * Watched playlist A: [101, 102, 103]
     * Watched playlist B: [102, 103, 104]
     * Run worker once:
     * - total enqueued tracks = 4 (101, 102, 103, 104)
     * - 102 and 103 enqueued once only
     * - both playlists become SYNCED after download completion
     */
    @Test
    fun test36_multiplePlaylistDedup() {
        val downloaded = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val latch = CountDownLatch(4)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { track, _ ->
                downloaded.add(track.id)
                val bytes = ByteArray(4_000) { 6 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                latch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(1, "Playlist A", nowMs = 1000L)
        coordinator.enableWatch(2, "Playlist B", nowMs = 2000L)

        val tracksA = listOf(sampleTrack(101), sampleTrack(102), sampleTrack(103))
        val tracksB = listOf(sampleTrack(102), sampleTrack(103), sampleTrack(104))

        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 1, name = "Playlist A", description = "", trackCount = 3),
                CarPlaylist(id = 2, name = "Playlist B", description = "", trackCount = 3),
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult {
                val items = if (playlistId == 1) tracksA else tracksB
                return CollectionPlaybackResult(items, items.size, items.size, 0)
            }
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(4, workerRes.enqueuedTrackCount)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(101, 102, 103, 104), downloaded)

        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(1, "Playlist A", tracksA).state)
        assertEquals(PlaylistSyncState.SYNCED, coordinator.getPlaylistSyncStatus(2, "Playlist B", tracksB).state)
    }

    /**
     * Section 37: Worker + WIFI_ONLY on Cellular
     * Policy = WIFI_ONLY, active network = cellular only (CONNECTED true, WIFI false).
     * Watched playlist gains 2 new remote tracks.
     * Run worker:
     * - worker fetches metadata and enqueues 2 tracks
     * - tracks enter WAITING_FOR_WIFI
     * - 0 audio bytes transferred
     * - PARTIAL_WAKE_LOCK not held
     * - worker returns Result.success()
     * Then switch network to validated Wi-Fi:
     * - existing download engine resumes and completes both tracks
     */
    @Test
    fun test37_wifiOnlyOnCellularEntersWaitingForWifiNoBytesNoWakeLock() {
        val resolver = SmartOfflinePlaylistSyncTest.ControllableNetworkPolicyResolver(
            NetworkTransportInfo(
                hasWifiTransport = false,
                hasCellularTransport = true,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        val wakeLock = SmartOfflinePlaylistSyncTest.FakeWakeLock()
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
                val bytes = ByteArray(4_000) { 7 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(50, "WiFi Only Sync")

        val remoteTracks = listOf(sampleTrack(501), sampleTrack(502))
        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 50, name = "WiFi Only Sync", description = "", trackCount = 2)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(remoteTracks, 2, 2, 0)
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(2, workerRes.enqueuedTrackCount)
        assertEquals(0, openerCalls.get())
        assertEquals(0, wakeLock.acquireCount.get())
        assertFalse(wakeLock.isHeld)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(501L).state)
        assertEquals(DownloadState.WAITING_FOR_WIFI, manager.getDownloadStatus(502L).state)

        // Switch network to validated Wi-Fi -> existing download engine resumes and completes both
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
        assertTrue(manager.isOfflineReady(501L))
        assertTrue(manager.isOfflineReady(502L))
        assertFalse(wakeLock.isHeld)
    }

    /**
     * Section 38: Worker + ANY_NETWORK on Cellular
     * Policy = ANY_NETWORK, active network = cellular.
     * Watched playlist gains 1 new remote track.
     * Run worker -> track is enqueued and downloaded by existing engine -> reaches OFFLINE_READY.
     */
    @Test
    fun test38_anyNetworkOnCellularAllowsEngineDownload() {
        val resolver = SmartOfflinePlaylistSyncTest.ControllableNetworkPolicyResolver(
            NetworkTransportInfo(
                hasWifiTransport = false,
                hasCellularTransport = true,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        val completedLatch = CountDownLatch(1)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.ANY_NETWORK,
            streamOpener = { _, _ ->
                val bytes = ByteArray(4_000) { 8 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(60, "Any Network Sync")

        val remoteTracks = listOf(sampleTrack(601))
        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 60, name = "Any Network Sync", description = "", trackCount = 1)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(remoteTracks, 1, 1, 0)
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(1, workerRes.enqueuedTrackCount)
        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager.isOfflineReady(601L))
    }

    /**
     * Section 39: Worker + No Network Constraint
     * Verify WorkManager constraint is configured with NetworkType.CONNECTED (not UNMETERED).
     */
    @Test
    fun test39_noNetworkConstraintConfiguredConnected() {
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )
        coordinator.enableWatch(70, "Offline Constraint Check")

        val spec = coordinator.scheduler.getScheduledSpec()
        assertNotNull(spec)
        assertEquals(NetworkType.CONNECTED, spec!!.requiredNetworkType)
        assertFalse("WorkManager constraint must NOT be UNMETERED", spec.requiredNetworkType == NetworkType.UNMETERED)
    }

    /**
     * Section 40: Worker + Storage Cap / Reserve Block
     * Configure cap or free space so new playlist track exceeds allowed storage.
     * Run worker:
     * - 0 tracks enqueued
     * - no .part file created
     * - no UI dialog attempted
     * - playlist remains watched (STORAGE_BLOCKED recorded passively)
     * - worker returns Result.success()
     */
    @Test
    fun test40_storageBlockPassiveNoAlertDialogSafeCompletion() {
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ -> Pair(ByteArrayInputStream(ByteArray(1000)), 1000L) }
        )
        manager.setAvailableSpaceProviderForTesting { PinnedDownloadManager.MIN_FREE_RESERVE_BYTES + 500_000L }

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(80, "Low Storage Playlist")

        val remoteTracks = listOf(sampleTrack(801, sizeBytes = 5_000_000L))
        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 80, name = "Low Storage Playlist", description = "", trackCount = 1)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(remoteTracks, 1, 1, 0)
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(coordinator, fakeApi, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(0, workerRes.enqueuedTrackCount)
        assertTrue(coordinator.isPlaylistWatched(80))
        assertTrue(coordinator.getWatchedPlaylistRecord(80)?.isStorageBlocked == true)
        assertFalse(File(storeDir, "track_801.part").exists())
        assertEquals(
            PlaylistSyncState.STORAGE_BLOCKED,
            coordinator.getPlaylistSyncStatus(80, "Low Storage Playlist", remoteTracks).state
        )
    }

    /**
     * Section 41: Worker + Remote Fetch Failure
     * Simulate /v1/playlists or playlist track fetch throwing IOException / HTTP 503,
     * as well as Auth/Config failure (401/403 or missing config).
     * Verify:
     * - watched playlist IDs preserved
     * - local files untouched
     * - bounded retry (RETRY on attempt 0 and 1, SUCCESS at attempt >= 2)
     * - auth/config failure returns SUCCESS immediately without retry loop
     */
    @Test
    fun test41_fetchFailurePreservesWatchesAndBoundedRetry() {
        seedOfflineReadyTrack(901L)
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(90, "Resilient Playlist")

        val transientFailApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> {
                throw IOException("HTTP 503 Service Unavailable")
            }
        }

        // Attempt 0 -> RETRY
        val res0 = OfflinePlaylistSyncWorker.executeHeadless(coordinator, transientFailApi, runAttemptCount = 0)
        assertEquals(BackgroundSyncDisposition.RETRY, res0.disposition)
        assertTrue(coordinator.isPlaylistWatched(90))
        assertTrue(manager.isOfflineReady(901L))

        // Attempt 1 -> RETRY
        val res1 = OfflinePlaylistSyncWorker.executeHeadless(coordinator, transientFailApi, runAttemptCount = 1)
        assertEquals(BackgroundSyncDisposition.RETRY, res1.disposition)
        assertTrue(coordinator.isPlaylistWatched(90))

        // Attempt 2 (MAX_RETRY_ATTEMPTS reached) -> SUCCESS (stop retrying until next 6h window)
        val res2 = OfflinePlaylistSyncWorker.executeHeadless(coordinator, transientFailApi, runAttemptCount = 2)
        assertEquals(BackgroundSyncDisposition.SUCCESS, res2.disposition)
        assertEquals("TRANSIENT_FETCH_ERROR_BOUNDED_STOP", res2.diagnostic)
        assertTrue(coordinator.isPlaylistWatched(90))
        assertTrue(manager.isOfflineReady(901L))

        // Auth 401 failure -> immediate SUCCESS (no retry loop, watched state preserved)
        val authFailApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> {
                throw IOException("HTTP 401 Unauthorized")
            }
        }
        val resAuth = OfflinePlaylistSyncWorker.executeHeadless(coordinator, authFailApi, runAttemptCount = 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, resAuth.disposition)
        assertEquals("AUTH_OR_CONFIG_ERROR", resAuth.diagnostic)
        assertTrue(coordinator.isPlaylistWatched(90))
        assertTrue(manager.isOfflineReady(901L))

        // Missing config -> immediate SUCCESS
        val unconfiguredApi = object : CarApiClient() {
            override fun hasValidConfiguration(): Boolean = false
        }
        val resUnconf = OfflinePlaylistSyncWorker.executeHeadless(coordinator, unconfiguredApi, runAttemptCount = 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, resUnconf.disposition)
        assertEquals("CONFIG_UNAVAILABLE", resUnconf.diagnostic)
        assertTrue(coordinator.isPlaylistWatched(90))
    }

    /**
     * Section 42: Worker + Authoritative Deleted Playlist
     * Watched playlists: [10, 20].
     * /v1/playlists succeeds and returns only [10].
     * Run worker:
     * - 20 removed from watched set
     * - 10 remains watched
     * - local files from 20 remain untouched
     * - scheduler remains active because 10 is still watched
     * If /v1/playlists returns []:
     * - 10 removed
     * - watched set becomes empty
     * - scheduler cancels periodic work
     */
    @Test
    fun test42_authoritativeDeletionRemovesWatchAndUpdatesScheduler() {
        seedOfflineReadyTrack(2001L)
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            schedulerBackend = backend,
        )

        coordinator.enableWatch(10, "Playlist 10")
        coordinator.enableWatch(20, "Playlist 20")
        assertTrue(coordinator.scheduler.isScheduled())

        val apiWithOnly10 = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 10, name = "Playlist 10", description = "", trackCount = 1)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(2001)), 1, 1, 0)
        }

        val run1 = OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiWithOnly10, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, run1.disposition)
        assertFalse("Playlist 20 must be removed", coordinator.isPlaylistWatched(20))
        assertTrue("Playlist 10 must remain watched", coordinator.isPlaylistWatched(10))
        assertTrue("Local file 2001 must remain untouched", manager.isOfflineReady(2001L))
        assertTrue("Scheduler must remain active while 10 is watched", coordinator.scheduler.isScheduled())

        // Now /v1/playlists returns empty list []
        val apiEmpty = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = emptyList()
        }
        val run2 = OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiEmpty, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, run2.disposition)
        assertTrue(coordinator.getWatchedPlaylistIds().isEmpty())
        assertFalse("Scheduler must cancel periodic work when watched set becomes empty", coordinator.scheduler.isScheduled())
        assertTrue("Local file 2001 must still remain untouched", manager.isOfflineReady(2001L))
    }

    /**
     * Section 43: Headless Worker Execution Without Activity
     * Instantiate and run worker/coordinator using application/headless components only (MainActivity not created).
     * Verify:
     * - watched playlists loaded from disk
     * - remote playlist fetched
     * - missing track enqueued into PinnedDownloadManager
     * - no reference to MainActivity required
     */
    @Test
    fun test43_headlessWorkerExecutionWithoutActivity() {
        val persistenceFile = File(storeDir, "offline_watched_playlists.json")

        // Writer instance persists watched playlist 55 to disk
        val manager1 = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator1 = OfflinePlaylistSyncCoordinator(
            downloadManager = manager1,
            persistenceFile = persistenceFile,
        )
        coordinator1.enableWatch(55, "Headless Playlist")

        // Fresh headless instance (simulating Worker cold start without MainActivity)
        val completedLatch = CountDownLatch(1)
        val store2 = OfflineMetadataStore(storeDir)
        val manager2 = PinnedDownloadManager(
            store = store2,
            executor = Executors.newSingleThreadExecutor(),
            streamOpener = { _, _ ->
                val bytes = ByteArray(4_000) { 9 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager2.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })
        val headlessCoordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager2,
            persistenceFile = persistenceFile,
        )

        val fakeApi = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(
                CarPlaylist(id = 55, name = "Headless Playlist", description = "", trackCount = 1)
            )
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(5501)), 1, 1, 0)
        }

        val workerRes = OfflinePlaylistSyncWorker.executeHeadless(headlessCoordinator, fakeApi, 0)
        assertEquals(BackgroundSyncDisposition.SUCCESS, workerRes.disposition)
        assertEquals(1, workerRes.enqueuedTrackCount)
        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(manager2.isOfflineReady(5501L))
    }

    /**
     * Section 44: WakeLock Semantics in Background Sync
     * Case A: Worker runs and finds 0 new tracks -> WakeLock acquire count = 0.
     * Case B: Worker runs under WIFI_ONLY on cellular -> tracks enter WAITING_FOR_WIFI -> WakeLock acquire count = 0.
     * Case C: Worker runs and enqueues 1 track on allowed network -> PinnedDownloadManager acquires WakeLock
     * during byte transfer and releases it when queue becomes idle.
     */
    @Test
    fun test44_wakeLockSemanticsInBackgroundSync() {
        seedOfflineReadyTrack(1L)
        val wakeLock = SmartOfflinePlaylistSyncTest.FakeWakeLock()
        val resolver = SmartOfflinePlaylistSyncTest.ControllableNetworkPolicyResolver(
            NetworkTransportInfo(
                hasWifiTransport = true,
                hasCellularTransport = false,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        val completedLatch = CountDownLatch(1)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            wakeLock = wakeLock,
            networkPolicyResolver = resolver,
            initialNetworkPolicy = DownloadNetworkPolicy.WIFI_ONLY,
            streamOpener = { _, _ ->
                val bytes = ByteArray(4_000) { 10 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(10, "WakeLock Test")

        // Case A: 0 new tracks (track 1 already OFFLINE_READY)
        val apiNoNew = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(CarPlaylist(10, "WakeLock Test", "", 1))
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(1)), 1, 1, 0)
        }
        OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiNoNew, 0)
        assertEquals("Case A: 0 new tracks must not acquire WakeLock", 0, wakeLock.acquireCount.get())
        assertFalse(wakeLock.isHeld)

        // Case B: 1 new track (track 2) under WIFI_ONLY on cellular
        resolver.transports = listOf(
            NetworkTransportInfo(
                hasWifiTransport = false,
                hasCellularTransport = true,
                hasVpnTransport = false,
                hasInternetCapability = true,
                isValidated = true,
            )
        )
        val apiWithNew = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(CarPlaylist(10, "WakeLock Test", "", 2))
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(1), sampleTrack(2)), 2, 2, 0)
        }
        OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiWithNew, 0)
        assertEquals("Case B: WAITING_FOR_WIFI must not acquire WakeLock", 0, wakeLock.acquireCount.get())
        assertFalse(wakeLock.isHeld)

        // Case C: Switch to Wi-Fi (or enqueue on allowed network) -> engine acquires WakeLock during transfer and releases when idle
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
        assertTrue("Case C: Engine must acquire WakeLock during active byte transfer", wakeLock.acquireCount.get() >= 1)
        assertFalse("Case C: Engine must release WakeLock when queue becomes idle", wakeLock.isHeld)
    }

    /**
     * Section 45: Foreground Service Semantics
     * Case A: Worker runs and finds 0 missing tracks -> PinnedDownloadService is NOT started.
     * Case B: Worker runs and enqueues >=1 downloadable track -> PinnedDownloadManager starts/coordinates PinnedDownloadService.
     */
    @Test
    fun test45_foregroundServiceNotStartedWhenZeroMissingTracks() {
        seedOfflineReadyTrack(1L)
        val serviceController = SmartOfflinePlaylistSyncTest.FakeServiceController()
        val completedLatch = CountDownLatch(1)
        val manager = PinnedDownloadManager(
            store = store,
            executor = Executors.newSingleThreadExecutor(),
            serviceController = serviceController,
            streamOpener = { _, _ ->
                val bytes = ByteArray(4_000) { 11 }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            }
        )
        manager.addListener(object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
                completedLatch.countDown()
            }
        })

        val coordinator = OfflinePlaylistSyncCoordinator(downloadManager = manager)
        coordinator.enableWatch(10, "Service Test")

        // Case A: 0 missing tracks
        val apiZeroMissing = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(CarPlaylist(10, "Service Test", "", 1))
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(1)), 1, 1, 0)
        }
        val resA = OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiZeroMissing, 0)
        assertEquals(0, resA.enqueuedTrackCount)
        assertEquals("PinnedDownloadService must NOT start when 0 tracks are enqueued", 0, serviceController.activeCalls.get())

        // Case B: 1 missing track
        val apiOneMissing = object : CarApiClient() {
            override fun playlists(): List<CarPlaylist> = listOf(CarPlaylist(10, "Service Test", "", 2))
            override fun fetchCollectionPlayback(
                kind: String, artist: String?, album: String?, root: Int?, path: String?,
                playlistId: Int?, sort: String, recursive: Boolean?,
            ): CollectionPlaybackResult = CollectionPlaybackResult(listOf(sampleTrack(1), sampleTrack(2)), 2, 2, 0)
        }
        val resB = OfflinePlaylistSyncWorker.executeHeadless(coordinator, apiOneMissing, 0)
        assertEquals(1, resB.enqueuedTrackCount)
        assertTrue(completedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("PinnedDownloadService must be activated when tracks are enqueued", serviceController.activeCalls.get() >= 1)
        assertFalse("PinnedDownloadService must become idle after completion", serviceController.isServiceActive)
    }

    /**
     * Section 46: Process Recreation / Startup Scheduler Repair
     * Persist watched playlist IDs on disk.
     * Simulate fresh process startup with new scheduler/coordinator instance.
     * Run startup ensureScheduled() -> verify periodic work is ensured (KEEP).
     */
    @Test
    fun test46_processRecreationSchedulerRepair() {
        val persistenceFile = File(storeDir, "offline_watched_playlists.json")
        val prefs = FakeSharedPreferences()

        val manager1 = PinnedDownloadManager(store = store, executor = Executors.newSingleThreadExecutor())
        val coordinator1 = OfflinePlaylistSyncCoordinator(
            downloadManager = manager1,
            prefs = prefs,
            persistenceFile = persistenceFile,
        )
        coordinator1.enableWatch(1073, "Smoke_1790971479")

        // Fresh process startup with new backend, new manager, new coordinator
        val newBackend = InMemoryWorkSchedulerBackend()
        val store2 = OfflineMetadataStore(storeDir)
        val manager2 = PinnedDownloadManager(store = store2, executor = Executors.newSingleThreadExecutor())
        val coordinator2 = OfflinePlaylistSyncCoordinator(
            downloadManager = manager2,
            prefs = prefs,
            persistenceFile = persistenceFile,
            schedulerBackend = newBackend,
        )

        assertTrue(coordinator2.isPlaylistWatched(1073))
        assertTrue("Constructor + ensureBackgroundSyncScheduled must repair periodic work on startup", coordinator2.ensureBackgroundSyncScheduled())
        assertEquals(1, newBackend.getScheduledWorkCount())
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, newBackend.getScheduledSpec("melovault_offline_playlist_sync")?.existingWorkPolicy)
    }

    /**
     * Section 47: Upgrade Migration From v0.6.26
     * Seed v0.6.26 state:
     * - OFFLINE_READY tracks
     * - storage cap
     * - network policy
     * - watched playlist IDs
     * Initialize v0.6.27 coordinator/scheduler:
     * - all existing state preserved
     * - periodic worker is scheduled because watched playlists exist
     */
    @Test
    fun test47_upgradeMigrationFromV0626PreservesStateAndSchedulesWorker() {
        // 1. Seed v0.6.26 OFFLINE_READY tracks
        seedOfflineReadyTrack(11200L, sizeBytes = 35_000_000L)
        seedOfflineReadyTrack(11201L, sizeBytes = 36_000_000L)
        seedOfflineReadyTrack(11202L, sizeBytes = 37_000_000L)

        // 2. Seed v0.6.26 storage cap and network policy in prefs
        val prefs = FakeSharedPreferences()
        prefs.edit()
            .putLong(PinnedDownloadManager.PREF_OFFLINE_STORAGE_CAP_BYTES, PinnedDownloadManager.CAP_10_GB)
            .putString(PinnedDownloadManager.PREF_DOWNLOAD_NETWORK_POLICY, DownloadNetworkPolicy.WIFI_ONLY.name)
            .apply()

        // 3. Seed v0.6.26 watched playlist file on disk (written before v0.6.27 scheduler existed)
        val persistenceFile = File(storeDir, OfflinePlaylistSyncCoordinator.REGISTRY_FILE_NAME)
        persistenceFile.writeText(
            """
            {
              "watched_playlists": [
                {
                  "playlist_id": 1073,
                  "display_name": "Smoke_1790971479",
                  "enabled_at_ms": 1700000000000,
                  "is_storage_blocked": false,
                  "storage_blocked_message": null
                }
              ]
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )

        // 4. Initialize v0.6.27 PinnedDownloadManager + OfflinePlaylistSyncCoordinator + Scheduler
        val backend = InMemoryWorkSchedulerBackend()
        val manager = PinnedDownloadManager(
            store = OfflineMetadataStore(storeDir),
            executor = Executors.newSingleThreadExecutor(),
            initialNetworkPolicy = DownloadNetworkPolicy.fromString(
                prefs.getString(PinnedDownloadManager.PREF_DOWNLOAD_NETWORK_POLICY, DownloadNetworkPolicy.ANY_NETWORK.name)
            ),
        )
        manager.setOfflineStorageCapBytes(
            prefs.getLong(PinnedDownloadManager.PREF_OFFLINE_STORAGE_CAP_BYTES, PinnedDownloadManager.CAP_UNLIMITED)
        )
        val coordinator = OfflinePlaylistSyncCoordinator(
            downloadManager = manager,
            prefs = prefs,
            persistenceFile = persistenceFile,
            schedulerBackend = backend,
        )

        // 5. Verify all v0.6.26 state is preserved and v0.6.27 periodic worker is scheduled
        assertTrue(manager.isOfflineReady(11200L))
        assertTrue(manager.isOfflineReady(11201L))
        assertTrue(manager.isOfflineReady(11202L))
        assertEquals(PinnedDownloadManager.CAP_10_GB, manager.getOfflineStorageCapBytes())
        assertEquals(DownloadNetworkPolicy.WIFI_ONLY, manager.getDownloadNetworkPolicy())
        assertTrue(coordinator.isPlaylistWatched(1073))
        assertEquals("Smoke_1790971479", coordinator.getWatchedPlaylistRecord(1073)?.displayName)

        assertTrue("v0.6.27 must automatically schedule periodic worker on upgrade when watched playlists exist", coordinator.scheduler.isScheduled())
        val spec = coordinator.scheduler.getScheduledSpec()
        assertNotNull(spec)
        assertEquals("melovault_offline_playlist_sync", spec!!.uniqueWorkName)
        assertEquals(6L * 60L * 60L * 1000L, spec.repeatIntervalMs)
        assertEquals(NetworkType.CONNECTED, spec.requiredNetworkType)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, spec.existingWorkPolicy)
    }
}
