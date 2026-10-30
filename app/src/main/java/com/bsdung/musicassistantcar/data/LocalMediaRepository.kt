package com.bsdung.musicassistantcar.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class LocalMediaTrack(
    val id: Long,
    val volumeName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val relativePath: String, // Normalized relative path (e.g. "Music/Vietnam" or "")
    val contentUri: Uri,
) {
    val mediaId: String = "device:$volumeName:$id"
}

data class LocalMediaFolder(
    val volumeName: String,
    val relativePath: String, // Normalized relative path (e.g. "Music/Vietnam" or "")
    val displayName: String,  // e.g. "Vietnam" or volume label for volume root
    val directTracks: List<LocalMediaTrack> = emptyList(),
    val childFolders: List<String> = emptyList(), // Direct child folder relative paths
) {
    val id: String = "device_folder:$volumeName:$relativePath"
    val directTrackCount: Int get() = directTracks.size
    val childFolderCount: Int get() = childFolders.size
}

data class LocalMediaVolume(
    val volumeName: String,
    val displayName: String,
    val isRemovable: Boolean,
    val totalTrackCount: Int,
) {
    val id: String = "device_folder:$volumeName:"
}

class LocalMediaRepository(
    private val context: Context? = null,
    private val contentResolverSupplier: () -> ContentResolver? = { context?.contentResolver },
    private val permissionChecker: (() -> Boolean)? = null,
    private val tracksProvider: (() -> List<LocalMediaTrack>)? = null,
    private val sharedPrefsSupplier: (() -> SharedPreferences?)? = {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    },
) {
    /** Result fact for the last uncached track read; cached reads retain that result. */
    @Volatile
    var lastTrackReadFailed: Boolean = false
        private set

    companion object {
        private const val TAG = "LocalMediaRepository"
        const val DEVICE_PREFIX = "device:"
        const val DEVICE_FOLDER_PREFIX = "device_folder:"

        const val PREFS_NAME = "melovault_local_prefs"
        const val KEY_LAST_DEVICE_FOLDER_VOLUME = "last_device_music_folder_volume"
        const val KEY_LAST_DEVICE_FOLDER_PATH = "last_device_music_folder_path"

        @Volatile
        private var defaultInstance: LocalMediaRepository? = null

        fun getDefault(context: Context): LocalMediaRepository {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: LocalMediaRepository(context.applicationContext).also {
                    defaultInstance = it
                }
            }
        }

        fun normalizeRelativePath(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.trim()
                .replace('\\', '/')
                .trim('/')
        }

        fun buildFolderHierarchy(
            tracks: List<LocalMediaTrack>,
            volumeNameResolver: (String) -> String = { vol ->
                if (vol.isEmpty() || vol == "external_primary" || vol == "primary" || vol == "internal") {
                    "Bộ nhớ trong"
                } else {
                    "Thiết bị lưu trữ"
                }
            },
        ): Map<String, Map<String, LocalMediaFolder>> {
            val volumeMap = mutableMapOf<String, MutableMap<String, MutableFolder>>()

            for (track in tracks) {
                val vol = track.volumeName.ifBlank { "external_primary" }
                val folderMap = volumeMap.getOrPut(vol) { mutableMapOf() }
                // Ensure volume root exists
                folderMap.getOrPut("") { MutableFolder(vol, "") }

                val normPath = normalizeRelativePath(track.relativePath)
                if (normPath.isEmpty()) {
                    folderMap[""]!!.directTracks.add(track)
                } else {
                    val segments = normPath.split('/')
                    var currentSubPath = ""
                    for (segment in segments) {
                        val parentPath = currentSubPath
                        currentSubPath = if (parentPath.isEmpty()) segment else "$parentPath/$segment"
                        val parentFolder = folderMap.getOrPut(parentPath) { MutableFolder(vol, parentPath) }
                        folderMap.getOrPut(currentSubPath) { MutableFolder(vol, currentSubPath) }
                        parentFolder.childFolderPaths.add(currentSubPath)
                    }
                    folderMap[normPath]!!.directTracks.add(track)
                }
            }

            // Convert to immutable LocalMediaFolder
            return volumeMap.mapValues { (vol, folderMap) ->
                folderMap.mapValues { (path, mutFolder) ->
                    val dispName = if (path.isEmpty()) {
                        volumeNameResolver(vol)
                    } else {
                        path.substringAfterLast('/')
                    }

                    val sortedChildFolders = mutFolder.childFolderPaths.toList().sortedWith(
                        compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it.substringAfterLast('/') }
                            .thenBy { it }
                    )

                    val sortedTracks = mutFolder.directTracks.sortedWith(
                        compareBy<LocalMediaTrack, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
                            .thenBy { it.id }
                    )

                    LocalMediaFolder(
                        volumeName = vol,
                        relativePath = path,
                        displayName = dispName,
                        directTracks = sortedTracks,
                        childFolders = sortedChildFolders,
                    )
                }
            }
        }

        private class MutableFolder(
            val volumeName: String,
            val relativePath: String,
            val directTracks: MutableList<LocalMediaTrack> = mutableListOf(),
            val childFolderPaths: MutableSet<String> = mutableSetOf(),
        )
    }

    @Volatile
    private var cachedTracks: List<LocalMediaTrack>? = null

    @Volatile
    private var cachedHierarchy: Map<String, Map<String, LocalMediaFolder>>? = null

    @Synchronized
    fun invalidateCache() {
        cachedTracks = null
        cachedHierarchy = null
    }

    fun hasPermission(): Boolean {
        if (permissionChecker != null) return permissionChecker.invoke()
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_MEDIA_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getVolumeDisplayName(volumeName: String): String {
        val clean = volumeName.trim()
        if (clean.isEmpty() || clean == "external_primary" || clean == "primary" || clean == "internal") {
            return "Bộ nhớ trong"
        }
        val ctx = context
        if (ctx != null) {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null) {
                for (vol in sm.storageVolumes) {
                    val matches = if (Build.VERSION.SDK_INT >= 30) {
                        vol.mediaStoreVolumeName == clean
                    } else {
                        vol.uuid == clean
                    }
                    if (matches) {
                        val desc = vol.getDescription(ctx)
                        val isSd = vol.isRemovable && desc.contains("SD", ignoreCase = true)
                        return if (isSd) "Thẻ SD" else "Thiết bị lưu trữ"
                    }
                }
            }
        }
        return "Thiết bị lưu trữ"
    }

    fun getAllTracks(): List<LocalMediaTrack> {
        cachedTracks?.let { return it }
        synchronized(this) {
            cachedTracks?.let { return it }
            lastTrackReadFailed = false
            val tracks = try {
                if (tracksProvider != null) tracksProvider.invoke() else queryAllLocalTracks()
            } catch (e: Exception) {
                lastTrackReadFailed = true
                throw e
            }
            cachedTracks = tracks
            cachedHierarchy = buildFolderHierarchy(tracks) { vol -> getVolumeDisplayName(vol) }
            return tracks
        }
    }

    fun getHierarchy(): Map<String, Map<String, LocalMediaFolder>> {
        cachedHierarchy?.let { return it }
        getAllTracks()
        return cachedHierarchy ?: emptyMap()
    }

    fun getVolumes(): List<LocalMediaVolume> {
        val hierarchy = getHierarchy()
        if (hierarchy.isEmpty()) return emptyList()

        return hierarchy.keys.map { volName ->
            val folderMap = hierarchy[volName] ?: emptyMap()
            val totalTracks = folderMap.values.sumOf { it.directTrackCount }
            val dispName = getVolumeDisplayName(volName)
            val isRemovable = volName != "external_primary" && volName.isNotBlank()
            LocalMediaVolume(
                volumeName = volName,
                displayName = dispName,
                isRemovable = isRemovable,
                totalTrackCount = totalTracks,
            )
        }.sortedWith(compareBy({ it.volumeName != "external_primary" }, { it.displayName }))
    }

    fun getDeviceMusicCategories(): List<MediaItem> {
        val totalTracks = getAllTracks().size
        return listOf(
            MediaItem.Builder()
                .setMediaId(RemoteCatalog.DEVICE_LOCAL_ALL_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Tất cả bài hát")
                        .setSubtitle(if (totalTracks > 0) "$totalTracks bài" else "Toàn bộ bài hát trên thiết bị")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build(),
            MediaItem.Builder()
                .setMediaId(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Theo thư mục")
                        .setSubtitle("Duyệt theo cấu trúc thư mục")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build(),
        )
    }

    fun getDeviceFolderRoots(): List<MediaItem> {
        val volumes = getVolumes()
        return volumes.map { vol ->
            val extras = Bundle().apply {
                putString("volume_name", vol.volumeName)
                putString("relative_path", "")
                putBoolean("is_device_folder", true)
            }
            MediaItem.Builder()
                .setMediaId(vol.id) // "device_folder:$volName:"
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(vol.displayName)
                        .setSubtitle("${vol.totalTrackCount} bài")
                        .setExtras(extras)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }
    }

    fun getFolderContents(
        volume: String,
        relativePath: String,
        page: Int = 0,
        pageSize: Int = 50,
    ): List<MediaItem> {
        val hierarchy = getHierarchy()
        val volMap = hierarchy[volume] ?: return emptyList()
        val normPath = normalizeRelativePath(relativePath)
        val folder = volMap[normPath] ?: return emptyList()

        val childFolderItems = folder.childFolders.mapNotNull { childRelPath ->
            val child = volMap[childRelPath] ?: return@mapNotNull null
            val sub = when {
                child.directTrackCount > 0 && child.childFolderCount > 0 ->
                    "${child.childFolderCount} thư mục · ${child.directTrackCount} bài"
                child.directTrackCount > 0 ->
                    "${child.directTrackCount} bài"
                child.childFolderCount > 0 ->
                    "${child.childFolderCount} thư mục"
                else -> ""
            }
            val extras = Bundle().apply {
                putString("volume_name", volume)
                putString("relative_path", childRelPath)
                putBoolean("is_device_folder", true)
            }
            MediaItem.Builder()
                .setMediaId(child.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(child.displayName)
                        .setSubtitle(sub)
                        .setExtras(extras)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
        }

        val directTrackItems = folder.directTracks.map { track ->
            trackToMediaItem(track)
        }

        val combined = childFolderItems + directTrackItems
        val offset = page.coerceAtLeast(0) * pageSize
        if (offset >= combined.size) return emptyList()
        return combined.subList(offset, (offset + pageSize).coerceAtMost(combined.size))
    }

    fun folderExists(volume: String, relativePath: String): Boolean {
        val hierarchy = getHierarchy()
        val volMap = hierarchy[volume] ?: return false
        val normPath = normalizeRelativePath(relativePath)
        return volMap.containsKey(normPath)
    }

    fun getFolderItem(folderId: String): MediaItem? {
        if (!folderId.startsWith(DEVICE_FOLDER_PREFIX)) return null
        val payload = folderId.removePrefix(DEVICE_FOLDER_PREFIX)
        val colonIdx = payload.indexOf(':')
        val volume = if (colonIdx >= 0) payload.substring(0, colonIdx) else payload
        val relPath = if (colonIdx >= 0) payload.substring(colonIdx + 1) else ""
        val normPath = normalizeRelativePath(relPath)

        val folder = getHierarchy()[volume]?.get(normPath)
        val title = folder?.displayName ?: if (normPath.isEmpty()) getVolumeDisplayName(volume) else normPath.substringAfterLast('/')
        val sub = if (normPath.isEmpty()) "${folder?.directTrackCount ?: 0} bài" else "Thư mục thiết bị"

        val extras = Bundle().apply {
            putString("volume_name", volume)
            putString("relative_path", normPath)
            putBoolean("is_device_folder", true)
        }
        return MediaItem.Builder()
            .setMediaId(folderId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(sub)
                    .setExtras(extras)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    fun trackToMediaItem(track: LocalMediaTrack): MediaItem {
        val extras = Bundle().apply {
            putLong("duration_ms", track.durationMs)
            putLong("size_bytes", track.sizeBytes)
            track.mimeType?.let { putString("format", it) }
            putString("volume_name", track.volumeName)
            putString("relative_path", track.relativePath)
            putBoolean("is_device_local", true)
        }
        return MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(track.contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title.ifBlank { "Nhạc thiết bị #${track.id}" })
                    .setArtist(track.artist.ifBlank { "Nghệ sĩ không rõ" })
                    .setAlbumTitle(track.album)
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    fun getLocalTracks(
        page: Int = 0,
        pageSize: Int = 50,
        sort: String = "",
    ): List<MediaItem> {
        if (!hasPermission()) {
            Log.w(TAG, "Audio permission not granted; returning empty local tracks")
            return emptyList()
        }

        val allTracks = getAllTracks()
        val sorted = allTracks.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )

        val offset = page.coerceAtLeast(0) * pageSize
        if (offset >= sorted.size) return emptyList()
        return sorted.subList(offset, (offset + pageSize).coerceAtMost(sorted.size))
            .map { trackToMediaItem(it) }
    }

    fun getLocalTrack(mediaId: String): MediaItem? {
        if (!mediaId.startsWith(DEVICE_PREFIX)) return null
        val payload = mediaId.removePrefix(DEVICE_PREFIX)
        val parts = payload.split(':')
        val (volume, id) = if (parts.size >= 2) {
            parts[0] to parts[1].toLongOrNull()
        } else {
            null to parts[0].toLongOrNull()
        }
        if (id == null) return null

        val cached = getAllTracks().firstOrNull { it.id == id && (volume == null || it.volumeName == volume) }
        if (cached != null) {
            return trackToMediaItem(cached)
        }

        return queryLocalTrackById(id, volume)
    }

    private fun queryLocalTrackById(id: Long, volume: String?): MediaItem? {
        val resolver = contentResolverSupplier() ?: return null
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                    val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val album = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val volName = volume ?: "external_primary"
                    val extras = Bundle().apply {
                        putLong("duration_ms", durationMs)
                        putLong("size_bytes", sizeBytes)
                        mimeType?.let { putString("format", it) }
                        putString("volume_name", volName)
                        putBoolean("is_device_local", true)
                    }

                    return MediaItem.Builder()
                        .setMediaId("device:$volName:$id")
                        .setUri(contentUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title?.ifBlank { "Nhạc thiết bị #$id" } ?: "Nhạc thiết bị #$id")
                                .setArtist(artist?.ifBlank { "Nghệ sĩ không rõ" } ?: "Nghệ sĩ không rõ")
                                .setAlbumTitle(album.orEmpty())
                                .setExtras(extras)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                        )
                        .build()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving local track $id: ${e.message}", e)
        }
        return null
    }

    private fun queryAllLocalTracks(): List<LocalMediaTrack> {
        if (!hasPermission()) {
            lastTrackReadFailed = true
            return emptyList()
        }
        val resolver = contentResolverSupplier() ?: run {
            lastTrackReadFailed = true
            return emptyList()
        }
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val hasSdk29 = Build.VERSION.SDK_INT >= 29

        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
        )
        if (hasSdk29) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
            projectionList.add(MediaStore.Audio.Media.VOLUME_NAME)
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val results = mutableListOf<LocalMediaTrack>()

        try {
            resolver.query(uri, projectionList.toTypedArray(), selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (hasSdk29) cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
                val volCol = if (hasSdk29) cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val rawArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val rawAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val data = if (dataCol >= 0) cursor.getString(dataCol) else null

                    val volumeName = if (volCol >= 0) {
                        cursor.getString(volCol)?.takeIf { it.isNotBlank() } ?: "external_primary"
                    } else {
                        "external_primary"
                    }

                    val rawRelPath = if (relPathCol >= 0) {
                        cursor.getString(relPathCol)
                    } else null

                    val relPath = if (!rawRelPath.isNullOrBlank()) {
                        normalizeRelativePath(rawRelPath)
                    } else if (!data.isNullOrBlank()) {
                        deriveRelativePathFromData(data)
                    } else {
                        ""
                    }

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    results.add(
                        LocalMediaTrack(
                            id = id,
                            volumeName = volumeName,
                            title = rawTitle?.ifBlank { "Nhạc thiết bị #$id" } ?: "Nhạc thiết bị #$id",
                            artist = rawArtist?.ifBlank { "Nghệ sĩ không rõ" } ?: "Nghệ sĩ không rõ",
                            album = rawAlbum.orEmpty(),
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            mimeType = mimeType,
                            relativePath = relPath,
                            contentUri = contentUri,
                        )
                    )
                }
            } ?: run { lastTrackReadFailed = true }
        } catch (e: Exception) {
            lastTrackReadFailed = true
            Log.e(TAG, "Failed querying MediaStore: ${e.message}", e)
        }

        return results
    }

    private fun deriveRelativePathFromData(data: String): String {
        val file = java.io.File(data)
        val parent = file.parentFile ?: return ""
        val parentPath = parent.absolutePath.replace('\\', '/')
        val commonRoots = listOf("/storage/emulated/0/", "/sdcard/", "/storage/")
        for (root in commonRoots) {
            if (parentPath.startsWith(root)) {
                val rel = parentPath.removePrefix(root).trim('/')
                return if (root == "/storage/" && rel.contains('/')) {
                    rel.substringAfter('/').trim('/')
                } else {
                    rel
                }
            }
        }
        return normalizeRelativePath(parent.name)
    }

    fun getLastBrowsedFolder(): Pair<String, String>? {
        val prefs = sharedPrefsSupplier?.invoke() ?: return null
        val vol = prefs.getString(KEY_LAST_DEVICE_FOLDER_VOLUME, null) ?: return null
        val path = prefs.getString(KEY_LAST_DEVICE_FOLDER_PATH, null) ?: return null
        return vol to path
    }

    fun saveLastBrowsedFolder(volume: String, relativePath: String) {
        val prefs = sharedPrefsSupplier?.invoke() ?: return
        prefs.edit()
            .putString(KEY_LAST_DEVICE_FOLDER_VOLUME, volume)
            .putString(KEY_LAST_DEVICE_FOLDER_PATH, normalizeRelativePath(relativePath))
            .apply()
    }

    fun clearLastBrowsedFolder() {
        val prefs = sharedPrefsSupplier?.invoke() ?: return
        prefs.edit()
            .remove(KEY_LAST_DEVICE_FOLDER_VOLUME)
            .remove(KEY_LAST_DEVICE_FOLDER_PATH)
            .apply()
    }
}
