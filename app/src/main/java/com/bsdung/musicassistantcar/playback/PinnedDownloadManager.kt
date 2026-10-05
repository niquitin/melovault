package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
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
    val attemptId: Long = 0L,
    val batchId: String? = null,
)

data class DownloadBatch(
    val batchId: String = "download_batch:${java.util.UUID.randomUUID()}",
    val sourceType: String, // "PLAYLIST", "CURRENT_REMOTE_LIST", "ALBUM", "ARTIST", "DRIVE_FOLDER"
    val sourceId: String,
    val sourceDisplayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val trackIds: List<Long>,
    val initialEligibleCount: Int,
    val knownTotalBytes: Long,
    val hasUnknownSizes: Boolean,
)

data class BulkClassificationResult(
    val totalTracks: Int,
    val alreadyOfflineTracks: List<CarTrack>,
    val inProgressTracks: List<CarTrack>,
    val eligibleNewTracks: List<CarTrack>,
    val knownTotalBytes: Long,
    val unknownSizeCount: Int,
)

data class BatchProgress(
    val batchId: String,
    val sourceDisplayName: String,
    val totalTracks: Int,
    val completedTracks: Int,
    val failedTracks: Int,
    val cancelledTracks: Int,
    val inProgressTracks: Int,
    val totalBytesKnown: Long,
    val downloadedBytes: Long,
    val hasUnknownSizes: Boolean,
    val currentTrackTitle: String?,
    val currentTrackProgress: DownloadStatus?,
)

interface DownloadWakeLock {
    fun acquire()
    fun release()
    val isHeld: Boolean
}

class AndroidDownloadWakeLock(private val context: Context) : DownloadWakeLock {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    override fun acquire() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "melovault:pinned_download_wakelock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire(30 * 60 * 1000L)
                    Log.d("DownloadWakeLock", "PARTIAL_WAKE_LOCK acquired")
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadWakeLock", "Failed to acquire wake lock", e)
        }
    }

    @Synchronized
    override fun release() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("DownloadWakeLock", "PARTIAL_WAKE_LOCK released")
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadWakeLock", "Failed to release wake lock", e)
        }
    }

    override val isHeld: Boolean
        get() = wakeLock?.isHeld == true
}

interface DownloadServiceController {
    fun onQueueActive()
    fun onQueueIdle()
    val isServiceActive: Boolean
}

class AndroidDownloadServiceController(private val context: Context) : DownloadServiceController {
    override fun onQueueActive() {
        PinnedDownloadService.start(context)
    }

    override fun onQueueIdle() {
        PinnedDownloadService.stop(context)
    }

    override val isServiceActive: Boolean
        get() = PinnedDownloadService.isServiceRunning
}

