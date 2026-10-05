package com.bsdung.musicassistantcar.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class RemoteCatalog(
    private val api: CarApiClient = CarApiClient(),
    private val localRepoSupplier: () -> LocalMediaRepository? = { null },
    private val downloadManagerSupplier: () -> com.bsdung.musicassistantcar.playback.PinnedDownloadManager? = { null },
) {
    companion object {
        const val ROOT_ID = "root"
        const val DEVICE_LOCAL_ID = "category:device_local"
        const val DEVICE_LOCAL_ALL_ID = "category:device_local_all"
        const val DEVICE_LOCAL_FOLDERS_ID = "category:device_local_folders"
        const val DOWNLOADED_ID = "category:downloaded"
        const val RECENT_ID = "category:recent"
        const val ALL_ID = "source:all"
        const val DRIVE_ID = "source:drive"
        const val TELEGRAM_ID = "source:telegram"
        const val PLAYLISTS_ID = "category:playlists"
        const val PLAYLIST_PREFIX = "playlist:"
        const val TRACK_PREFIX = "track:"
        const val DEVICE_PREFIX = "device:"
        const val DEVICE_FOLDER_PREFIX = "device_folder:"
        const val DRIVE_ROOT_PREFIX = "drive-root:"
        const val DRIVE_FOLDER_PREFIX = "drive-folder:"
        const val DRIVE_FILE_PREFIX = "drive-file:"
        const val ARTIST_PREFIX = "artist:"
        const val ALBUM_PREFIX = "album:"
        const val NEW_PLAYLIST_ACTION = "action:new_playlist"

        fun safeEncode(s: String): String =
            runCatching { Uri.encode(s) }.getOrElse {
                java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
            }

        fun safeDecode(s: String): String =
            runCatching { Uri.decode(s) }.getOrElse {
                java.net.URLDecoder.decode(s, "UTF-8")
            }

        fun artistId(artist: String): String = "$ARTIST_PREFIX${safeEncode(artist)}"
        fun albumId(artist: String, album: String): String = "$ALBUM_PREFIX${safeEncode(artist)}::${safeEncode(album)}"

        /**
         * Pure, unit-testable canonical track identity resolver.
         *
         * - [mediaId] = the MediaItem's mediaId (e.g. `"drive-file:123"` or `"track:456"`)
         * - [trackId] = the canonical track ID from extras (`track_id`), or `null` if absent
         *
         * Returns `"track:<id>"` for any item that can be identified, or `null` otherwise.
         * No title/artist fuzzy matching is performed.
         */
        fun resolveCanonicalTrackMediaId(mediaId: String, trackId: Int?): String? {
            // Case 1: already a "track:" mediaId (album / artist / playlist rows)
            if (mediaId.startsWith(TRACK_PREFIX)) return mediaId

            // Case 2: drive-file row with a valid embedded track ID
            if (mediaId.startsWith(DRIVE_FILE_PREFIX) && trackId != null && trackId > 0) {
                return "$TRACK_PREFIX$trackId"
            }

            return null
        }

        /**
         * Returns the canonical `"track:<id>"` identity string for [item], or `null` if
         * [item] cannot be matched to a canonical track.
         *
         * - Drive-browse rows: `mediaId = "drive-file:<driveFileId>"` + extras `track_id = <trackId>`
         *   → returns `"track:<trackId>"`
         * - Collection-playback rows: `mediaId = "track:<trackId>"`
         *   → returns `"track:<trackId>"`
         * - All other items (folders, playlists, …): `null`
         *
         * No title/artist fuzzy matching is performed.
         */
        fun canonicalTrackMediaId(item: MediaItem): String? {
            val extras = item.mediaMetadata.extras
            val trackIdFromExtras = runCatching {
                extras?.getInt("track_id", 0)?.takeIf { it > 0 }
            }.getOrNull() ?: runCatching {
                extras?.getLong("track_id", 0L)?.takeIf { it > 0 }?.toInt()
            }.getOrNull()
            return resolveCanonicalTrackMediaId(item.mediaId, trackIdFromExtras)
        }
    }

    fun rootItem(): MediaItem =
        browsableItem(
            ROOT_ID,
            "MeloVault",
            "Your Personal Music Vault",
        )

    fun rootChildren(): List<MediaItem> = listOf(
        browsableItem(
            DEVICE_LOCAL_ID,
            "Nhạc trên thiết bị",
            "Bộ nhớ máy",
        ),
        browsableItem(
            DOWNLOADED_ID,
            "Đã tải về",
            "Phát offline",
        ),
        browsableItem(
            RECENT_ID,
            "Recent",
            "Nghe gần đây",
        ),
        browsableItem(
            ALL_ID,
            "All Music",
            "Toàn bộ thư viện",
        ),
        browsableItem(
            DRIVE_ID,
            "Google Drive",
            "Duyệt theo thư mục",
        ),
        browsableItem(
            TELEGRAM_ID,
            "Telegram",
            "Nhạc từ Telegram",
        ),
        browsableItem(
            PLAYLISTS_ID,
            "Playlists",
            "Danh sách phát",
        ),
    )

    fun children(
        parentId: String,
        page: Int,
        pageSize: Int,
        sort: String = "",
    ): List<MediaItem> {
        val offset = page.coerceAtLeast(0) * pageSize

        if (parentId == DEVICE_LOCAL_ID) {
            val repo = localRepoSupplier() ?: return emptyList()
            return repo.getDeviceMusicCategories()
        }

        if (parentId == DEVICE_LOCAL_ALL_ID) {
            val repo = localRepoSupplier() ?: return emptyList()
            return repo.getLocalTracks(page, pageSize, sort)
        }

        if (parentId == DEVICE_LOCAL_FOLDERS_ID) {
            val repo = localRepoSupplier() ?: return emptyList()
            return repo.getDeviceFolderRoots()
        }

        if (parentId.startsWith(DEVICE_FOLDER_PREFIX)) {
            val repo = localRepoSupplier() ?: return emptyList()
            val payload = parentId.removePrefix(DEVICE_FOLDER_PREFIX)
            val colonIdx = payload.indexOf(':')
            val volume = if (colonIdx >= 0) payload.substring(0, colonIdx) else payload
            val relPath = if (colonIdx >= 0) payload.substring(colonIdx + 1) else ""
            return repo.getFolderContents(volume, relPath, page, pageSize)
        }

        if (parentId == DOWNLOADED_ID) {
            val dm = downloadManagerSupplier() ?: return emptyList()
            val allDownloaded = dm.getAllDownloadedMediaItems()
            if (offset >= allDownloaded.size) return emptyList()
            return allDownloaded.subList(offset, (offset + pageSize).coerceAtMost(allDownloaded.size))
        }

        if (parentId == PLAYLISTS_ID) {
            val playlists = api.playlists()
            val items = mutableListOf<MediaItem>()
            if (page == 0) {
                items.add(
                    browsableItem(
                        NEW_PLAYLIST_ACTION,
                        "+ Danh sách phát mới",
                        "Tạo danh sách phát của bạn",
                    )
                )
            }
            playlists.forEach { playlist ->
                items.add(
                    browsableItem(
                        "$PLAYLIST_PREFIX${playlist.id}",
                        playlist.name,
                        "${playlist.trackCount} bài",
                    )
                )
            }
            return items
        }

        if (parentId == DRIVE_ID) {
            return api.driveRoots().map { root ->
                browsableItem(
                    "$DRIVE_ROOT_PREFIX${root.index}",
                    root.name,
                    "Thư mục Google Drive",
                )
            }
        }

        if (parentId.startsWith(DRIVE_ROOT_PREFIX)) {
            val root = parentId
                .removePrefix(DRIVE_ROOT_PREFIX)
                .toIntOrNull()
                ?: return emptyList()
            val browseRes = api.driveBrowseWithMetadata(
                root,
                "",
                pageSize,
                offset,
                sort,
            )
            return browseRes.items.map(::driveBrowseItem).attachDriveCounts(
                total = browseRes.total,
                trackCount = browseRes.trackCount,
                folderCount = browseRes.folderCount,
            )
        }

        if (parentId.startsWith(DRIVE_FOLDER_PREFIX)) {
            val payload = parentId
                .removePrefix(DRIVE_FOLDER_PREFIX)
            val parts = payload.split(":", limit = 2)
            if (parts.size != 2) {
                return emptyList()
            }
            val root = parts[0].toIntOrNull()
                ?: return emptyList()
            val path = safeDecode(parts[1])
            val browseRes = api.driveBrowseWithMetadata(
                root,
                path,
                pageSize,
                offset,
                sort,
            )
            return browseRes.items.map(::driveBrowseItem).attachDriveCounts(
                total = browseRes.total,
                trackCount = browseRes.trackCount,
                folderCount = browseRes.folderCount,
            )
        }

        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            val id = parentId
                .removePrefix(PLAYLIST_PREFIX)
                .toIntOrNull()
                ?: return emptyList()
            val (rawTracks, total) = api.playlistTracksWithTotal(
                id,
                pageSize,
                offset,
            )
            return rawTracks.map(::trackItem).attachTotal(total)
        }

        if (parentId.startsWith(ARTIST_PREFIX)) {
            val encodedArtist = parentId.removePrefix(ARTIST_PREFIX)
            val artist = safeDecode(encodedArtist)
            val (rawTracks, total) = api.artistTracksWithTotal(artist, pageSize, offset, sort)
            return rawTracks.map(::trackItem).attachTotal(total)
        }

        if (parentId.startsWith(ALBUM_PREFIX)) {
            val payload = parentId.removePrefix(ALBUM_PREFIX)
            val parts = payload.split("::", limit = 2)
            val artist = if (parts.size == 2) safeDecode(parts[0]) else ""
            val album = if (parts.size == 2) safeDecode(parts[1]) else safeDecode(parts[0])
            val (rawTracks, total) = api.albumTracksWithTotal(album = album, artist = artist, limit = pageSize, offset = offset, sort = sort)
            return rawTracks.map(::trackItem).attachTotal(total)
        }

        val tracks = when {
            parentId == RECENT_ID ->
                api.recent(pageSize, offset)

            parentId == ALL_ID ->
                api.sourceTracks(
                    "all",
                    pageSize,
                    offset,
                )

            parentId == TELEGRAM_ID ->
                api.sourceTracks(
                    "telegram",
                    pageSize,
                    offset,
                )

            else -> emptyList()
        }
        return tracks.map(::trackItem)
    }

    private fun List<MediaItem>.attachTotal(total: Int): List<MediaItem> {
        if (isEmpty()) return this
        return map { item ->
            val extras = item.mediaMetadata.extras?.let { android.os.Bundle(it) } ?: android.os.Bundle()
            extras.putInt("collection_total", total)
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                .build()
        }
    }

    private fun List<MediaItem>.attachDriveCounts(
        total: Int,
        trackCount: Int?,
        folderCount: Int?,
    ): List<MediaItem> {
        if (isEmpty()) return this
        return map { item ->
            val extras = item.mediaMetadata.extras?.let { android.os.Bundle(it) } ?: android.os.Bundle()
            extras.putInt("collection_total", total)
            if (trackCount != null) {
                extras.putInt("collection_track_count", trackCount)
            }
            if (folderCount != null) {
                extras.putInt("collection_folder_count", folderCount)
            }
            item.buildUpon()
                .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                .build()
        }
    }

    fun reorderPlaylist(playlistId: Int, mediaIds: List<String>, offset: Int? = null): Boolean =
        api.reorderPlaylist(playlistId, mediaIds, offset)

    fun fetchCollectionPlayback(
        kind: String,
        artist: String? = null,
        album: String? = null,
        root: Int? = null,
        path: String? = null,
        playlistId: Int? = null,
        sort: String = "",
        recursive: Boolean? = null,
    ): CollectionPlaybackResult =
        api.fetchCollectionPlayback(
            kind = kind,
            artist = artist,
            album = album,
            root = root,
            path = path,
            playlistId = playlistId,
            sort = sort,
            recursive = recursive,
        )

    fun batchAddToPlaylist(playlistId: Int, mediaIds: List<String>): BatchResult =
        api.batchAddToPlaylist(playlistId, mediaIds)

    fun batchRemoveFromPlaylist(playlistId: Int, mediaIds: List<String>): BatchResult =
        api.batchRemoveFromPlaylist(playlistId, mediaIds)

    fun addCollectionToPlaylist(
        playlistId: Int,
        kind: String,
        artist: String? = null,
        album: String? = null,
        root: Int? = null,
        path: String? = null,
    ): CollectionActionResult =
        api.addCollectionToPlaylist(playlistId, kind, artist, album, root, path)

    fun renamePlaylist(playlistId: Int, newName: String): CarPlaylist =
        api.renamePlaylist(playlistId, newName)

    fun item(mediaId: String): MediaItem? {
        if (mediaId == ROOT_ID) {
            return rootItem()
        }
        rootChildren()
            .firstOrNull { it.mediaId == mediaId }
            ?.let { return it }

        if (mediaId == DEVICE_LOCAL_ALL_ID) {
            return browsableItem(DEVICE_LOCAL_ALL_ID, "Tất cả bài hát", "Toàn bộ bài hát trên thiết bị")
        }
        if (mediaId == DEVICE_LOCAL_FOLDERS_ID) {
            return browsableItem(DEVICE_LOCAL_FOLDERS_ID, "Theo thư mục", "Duyệt theo cấu trúc thư mục")
        }

        if (mediaId.startsWith(DEVICE_FOLDER_PREFIX)) {
            val repo = localRepoSupplier()
            return repo?.getFolderItem(mediaId)
        }

        if (mediaId.startsWith(DEVICE_PREFIX)) {
            val repo = localRepoSupplier()
            return repo?.getLocalTrack(mediaId)
        }

        if (mediaId.startsWith(TRACK_PREFIX)) {
            val id = mediaId
                .removePrefix(TRACK_PREFIX)
                .toIntOrNull()
                ?: return null

            val dm = downloadManagerSupplier()
            if (dm != null && dm.isOfflineReady(id.toLong())) {
                val record = dm.getRecord(id.toLong())
                if (record != null) {
                    return dm.toMediaItem(record)
                }
            }
            return trackItem(api.track(id))
        }

        if (mediaId.startsWith(DRIVE_FILE_PREFIX)) {
            val id = mediaId
                .removePrefix(DRIVE_FILE_PREFIX)
                .toIntOrNull()
                ?: return null
            return driveFileItem(id)
        }
        return null
    }

    fun driveFileItem(driveFileId: Int): MediaItem {
        val item = runCatching { api.driveFile(driveFileId) }.getOrNull()
        val displayTitle = item?.title?.ifBlank { null }
            ?: item?.name?.substringBeforeLast('.', item.name)
            ?: "Drive Track #$driveFileId"
        val extras = android.os.Bundle()
        item?.format?.let { extras.putString("format", it) }
        item?.sizeBytes?.let { extras.putLong("size_bytes", it) }
        item?.codec?.let { extras.putString("codec", it) }
        item?.bitrate?.let { extras.putInt("bitrate", it) }
        item?.sampleRate?.let { extras.putInt("sample_rate", it) }
        item?.channels?.let { extras.putInt("channels", it) }
        return MediaItem.Builder()
            .setMediaId("$DRIVE_FILE_PREFIX$driveFileId")
            .setUri(api.driveFileStreamUri(driveFileId))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setArtist(item?.artist.orEmpty())
                    .setAlbumTitle(item?.album.orEmpty())
                    .setArtworkUri(
                        api.driveFileCoverUri(driveFileId)
                    )
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    fun search(
        query: String,
        page: Int,
        pageSize: Int,
    ): List<MediaItem> =
        api.search(
            query,
            pageSize,
            page.coerceAtLeast(0) * pageSize,
        ).map(::trackItem)

    fun trackExtras(track: CarTrack): android.os.Bundle {
        val extras = android.os.Bundle()
        if (track.duration != null && track.duration > 0.0) {
            extras.putLong("duration_ms", (track.duration * 1000).toLong())
        }
        track.sourceFormat?.let { extras.putString("format", it) }
        track.sourceSizeBytes?.let { extras.putLong("size_bytes", it) }
        track.sourceCodec?.let { extras.putString("codec", it) }
        track.sourceBitrate?.let { extras.putInt("bitrate", it) }
        track.sourceSampleRate?.let { extras.putInt("sample_rate", it) }
        track.sourceChannels?.let { extras.putInt("channels", it) }
        track.sourceType?.let { extras.putString("source_type", it) }
        track.loudnessLufs?.let { extras.putDouble("loudness_lufs", it) }
        track.truePeakDbtp?.let { extras.putDouble("true_peak_dbtp", it) }
        track.loudnessRangeLu?.let { extras.putDouble("loudness_range_lu", it) }
        track.loudnessAnalysisVersion?.let { extras.putInt("loudness_analysis_version", it) }
        return extras
    }

    fun trackItem(track: CarTrack): MediaItem {
        val extras = trackExtras(track)
        return MediaItem.Builder()
            .setMediaId("$TRACK_PREFIX${track.id}")
            .setUri(api.streamUri(track.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(
                        track.title.ifBlank {
                            "Chưa xác định"
                        }
                    )
                    .setArtist(
                        track.artist.ifBlank {
                            "Nghệ sĩ không rõ"
                        }
                    )
                    .setAlbumTitle(track.album)
                    .setArtworkUri(
                        api.trackCoverUri(track.id)
                    )
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun driveBrowseItem(
        item: DriveBrowseItem,
    ): MediaItem {
        if (item.kind == "folder") {
            val encodedPath = Uri.encode(item.path)
            return browsableItem(
                "$DRIVE_FOLDER_PREFIX${item.root}:$encodedPath",
                item.name,
                "",
            )
        }

        val driveFileId = item.driveFileId
            ?: return browsableItem(
                "invalid-drive:${item.path}",
                item.name,
                "Không thể phát",
            )
        val displayTitle = item.name
            .substringBeforeLast('.', item.name)
        val extras = android.os.Bundle()
        item.format?.let { extras.putString("format", it) }
        item.sizeBytes?.let { extras.putLong("size_bytes", it) }
        item.codec?.let { extras.putString("codec", it) }
        item.bitrate?.let { extras.putInt("bitrate", it) }
        item.sampleRate?.let { extras.putInt("sample_rate", it) }
        item.channels?.let { extras.putInt("channels", it) }
        // Store canonical track ID so Play From Here can match against
        // collection-playback results (which use "track:<id>" mediaIds).
        item.trackId?.let { extras.putInt("track_id", it) }
        return MediaItem.Builder()
            .setMediaId("$DRIVE_FILE_PREFIX$driveFileId")
            .setUri(api.driveFileStreamUri(driveFileId))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(displayTitle)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .setArtworkUri(
                        api.driveFileCoverUri(driveFileId)
                    )
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
    fun withCurrentQuality(item: MediaItem): MediaItem {
        if (item.mediaMetadata.extras?.getBoolean("is_offline_download") == true ||
            item.mediaMetadata.extras?.getBoolean("is_device_local") == true ||
            item.localConfiguration?.uri?.scheme == "file" ||
            item.localConfiguration?.uri?.scheme == "content"
        ) {
            return item
        }

        val uri = when {
            item.mediaId.startsWith(TRACK_PREFIX) -> {
                val id = item.mediaId
                    .removePrefix(TRACK_PREFIX)
                    .toIntOrNull()
                    ?: return item
                api.streamUri(id)
            }

            item.mediaId.startsWith(DRIVE_FILE_PREFIX) -> {
                val id = item.mediaId
                    .removePrefix(DRIVE_FILE_PREFIX)
                    .toIntOrNull()
                    ?: return item
                api.driveFileStreamUri(id)
            }

            else -> return item
        }
        return MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(uri)
            .setMediaMetadata(item.mediaMetadata)
            .build()
    }

    fun authorizationHeaders(): Map<String, String> =
        api.authorizationHeaders()

    fun prewarm(
        mediaId: String,
        quality: StreamQuality = PlaybackQuality.current,
    ): Boolean = api.prewarm(mediaId, quality)

    fun backendLabel(): String =
        api.probe()

    fun getFavoriteStatus(mediaId: String): Boolean =
        api.getFavoriteStatus(mediaId)

    fun getMediaInfo(mediaId: String): MediaTechnicalInfo? =
        api.getMediaInfo(mediaId)

    fun getSignalPath(mediaId: String, quality: String): SignalPathInfo? =
        api.getSignalPath(mediaId, quality)

    fun getMediaInfoBatch(mediaIds: List<String>): Map<String, MediaTechnicalSummary> =
        api.getMediaInfoBatch(mediaIds)

    fun activeEndpoint(): String? =
        api.activeEndpoint()

    fun toggleFavorite(mediaId: String): Boolean =
        api.toggleFavorite(mediaId)

    fun setFavorite(mediaId: String, favorite: Boolean): Boolean =
        api.setFavorite(mediaId, favorite)

    fun createPlaylist(name: String, description: String = ""): CarPlaylist =
        api.createPlaylist(name, description)

    fun addToPlaylist(playlistId: Int, mediaId: String): Boolean =
        api.addToPlaylist(playlistId, mediaId)

    fun removeFromPlaylist(playlistId: Int, mediaId: String): Boolean =
        api.removeFromPlaylist(playlistId, mediaId)

    fun reportPlay(mediaId: String): Int =
        api.reportPlay(mediaId)

    fun playlists(): List<CarPlaylist> =
        api.playlists()

    private fun browsableItem(
        id: String,
        title: String,
        subtitle: String,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
}
