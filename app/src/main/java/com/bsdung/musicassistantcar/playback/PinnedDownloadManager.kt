package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PinnedDownloadManager(
    private val store: OfflineMetadataStore,
    private val executor: ExecutorService = Executors.newFixedThreadPool(2),
) {
    private var context: Context? = null

    constructor(
        context: Context,
        store: OfflineMetadataStore = OfflineMetadataStore.getDefault(context),
        executor: ExecutorService = Executors.newFixedThreadPool(2),
    ) : this(store, executor) {
        this.context = context
    }
    companion object {
        private const val TAG = "PinnedDownloadManager"
        const val DOWNLOADED_PREFIX = "downloaded:"

        @Volatile
        private var defaultInstance: PinnedDownloadManager? = null

        fun getDefault(context: Context): PinnedDownloadManager {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: PinnedDownloadManager(context.applicationContext).also {
                    defaultInstance = it
                }
            }
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            val hexArray = "0123456789abcdef".toCharArray()
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xFF
                hexChars[j * 2] = hexArray[v ushr 4]
                hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            }
            return String(hexChars)
        }
    }

    interface DownloadListener {
        fun onDownloadStarted(trackId: Long)
        fun onDownloadProgress(trackId: Long, bytesDownloaded: Long)
        fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord)
        fun onDownloadFailed(trackId: Long, error: String)
    }

    private val listeners = Collections.synchronizedSet(mutableSetOf<DownloadListener>())
    private val activeDownloads = ConcurrentHashMap<Long, Boolean>()

    fun addListener(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    fun isDownloading(trackId: Long): Boolean {
        return activeDownloads[trackId] == true
    }

    fun isDownloaded(trackId: Long): Boolean {
        return store.hasTrack(trackId)
    }

    fun getRecord(trackId: Long): OfflineTrackRecord? {
        return store.getRecord(trackId)
    }

    fun deleteDownload(trackId: Long): Boolean {
        val removed = store.removeRecord(trackId)
        if (removed) {
            notifyFailed(trackId, "Deleted")
        }
        return removed
    }

    fun startDownload(
        track: CarTrack,
        apiClient: CarApiClient = CarApiClient(),
    ) {
        val trackId = track.id.toLong()
        if (isDownloaded(trackId)) {
            Log.i(TAG, "Track $trackId already downloaded, skipping")
            return
        }
        if (activeDownloads.putIfAbsent(trackId, true) != null) {
            Log.i(TAG, "Track $trackId is already downloading")
            return
        }

        notifyStarted(trackId)

        executor.submit {
            var partFile: File? = null
            try {
                val streamUrl = apiClient.streamUri(track.id).toString()
                val headers = apiClient.authorizationHeaders()
                val storageDir = store.getStorageDirectory()
                if (!storageDir.exists()) {
                    storageDir.mkdirs()
                }

                val ext = track.sourceFormat?.lowercase()?.takeIf { it.isNotBlank() } ?: "audio"
                val finalFileName = "track_${trackId}.$ext"
                val partFileName = "track_${trackId}.part"
                partFile = File(storageDir, partFileName)
                val finalFile = File(storageDir, finalFileName)

                if (partFile.exists()) {
                    partFile.delete()
                }

                val url = URL(streamUrl)
                val conn = url.openConnection() as HttpURLConnection
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.connectTimeout = 20_000
                conn.readTimeout = 60_000
                conn.requestMethod = "GET"

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("HTTP error $responseCode downloading track $trackId")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytesRead = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            digest.update(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                        }
                        output.flush()
                    }
                }

                if (totalBytesRead <= 0L) {
                    throw IOException("Downloaded empty file for track $trackId")
                }

                val sha256 = bytesToHex(digest.digest())

                // Atomic rename from .part to final
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!partFile.renameTo(finalFile)) {
                    // Fallback rename
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val record = OfflineTrackRecord(
                    trackId = trackId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = ((track.duration ?: 0.0) * 1000).toLong(),
                    relativeFileName = finalFileName,
                    fileSizeBytes = totalBytesRead,
                    sha256 = sha256,
                    loudnessLufs = track.loudnessLufs,
                    truePeakDbtp = track.truePeakDbtp,
                    loudnessRangeLu = track.loudnessRangeLu,
                    analysisVersion = track.loudnessAnalysisVersion,
                    downloadedAtMs = System.currentTimeMillis(),
                    format = track.sourceFormat,
                    codec = track.sourceCodec,
                    bitrate = track.sourceBitrate,
                    sampleRate = track.sourceSampleRate,
                    channels = track.sourceChannels,
                )

                store.saveRecord(record)
                Log.i(TAG, "Successfully downloaded track $trackId ($totalBytesRead bytes, sha256=${sha256.take(8)}...)")
                notifyCompleted(trackId, record)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for track $trackId: ${e.message}", e)
                partFile?.let { if (it.exists()) it.delete() }
                notifyFailed(trackId, e.message ?: "Download failed")
            } finally {
                activeDownloads.remove(trackId)
            }
        }
    }

    private fun notifyStarted(trackId: Long) {
        listeners.toList().forEach { it.onDownloadStarted(trackId) }
    }

    private fun notifyCompleted(trackId: Long, record: OfflineTrackRecord) {
        listeners.toList().forEach { it.onDownloadCompleted(trackId, record) }
    }

    private fun notifyFailed(trackId: Long, error: String) {
        listeners.toList().forEach { it.onDownloadFailed(trackId, error) }
    }

    fun toMediaItem(record: OfflineTrackRecord): MediaItem {
        val file = store.getTrackFile(record)
        val fileUri = Uri.fromFile(file)
        val extras = Bundle().apply {
            putLong("track_id", record.trackId)
            putLong("duration_ms", record.durationMs)
            putLong("size_bytes", record.fileSizeBytes)
            record.format?.let { putString("format", it) }
            record.codec?.let { putString("codec", it) }
            record.bitrate?.let { putInt("bitrate", it) }
            record.sampleRate?.let { putInt("sample_rate", it) }
            record.channels?.let { putInt("channels", it) }
            // Preserve full loudness normalization metadata offline
            record.loudnessLufs?.let { putDouble("loudness_lufs", it) }
            record.truePeakDbtp?.let { putDouble("true_peak_dbtp", it) }
            record.loudnessRangeLu?.let { putDouble("loudness_range_lu", it) }
            record.analysisVersion?.let { putInt("loudness_analysis_version", it) }
            putBoolean("is_offline_download", true)
        }

        return MediaItem.Builder()
            .setMediaId("${RemoteCatalog.TRACK_PREFIX}${record.trackId}")
            .setUri(fileUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(record.title.ifBlank { "Bài hát #${record.trackId}" })
                    .setArtist(record.artist.ifBlank { "Nghệ sĩ không rõ" })
                    .setAlbumTitle(record.album)
                    .setExtras(extras)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    fun getAllDownloadedMediaItems(): List<MediaItem> {
        return store.getAllRecords()
            .filter { store.hasTrack(it.trackId) }
            .map { toMediaItem(it) }
    }
}