open class PinnedDownloadManager(
    private val store: OfflineMetadataStore,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val streamOpener: ((track: CarTrack, apiClient: CarApiClient) -> Pair<InputStream, Long?>)? = null,
    private val wakeLock: DownloadWakeLock? = null,
    private val serviceController: DownloadServiceController? = null,
) {
    private var context: Context? = null
    private val attemptCounter = java.util.concurrent.atomic.AtomicLong(1L)

    constructor(
        context: Context,
        store: OfflineMetadataStore = OfflineMetadataStore.getDefault(context),
        executor: ExecutorService = Executors.newSingleThreadExecutor(),
        wakeLock: DownloadWakeLock? = AndroidDownloadWakeLock(context.applicationContext),
        serviceController: DownloadServiceController? = AndroidDownloadServiceController(context.applicationContext),
    ) : this(store, executor, null, wakeLock, serviceController) {
        this.context = context.applicationContext
    }

    companion object {
        private const val TAG = "PinnedDownloadManager"
        const val DOWNLOADED_PREFIX = "downloaded:"

        @Volatile
        private var defaultInstance: PinnedDownloadManager? = null

        fun getDefault(context: Context): PinnedDownloadManager {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: PinnedDownloadManager(
                    context = context.applicationContext,
                    store = OfflineMetadataStore.getDefault(context),
                    executor = Executors.newSingleThreadExecutor(),
                    wakeLock = AndroidDownloadWakeLock(context.applicationContext),
                    serviceController = AndroidDownloadServiceController(context.applicationContext),
                ).also {
                    defaultInstance = it
                }
            }
        }

        fun setInstanceForTesting(instance: PinnedDownloadManager?) {
            defaultInstance = instance
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
    @Volatile
    private var activeBatch: DownloadBatch? = null

    init {
        cleanOrphanParts()
        restoreQueueState()
    }

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

    fun hasActiveJobs(): Boolean {
        return jobs.values.any {
            it.state == DownloadState.QUEUED ||
            it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.FINALIZING
        }
    }

    fun getCurrentActiveJob(): DownloadJob? {
        return jobs.values.firstOrNull {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.FINALIZING
        } ?: jobs.values.firstOrNull { it.state == DownloadState.QUEUED }
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
        if (!hasActiveJobs()) {
            serviceController?.onQueueIdle()
        }
        persistQueueState()
        return removed
    }

    fun cancelDownload(trackId: Long): Boolean {
        val job = jobs[trackId] ?: return false
        synchronized(job) {
            if (job.state == DownloadState.OFFLINE_READY) return false
            job.isCancelled = true
            val wasActive = (job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING)
            job.state = DownloadState.CANCELLED
            activeStreams.remove(trackId)?.let { runCatching { it.close() } }
            val storageDir = store.getStorageDirectory()
            val partFile = File(storageDir, "track_${trackId}.part")
            if (!wasActive) {
                if (partFile.exists()) {
                    partFile.delete()
                }
                notifyCancelled(trackId)
                if (!hasActiveJobs()) {
                    serviceController?.onQueueIdle()
                }
            }
            persistQueueState()
            return true
        }
    }

    fun retryDownload(
        trackId: Long,
        apiClient: CarApiClient = CarApiClient(),
    ): Boolean {
        val existingJob = jobs[trackId] ?: return false
        if (existingJob.state != DownloadState.FAILED && existingJob.state != DownloadState.CANCELLED) return false
        val storageDir = store.getStorageDirectory()
        val partFile = File(storageDir, "track_${trackId}.part")
        if (partFile.exists()) {
            partFile.delete()
        }
        val newJob = DownloadJob(
            track = existingJob.track,
            state = DownloadState.QUEUED,
            totalBytes = existingJob.track.sourceSizeBytes ?: 0L,
            attemptId = attemptCounter.incrementAndGet(),
            batchId = existingJob.batchId,
        )
        jobs[trackId] = newJob
        notifyStarted(trackId)
        serviceController?.onQueueActive()
        persistQueueState()
        executor.submit { runDownloadJob(newJob, apiClient) }
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
            attemptId = attemptCounter.incrementAndGet(),
        )
        jobs[trackId] = job
        notifyStarted(trackId)
        serviceController?.onQueueActive()
        persistQueueState()

        executor.submit { runDownloadJob(job, apiClient) }
    }

    fun classifyTracksForDownload(tracks: List<CarTrack>): BulkClassificationResult {
        val distinctTracks = tracks.distinctBy { it.id }
        val alreadyOffline = mutableListOf<CarTrack>()
        val inProgress = mutableListOf<CarTrack>()
        val eligibleNew = mutableListOf<CarTrack>()

        for (t in distinctTracks) {
            val trackId = t.id.toLong()
            if (isOfflineReady(trackId)) {
                alreadyOffline.add(t)
            } else {
                val job = jobs[trackId]
                if (job != null && (job.state == DownloadState.QUEUED ||
                        job.state == DownloadState.DOWNLOADING ||
                        job.state == DownloadState.FINALIZING)) {
                    inProgress.add(t)
                } else {
                    eligibleNew.add(t)
                }
            }
        }

        var knownBytes = 0L
        var unknownCount = 0
        for (t in eligibleNew) {
            val size = t.sourceSizeBytes
            if (size != null && size > 0L) {
                knownBytes += size
            } else {
                unknownCount++
            }
        }

        return BulkClassificationResult(
            totalTracks = distinctTracks.size,
            alreadyOfflineTracks = alreadyOffline,
            inProgressTracks = inProgress,
            eligibleNewTracks = eligibleNew,
            knownTotalBytes = knownBytes,
            unknownSizeCount = unknownCount,
        )
    }

    fun getAvailableStorageBytes(): Long {
        return try {
            store.getStorageDirectory().usableSpace
        } catch (e: Exception) {
            0L
        }
    }

    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return true
        return getAvailableStorageBytes() >= requiredBytes
    }

    fun getActiveBatch(): DownloadBatch? = activeBatch

    fun setActiveBatch(batch: DownloadBatch?) {
        activeBatch = batch
    }

    fun getActiveBatchProgress(): BatchProgress? {
        val batch = activeBatch ?: return null
        val trackIds = batch.trackIds
        var completedCount = 0
        var failedCount = 0
        var cancelledCount = 0
        var inProgressCount = 0
        var currentDownloadingTrack: CarTrack? = null
        var currentDownloadingStatus: DownloadStatus? = null
        var downloadedBytesSum = 0L

        for (id in trackIds) {
            val job = jobs[id]
            if (isOfflineReady(id)) {
                completedCount++
                val record = store.getRecord(id)
                downloadedBytesSum += (record?.fileSizeBytes ?: 0L)
            } else if (job != null) {
                when (job.state) {
                    DownloadState.QUEUED -> {
                        inProgressCount++
                    }
                    DownloadState.DOWNLOADING, DownloadState.FINALIZING -> {
                        inProgressCount++
                        if (currentDownloadingTrack == null) {
                            currentDownloadingTrack = job.track
                            currentDownloadingStatus = DownloadStatus(
                                trackId = id,
                                state = job.state,
                                bytesDownloaded = job.bytesDownloaded,
                                totalBytes = job.totalBytes,
                                errorMessage = job.errorMessage,
                                title = job.track.title,
                                artist = job.track.artist,
                            )
                        }
                        downloadedBytesSum += job.bytesDownloaded
                    }
                    DownloadState.FAILED -> {
                        failedCount++
                    }
                    DownloadState.CANCELLED -> {
                        cancelledCount++
                    }
                    DownloadState.OFFLINE_READY -> {
                        completedCount++
                        val record = store.getRecord(id)
                        downloadedBytesSum += (record?.fileSizeBytes ?: 0L)
                    }
                    DownloadState.NOT_DOWNLOADED -> {}
                }
            }
        }

        return BatchProgress(
            batchId = batch.batchId,
            sourceDisplayName = batch.sourceDisplayName,
            totalTracks = trackIds.size,
            completedTracks = completedCount,
            failedTracks = failedCount,
            cancelledTracks = cancelledCount,
            inProgressTracks = inProgressCount,
            totalBytesKnown = batch.knownTotalBytes,
            downloadedBytes = downloadedBytesSum,
            hasUnknownSizes = batch.hasUnknownSizes,
            currentTrackTitle = currentDownloadingTrack?.title,
            currentTrackProgress = currentDownloadingStatus,
        )
    }

    fun enqueueBatch(
        batch: DownloadBatch,
        tracksToEnqueue: List<CarTrack>,
        apiClient: CarApiClient = CarApiClient(),
    ): Int {
        activeBatch = batch
        var enqueuedCount = 0
        val distinctTracks = tracksToEnqueue.distinctBy { it.id }

        for (track in distinctTracks) {
            val trackId = track.id.toLong()
            if (isOfflineReady(trackId)) {
                continue
            }
            val existingJob = jobs[trackId]
            if (existingJob != null && (existingJob.state == DownloadState.QUEUED ||
                    existingJob.state == DownloadState.DOWNLOADING ||
                    existingJob.state == DownloadState.FINALIZING)) {
                continue
            }

            val storageDir = store.getStorageDirectory()
            val partFile = File(storageDir, "track_${trackId}.part")
            if (partFile.exists()) {
                partFile.delete()
            }

            val total = track.sourceSizeBytes ?: 0L
            val job = DownloadJob(
                track = track,
                state = DownloadState.QUEUED,
                totalBytes = total,
                attemptId = attemptCounter.incrementAndGet(),
                batchId = batch.batchId,
            )
            jobs[trackId] = job
            enqueuedCount++
            notifyStarted(trackId)
            executor.submit { runDownloadJob(job, apiClient) }
        }

        if (enqueuedCount > 0) {
            serviceController?.onQueueActive()
        }
        persistQueueState()
        return enqueuedCount
    }

    fun cancelPendingQueue(): Int {
        var cancelledCount = 0
        for (job in jobs.values) {
            synchronized(job) {
                if (job.state == DownloadState.QUEUED) {
                    job.isCancelled = true
                    job.state = DownloadState.CANCELLED
                    notifyCancelled(job.track.id.toLong())
                    cancelledCount++
                }
            }
        }
        if (!hasActiveJobs()) {
            serviceController?.onQueueIdle()
        }
        persistQueueState()
        return cancelledCount
    }

    fun cancelAllJobs(): Int {
        var cancelledCount = 0
        val storageDir = store.getStorageDirectory()
        for ((trackId, job) in jobs) {
            synchronized(job) {
                if (job.state == DownloadState.QUEUED || job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING) {
                    job.isCancelled = true
                    val wasActive = (job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING)
                    job.state = DownloadState.CANCELLED
                    activeStreams.remove(trackId)?.let { runCatching { it.close() } }
                    val partFile = File(storageDir, "track_${trackId}.part")
                    if (partFile.exists()) {
                        partFile.delete()
                    }
                    if (!wasActive) {
                        notifyCancelled(trackId)
                    }
                    cancelledCount++
                }
            }
        }
        if (!hasActiveJobs()) {
            serviceController?.onQueueIdle()
        }
        persistQueueState()
        return cancelledCount
    }

    fun deleteOfflinePlaylist(trackIds: List<Long>): Int {
        var count = 0
        for (trackId in trackIds.distinct()) {
            if (isOfflineReady(trackId)) {
                if (deleteDownload(trackId)) {
                    count++
                }
            }
        }
        return count
    }

    private fun runDownloadJob(job: DownloadJob, apiClient: CarApiClient) {
        val track = job.track
        val trackId = track.id.toLong()

        if (jobs[trackId] !== job) {
            return
        }

        if (job.isCancelled) {
            if (jobs[trackId] === job) {
                job.state = DownloadState.CANCELLED
                notifyCancelled(trackId)
                if (!hasActiveJobs()) {
                    serviceController?.onQueueIdle()
                }
            }
            return
        }

        var partFile: File? = null
        var finalFile: File? = null
        var stream: InputStream? = null
        try {
            wakeLock?.acquire()
            job.state = DownloadState.DOWNLOADING
            val storageDir = store.getStorageDirectory()
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val ext = track.sourceFormat?.lowercase()?.takeIf { it.isNotBlank() } ?: "audio"
            val finalFileName = "track_${trackId}.$ext"
            val partFileName = "track_${trackId}.part"
            partFile = File(storageDir, partFileName)
            finalFile = File(storageDir, finalFileName)

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

            // Transfer resource ownership immediately before cancellation/supersession check
            stream = input
            activeStreams[trackId] = input

            if (job.isCancelled || jobs[trackId] !== job) {
                throw IOException("Download cancelled")
            }

            // REQUIREMENT 1: Use ONLY a valid HTTP response-reported length for active transfer percentage.
            // If Content-Length is absent, totalTarget is 0L (indeterminate). Do not invent a percentage from metadata sourceSizeBytes.
            val totalTarget = (reportedLength?.takeIf { it > 0L }) ?: 0L
            job.totalBytes = totalTarget
            if (jobs[trackId] === job) {
                notifyProgress(trackId, 0L, totalTarget)
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesRead = 0L

            FileOutputStream(partFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var lastProgressNotifyMs = System.currentTimeMillis()
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (job.isCancelled || jobs[trackId] !== job) {
                        throw IOException("Download cancelled")
                    }
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    job.bytesDownloaded = totalBytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressNotifyMs >= 100) {
                        lastProgressNotifyMs = now
                        if (jobs[trackId] === job) {
                            notifyProgress(trackId, totalBytesRead, totalTarget)
                        }
                    }
                }
                output.flush()
            }

            if (job.isCancelled || jobs[trackId] !== job) {
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
            if (jobs[trackId] === job && !job.isCancelled) {
                notifyFinalizing(trackId)
            }

            if (job.isCancelled || jobs[trackId] !== job) {
                throw IOException("Download cancelled during finalization")
            }

            val sha256 = bytesToHex(digest.digest())
            if (sha256.isBlank()) {
                throw IOException("Failed to calculate SHA-256 for track $trackId")
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

            var committed = false
            synchronized(job) {
                if (job.isCancelled || jobs[trackId] !== job) {
                    throw IOException("Download cancelled before commit")
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

                store.saveRecord(record)
                job.state = DownloadState.OFFLINE_READY
                jobs.remove(trackId)

                if (!isOfflineReady(trackId)) {
                    if (finalFile.exists()) finalFile.delete()
                    store.removeRecord(trackId)
                    throw IOException("Offline ready verification failed after saving record")
                }

                committed = true
            }

            wakeLock?.release()
            if (committed) {
                Log.i(TAG, "Successfully downloaded track $trackId ($totalBytesRead bytes, sha256=${sha256.take(8)}...)")
                if (!hasActiveJobs()) {
                    serviceController?.onQueueIdle()
                }
                notifyCompleted(trackId, record)
            }
        } catch (e: Exception) {
            wakeLock?.release()
            activeStreams.remove(trackId)
            runCatching { stream?.close() }
            if (jobs[trackId] === job) {
                partFile?.let { if (it.exists()) it.delete() }
                finalFile?.let { if (it.exists() && !isOfflineReady(trackId)) it.delete() }
                if (job.isCancelled) {
                    job.state = DownloadState.CANCELLED
                    if (!hasActiveJobs()) {
                        serviceController?.onQueueIdle()
                    }
                    notifyCancelled(trackId)
                } else {
                    val isNoSpace = e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                        e.message?.contains("No space left", ignoreCase = true) == true
                    val errorMsg = if (isNoSpace) "Không đủ dung lượng bộ nhớ" else (e.message ?: "Download failed")
                    Log.e(TAG, "Download failed for track $trackId: $errorMsg", e)
                    job.state = DownloadState.FAILED
                    job.errorMessage = errorMsg
                    if (!hasActiveJobs()) {
                        serviceController?.onQueueIdle()
                    }
                    notifyFailed(trackId, errorMsg)
                }
            } else {
                partFile?.let { if (it.exists()) it.delete() }
                finalFile?.let { if (it.exists() && !isOfflineReady(trackId)) it.delete() }
                Log.i(TAG, "Attempt ${job.attemptId} for track $trackId superseded; suppressed late failure publication")
            }
        } finally {
            activeStreams.remove(trackId)
            runCatching { stream?.close() }
            wakeLock?.release()
            if (!hasActiveJobs()) {
                serviceController?.onQueueIdle()
            }
            persistQueueState()
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

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (ch in str) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescapeJson(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                when (val next = str[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    'b' -> sb.append('\b')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '/' -> sb.append('/')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    @Synchronized
    fun persistQueueState() {
        try {
            val storageDir = store.getStorageDirectory()
            val stateFile = File(storageDir, "download_queue_state.json")
            val tmpFile = File(storageDir, "download_queue_state.json.tmp")

            val activeJobs = jobs.values.filter {
                it.state == DownloadState.QUEUED ||
                it.state == DownloadState.DOWNLOADING ||
                it.state == DownloadState.FINALIZING
            }

            if (activeJobs.isEmpty()) {
                if (stateFile.exists()) stateFile.delete()
                if (tmpFile.exists()) tmpFile.delete()
                return
            }

            val sb = StringBuilder()
            sb.append("{\n")
            val b = activeBatch
            if (b != null) {
                sb.append("  \"batch\": {\n")
                sb.append("    \"batch_id\": \"${escapeJson(b.batchId)}\",\n")
                sb.append("    \"source_type\": \"${escapeJson(b.sourceType)}\",\n")
                sb.append("    \"source_id\": \"${escapeJson(b.sourceId)}\",\n")
                sb.append("    \"source_display_name\": \"${escapeJson(b.sourceDisplayName)}\",\n")
                sb.append("    \"created_at\": ${b.createdAt},\n")
                sb.append("    \"track_ids\": [${b.trackIds.joinToString(",")}],\n")
                sb.append("    \"initial_eligible_count\": ${b.initialEligibleCount},\n")
                sb.append("    \"known_total_bytes\": ${b.knownTotalBytes},\n")
                sb.append("    \"has_unknown_sizes\": ${b.hasUnknownSizes}\n")
                sb.append("  },\n")
            }
            sb.append("  \"jobs\": [\n")
            val jobStrings = activeJobs.map { j ->
                val t = j.track
                val fields = mutableListOf<String>()
                fields.add("      \"track_id\": ${t.id}")
                fields.add("      \"title\": \"${escapeJson(t.title)}\"")
                fields.add("      \"artist\": \"${escapeJson(t.artist)}\"")
                fields.add("      \"album\": \"${escapeJson(t.album)}\"")
                fields.add("      \"duration\": ${t.duration ?: 0.0}")
                t.sourceSizeBytes?.let { fields.add("      \"source_size_bytes\": $it") }
                t.sourceFormat?.let { fields.add("      \"source_format\": \"${escapeJson(it)}\"") }
                t.sourceCodec?.let { fields.add("      \"source_codec\": \"${escapeJson(it)}\"") }
                t.sourceBitrate?.let { fields.add("      \"source_bitrate\": $it") }
                t.sourceSampleRate?.let { fields.add("      \"source_sample_rate\": $it") }
                t.sourceChannels?.let { fields.add("      \"source_channels\": $it") }
                t.loudnessLufs?.let { fields.add("      \"loudness_lufs\": $it") }
                t.truePeakDbtp?.let { fields.add("      \"true_peak_dbtp\": $it") }
                t.loudnessRangeLu?.let { fields.add("      \"loudness_range_lu\": $it") }
                t.loudnessAnalysisVersion?.let { fields.add("      \"loudness_analysis_version\": $it") }
                j.batchId?.let { fields.add("      \"batch_id\": \"${escapeJson(it)}\"") }
                fields.add("      \"enqueued_at_ms\": ${j.enqueuedAtMs}")
                "    {\n" + fields.joinToString(",\n") + "\n    }"
            }
            sb.append(jobStrings.joinToString(",\n"))
            sb.append("\n  ]\n}")

            tmpFile.writeText(sb.toString(), Charsets.UTF_8)
            if (stateFile.exists()) {
                stateFile.delete()
            }
            if (!tmpFile.renameTo(stateFile)) {
                tmpFile.copyTo(stateFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist queue state", e)
        }
    }

    @Synchronized
    fun restoreQueueState(apiClient: CarApiClient = CarApiClient()): Int {
        val storageDir = store.getStorageDirectory()
        val stateFile = File(storageDir, "download_queue_state.json")
        if (!stateFile.exists() || stateFile.length() == 0L) {
            return 0
        }

        var restoredCount = 0
        try {
            val content = stateFile.readText(Charsets.UTF_8).trim()
            if (!content.startsWith("{") || !content.endsWith("}")) {
                stateFile.delete()
                return 0
            }

            val propRegex = Regex(""""([^"]+)"\s*:\s*(?:"((?:\\.|[^"\\])*)"|(null)|(true|false)|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))""")

            // Parse batch if present
            val batchStart = content.indexOf("\"batch\"")
            if (batchStart != -1) {
                val batchObjStart = content.indexOf('{', batchStart)
                val batchObjEnd = content.indexOf('}', batchObjStart)
                if (batchObjStart != -1 && batchObjEnd != -1 && batchObjEnd > batchObjStart) {
                    val batchBlock = content.substring(batchObjStart + 1, batchObjEnd)
                    val map = mutableMapOf<String, String?>()
                    for (prop in propRegex.findAll(batchBlock)) {
                        val key = prop.groupValues[1]
                        val strVal = prop.groups[2]?.value
                        val nullVal = prop.groups[3]?.value
                        val boolVal = prop.groups[4]?.value
                        val numVal = prop.groups[5]?.value
                        when {
                            strVal != null -> map[key] = unescapeJson(strVal)
                            nullVal != null -> map[key] = null
                            boolVal != null -> map[key] = boolVal
                            numVal != null -> map[key] = numVal
                        }
                    }

                    // Parse track_ids array
                    val trackIdsStart = content.indexOf("\"track_ids\"", batchObjStart)
                    val trackIdsList = mutableListOf<Long>()
                    if (trackIdsStart in batchObjStart..batchObjEnd) {
                        val arrStart = content.indexOf('[', trackIdsStart)
                        val arrEnd = content.indexOf(']', arrStart)
                        if (arrStart != -1 && arrEnd != -1 && arrEnd > arrStart) {
                            val nums = content.substring(arrStart + 1, arrEnd).split(',')
                            for (n in nums) {
                                n.trim().toLongOrNull()?.let { trackIdsList.add(it) }
                            }
                        }
                    }

                    val bId = map["batch_id"] ?: "download_batch:${java.util.UUID.randomUUID()}"
                    activeBatch = DownloadBatch(
                        batchId = bId,
                        sourceType = map["source_type"] ?: "PLAYLIST",
                        sourceId = map["source_id"] ?: "",
                        sourceDisplayName = map["source_display_name"] ?: "",
                        createdAt = map["created_at"]?.toLongOrNull() ?: System.currentTimeMillis(),
                        trackIds = trackIdsList,
                        initialEligibleCount = map["initial_eligible_count"]?.toIntOrNull() ?: trackIdsList.size,
                        knownTotalBytes = map["known_total_bytes"]?.toLongOrNull() ?: 0L,
                        hasUnknownSizes = map["has_unknown_sizes"]?.toBooleanStrictOrNull() ?: false,
                    )
                }
            }

            // Parse jobs
            val jobsStart = content.indexOf("\"jobs\"")
            if (jobsStart != -1) {
                val arrayStart = content.indexOf('[', jobsStart)
                val arrayEnd = content.lastIndexOf(']')
                if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                    val jobsBlock = content.substring(arrayStart + 1, arrayEnd)
                    val objRegex = Regex("""\{([^{}]+)\}""")
                    for (match in objRegex.findAll(jobsBlock)) {
                        val block = match.groupValues[1]
                        val map = mutableMapOf<String, String?>()
                        for (prop in propRegex.findAll(block)) {
                            val key = prop.groupValues[1]
                            val strVal = prop.groups[2]?.value
                            val nullVal = prop.groups[3]?.value
                            val boolVal = prop.groups[4]?.value
                            val numVal = prop.groups[5]?.value
                            when {
                                strVal != null -> map[key] = unescapeJson(strVal)
                                nullVal != null -> map[key] = null
                                boolVal != null -> map[key] = boolVal
                                numVal != null -> map[key] = numVal
                            }
                        }
                        val trackId = map["track_id"]?.toLongOrNull() ?: continue
                        if (trackId <= 0L) continue

                        // Skip if already offline ready
                        if (isOfflineReady(trackId)) continue

                        val track = CarTrack(
                            id = trackId.toInt(),
                            title = map["title"] ?: "",
                            artist = map["artist"] ?: "",
                            album = map["album"] ?: "",
                            status = "ready",
                            duration = map["duration"]?.toDoubleOrNull() ?: 0.0,
                            sourceSizeBytes = map["source_size_bytes"]?.toLongOrNull(),
                            sourceFormat = map["source_format"],
                            sourceCodec = map["source_codec"],
                            sourceBitrate = map["source_bitrate"]?.toIntOrNull(),
                            sourceSampleRate = map["source_sample_rate"]?.toIntOrNull(),
                            sourceChannels = map["source_channels"]?.toIntOrNull(),
                            loudnessLufs = map["loudness_lufs"]?.toDoubleOrNull(),
                            truePeakDbtp = map["true_peak_dbtp"]?.toDoubleOrNull(),
                            loudnessRangeLu = map["loudness_range_lu"]?.toDoubleOrNull(),
                            loudnessAnalysisVersion = map["loudness_analysis_version"]?.toIntOrNull(),
                        )

                        val bId = map["batch_id"]
                        val newJob = DownloadJob(
                            track = track,
                            state = DownloadState.QUEUED,
                            totalBytes = track.sourceSizeBytes ?: 0L,
                            attemptId = attemptCounter.incrementAndGet(),
                            batchId = bId,
                        )
                        jobs[trackId] = newJob
                        restoredCount++
                        notifyStarted(trackId)
                        executor.submit { runDownloadJob(newJob, apiClient) }
                    }
                }
            }

            if (restoredCount > 0) {
                serviceController?.onQueueActive()
            }
            persistQueueState()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore queue state", e)
            try { stateFile.delete() } catch (ignored: Exception) {}
        }
        return restoredCount
    }
}
