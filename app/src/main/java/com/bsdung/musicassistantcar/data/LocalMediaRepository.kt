package com.bsdung.musicassistantcar.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class LocalMediaRepository(
    private val context: Context? = null,
    private val contentResolverSupplier: () -> ContentResolver? = { context?.contentResolver },
    private val permissionChecker: (() -> Boolean)? = null,
) {
    companion object {
        private const val TAG = "LocalMediaRepository"
        const val DEVICE_PREFIX = "device:"

        @Volatile
        private var defaultInstance: LocalMediaRepository? = null

        fun getDefault(context: Context): LocalMediaRepository {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: LocalMediaRepository(context.applicationContext).also {
                    defaultInstance = it
                }
            }
        }
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

    fun getLocalTracks(
        page: Int = 0,
        pageSize: Int = 50,
        sort: String = "",
    ): List<MediaItem> {
        if (!hasPermission()) {
            Log.w(TAG, "Audio permission not granted; returning empty local tracks")
            return emptyList()
        }

        val resolver = contentResolverSupplier() ?: return emptyList()
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
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val items = mutableListOf<MediaItem>()

        try {
            resolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val album = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    val extras = Bundle().apply {
                        putLong("duration_ms", durationMs)
                        putLong("size_bytes", sizeBytes)
                        mimeType?.let { putString("format", it) }
                        putBoolean("is_device_local", true)
                        // Note: NO loudness normalization fields; falls back safely to unity gain (0.0 dB)
                    }

                    val mediaItem = MediaItem.Builder()
                        .setMediaId("$DEVICE_PREFIX$id")
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

                    items.add(mediaItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed querying MediaStore: ${e.message}", e)
            return emptyList()
        }

        val offset = page.coerceAtLeast(0) * pageSize
        if (offset >= items.size) return emptyList()
        return items.subList(offset, (offset + pageSize).coerceAtMost(items.size))
    }

    fun getLocalTrack(mediaId: String): MediaItem? {
        if (!mediaId.startsWith(DEVICE_PREFIX)) return null
        val id = mediaId.removePrefix(DEVICE_PREFIX).toLongOrNull() ?: return null
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
                    val extras = Bundle().apply {
                        putLong("duration_ms", durationMs)
                        putLong("size_bytes", sizeBytes)
                        mimeType?.let { putString("format", it) }
                        putBoolean("is_device_local", true)
                    }

                    return MediaItem.Builder()
                        .setMediaId("$DEVICE_PREFIX$id")
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
}
