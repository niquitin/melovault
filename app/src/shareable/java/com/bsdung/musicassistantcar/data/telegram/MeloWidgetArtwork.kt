package com.bsdung.musicassistantcar.data.telegram

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.util.Size
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/** Worker-thread only. Reads the current owner's existing files; never requests TDLib or network. */
internal object MeloWidgetArtwork {
    private fun ownerCurrent(owner: MeloWidgetSession.Owner): Boolean =
        ShareableTdlibOwner.authorizedAccount() == owner.account && ShareableTdlibOwner.authEpoch() == owner.epoch

    fun load(context: Context, item: MediaItem, metadata: MediaMetadata, owner: MeloWidgetSession.Owner?): Bitmap? =
        runCatching {
            if (Thread.currentThread().isInterrupted || (owner != null && !ownerCurrent(owner))) return null
            decode(metadata.artworkData)?.let { return it }
            val uri = item.localConfiguration?.uri ?: return null
            if (item.mediaId.startsWith("telegram:")) {
                if (owner == null || !ownerCurrent(owner) || uri.scheme != "file") return null
                val file = uri.path?.let(::File) ?: return null
                if (!TelegramAccountStoragePolicy.isOwnedFile(owner.root, file) || !file.isFile) return null
                val result = embedded { setDataSource(file.path) }
                if (!ownerCurrent(owner) || !TelegramAccountStoragePolicy.isOwnedFile(owner.root, file)) {
                    result?.recycle()
                    return null
                }
                return result
            }
            val localId = MeloLocalSourcePolicy.idOfMediaId(item.mediaId)
            if (localId != null) {
                val expected = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, localId)
                if (uri != expected) return null
            } else if (item.mediaId != "picked:audio" || uri.scheme != "content" ||
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    != PackageManager.PERMISSION_GRANTED) return null
            // Opening the descriptor checks the current read permission, including transient URI grants.
            val picture = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                embedded { setDataSource(descriptor.fileDescriptor) }
            }
            if (picture != null) return picture
            if (localId != null && Build.VERSION.SDK_INT >= 29) {
                return scale(context.contentResolver.loadThumbnail(uri,
                    Size(MeloWidgetPolicy.MAX_ART_SIDE, MeloWidgetPolicy.MAX_ART_SIDE), null))
            }
            null
        }.getOrNull()

    private fun embedded(setSource: MediaMetadataRetriever.() -> Unit): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try { retriever.setSource(); decode(retriever.embeddedPicture) }
        finally { runCatching { retriever.release() } }
    }

    private fun decode(bytes: ByteArray?): Bitmap? {
        if (bytes == null || !MeloRowArtPolicy.embeddedAllowed(bytes.size)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (minOf(bounds.outWidth, bounds.outHeight) <= 0 || longest > 32768) return null
        val options = BitmapFactory.Options().apply {
            // Pass the longer edge twice so panoramas also stay small before final scaling.
            inSampleSize = MeloRowArtPolicy.decodeSample(longest, longest, MeloWidgetPolicy.MAX_ART_SIDE)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let(::scale)
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MeloWidgetPolicy.MAX_ART_SIDE) return bitmap
        val ratio = MeloWidgetPolicy.MAX_ART_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1), true).also { if (it !== bitmap) bitmap.recycle() }
    }
}
