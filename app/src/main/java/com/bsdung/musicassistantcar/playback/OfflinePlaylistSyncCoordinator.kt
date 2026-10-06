package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarPlaylist
import com.bsdung.musicassistantcar.data.CarTrack
import java.io.File

data class WatchedPlaylistRecord(
    val playlistId: Int,
    val displayName: String,
    val enabledAtMs: Long,
    val isStorageBlocked: Boolean = false,
    val storageBlockedMessage: String? = null,
)

enum class PlaylistSyncState {
    OFF,
    SYNCED,
    PARTIAL_UNSUPPORTED,
    SYNCING,
    WAITING_FOR_WIFI,
    STORAGE_BLOCKED,
    HAS_FAILURES,
}

data class PlaylistSyncClassification(
    val playlistId: Int,
    val playlistName: String,
    val totalTracksInPlaylist: Int,
    val eligibleTracks: List<CarTrack>,
    val alreadyOfflineTracks: List<CarTrack>,
    val inProgressTracks: List<CarTrack>,
    val waitingForWifiTracks: List<CarTrack>,
    val failedTracks: List<CarTrack>,
    val cancelledTracks: List<CarTrack>,
    val notDownloadedTracks: List<CarTrack>,
    val eligibleNewTracks: List<CarTrack>,
    val unsupportedCount: Int,
    val knownNewBytes: Long,
    val unknownSizeCount: Int,
) {
    val eligibleCount: Int get() = eligibleTracks.size
    val alreadyOfflineCount: Int get() = alreadyOfflineTracks.size
    val inProgressCount: Int get() = inProgressTracks.size
    val eligibleNewCount: Int get() = eligibleNewTracks.size
    val isAllEligibleOffline: Boolean get() = eligibleCount > 0 && alreadyOfflineCount == eligibleCount && inProgressCount == 0 && eligibleNewCount == 0
}

data class PlaylistSyncStatus(
    val playlistId: Int,
    val playlistName: String,
    val isWatched: Boolean,
    val state: PlaylistSyncState,
    val toggleLabel: String,
    val statusText: String,
    val secondaryStatusText: String? = null,
    val eligibleCount: Int = 0,
    val offlineCount: Int = 0,
    val inProgressCount: Int = 0,
    val waitingForWifiCount: Int = 0,
    val failedCount: Int = 0,
    val missingCount: Int = 0,
    val unsupportedCount: Int = 0,
    val storageBlockedMessage: String? = null,
) {
    fun formatCombinedBarText(): String {
        if (!isWatched || state == PlaylistSyncState.OFF) {
            return "Luôn giữ offline: Tắt"
        }
        val base = "Luôn giữ offline: Bật · $statusText"
        return if (!secondaryStatusText.isNullOrBlank() && !statusText.contains(secondaryStatusText)) {
            "$base · $secondaryStatusText"
        } else {
            base
        }
    }
}

data class ReconcileResult(
    val playlistId: Int,
    val playlistName: String,
    val isWatched: Boolean,
    val enqueuedCount: Int,
    val classification: PlaylistSyncClassification,
    val storagePrecheck: StoragePrecheckResult?,
    val status: PlaylistSyncStatus,
)

data class RemoteSyncSummary(
    val authoritativeListFetched: Boolean,
    val removedDeletedPlaylistIds: List<Int>,
    val reconciledResults: List<ReconcileResult>,
)

data class EnableConfirmationPrompt(
    val title: String,
    val message: String,
    val positiveButtonText: String = "BẬT & ĐỒNG BỘ",
    val negativeButtonText: String = "HỦY",
)

