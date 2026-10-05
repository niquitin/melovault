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

enum class DownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    FINALIZING,
    OFFLINE_READY,
    FAILED,
    CANCELLED,
}

data class DownloadStatus(
    val trackId: Long,
    val state: DownloadState,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null,
    val title: String = "",
    val artist: String = "",
)

data class DownloadJob(
    val track: CarTrack,
    @Volatile var state: DownloadState = DownloadState.QUEUED,
    @Volatile var bytesDownloaded: Long = 0L,
    @Volatile var totalBytes: Long = 0L,
    @Volatile var errorMessage: String? = null,
    @Volatile var isCancelled: Boolean = false,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
)

open class PinnedDownloadManager(
    private val store: OfflineMetadataStore,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val streamOpener: ((track: CarTrack, apiClient: CarApiClient) -> Pair<InputStream, Long?>)? = null,
) {
    private var context: Context? = null

    constructor(
        context: Context,
        store: OfflineMetadataStore = OfflineMetadataStore.getDefault(context),
        executor: ExecutorService = Executors.newSingleThreadExecutor(),
    ) : this(store, executor, null) {
        this.context = context
    }

    init {
        cleanOrphanParts()
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
        fun onDownloadStarted(trackId: Long) {}
        fun onDownloadProgress(trackId: Long, bytesDownloaded: Long) {}
        fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
            onDownloadProgress(trackId, bytesDownloaded)
        }
        fun onDownloadFinalizing(trackId: Long) {}
        fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {}
        fun onDownloadFailed(trackId: Long, error: String) {}
        fun onDownloadCancelled(trackId: Long) {}
        fun onDownloadDeleted(trackId: Long) {}
    }

    private val listeners = Collections.synchronizedSet(mutableSetOf<DownloadListener>())
    private val jobs = ConcurrentHashMap<Long, DownloadJob>()
    private val activeStreams = ConcurrentHashMap<Long, InputStream>()

    fun cleanOrphanParts(): Int {
        val storageDir = store.getStorageDirectory()
        val partFiles = storageDir.listFiles { _, name -> name.endsWith(".part") } ?: emptyArray()
        var count = 0
        for (file in partFiles) {
            if (file.delete()) {
                count++
            }
        }
        if (count > 0) {
            Log.i(TAG, "Cleaned $count orphan .part files on startup")
        }
        return count
    }

    fun addListener(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    fun isDownloading(trackId: Long): Boolean {
        val job = jobs[trackId] ?: return false
        return job.state == DownloadState.QUEUED ||
            job.state == DownloadState.DOWNLOADING ||
            job.state == DownloadState.FINALIZING
    }

    fun isOfflineReady(trackId: Long): Boolean {
        val job = jobs[trackId]
        if (job != null && job.state != DownloadState.OFFLINE_READY) {
            return false
        }
        val record = store.getRecord(trackId) ?: return false
        if (record.sha256.isBlank()) {
            return false
        }
        if (record.fileSizeBytes <= 0L) {
            return false
        }
        val storageDir = store.getStorageDirectory()
        val partFile = File(storageDir, "track_${trackId}.part")
        if (partFile.exists()) {
            return false
        }
        val finalFile = store.getTrackFile(record)
        if (!finalFile.exists() || !finalFile.isFile) {
            return false
        }
        val currentLength = finalFile.length()
        if (currentLength <= 0L || currentLength != record.fileSizeBytes) {
            return false
        }
        return true
    }

    fun isDownloaded(trackId: Long): Boolean {
        return isOfflineReady(trackId)
    }

    fun getDownloadStatus(trackId: Long): DownloadStatus {
        jobs[trackId]?.let { job ->
            return DownloadStatus(
                trackId = trackId,
                state = job.state,
                bytesDownloaded = job.bytesDownloaded,
                totalBytes = job.totalBytes,
                errorMessage = job.errorMessage,
                title = job.track.title,
                artist = job.track.artist,
            )
        }
        if (isOfflineReady(trackId)) {
            val record = store.getRecord(trackId)
            val size = record?.fileSizeBytes ?: 0L
            return DownloadStatus(
                trackId = trackId,
                state = DownloadState.OFFLINE_READY,
                bytesDownloaded = size,
                totalBytes = size,
                title = record?.title ?: "",
                artist = record?.artist ?: "",
            )
        }
        return DownloadStatus(trackId, DownloadState.NOT_DOWNLOADED)
    }

    fun getRecord(trackId: Long): OfflineTrackRecord? {
        return store.getRecord(trackId)
    }

    fun deleteDownload(trackId: Long): Boolean {
        cancelDownload(trackId)
        jobs.remove(trackId)
        val storageDir = store.getStorageDirectory()
        val partFile = File(storageDir, "track_${trackId}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        val removed = store.removeRecord(trackId)
        if (removed) {
            notifyFailed(trackId, "Deleted")
            notifyDeleted(trackId)
        }
        return removed
    }

    fun cancelDownload(trackId: Long): Boolean {
        val job = jobs[trackId] ?: return false
        if (job.state == DownloadState.OFFLINE_READY) return false
        job.isCancelled = true
        val wasDownloading = (job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING)
        job.state = DownloadState.CANCELLED
        activeStreams.remove(trackId)?.let { runCatching { it.close() } }
        val storageDir = store.getStorageDirectory()
        val partFile = File(storageDir, "track_${trackId}.part")
        if (!wasDownloading) {
            if (partFile.exists()) {
                partFile.delete()
            }
            notifyCancelled(trackId)
        }
        return true
    }

    fun retryDownload(
        trackId: Long,
        apiClient: CarApiClient = CarApiClient(),
    ): Boolean {
        val job = jobs[trackId] ?: return false
        if (job.state != DownloadState.FAILED && job.state != DownloadState.CANCELLED) return false
        val storageDir = store.getStorageDirectory()
        val partFile = File(storageDir, "track_${trackId}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        job.isCancelled = false
        job.state = DownloadState.QUEUED
        job.bytesDownloaded = 0L
        job.errorMessage = null
        notifyStarted(trackId)
        executor.submit { runDownloadJob(job, apiClient) }
        return true
    }

    fun startDownload(
        track: CarTrack,
        apiClient: CarApiClient = CarApiClient(),
    ) {
        val trackId = track.id.toLong()
        if (isOfflineReady(trackId)) {
            Log.i(TAG, "Track $trackId already offline-ready, skipping")
            return
        }

        val existingJob = jobs[trackId]
        if (existingJob != null) {
            if (existingJob.state == DownloadState.QUEUED ||
                existingJob.state == DownloadState.DOWNLOADING ||
                existingJob.state == DownloadState.FINALIZING
            ) {
                Log.i(TAG, "Track $trackId is already downloading or queued")
                return
            }
        }

        val total = track.sourceSizeBytes ?: 0L
        val job = DownloadJob(
            track = track,
            state = DownloadState.QUEUED,
            totalBytes = total,
        )
        jobs[trackId] = job
        notifyStarted(trackId)

        executor.submit { runDownloadJob(job, apiClient) }
    }

    private fun runDownloadJob(job: DownloadJob, apiClient: CarApiClient) {
        val track = job.track
        val trackId = track.id.toLong()

        if (job.isCancelled) {
            job.state = DownloadState.CANCELLED
            notifyCancelled(trackId)
            return
        }

        var partFile: File? = null
        var stream: InputStream? = null
        try {
            job.state = DownloadState.DOWNLOADING
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

            val (input, reportedLength) = if (streamOpener != null) {
                streamOpener.invoke(track, apiClient)
            } else {
                val streamUrl = apiClient.streamUri(track.id).toString()
                val headers = apiClient.authorizationHeaders()
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
                Pair(conn.inputStream, conn.contentLengthLong.takeIf { it > 0L })
            }

            stream = input
            activeStreams[trackId] = input

            val expectedLength = reportedLength ?: (track.sourceSizeBytes?.takeIf { it > 0L })
            val totalTarget = expectedLength ?: 0L
            job.totalBytes = totalTarget
            notifyProgress(trackId, 0L, totalTarget)

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesRead = 0L

            FileOutputStream(partFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var lastProgressNotifyMs = System.currentTimeMillis()
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (job.isCancelled) {
                        throw IOException("Download cancelled")
                    }
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    job.bytesDownloaded = totalBytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressNotifyMs >= 100) {
                        lastProgressNotifyMs = now
                        notifyProgress(trackId, totalBytesRead, totalTarget)
                    }
                }
                output.flush()
            }

            if (job.isCancelled) {
                throw IOException("Download cancelled")
            }

            if (totalBytesRead <= 0L) {
                throw IOException("Downloaded empty file for track $trackId")
            }

            if (reportedLength != null && reportedLength > 0L && totalBytesRead != reportedLength) {
                throw IOException("Content-Length mismatch: expected $reportedLength, got $totalBytesRead")
            }

            job.state = DownloadState.FINALIZING
            job.bytesDownloaded = totalBytesRead
            job.totalBytes = totalBytesRead
            notifyFinalizing(trackId)

            val sha256 = bytesToHex(digest.digest())
            if (sha256.isBlank()) {
                throw IOException("Failed to calculate SHA-256 for track $trackId")
            }

            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            if (!finalFile.exists() || finalFile.length() != totalBytesRead) {
                throw IOException("Final file verification failed after rename")
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
            job.state = DownloadState.OFFLINE_READY
            jobs.remove(trackId)

            if (!isOfflineReady(trackId)) {
                throw IOException("Offline ready verification failed after saving record")
            }

            Log.i(TAG, "Successfully downloaded track $trackId ($totalBytesRead bytes, sha256=${sha256.take(8)}...)")
            notifyCompleted(trackId, record)
        } catch (e: Exception) {
            activeStreams.remove(trackId)
            runCatching { stream?.close() }
            partFile?.let { if (it.exists()) it.delete() }
            if (job.isCancelled) {
                job.state = DownloadState.CANCELLED
                notifyCancelled(trackId)
            } else {
                Log.e(TAG, "Download failed for track $trackId: ${e.message}", e)
                job.state = DownloadState.FAILED
                job.errorMessage = e.message ?: "Download failed"
                notifyFailed(trackId, job.errorMessage ?: "Download failed")
            }
        } finally {
            activeStreams.remove(trackId)
            runCatching { stream?.close() }
        }
    }

    private fun notifyStarted(trackId: Long) {
        listeners.toList().forEach { it.onDownloadStarted(trackId) }
    }

    private fun notifyProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
        listeners.toList().forEach {
            it.onDownloadProgress(trackId, bytesDownloaded, totalBytes)
        }
    }

    private fun notifyFinalizing(trackId: Long) {
        listeners.toList().forEach { it.onDownloadFinalizing(trackId) }
    }

    private fun notifyCompleted(trackId: Long, record: OfflineTrackRecord) {
        listeners.toList().forEach { it.onDownloadCompleted(trackId, record) }
    }

    private fun notifyFailed(trackId: Long, error: String) {
        listeners.toList().forEach { it.onDownloadFailed(trackId, error) }
    }

    private fun notifyCancelled(trackId: Long) {
        listeners.toList().forEach { it.onDownloadCancelled(trackId) }
    }

    private fun notifyDeleted(trackId: Long) {
        listeners.toList().forEach { it.onDownloadDeleted(trackId) }
    }

    fun toMediaItem(record: OfflineTrackRecord): MediaItem {
        val file = store.getTrackFile(record)
        val fileUri = Uri.fromFile(file)
        val extras = Bundle().apply {
            putInt("track_id", record.trackId.toInt())
            putLong("track_id_long", record.trackId)
            putLong("duration_ms", record.durationMs)
            putLong("size_bytes", record.fileSizeBytes)
            record.format?.let { putString("format", it) }
            record.codec?.let { putString("codec", it) }
            record.bitrate?.let { putInt("bitrate", it) }
            record.sampleRate?.let { putInt("sample_rate", it) }
            record.channels?.let { putInt("channels", it) }
            record.loudnessLufs?.let { putDouble("loudness_lufs", it) }
            record.truePeakDbtp?.let { putDouble("true_peak_dbtp", it) }
            record.loudnessRangeLu?.let { putDouble("loudness_range_lu", it) }
            record.analysisVersion?.let { putInt("loudness_analysis_version", it) }
            putBoolean("is_offline_download", true)
            putString("playback_source", "PINNED_DOWNLOAD")
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
        store.reconcile()
        return store.getAllRecords()
            .filter { isOfflineReady(it.trackId) }
            .map { toMediaItem(it) }
    }

    fun getActiveJobMediaItems(): List<MediaItem> {
        return jobs.values
            .filter { it.state != DownloadState.OFFLINE_READY }
            .sortedBy { it.enqueuedAtMs }
            .map { job ->
                val extras = Bundle().apply {
                    putLong("track_id_long", job.track.id.toLong())
                    putInt("track_id", job.track.id)
                    putString("download_state", job.state.name)
                    putLong("bytes_downloaded", job.bytesDownloaded)
                    putLong("total_bytes", job.totalBytes)
                    putString("error_message", job.errorMessage)
                    putBoolean("is_download_job", true)
                }
                MediaItem.Builder()
                    .setMediaId("download_job:${job.track.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(job.track.title.ifBlank { "Bài hát #${job.track.id}" })
                            .setArtist(job.track.artist.ifBlank { "Nghệ sĩ không rõ" })
                            .setAlbumTitle(job.track.album)
                            .setExtras(extras)
                            .setIsBrowsable(false)
                            .setIsPlayable(false)
                            .build()
                    )
                    .build()
            }
    }
}