class OfflinePlaylistSyncCoordinator(
    private val downloadManager: PinnedDownloadManager,
    private val store: OfflineMetadataStore = downloadManager.getMetadataStore(),
    private val prefs: SharedPreferences? = null,
    private val playlistTracksFetcher: ((Int, CarApiClient) -> Pair<List<CarTrack>, Int>)? = null,
) {
    private val watchedPlaylists = LinkedHashMap<Int, WatchedPlaylistRecord>()
    private val persistenceFile: File = File(store.getStorageDirectory(), REGISTRY_FILE_NAME)

    init {
        loadWatchedPlaylists()
    }

    companion object {
        const val PREF_WATCHED_PLAYLIST_IDS = "offline_sync_playlist_ids"
        const val PREF_WATCHED_PLAYLISTS_JSON = "offline_sync_playlists_json"
        const val REGISTRY_FILE_NAME = "offline_sync_playlists.json"
        const val SOURCE_TYPE_PLAYLIST_AUTO_SYNC = "PLAYLIST_AUTO_SYNC"

        private val UNSUPPORTED_STATUSES = setOf(
            "unsupported",
            "unplayable",
            "missing",
            "reference",
            "error",
            "unavailable",
            "deleted",
            "invalid",
        )

        @Volatile
        private var instance: OfflinePlaylistSyncCoordinator? = null

        fun getDefault(context: Context): OfflinePlaylistSyncCoordinator {
            return instance ?: synchronized(this) {
                instance ?: OfflinePlaylistSyncCoordinator(
                    downloadManager = PinnedDownloadManager.getDefault(context.applicationContext),
                    prefs = context.applicationContext.getSharedPreferences(
                        PinnedDownloadManager.PREFS_NAME,
                        Context.MODE_PRIVATE,
                    ),
                ).also { instance = it }
            }
        }

        fun isTrackEligibleForOfflineSync(track: CarTrack): Boolean {
            if (track.id <= 0) return false
            if (track.sourceType.equals("device_local", ignoreCase = true)) return false
            val st = track.status.trim().lowercase()
            if (st in UNSUPPORTED_STATUSES) return false
            return true
        }
    }

    @Synchronized
    fun isPlaylistWatched(playlistId: Int): Boolean {
        return watchedPlaylists.containsKey(playlistId)
    }

    @Synchronized
    fun getWatchedPlaylistIds(): Set<Int> {
        return watchedPlaylists.keys.toSet()
    }

    @Synchronized
    fun getWatchedPlaylists(): List<WatchedPlaylistRecord> {
        return watchedPlaylists.values.sortedWith(
            compareBy<WatchedPlaylistRecord> { it.enabledAtMs }.thenBy { it.playlistId }
        )
    }

    @Synchronized
    fun getWatchedPlaylistRecord(playlistId: Int): WatchedPlaylistRecord? {
        return watchedPlaylists[playlistId]
    }

    @Synchronized
    fun enableWatch(
        playlistId: Int,
        displayName: String,
        nowMs: Long = System.currentTimeMillis(),
    ): WatchedPlaylistRecord {
        val existing = watchedPlaylists[playlistId]
        val resolvedName = displayName.trim().ifBlank {
            existing?.displayName?.ifBlank { "Playlist #$playlistId" } ?: "Playlist #$playlistId"
        }
        val record = WatchedPlaylistRecord(
            playlistId = playlistId,
            displayName = resolvedName,
            enabledAtMs = existing?.enabledAtMs ?: nowMs,
            isStorageBlocked = false,
            storageBlockedMessage = null,
        )
        watchedPlaylists[playlistId] = record
        persistWatchedPlaylists()
        return record
    }

    @Synchronized
    fun enableWatchAndSync(
        playlistId: Int,
        displayName: String,
        tracks: List<CarTrack>,
        additionalUnsupportedCount: Int = 0,
        apiClient: CarApiClient = CarApiClient(),
    ): ReconcileResult {
        enableWatch(playlistId, displayName)
        return reconcilePlaylist(
            playlistId = playlistId,
            playlistName = displayName,
            tracks = tracks,
            additionalUnsupportedCount = additionalUnsupportedCount,
            apiClient = apiClient,
        )
    }

    @Synchronized
    fun disableWatch(playlistId: Int): Boolean {
        val removed = watchedPlaylists.remove(playlistId) != null
        if (removed) {
            persistWatchedPlaylists()
        }
        return removed
    }

    @Synchronized
    fun updatePlaylistDisplayName(playlistId: Int, newDisplayName: String): Boolean {
        val existing = watchedPlaylists[playlistId] ?: return false
        val trimmed = newDisplayName.trim()
        if (trimmed.isBlank() || trimmed == existing.displayName) return false
        watchedPlaylists[playlistId] = existing.copy(displayName = trimmed)
        persistWatchedPlaylists()
        return true
    }

    @Synchronized
    fun classifyPlaylist(
        playlistId: Int,
        playlistName: String,
        tracks: List<CarTrack>,
        additionalUnsupportedCount: Int = 0,
    ): PlaylistSyncClassification {
        val effectiveName = playlistName.trim().ifBlank {
            watchedPlaylists[playlistId]?.displayName ?: "Playlist #$playlistId"
        }
        var unsupportedInList = 0
        val seenIds = HashSet<Int>()
        val eligibleTracks = mutableListOf<CarTrack>()

        for (track in tracks) {
            if (!isTrackEligibleForOfflineSync(track)) {
                unsupportedInList++
                continue
            }
            if (seenIds.add(track.id)) {
                eligibleTracks.add(track)
            }
        }

        val totalUnsupported = unsupportedInList + additionalUnsupportedCount.coerceAtLeast(0)
        val totalTracksInPlaylist = eligibleTracks.size + totalUnsupported

        val alreadyOffline = mutableListOf<CarTrack>()
        val inProgress = mutableListOf<CarTrack>()
        val waitingForWifi = mutableListOf<CarTrack>()
        val failed = mutableListOf<CarTrack>()
        val cancelled = mutableListOf<CarTrack>()
        val notDownloaded = mutableListOf<CarTrack>()
        val eligibleNew = mutableListOf<CarTrack>()
        var knownNewBytes = 0L
        var unknownSizeCount = 0

        for (track in eligibleTracks) {
            val trackId = track.id.toLong()
            if (downloadManager.isOfflineReady(trackId)) {
                alreadyOffline.add(track)
                continue
            }

            val status = downloadManager.getDownloadStatus(trackId)
            when (status.state) {
                DownloadState.OFFLINE_READY -> {
                    alreadyOffline.add(track)
                }
                DownloadState.QUEUED,
                DownloadState.DOWNLOADING,
                DownloadState.FINALIZING -> {
                    inProgress.add(track)
                }
                DownloadState.WAITING_FOR_WIFI -> {
                    inProgress.add(track)
                    waitingForWifi.add(track)
                }
                DownloadState.FAILED -> {
                    failed.add(track)
                    eligibleNew.add(track)
                    val sz = track.sourceSizeBytes ?: 0L
                    if (sz > 0L) knownNewBytes += sz else unknownSizeCount++
                }
                DownloadState.CANCELLED -> {
                    cancelled.add(track)
                    eligibleNew.add(track)
                    val sz = track.sourceSizeBytes ?: 0L
                    if (sz > 0L) knownNewBytes += sz else unknownSizeCount++
                }
                DownloadState.NOT_DOWNLOADED -> {
                    notDownloaded.add(track)
                    eligibleNew.add(track)
                    val sz = track.sourceSizeBytes ?: 0L
                    if (sz > 0L) knownNewBytes += sz else unknownSizeCount++
                }
            }
        }

        return PlaylistSyncClassification(
            playlistId = playlistId,
            playlistName = effectiveName,
            totalTracksInPlaylist = totalTracksInPlaylist,
            eligibleTracks = eligibleTracks,
            alreadyOfflineTracks = alreadyOffline,
            inProgressTracks = inProgress,
            waitingForWifiTracks = waitingForWifi,
            failedTracks = failed,
            cancelledTracks = cancelled,
            notDownloadedTracks = notDownloaded,
            eligibleNewTracks = eligibleNew,
            unsupportedCount = totalUnsupported,
            knownNewBytes = knownNewBytes,
            unknownSizeCount = unknownSizeCount,
        )
    }

    @Synchronized
    fun getPlaylistSyncStatus(
        playlistId: Int,
        playlistName: String,
        tracks: List<CarTrack>,
        additionalUnsupportedCount: Int = 0,
    ): PlaylistSyncStatus {
        val classification = classifyPlaylist(playlistId, playlistName, tracks, additionalUnsupportedCount)
        val record = watchedPlaylists[playlistId]
        val isWatched = record != null
        val effectiveName = record?.displayName?.ifBlank { classification.playlistName } ?: classification.playlistName

        val eligibleCount = classification.eligibleCount
        val offlineCount = classification.alreadyOfflineCount
        val inProgressCount = classification.inProgressCount
        val waitingWifiCount = classification.waitingForWifiTracks.size
        val failedCount = classification.failedTracks.size
        val missingCount = (eligibleCount - offlineCount).coerceAtLeast(0)
        val unsupportedCount = classification.unsupportedCount

        if (!isWatched) {
            return PlaylistSyncStatus(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = false,
                state = PlaylistSyncState.OFF,
                toggleLabel = "Luôn giữ offline: Tắt",
                statusText = "Luôn giữ offline: Tắt",
                eligibleCount = eligibleCount,
                offlineCount = offlineCount,
                inProgressCount = inProgressCount,
                waitingForWifiCount = waitingWifiCount,
                failedCount = failedCount,
                missingCount = missingCount,
                unsupportedCount = unsupportedCount,
            )
        }

        // If all eligible tracks are now OFFLINE_READY, clear any stale storage block flag
        if (missingCount == 0 && record != null && record.isStorageBlocked) {
            watchedPlaylists[playlistId] = record.copy(isStorageBlocked = false, storageBlockedMessage = null)
            persistWatchedPlaylists()
        }

        val currentRecord = watchedPlaylists[playlistId]

        // 1. Blocked by storage cap or 500 MB reserve
        if (currentRecord?.isStorageBlocked == true && missingCount > 0) {
            return PlaylistSyncStatus(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                state = PlaylistSyncState.STORAGE_BLOCKED,
                toggleLabel = "Luôn giữ offline: Bật",
                statusText = "Thiếu dung lượng · $offlineCount/$eligibleCount bài",
                secondaryStatusText = if (unsupportedCount > 0) "$unsupportedCount bài không thể tải offline" else null,
                eligibleCount = eligibleCount,
                offlineCount = offlineCount,
                inProgressCount = inProgressCount,
                waitingForWifiCount = waitingWifiCount,
                failedCount = failedCount,
                missingCount = missingCount,
                unsupportedCount = unsupportedCount,
                storageBlockedMessage = currentRecord.storageBlockedMessage,
            )
        }

        // 2. All eligible tracks are OFFLINE_READY (or 0 eligible tracks)
        if (missingCount == 0) {
            if (unsupportedCount > 0) {
                return PlaylistSyncStatus(
                    playlistId = playlistId,
                    playlistName = effectiveName,
                    isWatched = true,
                    state = PlaylistSyncState.PARTIAL_UNSUPPORTED,
                    toggleLabel = "Luôn giữ offline: Bật",
                    statusText = "$offlineCount/${classification.totalTracksInPlaylist} bài có thể tải đã offline",
                    secondaryStatusText = "$unsupportedCount bài không thể tải offline",
                    eligibleCount = eligibleCount,
                    offlineCount = offlineCount,
                    inProgressCount = 0,
                    waitingForWifiCount = 0,
                    failedCount = 0,
                    missingCount = 0,
                    unsupportedCount = unsupportedCount,
                )
            }
            return PlaylistSyncStatus(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                state = PlaylistSyncState.SYNCED,
                toggleLabel = "Luôn giữ offline: Bật",
                statusText = "✓ Đã đồng bộ offline",
                eligibleCount = eligibleCount,
                offlineCount = offlineCount,
                inProgressCount = 0,
                waitingForWifiCount = 0,
                failedCount = 0,
                missingCount = 0,
                unsupportedCount = 0,
            )
        }

        // 3. Failed tracks exist and no active/waiting downloads remain
        if (failedCount > 0 && inProgressCount == 0) {
            val secondary = buildString {
                append("$offlineCount/$eligibleCount offline · $failedCount bài tải lỗi")
                if (unsupportedCount > 0) {
                    append(" · $unsupportedCount bài không thể tải offline")
                }
            }
            return PlaylistSyncStatus(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                state = PlaylistSyncState.HAS_FAILURES,
                toggleLabel = "Luôn giữ offline: Bật",
                statusText = "Có $failedCount bài tải lỗi",
                secondaryStatusText = secondary,
                eligibleCount = eligibleCount,
                offlineCount = offlineCount,
                inProgressCount = inProgressCount,
                waitingForWifiCount = waitingWifiCount,
                failedCount = failedCount,
                missingCount = missingCount,
                unsupportedCount = unsupportedCount,
            )
        }

        // 4. Waiting for Wi-Fi under WIFI_ONLY policy
        val networkBlocked = !downloadManager.isNetworkAllowedForDownload()
        if (waitingWifiCount > 0 || (networkBlocked && (inProgressCount > 0 || missingCount > 0))) {
            return PlaylistSyncStatus(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                state = PlaylistSyncState.WAITING_FOR_WIFI,
                toggleLabel = "Luôn giữ offline: Bật",
                statusText = "Đang chờ Wi-Fi · $offlineCount/$eligibleCount bài",
                secondaryStatusText = if (unsupportedCount > 0) "$unsupportedCount bài không thể tải offline" else null,
                eligibleCount = eligibleCount,
                offlineCount = offlineCount,
                inProgressCount = inProgressCount,
                waitingForWifiCount = waitingWifiCount,
                failedCount = failedCount,
                missingCount = missingCount,
                unsupportedCount = unsupportedCount,
            )
        }

        // 5. Missing or in-progress downloads
        val secondary = when {
            failedCount > 0 && unsupportedCount > 0 -> "Có $failedCount bài tải lỗi · $unsupportedCount bài không thể tải offline"
            failedCount > 0 -> "Có $failedCount bài tải lỗi"
            unsupportedCount > 0 -> "$unsupportedCount bài không thể tải offline"
            else -> null
        }
        return PlaylistSyncStatus(
            playlistId = playlistId,
            playlistName = effectiveName,
            isWatched = true,
            state = PlaylistSyncState.SYNCING,
            toggleLabel = "Luôn giữ offline: Bật",
            statusText = "Đang đồng bộ offline · $offlineCount/$eligibleCount bài",
            secondaryStatusText = secondary,
            eligibleCount = eligibleCount,
            offlineCount = offlineCount,
            inProgressCount = inProgressCount,
            waitingForWifiCount = waitingWifiCount,
            failedCount = failedCount,
            missingCount = missingCount,
            unsupportedCount = unsupportedCount,
        )
    }

    @Synchronized
    fun reconcilePlaylist(
        playlistId: Int,
        playlistName: String,
        tracks: List<CarTrack>,
        additionalUnsupportedCount: Int = 0,
        apiClient: CarApiClient = CarApiClient(),
    ): ReconcileResult {
        val existingRecord = watchedPlaylists[playlistId]
        if (existingRecord == null) {
            val classification = classifyPlaylist(playlistId, playlistName, tracks, additionalUnsupportedCount)
            val status = getPlaylistSyncStatus(playlistId, playlistName, tracks, additionalUnsupportedCount)
            return ReconcileResult(
                playlistId = playlistId,
                playlistName = classification.playlistName,
                isWatched = false,
                enqueuedCount = 0,
                classification = classification,
                storagePrecheck = null,
                status = status,
            )
        }

        val trimmedName = playlistName.trim()
        val effectiveName = if (trimmedName.isNotBlank()) {
            if (trimmedName != existingRecord.displayName) {
                watchedPlaylists[playlistId] = existingRecord.copy(displayName = trimmedName)
                persistWatchedPlaylists()
            }
            trimmedName
        } else {
            existingRecord.displayName
        }

        val classification = classifyPlaylist(playlistId, effectiveName, tracks, additionalUnsupportedCount)

        // Idempotent no-op if no new eligible tracks need enqueuing
        if (classification.eligibleNewTracks.isEmpty()) {
            val currentRec = watchedPlaylists[playlistId]
            if (currentRec != null && currentRec.isStorageBlocked) {
                watchedPlaylists[playlistId] = currentRec.copy(isStorageBlocked = false, storageBlockedMessage = null)
                persistWatchedPlaylists()
            }
            val status = getPlaylistSyncStatus(playlistId, effectiveName, tracks, additionalUnsupportedCount)
            return ReconcileResult(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                enqueuedCount = 0,
                classification = classification,
                storagePrecheck = null,
                status = status,
            )
        }

        // Check storage policy (configured cap + 500 MB free reserve)
        val precheck = downloadManager.checkBulkDownloadStorage(classification.eligibleNewTracks)
        if (!precheck.isAllowed) {
            val currentRec = watchedPlaylists[playlistId]
            if (currentRec != null) {
                watchedPlaylists[playlistId] = currentRec.copy(
                    isStorageBlocked = true,
                    storageBlockedMessage = precheck.message,
                )
                persistWatchedPlaylists()
            }
            val status = getPlaylistSyncStatus(playlistId, effectiveName, tracks, additionalUnsupportedCount)
            return ReconcileResult(
                playlistId = playlistId,
                playlistName = effectiveName,
                isWatched = true,
                enqueuedCount = 0,
                classification = classification,
                storagePrecheck = precheck,
                status = status,
            )
        }

        // Storage is allowed: clear any prior storage block
        val currentRec = watchedPlaylists[playlistId]
        if (currentRec != null && currentRec.isStorageBlocked) {
            watchedPlaylists[playlistId] = currentRec.copy(isStorageBlocked = false, storageBlockedMessage = null)
            persistWatchedPlaylists()
        }

        val batch = DownloadBatch(
            sourceType = SOURCE_TYPE_PLAYLIST_AUTO_SYNC,
            sourceId = playlistId.toString(),
            sourceDisplayName = effectiveName,
            trackIds = classification.eligibleTracks.map { it.id.toLong() },
            initialEligibleCount = classification.eligibleNewCount,
            knownTotalBytes = classification.knownNewBytes,
            hasUnknownSizes = classification.unknownSizeCount > 0,
        )
        val enqueuedCount = downloadManager.enqueueBatch(
            batch = batch,
            tracksToEnqueue = classification.eligibleNewTracks,
            apiClient = apiClient,
        )

        val updatedClassification = classifyPlaylist(playlistId, effectiveName, tracks, additionalUnsupportedCount)
        val updatedStatus = getPlaylistSyncStatus(playlistId, effectiveName, tracks, additionalUnsupportedCount)
        return ReconcileResult(
            playlistId = playlistId,
            playlistName = effectiveName,
            isWatched = true,
            enqueuedCount = enqueuedCount,
            classification = updatedClassification,
            storagePrecheck = precheck,
            status = updatedStatus,
        )
    }

    @Synchronized
    fun reconcileMultiplePlaylists(
        playlistsWithTracks: List<Triple<Int, String, List<CarTrack>>>,
        apiClient: CarApiClient = CarApiClient(),
    ): List<ReconcileResult> {
        val results = mutableListOf<ReconcileResult>()
        for ((playlistId, playlistName, tracks) in playlistsWithTracks) {
            if (isPlaylistWatched(playlistId)) {
                results.add(
                    reconcilePlaylist(
                        playlistId = playlistId,
                        playlistName = playlistName,
                        tracks = tracks,
                        additionalUnsupportedCount = 0,
                        apiClient = apiClient,
                    )
                )
            }
        }
        return results
    }

    /**
     * Authoritative playlist-list reconciliation:
     * - Updates display names for renamed playlists with the same [CarPlaylist.id].
     * - Removes stale watched playlist IDs that are no longer present in [authoritativePlaylists].
     * - Never deletes any local pinned offline files.
     *
     * IMPORTANT: Call this ONLY when `/v1/playlists` succeeded with an authoritative list.
     * Never call on network error, timeout, or HTTP 5xx.
     */
    @Synchronized
    fun reconcileAuthoritativePlaylistList(authoritativePlaylists: List<CarPlaylist>): List<Int> {
        if (watchedPlaylists.isEmpty()) return emptyList()
        val authoritativeById = authoritativePlaylists.associateBy { it.id }
        val removedIds = mutableListOf<Int>()
        var changed = false

        for (playlistId in watchedPlaylists.keys.toList()) {
            val remotePl = authoritativeById[playlistId]
            if (remotePl == null) {
                watchedPlaylists.remove(playlistId)
                removedIds.add(playlistId)
                changed = true
            } else {
                val existing = watchedPlaylists[playlistId]
                val remoteName = remotePl.name.trim()
                if (existing != null && remoteName.isNotBlank() && remoteName != existing.displayName) {
                    watchedPlaylists[playlistId] = existing.copy(displayName = remoteName)
                    changed = true
                }
            }
        }

        if (changed) {
            persistWatchedPlaylists()
        }
        return removedIds
    }

    /**
     * Fetches the authoritative playlist list from [apiClient] and reconciles all watched playlists.
     * If fetching the playlist list fails due to a transient error (offline, timeout, HTTP 5xx),
     * watched playlist entries and local files are preserved untouched.
     */
    fun syncAllWatchedPlaylistsFromRemote(apiClient: CarApiClient = CarApiClient()): RemoteSyncSummary {
        val watchedSnapshot = getWatchedPlaylists()
        if (watchedSnapshot.isEmpty()) {
            return RemoteSyncSummary(
                authoritativeListFetched = true,
                removedDeletedPlaylistIds = emptyList(),
                reconciledResults = emptyList(),
            )
        }

        val remotePlaylists = try {
            apiClient.playlists()
        } catch (_: Exception) {
            return RemoteSyncSummary(
                authoritativeListFetched = false,
                removedDeletedPlaylistIds = emptyList(),
                reconciledResults = emptyList(),
            )
        }

        val removedIds = reconcileAuthoritativePlaylistList(remotePlaylists)
        val remainingWatched = getWatchedPlaylists()
        val results = mutableListOf<ReconcileResult>()

        for (record in remainingWatched) {
            val fetched = try {
                if (playlistTracksFetcher != null) {
                    playlistTracksFetcher.invoke(record.playlistId, apiClient)
                } else {
                    val playbackRes = apiClient.fetchCollectionPlayback(
                        kind = "playlist",
                        playlistId = record.playlistId,
                    )
                    Pair(playbackRes.items, playbackRes.skipped)
                }
            } catch (_: Exception) {
                try {
                    val (items, total) = apiClient.playlistTracksWithTotal(record.playlistId, 1000, 0)
                    val skipped = (total - items.size).coerceAtLeast(0)
                    Pair(items, skipped)
                } catch (_: Exception) {
                    null
                }
            } ?: continue

            val (tracks, skippedCount) = fetched
            val res = reconcilePlaylist(
                playlistId = record.playlistId,
                playlistName = record.displayName,
                tracks = tracks,
                additionalUnsupportedCount = skippedCount,
                apiClient = apiClient,
            )
            results.add(res)
        }

        return RemoteSyncSummary(
            authoritativeListFetched = true,
            removedDeletedPlaylistIds = removedIds,
            reconciledResults = results,
        )
    }

    fun buildEnableConfirmationPrompt(
        playlistName: String,
        classification: PlaylistSyncClassification,
    ): EnableConfirmationPrompt {
        val summary = downloadManager.getStorageSummary()
        val knownBytesLabel = if (classification.knownNewBytes > 0L) {
            "~${downloadManager.formatStorageBytes(classification.knownNewBytes)}"
        } else {
            "0 B"
        }
        val networkLabel = downloadManager.formatNetworkPolicyLabel()
        val capLabel = downloadManager.formatCapLabel(summary.capBytes)
        val freeLabel = downloadManager.formatStorageBytes(summary.availableBytes)

        val msg = buildString {
            append("Playlist: $playlistName\n")
            append("Tổng: ${classification.totalTracksInPlaylist} bài\n")
            append("Đã offline: ${classification.alreadyOfflineCount} bài\n")
            append("Đang chờ/đang tải: ${classification.inProgressCount} bài\n")
            append("Cần tải thêm: ${classification.eligibleNewCount} bài\n")
            append("Dung lượng biết trước: $knownBytesLabel\n")
            append("Mạng tải xuống: $networkLabel\n")
            append("Giới hạn offline: $capLabel · Còn trống: $freeLabel\n")
            if (classification.unknownSizeCount > 0) {
                append("Có ${classification.unknownSizeCount} bài chưa rõ dung lượng.\n")
            }
            if (classification.unsupportedCount > 0) {
                append("Có ${classification.unsupportedCount} bài không thể tải offline.\n")
            }
            append("\n")
            append("Khi thêm bài mới vào playlist này, MeloVault sẽ tự tải offline các bài còn thiếu khi mở ứng dụng hoặc làm mới playlist.\n")
            append("Khi xóa bài khỏi playlist hoặc tắt chế độ này, các file đã tải vẫn được giữ lại.")
        }

        return EnableConfirmationPrompt(
            title = "Luôn giữ playlist này offline?",
            message = msg,
        )
    }

    @Synchronized
    fun reloadWatchedPlaylists() {
        watchedPlaylists.clear()
        loadWatchedPlaylists()
    }

    @Synchronized
    private fun loadWatchedPlaylists() {
        var loadedFromFile = false
        if (persistenceFile.exists() && persistenceFile.isFile) {
            try {
                val jsonText = persistenceFile.readText(Charsets.UTF_8)
                val parsed = parseWatchedPlaylistsJson(jsonText)
                for (rec in parsed) {
                    watchedPlaylists[rec.playlistId] = rec
                }
                loadedFromFile = true
            } catch (_: Exception) {
            }
        }

        if (prefs != null) {
            try {
                val prefsJson = prefs.getString(PREF_WATCHED_PLAYLISTS_JSON, null)
                if (!prefsJson.isNullOrBlank()) {
                    val parsedFromPrefs = parseWatchedPlaylistsJson(prefsJson)
                    if (!loadedFromFile) {
                        for (rec in parsedFromPrefs) {
                            watchedPlaylists[rec.playlistId] = rec
                        }
                    }
                } else if (!loadedFromFile) {
                    val idStrings = prefs.getStringSet(PREF_WATCHED_PLAYLIST_IDS, emptySet()).orEmpty()
                    for (idStr in idStrings) {
                        val id = idStr.toIntOrNull()
                        if (id != null && id > 0 && !watchedPlaylists.containsKey(id)) {
                            watchedPlaylists[id] = WatchedPlaylistRecord(
                                playlistId = id,
                                displayName = "Playlist #$id",
                                enabledAtMs = System.currentTimeMillis(),
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    private fun persistWatchedPlaylists() {
        val json = serializeWatchedPlaylistsJson(watchedPlaylists.values.toList())
        try {
            val parent = persistenceFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tmpFile = File(persistenceFile.parentFile, "${persistenceFile.name}.tmp")
            tmpFile.writeText(json, Charsets.UTF_8)
            if (!tmpFile.renameTo(persistenceFile)) {
                persistenceFile.writeText(json, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
        }

        if (prefs != null) {
            try {
                val idSet = watchedPlaylists.keys.map { it.toString() }.toSet()
                prefs.edit()
                    .putStringSet(PREF_WATCHED_PLAYLIST_IDS, idSet)
                    .putString(PREF_WATCHED_PLAYLISTS_JSON, json)
                    .apply()
            } catch (_: Exception) {
            }
        }
    }

    private fun serializeWatchedPlaylistsJson(records: List<WatchedPlaylistRecord>): String {
        val sb = StringBuilder()
        sb.append("{\n  \"watched_playlists\": [\n")
        records.forEachIndexed { index, rec ->
            sb.append("    {\n")
            sb.append("      \"playlist_id\": ${rec.playlistId},\n")
            sb.append("      \"display_name\": \"${escapeJson(rec.displayName)}\",\n")
            sb.append("      \"enabled_at_ms\": ${rec.enabledAtMs},\n")
            sb.append("      \"is_storage_blocked\": ${rec.isStorageBlocked},\n")
            if (rec.storageBlockedMessage != null) {
                sb.append("      \"storage_blocked_message\": \"${escapeJson(rec.storageBlockedMessage)}\"\n")
            } else {
                sb.append("      \"storage_blocked_message\": null\n")
            }
            sb.append("    }")
            if (index < records.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    private fun parseWatchedPlaylistsJson(json: String): List<WatchedPlaylistRecord> {
        val result = mutableListOf<WatchedPlaylistRecord>()
        val objPattern = Regex("""\{[^{}]*"playlist_id"\s*:\s*\d+[^{}]*\}""")
        val kvPattern = Regex(""""([^"]+)"\s*:\s*(?:"((?:\\.|[^"\\])*)"|(null)|(true|false)|(-?\d+))""")

        for (objMatch in objPattern.findAll(json)) {
            val block = objMatch.value
            val map = mutableMapOf<String, String?>()
            for (kv in kvPattern.findAll(block)) {
                val key = kv.groupValues[1]
                val strVal = kv.groups[2]?.value?.let { unescapeJson(it) }
                val nullVal = kv.groups[3]?.value
                val boolVal = kv.groups[4]?.value
                val numVal = kv.groups[5]?.value
                map[key] = when {
                    nullVal != null -> null
                    strVal != null -> strVal
                    boolVal != null -> boolVal
                    numVal != null -> numVal
                    else -> null
                }
            }
            val playlistId = map["playlist_id"]?.toIntOrNull() ?: continue
            if (playlistId <= 0) continue
            val displayName = map["display_name"]?.ifBlank { "Playlist #$playlistId" } ?: "Playlist #$playlistId"
            val enabledAtMs = map["enabled_at_ms"]?.toLongOrNull() ?: 0L
            val isStorageBlocked = map["is_storage_blocked"]?.toBooleanStrictOrNull() ?: false
            val storageBlockedMessage = map["storage_blocked_message"]
            result.add(
                WatchedPlaylistRecord(
                    playlistId = playlistId,
                    displayName = displayName,
                    enabledAtMs = enabledAtMs,
                    isStorageBlocked = isStorageBlocked,
                    storageBlockedMessage = storageBlockedMessage,
                )
            )
        }
        return result
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                val next = value[i + 1]
                when (next) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}
