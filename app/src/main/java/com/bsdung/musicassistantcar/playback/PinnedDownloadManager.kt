package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

enum class DownloadNetworkPolicy {
    ANY_NETWORK,
    WIFI_ONLY;

    companion object {
        fun fromString(value: String?): DownloadNetworkPolicy {
            return when (value?.trim()?.uppercase()) {
                "WIFI_ONLY" -> WIFI_ONLY
                "ANY_NETWORK" -> ANY_NETWORK
                else -> ANY_NETWORK
            }
        }
    }
}

data class NetworkTransportInfo(
    val hasWifiTransport: Boolean = false,
    val hasCellularTransport: Boolean = false,
    val hasVpnTransport: Boolean = false,
    val hasInternetCapability: Boolean = false,
    val isValidated: Boolean = false,
) {
    val isValidatedInternet: Boolean
        get() = hasInternetCapability && isValidated

    val isValidatedWifi: Boolean
        get() = hasWifiTransport && hasInternetCapability && isValidated
}

interface NetworkPolicyResolver {
    fun getActiveTransports(): List<NetworkTransportInfo>

    fun hasValidatedInternet(): Boolean {
        return getActiveTransports().any { it.isValidatedInternet }
    }

    fun hasValidatedWifi(): Boolean {
        return getActiveTransports().any { it.isValidatedWifi }
    }

    fun isDownloadAllowed(policy: DownloadNetworkPolicy): Boolean {
        return when (policy) {
            DownloadNetworkPolicy.ANY_NETWORK -> hasValidatedInternet()
            DownloadNetworkPolicy.WIFI_ONLY -> hasValidatedWifi()
        }
    }
}

object DefaultPermissiveNetworkPolicyResolver : NetworkPolicyResolver {
    override fun getActiveTransports(): List<NetworkTransportInfo> = listOf(
        NetworkTransportInfo(
            hasWifiTransport = true,
            hasInternetCapability = true,
            isValidated = true,
        )
    )
}

class AndroidNetworkPolicyResolver(private val context: Context) : NetworkPolicyResolver {
    override fun getActiveTransports(): List<NetworkTransportInfo> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return emptyList()
            val networks = LinkedHashSet<Network>()
            cm.activeNetwork?.let { networks.add(it) }
            cm.allNetworks?.forEach { if (it != null) networks.add(it) }
            networks.mapNotNull { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
                NetworkTransportInfo(
                    hasWifiTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    hasCellularTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    hasVpnTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                    hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                )
            }
        } catch (e: Exception) {
            Log.e("NetworkPolicyResolver", "Failed to query network transports", e)
            emptyList()
        }
    }
}

enum class DownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    WAITING_FOR_WIFI,
    DOWNLOADING,
    FINALIZING,
    OFFLINE_READY,
    FAILED,
    CANCELLED,
    STORAGE_TARGET_UNAVAILABLE,
    OFFLINE_MEDIA_UNAVAILABLE,
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
    @Volatile var pausedForWifi: Boolean = false,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
    @Volatile var attemptId: Long = 0L,
    val batchId: String? = null,
    val queueSequence: Long = 0L,
    @Volatile var targetStorage: OfflineStorageTarget = OfflineStorageTarget.INTERNAL,
    @Volatile var targetDir: File? = null,
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

data class OfflineStorageSummary(
    val trackCount: Int,
    val usedBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val capBytes: Long,
    val selectedTarget: OfflineStorageTarget = OfflineStorageTarget.INTERNAL,
    val internalTrackCount: Int = 0,
    val internalUsedBytes: Long = 0L,
    val internalAvailableBytes: Long = 0L,
    val internalTotalBytes: Long = 0L,
    val sdAvailable: Boolean = false,
    val sdTrackCount: Int = 0,
    val sdUsedBytes: Long = 0L,
    val sdAvailableBytes: Long = 0L,
    val sdTotalBytes: Long = 0L,
)

enum class StoragePrecheckStatus {
    ALLOWED,
    BLOCKED_CAP_EXCEEDED,
    BLOCKED_LOW_FREE_SPACE,
    STORAGE_TARGET_UNAVAILABLE,
}

data class StoragePrecheckResult(
    val status: StoragePrecheckStatus,
    val currentUsedBytes: Long,
    val requiredBytes: Long,
    val capBytes: Long,
    val availableBytes: Long,
    val reserveBytes: Long = PinnedDownloadManager.MIN_FREE_RESERVE_BYTES,
    val title: String? = null,
    val message: String? = null,
) {
    val isAllowed: Boolean
        get() = status == StoragePrecheckStatus.ALLOWED
}

data class DeleteAllResult(
    val success: Boolean,
    val deletedTrackCount: Int,
    val freedBytes: Long,
    val blockedByActiveDownloads: Boolean = false,
    val errorMessage: String? = null,
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
    @Volatile private var networkPolicyResolver: NetworkPolicyResolver = DefaultPermissiveNetworkPolicyResolver,
    initialNetworkPolicy: DownloadNetworkPolicy = DownloadNetworkPolicy.ANY_NETWORK,
    initialStorageTarget: OfflineStorageTarget = OfflineStorageTarget.INTERNAL,
    @Volatile private var volumeProvider: OfflineStorageVolumeProvider = store.getVolumeProvider(),
) {
    private var context: Context? = null
    private val attemptCounter = java.util.concurrent.atomic.AtomicLong(1L)
    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong(1L)
    @Volatile
    private var inMemoryStorageCapBytes: Long = CAP_UNLIMITED
    @Volatile
    private var inMemorySortKey: String = SORT_DATE_DESC
    @Volatile
    private var inMemoryNetworkPolicy: DownloadNetworkPolicy = initialNetworkPolicy
    @Volatile
    private var inMemoryStorageTarget: OfflineStorageTarget = initialStorageTarget
    @Volatile
    private var availableSpaceProvider: (() -> Long)? = null
    @Volatile
    private var targetAvailableSpaceProvider: ((OfflineStorageTarget) -> Long?)? = null
    @Volatile
    private var lastApiClient: CarApiClient = CarApiClient()

    constructor(
        context: Context,
        store: OfflineMetadataStore = OfflineMetadataStore.getDefault(context),
        executor: ExecutorService = Executors.newSingleThreadExecutor(),
        wakeLock: DownloadWakeLock? = AndroidDownloadWakeLock(context.applicationContext),
        serviceController: DownloadServiceController? = AndroidDownloadServiceController(context.applicationContext),
    ) : this(
        store = store,
        executor = executor,
        streamOpener = null,
        wakeLock = wakeLock,
        serviceController = serviceController,
        networkPolicyResolver = AndroidNetworkPolicyResolver(context.applicationContext),
        initialNetworkPolicy = DownloadNetworkPolicy.fromString(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(PREF_DOWNLOAD_NETWORK_POLICY, DownloadNetworkPolicy.ANY_NETWORK.name)
        ),
        initialStorageTarget = OfflineStorageTarget.fromString(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(PREF_OFFLINE_STORAGE_TARGET, OfflineStorageTarget.INTERNAL.name)
        ),
    ) {
        this.context = context.applicationContext
        if (store.getVolumeProvider() is SingleDirOfflineStorageVolumeProvider) {
            store.setVolumeProvider(AndroidOfflineStorageVolumeProvider(context.applicationContext))
        }
        val prefs = this.context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            inMemoryStorageCapBytes = prefs.getLong(PREF_OFFLINE_STORAGE_CAP_BYTES, CAP_UNLIMITED)
            inMemorySortKey = prefs.getString(PREF_DOWNLOADED_SORT, SORT_DATE_DESC) ?: SORT_DATE_DESC
            inMemoryNetworkPolicy = DownloadNetworkPolicy.fromString(
                prefs.getString(PREF_DOWNLOAD_NETWORK_POLICY, inMemoryNetworkPolicy.name)
            )
            inMemoryStorageTarget = OfflineStorageTarget.fromString(
                prefs.getString(PREF_OFFLINE_STORAGE_TARGET, inMemoryStorageTarget.name)
            )
        }
        registerNetworkCallbackIfNeeded(context.applicationContext)
    }

    companion object {
        private const val TAG = "PinnedDownloadManager"
        const val DOWNLOADED_PREFIX = "downloaded:"
        const val PREFS_NAME = "music_assistant_car"
        const val PREF_OFFLINE_STORAGE_CAP_BYTES = "offline_storage_cap_bytes"
        const val PREF_DOWNLOADED_SORT = "downloaded_music_sort"
        const val PREF_DOWNLOAD_NETWORK_POLICY = "download_network_policy"
        const val PREF_OFFLINE_STORAGE_TARGET = "offline_storage_target"

        const val MIN_FREE_RESERVE_BYTES = 500L * 1024L * 1024L
        const val CAP_UNLIMITED = 0L
        const val CAP_5_GB = 5L * 1024L * 1024L * 1024L
        const val CAP_10_GB = 10L * 1024L * 1024L * 1024L
        const val CAP_20_GB = 20L * 1024L * 1024L * 1024L
        const val CAP_50_GB = 50L * 1024L * 1024L * 1024L

        const val SORT_NAME_ASC = "name_asc"
        const val SORT_DATE_DESC = "date_desc"
        const val SORT_DATE_ASC = "date_asc"
        const val SORT_SIZE_DESC = "size_desc"
        const val SORT_SIZE_ASC = "size_asc"

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
        fun onDownloadWaitingForWifi(trackId: Long) {}
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
    private val activeOutputs = ConcurrentHashMap<Long, FileOutputStream>()
    @Volatile
    private var activeBatch: DownloadBatch? = null

    init {
        if (context == null) {
            if (initialNetworkPolicy == DownloadNetworkPolicy.ANY_NETWORK) {
                try {
                    val prefFile = File(store.getStorageDirectory(), "network_policy.pref")
                    if (prefFile.exists() && prefFile.isFile) {
                        inMemoryNetworkPolicy = DownloadNetworkPolicy.fromString(prefFile.readText(Charsets.UTF_8).trim())
                    }
                } catch (ignored: Exception) {}
            }
            if (initialStorageTarget == OfflineStorageTarget.INTERNAL) {
                try {
                    val targetPrefFile = File(store.getStorageDirectory(), "offline_storage_target.pref")
                    if (targetPrefFile.exists() && targetPrefFile.isFile) {
                        inMemoryStorageTarget = OfflineStorageTarget.fromString(targetPrefFile.readText(Charsets.UTF_8).trim())
                    }
                } catch (ignored: Exception) {}
            }
        }
        cleanOrphanParts()
        restoreQueueState()
    }

    private fun registerNetworkCallbackIfNeeded(appContext: Context) {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkStateChanged()
                }

                override fun onLost(network: Network) {
                    onNetworkStateChanged()
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    onNetworkStateChanged()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    fun setNetworkPolicyResolverForTesting(resolver: NetworkPolicyResolver) {
        this.networkPolicyResolver = resolver
    }

    fun getNetworkPolicyResolver(): NetworkPolicyResolver = networkPolicyResolver

    fun getDownloadNetworkPolicy(): DownloadNetworkPolicy {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            val stored = prefs.getString(PREF_DOWNLOAD_NETWORK_POLICY, null)
            if (stored != null) {
                inMemoryNetworkPolicy = DownloadNetworkPolicy.fromString(stored)
            }
        }
        return inMemoryNetworkPolicy
    }

    fun setDownloadNetworkPolicy(
        policy: DownloadNetworkPolicy,
        apiClient: CarApiClient = lastApiClient,
    ) {
        inMemoryNetworkPolicy = policy
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PREF_DOWNLOAD_NETWORK_POLICY, policy.name)
            ?.apply()
        try {
            val storageDir = store.getStorageDirectory()
            if (!storageDir.exists()) storageDir.mkdirs()
            File(storageDir, "network_policy.pref").writeText(policy.name, Charsets.UTF_8)
        } catch (ignored: Exception) {}
        onNetworkStateChanged(apiClient)
    }

    fun formatNetworkPolicyLabel(policy: DownloadNetworkPolicy = getDownloadNetworkPolicy()): String {
        return when (policy) {
            DownloadNetworkPolicy.ANY_NETWORK -> "Mọi mạng"
            DownloadNetworkPolicy.WIFI_ONLY -> "Chỉ Wi-Fi"
        }
    }

    fun getOfflineStorageTarget(): OfflineStorageTarget {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            val stored = prefs.getString(PREF_OFFLINE_STORAGE_TARGET, null)
            if (stored != null) {
                inMemoryStorageTarget = OfflineStorageTarget.fromString(stored)
            }
        }
        return inMemoryStorageTarget
    }

    fun setOfflineStorageTarget(target: OfflineStorageTarget) {
        inMemoryStorageTarget = target
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PREF_OFFLINE_STORAGE_TARGET, target.name)
            ?.apply()
        try {
            val storageDir = store.getStorageDirectory()
            if (!storageDir.exists()) storageDir.mkdirs()
            File(storageDir, "offline_storage_target.pref").writeText(target.name, Charsets.UTF_8)
        } catch (ignored: Exception) {}
    }

    fun formatStorageTargetLabel(target: OfflineStorageTarget = getOfflineStorageTarget()): String {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> "Bộ nhớ trong"
            OfflineStorageTarget.REMOVABLE_SD -> "Thẻ nhớ SD"
        }
    }

    fun getVolumeProvider(): OfflineStorageVolumeProvider = volumeProvider

    fun setVolumeProviderForTesting(provider: OfflineStorageVolumeProvider) {
        this.volumeProvider = provider
        this.store.setVolumeProvider(provider)
    }

    fun discoverStorageVolumes(): OfflineStorageDiscoveryResult {
        return volumeProvider.discoverVolumes()
    }

    fun isRemovableSdAvailable(): Boolean {
        return volumeProvider.isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)
    }

    fun isStorageTargetAvailable(target: OfflineStorageTarget = getOfflineStorageTarget()): Boolean {
        return volumeProvider.isTargetAvailable(target)
    }

    fun getStorageDirectoryForTarget(target: OfflineStorageTarget = getOfflineStorageTarget()): File? {
        return volumeProvider.getStorageDirectory(target)
    }

    fun isNetworkAllowedForDownload(): Boolean {
        return when (getDownloadNetworkPolicy()) {
            DownloadNetworkPolicy.ANY_NETWORK -> true
            DownloadNetworkPolicy.WIFI_ONLY -> networkPolicyResolver.isDownloadAllowed(DownloadNetworkPolicy.WIFI_ONLY)
        }
    }

    fun isQueueWaitingForWifi(): Boolean {
        return jobs.values.any { it.state == DownloadState.WAITING_FOR_WIFI } &&
            jobs.values.none { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.FINALIZING }
    }

    private fun abortActiveDownloadForWifiPolicy() {
        val abortedTrackIds = mutableListOf<Long>()
        for ((trackId, job) in jobs) {
            synchronized(job) {
                if (job.state == DownloadState.DOWNLOADING && !job.isCancelled) {
                    job.pausedForWifi = true
                    job.state = DownloadState.WAITING_FOR_WIFI
                    job.bytesDownloaded = 0L
                    job.errorMessage = null
                    activeStreams.remove(trackId)?.let { runCatching { it.close() } }
                    activeOutputs.remove(trackId)?.let { runCatching { it.close() } }
                    deletePartFileOnAllKnownTargets(trackId)
                    wakeLock?.release()
                    abortedTrackIds.add(trackId)
                }
            }
        }
        for (trackId in abortedTrackIds) {
            notifyWaitingForWifi(trackId)
        }
    }

    private fun deletePartFileForTarget(trackId: Long, target: OfflineStorageTarget) {
        try {
            val dir = volumeProvider.getStorageDirectory(target) ?: return
            val partFile = File(dir, "track_${trackId}.part")
            if (partFile.exists()) {
                partFile.delete()
            }
        } catch (ignored: Exception) {}
    }

    private fun deletePartFileOnAllKnownTargets(trackId: Long) {
        deletePartFileForTarget(trackId, OfflineStorageTarget.INTERNAL)
        if (volumeProvider.isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)) {
            deletePartFileForTarget(trackId, OfflineStorageTarget.REMOVABLE_SD)
        }
    }

    private fun reconcileWaitingQueueStates() {
        val allowed = isNetworkAllowedForDownload()
        val pendingJobs = jobs.values
            .filter { !it.isCancelled && (it.state == DownloadState.WAITING_FOR_WIFI || it.state == DownloadState.QUEUED) }
            .sortedBy { it.queueSequence }

        if (!allowed) {
            val hasFinalizing = jobs.values.any { it.state == DownloadState.FINALIZING }
            val becameWaitingIds = mutableListOf<Long>()
            for (job in pendingJobs) {
                synchronized(job) {
                    if (!job.isCancelled && job.state == DownloadState.QUEUED) {
                        job.state = DownloadState.WAITING_FOR_WIFI
                        job.bytesDownloaded = 0L
                        becameWaitingIds.add(job.track.id.toLong())
                    }
                }
            }
            if (!hasFinalizing && jobs.values.none { it.state == DownloadState.DOWNLOADING }) {
                wakeLock?.release()
            }
            for (trackId in becameWaitingIds) {
                notifyWaitingForWifi(trackId)
            }
        } else {
            for (job in pendingJobs) {
                synchronized(job) {
                    if (!job.isCancelled && job.state == DownloadState.WAITING_FOR_WIFI) {
                        job.state = DownloadState.QUEUED
                    }
                }
            }
        }
    }

    fun onNetworkStateChanged(apiClient: CarApiClient = lastApiClient) {
        lastApiClient = apiClient
        if (!isNetworkAllowedForDownload()) {
            abortActiveDownloadForWifiPolicy()
            reconcileWaitingQueueStates()
            persistQueueState()
        } else {
            val hadWaitingOrQueued = jobs.values.any {
                !it.isCancelled && (it.state == DownloadState.WAITING_FOR_WIFI || it.state == DownloadState.QUEUED)
            }
            if (hadWaitingOrQueued) {
                reconcileWaitingQueueStates()
                serviceController?.onQueueActive()
                persistQueueState()
                scheduleQueueProcessing(apiClient)
            }
        }
    }

    private fun scheduleQueueProcessing(apiClient: CarApiClient) {
        lastApiClient = apiClient
        executor.submit { processQueueLoop(apiClient) }
    }

    private fun processQueueLoop(apiClient: CarApiClient) {
        while (true) {
            if (!isNetworkAllowedForDownload()) {
                abortActiveDownloadForWifiPolicy()
                reconcileWaitingQueueStates()
                persistQueueState()
                return
            }

            val nextJob = jobs.values
                .filter { !it.isCancelled && (it.state == DownloadState.WAITING_FOR_WIFI || it.state == DownloadState.QUEUED) }
                .minByOrNull { it.queueSequence }
                ?: break

            synchronized(nextJob) {
                if (nextJob.isCancelled || jobs[nextJob.track.id.toLong()] !== nextJob) {
                    continue
                }
                nextJob.pausedForWifi = false
                nextJob.attemptId = attemptCounter.incrementAndGet()
            }

            runDownloadJob(nextJob, apiClient)
        }
    }

    fun cleanOrphanParts(): Int {
        var count = 0
        val dirs = mutableListOf<File>()
        volumeProvider.getStorageDirectory(OfflineStorageTarget.INTERNAL)?.let { dirs.add(it) }
        if (volumeProvider.isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)) {
            volumeProvider.getStorageDirectory(OfflineStorageTarget.REMOVABLE_SD)?.let { dirs.add(it) }
        }
        for (dir in dirs.distinctBy { it.absolutePath }) {
            val partFiles = dir.listFiles { _, name -> name.endsWith(".part") } ?: emptyArray()
            for (file in partFiles) {
                if (file.delete()) {
                    count++
                }
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
            job.state == DownloadState.WAITING_FOR_WIFI ||
            job.state == DownloadState.DOWNLOADING ||
            job.state == DownloadState.FINALIZING
    }

    fun hasActiveJobs(): Boolean {
        return jobs.values.any {
            it.state == DownloadState.QUEUED ||
            it.state == DownloadState.WAITING_FOR_WIFI ||
            it.state == DownloadState.DOWNLOADING ||
            it.state == DownloadState.FINALIZING
        }
    }

    fun getCurrentActiveJob(): DownloadJob? {
        return jobs.values.firstOrNull {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.FINALIZING
        } ?: jobs.values.filter {
            it.state == DownloadState.WAITING_FOR_WIFI || it.state == DownloadState.QUEUED
        }.minByOrNull { it.queueSequence }
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
        if (!volumeProvider.isTargetAvailable(record.storageTarget)) {
            return false
        }
        val targetDir = volumeProvider.getStorageDirectory(record.storageTarget) ?: return false
        val partFile = File(targetDir, "track_${trackId}.part")
        if (partFile.exists()) {
            return false
        }
        val finalFile = store.resolveSafeTrackFile(record) ?: return false
        if (!finalFile.exists() || !finalFile.isFile) {
            return false
        }
        val currentLength = finalFile.length()
        if (currentLength <= 0L || currentLength != record.fileSizeBytes) {
            return false
        }
        return true
    }

    fun isOfflineMediaUnavailable(trackId: Long): Boolean {
        val job = jobs[trackId]
        if (job != null && job.state != DownloadState.OFFLINE_READY) {
            return false
        }
        val record = store.getRecord(trackId) ?: return false
        if (record.sha256.isBlank() || record.fileSizeBytes <= 0L) {
            return false
        }
        if (!volumeProvider.isTargetAvailable(record.storageTarget)) {
            return true
        }
        return false
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
        if (isOfflineMediaUnavailable(trackId)) {
            val record = store.getRecord(trackId)
            val size = record?.fileSizeBytes ?: 0L
            return DownloadStatus(
                trackId = trackId,
                state = DownloadState.OFFLINE_MEDIA_UNAVAILABLE,
                bytesDownloaded = size,
                totalBytes = size,
                errorMessage = "Thẻ nhớ SD không khả dụng",
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
        val recordTarget = store.getRecord(trackId)?.storageTarget
        cancelDownload(trackId)
        jobs.remove(trackId)
        if (recordTarget != null) {
            deletePartFileForTarget(trackId, recordTarget)
        } else {
            deletePartFileForTarget(trackId, getOfflineStorageTarget())
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
        var shouldNotifyCancelled = false
        synchronized(job) {
            if (job.state == DownloadState.OFFLINE_READY) return false
            job.isCancelled = true
            job.pausedForWifi = false
            val wasActive = (job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING)
            job.state = DownloadState.CANCELLED
            activeStreams.remove(trackId)?.let { runCatching { it.close() } }
            activeOutputs.remove(trackId)?.let { runCatching { it.close() } }
            deletePartFileOnAllKnownTargets(trackId)
            if (!wasActive) {
                shouldNotifyCancelled = true
            }
        }
        reconcileWaitingQueueStates()
        if (shouldNotifyCancelled) {
            notifyCancelled(trackId)
            if (!hasActiveJobs()) {
                serviceController?.onQueueIdle()
            }
        }
        persistQueueState()
        return true
    }

    fun retryDownload(
        trackId: Long,
        apiClient: CarApiClient = CarApiClient(),
    ): Boolean {
        lastApiClient = apiClient
        val existingJob = jobs[trackId] ?: return false
        if (existingJob.state != DownloadState.FAILED &&
            existingJob.state != DownloadState.CANCELLED &&
            existingJob.state != DownloadState.STORAGE_TARGET_UNAVAILABLE
        ) {
            return false
        }
        val selectedTarget = getOfflineStorageTarget()
        if (!isStorageTargetAvailable(selectedTarget)) {
            val unavailableMsg = "Thẻ nhớ SD không khả dụng"
            val unavailableJob = DownloadJob(
                track = existingJob.track,
                state = DownloadState.STORAGE_TARGET_UNAVAILABLE,
                totalBytes = existingJob.track.sourceSizeBytes ?: 0L,
                errorMessage = unavailableMsg,
                attemptId = attemptCounter.incrementAndGet(),
                batchId = existingJob.batchId,
                queueSequence = sequenceCounter.incrementAndGet(),
            )
            jobs[trackId] = unavailableJob
            notifyFailed(trackId, unavailableMsg)
            if (!hasActiveJobs()) {
                serviceController?.onQueueIdle()
            }
            persistQueueState()
            return false
        }

        deletePartFileForTarget(trackId, selectedTarget)
        val newJob = DownloadJob(
            track = existingJob.track,
            state = DownloadState.QUEUED,
            totalBytes = existingJob.track.sourceSizeBytes ?: 0L,
            attemptId = attemptCounter.incrementAndGet(),
            batchId = existingJob.batchId,
            queueSequence = sequenceCounter.incrementAndGet(),
        )
        jobs[trackId] = newJob
        reconcileWaitingQueueStates()
        notifyStarted(trackId)
        serviceController?.onQueueActive()
        persistQueueState()
        if (isNetworkAllowedForDownload()) {
            scheduleQueueProcessing(apiClient)
        }
        return true
    }

    fun startDownload(
        track: CarTrack,
        apiClient: CarApiClient = CarApiClient(),
    ) {
        lastApiClient = apiClient
        val trackId = track.id.toLong()
        if (isOfflineReady(trackId) || isOfflineMediaUnavailable(trackId)) {
            Log.i(TAG, "Track $trackId already offline or on unmounted SD, skipping")
            return
        }

        val existingJob = jobs[trackId]
        if (existingJob != null) {
            if (existingJob.state == DownloadState.QUEUED ||
                existingJob.state == DownloadState.WAITING_FOR_WIFI ||
                existingJob.state == DownloadState.DOWNLOADING ||
                existingJob.state == DownloadState.FINALIZING
            ) {
                Log.i(TAG, "Track $trackId is already downloading or queued")
                return
            }
        }

        val selectedTarget = getOfflineStorageTarget()
        val total = track.sourceSizeBytes ?: 0L
        if (!isStorageTargetAvailable(selectedTarget)) {
            val unavailableMsg = "Thẻ nhớ SD không khả dụng"
            val unavailableJob = DownloadJob(
                track = track,
                state = DownloadState.STORAGE_TARGET_UNAVAILABLE,
                totalBytes = total,
                errorMessage = unavailableMsg,
                attemptId = attemptCounter.incrementAndGet(),
                queueSequence = sequenceCounter.incrementAndGet(),
            )
            jobs[trackId] = unavailableJob
            notifyFailed(trackId, unavailableMsg)
            if (!hasActiveJobs()) {
                serviceController?.onQueueIdle()
            }
            persistQueueState()
            return
        }

        deletePartFileForTarget(trackId, selectedTarget)

        val job = DownloadJob(
            track = track,
            state = DownloadState.QUEUED,
            totalBytes = total,
            attemptId = attemptCounter.incrementAndGet(),
            queueSequence = sequenceCounter.incrementAndGet(),
        )
        jobs[trackId] = job
        reconcileWaitingQueueStates()
        notifyStarted(trackId)
        serviceController?.onQueueActive()
        persistQueueState()

        if (isNetworkAllowedForDownload()) {
            scheduleQueueProcessing(apiClient)
        }
    }

    fun classifyTracksForDownload(tracks: List<CarTrack>): BulkClassificationResult {
        val distinctTracks = tracks.distinctBy { it.id }
        val alreadyOffline = mutableListOf<CarTrack>()
        val inProgress = mutableListOf<CarTrack>()
        val eligibleNew = mutableListOf<CarTrack>()

        for (t in distinctTracks) {
            val trackId = t.id.toLong()
            if (isOfflineReady(trackId) || isOfflineMediaUnavailable(trackId)) {
                alreadyOffline.add(t)
            } else {
                val job = jobs[trackId]
                if (job != null && (job.state == DownloadState.QUEUED ||
                        job.state == DownloadState.WAITING_FOR_WIFI ||
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

    fun setAvailableSpaceProviderForTesting(provider: (() -> Long)?) {
        availableSpaceProvider = provider
    }

    fun setTargetAvailableSpaceProviderForTesting(provider: ((OfflineStorageTarget) -> Long)?) {
        targetAvailableSpaceProvider = provider
    }

    fun getAvailableStorageBytes(target: OfflineStorageTarget = getOfflineStorageTarget()): Long {
        return try {
            targetAvailableSpaceProvider?.invoke(target)
                ?: availableSpaceProvider?.invoke()
                ?: volumeProvider.getAvailableBytes(target)
        } catch (e: Exception) {
            0L
        }
    }

    fun getTotalStorageBytes(target: OfflineStorageTarget = getOfflineStorageTarget()): Long {
        return try {
            volumeProvider.getTotalBytes(target)
        } catch (e: Exception) {
            0L
        }
    }

    fun getOfflineStorageCapBytes(): Long {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            inMemoryStorageCapBytes = prefs.getLong(PREF_OFFLINE_STORAGE_CAP_BYTES, inMemoryStorageCapBytes)
        }
        return inMemoryStorageCapBytes.coerceAtLeast(0L)
    }

    fun setOfflineStorageCapBytes(capBytes: Long) {
        val normalized = capBytes.coerceAtLeast(0L)
        inMemoryStorageCapBytes = normalized
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putLong(PREF_OFFLINE_STORAGE_CAP_BYTES, normalized)
            ?.apply()
    }

    fun getDownloadedSortKey(): String {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs != null) {
            inMemorySortKey = prefs.getString(PREF_DOWNLOADED_SORT, inMemorySortKey) ?: inMemorySortKey
        }
        return inMemorySortKey
    }

    fun setDownloadedSortKey(sortKey: String) {
        inMemorySortKey = sortKey
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PREF_DOWNLOADED_SORT, sortKey)
            ?.apply()
    }

    fun getEffectiveRecordSizeBytes(record: OfflineTrackRecord): Long {
        if (isDownloading(record.trackId)) return 0L
        return store.getEffectiveFileSizeBytes(record)
    }

    fun getEffectiveTrackSizeBytes(trackId: Long): Long {
        if (isDownloading(trackId)) return 0L
        val record = store.getRecord(trackId) ?: return 0L
        return store.getEffectiveFileSizeBytes(record)
    }

    fun getPinnedUsedBytes(excludeTrackId: Long? = null): Long {
        var sum = 0L
        for (record in store.getAllRecords()) {
            if (excludeTrackId != null && record.trackId == excludeTrackId) continue
            if (isDownloading(record.trackId)) continue
            val bytes = store.getEffectiveFileSizeBytes(record)
            if (bytes > 0L) {
                sum += bytes
            }
        }
        return sum
    }

    fun getPinnedUsedBytesForTarget(target: OfflineStorageTarget, excludeTrackId: Long? = null): Long {
        var sum = 0L
        for (record in store.getAllRecords()) {
            if (record.storageTarget != target) continue
            if (excludeTrackId != null && record.trackId == excludeTrackId) continue
            if (isDownloading(record.trackId)) continue
            val bytes = store.getEffectiveFileSizeBytes(record)
            if (bytes > 0L) {
                sum += bytes
            }
        }
        return sum
    }

    fun getStorageSummary(repairStaleMetadata: Boolean = true): OfflineStorageSummary {
        if (repairStaleMetadata) {
            store.reconcileMissingAndUnsafe()
        }
        var count = 0
        var used = 0L
        var internalCount = 0
        var internalUsed = 0L
        var sdCount = 0
        var sdUsed = 0L
        for (record in store.getAllRecords()) {
            if (isDownloading(record.trackId)) continue
            val bytes = store.getEffectiveFileSizeBytes(record)
            if (bytes > 0L) {
                count++
                used += bytes
                if (record.storageTarget == OfflineStorageTarget.REMOVABLE_SD) {
                    sdCount++
                    sdUsed += bytes
                } else {
                    internalCount++
                    internalUsed += bytes
                }
            }
        }
        val selectedTarget = getOfflineStorageTarget()
        val sdAvail = isRemovableSdAvailable()
        val internalFree = getAvailableStorageBytes(OfflineStorageTarget.INTERNAL)
        val internalTotal = getTotalStorageBytes(OfflineStorageTarget.INTERNAL)
        val sdFree = if (sdAvail) getAvailableStorageBytes(OfflineStorageTarget.REMOVABLE_SD) else 0L
        val sdTotal = if (sdAvail) getTotalStorageBytes(OfflineStorageTarget.REMOVABLE_SD) else 0L
        return OfflineStorageSummary(
            trackCount = count,
            usedBytes = used,
            availableBytes = getAvailableStorageBytes(selectedTarget),
            totalBytes = getTotalStorageBytes(selectedTarget),
            capBytes = getOfflineStorageCapBytes(),
            selectedTarget = selectedTarget,
            internalTrackCount = internalCount,
            internalUsedBytes = internalUsed,
            internalAvailableBytes = internalFree,
            internalTotalBytes = internalTotal,
            sdAvailable = sdAvail,
            sdTrackCount = sdCount,
            sdUsedBytes = sdUsed,
            sdAvailableBytes = sdFree,
            sdTotalBytes = sdTotal,
        )
    }

    fun formatStorageBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (safeBytes == 0L) return "0 MB"
        val oneGb = 1024L * 1024L * 1024L
        val oneMb = 1024L * 1024L
        return when {
            safeBytes >= oneGb -> {
                if (safeBytes % oneGb == 0L) {
                    "${safeBytes / oneGb} GB"
                } else {
                    val gb = safeBytes.toDouble() / oneGb.toDouble()
                    val twoDecimals = String.format(java.util.Locale.US, "%.2f", gb)
                    val trimmed = if (twoDecimals.endsWith("0")) twoDecimals.dropLast(1) else twoDecimals
                    "$trimmed GB"
                }
            }
            safeBytes >= 100L * 1024L -> {
                if (safeBytes % oneMb == 0L) {
                    "${safeBytes / oneMb} MB"
                } else {
                    val mb = safeBytes.toDouble() / oneMb.toDouble()
                    String.format(java.util.Locale.US, "%.1f MB", mb)
                }
            }
            else -> {
                val kb = safeBytes.toDouble() / 1024.0
                String.format(java.util.Locale.US, "%.1f KB", kb)
            }
        }
    }

    fun formatCapLabel(capBytes: Long = getOfflineStorageCapBytes()): String {
        return if (capBytes <= 0L) "Không giới hạn" else formatStorageBytes(capBytes)
    }

    fun formatDownloadDate(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US)
            sdf.format(java.util.Date(timestampMs))
        } catch (e: Exception) {
            ""
        }
    }

    fun evaluateStorageForDownload(knownRequiredBytes: Long): StoragePrecheckResult {
        val selectedTarget = getOfflineStorageTarget()
        val currentUsed = getPinnedUsedBytes()
        val cap = getOfflineStorageCapBytes()
        val req = knownRequiredBytes.coerceAtLeast(0L)

        // 0. Check if selected storage target volume is available
        if (!isStorageTargetAvailable(selectedTarget)) {
            val title = "Thẻ nhớ SD không khả dụng"
            val message = "Thẻ nhớ SD không khả dụng. Vui lòng kiểm tra thẻ nhớ hoặc chuyển nơi lưu sang Bộ nhớ trong."
            return StoragePrecheckResult(
                status = StoragePrecheckStatus.STORAGE_TARGET_UNAVAILABLE,
                currentUsedBytes = currentUsed,
                requiredBytes = req,
                capBytes = cap,
                availableBytes = 0L,
                title = title,
                message = message,
            )
        }

        val available = getAvailableStorageBytes(selectedTarget)

        // 1. Offline storage cap check (global across INTERNAL + REMOVABLE_SD)
        if (cap > 0L) {
            val capExceeded = if (req > 0L) {
                currentUsed >= cap || req > (cap - currentUsed)
            } else {
                currentUsed >= cap
            }
            if (capExceeded) {
                val title = "Vượt giới hạn dung lượng offline"
                val reqText = if (req > 0L) formatStorageBytes(req) else "Không rõ dung lượng"
                val message = buildString {
                    append("Vượt giới hạn dung lượng offline\n\n")
                    append("Đang dùng: ${formatStorageBytes(currentUsed)}\n")
                    append("Cần thêm: $reqText\n")
                    append("Giới hạn: ${formatStorageBytes(cap)}")
                }
                return StoragePrecheckResult(
                    status = StoragePrecheckStatus.BLOCKED_CAP_EXCEEDED,
                    currentUsedBytes = currentUsed,
                    requiredBytes = req,
                    capBytes = cap,
                    availableBytes = available,
                    title = title,
                    message = message,
                )
            }
        }

        // 2. Target volume 500 MB free-space reserve check for known-size downloads
        if (req > 0L) {
            if (available < req || (available - req) < MIN_FREE_RESERVE_BYTES) {
                val title = "Không đủ dung lượng bộ nhớ"
                val message = buildString {
                    append("Không đủ dung lượng bộ nhớ\n\n")
                    append("Cần: ${formatStorageBytes(req)}\n")
                    append("Còn trống: ${formatStorageBytes(available)}\n")
                    append("Dự phòng hệ thống: 500 MB")
                }
                return StoragePrecheckResult(
                    status = StoragePrecheckStatus.BLOCKED_LOW_FREE_SPACE,
                    currentUsedBytes = currentUsed,
                    requiredBytes = req,
                    capBytes = cap,
                    availableBytes = available,
                    title = title,
                    message = message,
                )
            }
        }

        return StoragePrecheckResult(
            status = StoragePrecheckStatus.ALLOWED,
            currentUsedBytes = currentUsed,
            requiredBytes = req,
            capBytes = cap,
            availableBytes = available,
        )
    }

    fun checkSingleDownloadStorage(track: CarTrack): StoragePrecheckResult {
        val knownBytes = track.sourceSizeBytes?.takeIf { it > 0L } ?: 0L
        return evaluateStorageForDownload(knownBytes)
    }

    fun checkBulkDownloadStorage(eligibleNewTracks: List<CarTrack>): StoragePrecheckResult {
        var knownBytes = 0L
        for (t in eligibleNewTracks) {
            val s = t.sourceSizeBytes
            if (s != null && s > 0L) {
                knownBytes += s
            }
        }
        return evaluateStorageForDownload(knownBytes)
    }

    fun hasSufficientStorage(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return isStorageTargetAvailable(getOfflineStorageTarget())
        return evaluateStorageForDownload(requiredBytes).isAllowed
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
        var waitingTrack: CarTrack? = null
        var waitingStatus: DownloadStatus? = null
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
                    DownloadState.WAITING_FOR_WIFI -> {
                        inProgressCount++
                        if (waitingTrack == null) {
                            waitingTrack = job.track
                            waitingStatus = DownloadStatus(
                                trackId = id,
                                state = job.state,
                                bytesDownloaded = job.bytesDownloaded,
                                totalBytes = job.totalBytes,
                                errorMessage = job.errorMessage,
                                title = job.track.title,
                                artist = job.track.artist,
                            )
                        }
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
                    DownloadState.FAILED, DownloadState.STORAGE_TARGET_UNAVAILABLE -> {
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
                    DownloadState.NOT_DOWNLOADED, DownloadState.OFFLINE_MEDIA_UNAVAILABLE -> {}
                }
            }
        }

        val activeOrWaitingTrack = currentDownloadingTrack ?: waitingTrack
        val activeOrWaitingStatus = currentDownloadingStatus ?: waitingStatus

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
            currentTrackTitle = activeOrWaitingTrack?.title,
            currentTrackProgress = activeOrWaitingStatus,
        )
    }

    fun getMetadataStore(): OfflineMetadataStore = store

    fun getJobQueueSequence(trackId: Long): Long? = jobs[trackId]?.queueSequence

    fun enqueueBatch(
        batch: DownloadBatch,
        tracksToEnqueue: List<CarTrack>,
        apiClient: CarApiClient = CarApiClient(),
    ): Int {
        lastApiClient = apiClient
        val selectedTarget = getOfflineStorageTarget()
        if (!isStorageTargetAvailable(selectedTarget)) {
            val unavailableMsg = "Thẻ nhớ SD không khả dụng"
            val distinctUnavailable = tracksToEnqueue.distinctBy { it.id }
            for (track in distinctUnavailable) {
                val trackId = track.id.toLong()
                if (isOfflineReady(trackId) || isOfflineMediaUnavailable(trackId)) continue
                val existingJob = jobs[trackId]
                if (existingJob != null && (existingJob.state == DownloadState.QUEUED ||
                        existingJob.state == DownloadState.WAITING_FOR_WIFI ||
                        existingJob.state == DownloadState.DOWNLOADING ||
                        existingJob.state == DownloadState.FINALIZING)) {
                    continue
                }
                val unavailableJob = DownloadJob(
                    track = track,
                    state = DownloadState.STORAGE_TARGET_UNAVAILABLE,
                    totalBytes = track.sourceSizeBytes ?: 0L,
                    errorMessage = unavailableMsg,
                    attemptId = attemptCounter.incrementAndGet(),
                    batchId = batch.batchId,
                    queueSequence = sequenceCounter.incrementAndGet(),
                )
                jobs[trackId] = unavailableJob
                notifyFailed(trackId, unavailableMsg)
            }
            if (!hasActiveJobs()) {
                serviceController?.onQueueIdle()
            }
            persistQueueState()
            return 0
        }

        if (activeBatch == null) {
            activeBatch = batch
        }
        var enqueuedCount = 0
        val distinctTracks = tracksToEnqueue.distinctBy { it.id }
        val newlyEnqueuedIds = mutableListOf<Long>()

        for (track in distinctTracks) {
            val trackId = track.id.toLong()
            if (isOfflineReady(trackId) || isOfflineMediaUnavailable(trackId)) {
                continue
            }
            val existingJob = jobs[trackId]
            if (existingJob != null && (existingJob.state == DownloadState.QUEUED ||
                    existingJob.state == DownloadState.WAITING_FOR_WIFI ||
                    existingJob.state == DownloadState.DOWNLOADING ||
                    existingJob.state == DownloadState.FINALIZING)) {
                continue
            }

            deletePartFileForTarget(trackId, selectedTarget)

            val total = track.sourceSizeBytes ?: 0L
            val job = DownloadJob(
                track = track,
                state = DownloadState.QUEUED,
                totalBytes = total,
                attemptId = attemptCounter.incrementAndGet(),
                batchId = batch.batchId,
                queueSequence = sequenceCounter.incrementAndGet(),
            )
            jobs[trackId] = job
            enqueuedCount++
            newlyEnqueuedIds.add(trackId)
        }

        reconcileWaitingQueueStates()
        for (trackId in newlyEnqueuedIds) {
            notifyStarted(trackId)
        }

        if (enqueuedCount > 0) {
            activeBatch = batch
            serviceController?.onQueueActive()
        }
        persistQueueState()
        if (enqueuedCount > 0 && isNetworkAllowedForDownload()) {
            scheduleQueueProcessing(apiClient)
        }
        return enqueuedCount
    }

    fun cancelPendingQueue(): Int {
        var cancelledCount = 0
        for (job in jobs.values) {
            synchronized(job) {
                if (job.state == DownloadState.QUEUED || job.state == DownloadState.WAITING_FOR_WIFI) {
                    job.isCancelled = true
                    job.pausedForWifi = false
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
        for ((trackId, job) in jobs) {
            synchronized(job) {
                if (job.state == DownloadState.QUEUED ||
                    job.state == DownloadState.WAITING_FOR_WIFI ||
                    job.state == DownloadState.DOWNLOADING ||
                    job.state == DownloadState.FINALIZING
                ) {
                    job.isCancelled = true
                    job.pausedForWifi = false
                    val wasActive = (job.state == DownloadState.DOWNLOADING || job.state == DownloadState.FINALIZING)
                    job.state = DownloadState.CANCELLED
                    activeStreams.remove(trackId)?.let { runCatching { it.close() } }
                    activeOutputs.remove(trackId)?.let { runCatching { it.close() } }
                    deletePartFileOnAllKnownTargets(trackId)
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

    fun deleteMultipleDownloads(trackIds: Collection<Long>): Int {
        var count = 0
        for (trackId in trackIds.distinct()) {
            if (isDownloading(trackId)) {
                continue
            }
            if (store.getRecord(trackId) != null || isOfflineReady(trackId)) {
                if (deleteDownload(trackId)) {
                    count++
                }
            }
        }
        return count
    }

    fun deleteAllOfflineDownloads(): DeleteAllResult {
        if (hasActiveJobs()) {
            return DeleteAllResult(
                success = false,
                deletedTrackCount = 0,
                freedBytes = 0L,
                blockedByActiveDownloads = true,
                errorMessage = "Đang có tiến trình tải xuống. Vui lòng hủy hoặc chờ tải xong trước khi xóa tất cả nhạc offline.",
            )
        }
        val freedBytes = getPinnedUsedBytes()
        val allRecords = store.getAllRecords()
        var deletedCount = 0
        for (record in allRecords) {
            if (store.removeRecord(record.trackId)) {
                deletedCount++
                notifyDeleted(record.trackId)
            }
        }
        cleanOrphanParts()
        jobs.clear()
        activeBatch = null
        persistQueueState()
        return DeleteAllResult(
            success = true,
            deletedTrackCount = deletedCount,
            freedBytes = freedBytes,
        )
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

        if (!isNetworkAllowedForDownload()) {
            wakeLock?.release()
            reconcileWaitingQueueStates()
            persistQueueState()
            return
        }

        val selectedTarget = getOfflineStorageTarget()
        var partFile: File? = null
        var finalFile: File? = null
        var stream: InputStream? = null
        try {
            if (!isStorageTargetAvailable(selectedTarget)) {
                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
            }
            val storageDir = volumeProvider.getStorageDirectory(selectedTarget)
                ?: throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
            if (!storageDir.exists() && !storageDir.mkdirs() && !storageDir.exists()) {
                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
            }
            val targetVolumeUuid = volumeProvider.getVolumeUuid(selectedTarget)

            val ext = track.sourceFormat?.lowercase()?.takeIf { it.isNotBlank() } ?: "audio"
            val finalFileName = "track_${trackId}.$ext"
            val partFileName = "track_${trackId}.part"
            val currentPartFile = File(storageDir, partFileName)
            val currentFinalFile = File(storageDir, finalFileName)
            partFile = currentPartFile
            finalFile = currentFinalFile

            if (currentPartFile.exists()) {
                currentPartFile.delete()
            }

            // Re-verify storage cap (global) and 500 MB reserve (on SELECTED TARGET volume) before acquiring wakeLock or opening stream
            val basePinnedUsedBytes = getPinnedUsedBytes(excludeTrackId = trackId)
            val initialCapBytes = getOfflineStorageCapBytes()
            val knownTrackBytes = track.sourceSizeBytes?.takeIf { it > 0L } ?: 0L
            if (initialCapBytes > 0L && (basePinnedUsedBytes >= initialCapBytes || (knownTrackBytes > 0L && knownTrackBytes > (initialCapBytes - basePinnedUsedBytes)))) {
                throw IOException("Vượt giới hạn dung lượng offline")
            }
            val availableBytes = getAvailableStorageBytes(selectedTarget)
            if (targetAvailableSpaceProvider != null || availableSpaceProvider != null || availableBytes > 0L) {
                if (availableBytes < MIN_FREE_RESERVE_BYTES || (knownTrackBytes > 0L && (availableBytes < knownTrackBytes || (availableBytes - knownTrackBytes) < MIN_FREE_RESERVE_BYTES))) {
                    throw IOException("Không đủ dung lượng bộ nhớ")
                }
            }

            synchronized(job) {
                if (job.isCancelled || jobs[trackId] !== job) {
                    throw IOException("Download cancelled")
                }
                if (job.pausedForWifi || !isNetworkAllowedForDownload()) {
                    job.pausedForWifi = true
                    throw IOException("Paused for Wi-Fi")
                }
                if (!isStorageTargetAvailable(selectedTarget)) {
                    throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                }
                wakeLock?.acquire()
                job.state = DownloadState.DOWNLOADING
                job.bytesDownloaded = 0L
                job.errorMessage = null
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

            // Transfer resource ownership immediately before cancellation/supersession/policy check
            stream = input
            activeStreams[trackId] = input

            if (job.isCancelled || jobs[trackId] !== job) {
                throw IOException("Download cancelled")
            }
            if (job.pausedForWifi || !isNetworkAllowedForDownload()) {
                job.pausedForWifi = true
                throw IOException("Paused for Wi-Fi")
            }
            if (!isStorageTargetAvailable(selectedTarget)) {
                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
            }

            // REQUIREMENT 1: Use ONLY a valid HTTP response-reported length for active transfer percentage.
            // If Content-Length is absent, totalTarget is 0L (indeterminate). Do not invent a percentage from metadata sourceSizeBytes.
            val totalTarget = (reportedLength?.takeIf { it > 0L }) ?: 0L
            job.totalBytes = totalTarget

            val capOnOpen = getOfflineStorageCapBytes()
            if (capOnOpen > 0L && totalTarget > 0L && (basePinnedUsedBytes >= capOnOpen || totalTarget > (capOnOpen - basePinnedUsedBytes))) {
                throw IOException("Vượt giới hạn dung lượng offline")
            }
            val availOnOpen = getAvailableStorageBytes(selectedTarget)
            if (totalTarget > 0L && (targetAvailableSpaceProvider != null || availableSpaceProvider != null || availOnOpen > 0L)) {
                if (availOnOpen < totalTarget || (availOnOpen - totalTarget) < MIN_FREE_RESERVE_BYTES) {
                    throw IOException("Không đủ dung lượng bộ nhớ")
                }
            }

            if (jobs[trackId] === job) {
                notifyProgress(trackId, 0L, totalTarget)
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytesRead = 0L

            val fileOutput = synchronized(job) {
                if (job.isCancelled || jobs[trackId] !== job) {
                    throw IOException("Download cancelled")
                }
                if (job.pausedForWifi || !isNetworkAllowedForDownload()) {
                    job.pausedForWifi = true
                    throw IOException("Paused for Wi-Fi")
                }
                if (!isStorageTargetAvailable(selectedTarget)) {
                    throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                }
                try {
                    FileOutputStream(currentPartFile).also { activeOutputs[trackId] = it }
                } catch (ioe: IOException) {
                    if (!isStorageTargetAvailable(selectedTarget)) {
                        throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                    }
                    throw ioe
                }
            }

            fileOutput.use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var lastProgressNotifyMs = System.currentTimeMillis()
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    synchronized(job) {
                        if (job.isCancelled || jobs[trackId] !== job) {
                            throw IOException("Download cancelled")
                        }
                        if (job.pausedForWifi || !isNetworkAllowedForDownload()) {
                            job.pausedForWifi = true
                            throw IOException("Paused for Wi-Fi")
                        }
                        if (!isStorageTargetAvailable(selectedTarget)) {
                            throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                        }
                        val activeCap = getOfflineStorageCapBytes()
                        if (activeCap > 0L && (basePinnedUsedBytes + totalBytesRead + bytesRead) > activeCap) {
                            throw IOException("Vượt giới hạn dung lượng offline")
                        }
                        try {
                            output.write(buffer, 0, bytesRead)
                        } catch (ioe: IOException) {
                            if (!isStorageTargetAvailable(selectedTarget)) {
                                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                            }
                            throw ioe
                        }
                        digest.update(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        job.bytesDownloaded = totalBytesRead
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastProgressNotifyMs >= 100) {
                        lastProgressNotifyMs = now
                        if (jobs[trackId] === job && !job.pausedForWifi && !job.isCancelled) {
                            notifyProgress(trackId, totalBytesRead, totalTarget)
                        }
                    }
                }
                synchronized(job) {
                    if (!job.isCancelled && !job.pausedForWifi) {
                        try {
                            output.flush()
                        } catch (ioe: IOException) {
                            if (!isStorageTargetAvailable(selectedTarget)) {
                                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                            }
                            throw ioe
                        }
                    }
                }
            }
            activeOutputs.remove(trackId)

            if (job.isCancelled || jobs[trackId] !== job) {
                throw IOException("Download cancelled")
            }
            if (job.pausedForWifi) {
                throw IOException("Paused for Wi-Fi")
            }
            if (!isStorageTargetAvailable(selectedTarget)) {
                throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
            }

            if (totalBytesRead <= 0L) {
                throw IOException("Downloaded empty file for track $trackId")
            }

            if (reportedLength != null && reportedLength > 0L && totalBytesRead != reportedLength) {
                throw IOException("Content-Length mismatch: expected $reportedLength, got $totalBytesRead")
            }

            // Transition to FINALIZING atomically: once in FINALIZING, local hash/rename/commit will not be aborted by Wi-Fi changes
            synchronized(job) {
                if (job.isCancelled || jobs[trackId] !== job) {
                    throw IOException("Download cancelled")
                }
                if (job.pausedForWifi) {
                    throw IOException("Paused for Wi-Fi")
                }
                if (!isStorageTargetAvailable(selectedTarget)) {
                    throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                }
                job.state = DownloadState.FINALIZING
                job.bytesDownloaded = totalBytesRead
                job.totalBytes = totalBytesRead
            }
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
                storageTarget = selectedTarget,
                storageVolumeUuid = targetVolumeUuid,
            )

            var committed = false
            synchronized(job) {
                if (job.isCancelled || jobs[trackId] !== job) {
                    throw IOException("Download cancelled before commit")
                }
                if (!isStorageTargetAvailable(selectedTarget)) {
                    throw StorageTargetUnavailableException(selectedTarget, "Thẻ nhớ SD không khả dụng")
                }

                if (currentFinalFile.exists()) {
                    currentFinalFile.delete()
                }
                if (!currentPartFile.renameTo(currentFinalFile)) {
                    currentPartFile.copyTo(currentFinalFile, overwrite = true)
                    currentPartFile.delete()
                }

                if (!currentFinalFile.exists() || currentFinalFile.length() != totalBytesRead) {
                    throw IOException("Final file verification failed after rename")
                }

                store.saveRecord(record)
                job.state = DownloadState.OFFLINE_READY
                jobs.remove(trackId)

                if (!isOfflineReady(trackId)) {
                    if (currentFinalFile.exists()) currentFinalFile.delete()
                    store.removeRecord(trackId)
                    throw IOException("Offline ready verification failed after saving record")
                }

                committed = true
            }

            wakeLock?.release()
            if (committed) {
                Log.i(TAG, "Successfully downloaded track $trackId ($totalBytesRead bytes, target=$selectedTarget, sha256=${sha256.take(8)}...)")
                if (!hasActiveJobs()) {
                    serviceController?.onQueueIdle()
                }
                notifyCompleted(trackId, record)
            }
        } catch (e: Exception) {
            wakeLock?.release()
            activeStreams.remove(trackId)?.let { runCatching { it.close() } }
            activeOutputs.remove(trackId)?.let { runCatching { it.close() } }
            runCatching { stream?.close() }
            if (jobs[trackId] === job) {
                runCatching { partFile?.let { if (it.exists()) it.delete() } }
                runCatching { finalFile?.let { if (it.exists() && !isOfflineReady(trackId)) it.delete() } }
                if (job.isCancelled) {
                    job.pausedForWifi = false
                    job.state = DownloadState.CANCELLED
                    reconcileWaitingQueueStates()
                    if (!hasActiveJobs()) {
                        serviceController?.onQueueIdle()
                    }
                    notifyCancelled(trackId)
                } else if (job.pausedForWifi || !isNetworkAllowedForDownload()) {
                    var shouldNotifyWaiting = false
                    synchronized(job) {
                        job.pausedForWifi = false
                        if (!job.isCancelled && jobs[trackId] === job) {
                            shouldNotifyWaiting = (job.state != DownloadState.WAITING_FOR_WIFI)
                            job.state = DownloadState.WAITING_FOR_WIFI
                            job.bytesDownloaded = 0L
                            job.errorMessage = null
                        }
                    }
                    reconcileWaitingQueueStates()
                    if (shouldNotifyWaiting) {
                        notifyWaitingForWifi(trackId)
                    }
                } else if (e is StorageTargetUnavailableException || !isStorageTargetAvailable(selectedTarget)) {
                    val errorMsg = e.message?.takeIf { it.isNotBlank() } ?: "Thẻ nhớ SD không khả dụng"
                    Log.e(TAG, "Storage target unavailable for track $trackId: $errorMsg", e)
                    job.state = DownloadState.STORAGE_TARGET_UNAVAILABLE
                    job.errorMessage = errorMsg
                    if (!hasActiveJobs()) {
                        serviceController?.onQueueIdle()
                    }
                    notifyFailed(trackId, errorMsg)
                } else {
                    val isNoSpace = e.message?.contains("ENOSPC", ignoreCase = true) == true ||
                        e.message?.contains("No space left", ignoreCase = true) == true ||
                        e.message?.contains("Không đủ dung lượng bộ nhớ", ignoreCase = true) == true
                    val isCapExceeded = e.message?.contains("Vượt giới hạn dung lượng offline", ignoreCase = true) == true
                    val errorMsg = when {
                        isCapExceeded -> "Vượt giới hạn dung lượng offline"
                        isNoSpace -> "Không đủ dung lượng bộ nhớ"
                        else -> (e.message ?: "Download failed")
                    }
                    Log.e(TAG, "Download failed for track $trackId: $errorMsg", e)
                    job.state = DownloadState.FAILED
                    job.errorMessage = errorMsg
                    if (!hasActiveJobs()) {
                        serviceController?.onQueueIdle()
                    }
                    notifyFailed(trackId, errorMsg)
                }
            } else {
                runCatching { partFile?.let { if (it.exists()) it.delete() } }
                runCatching { finalFile?.let { if (it.exists() && !isOfflineReady(trackId)) it.delete() } }
                Log.i(TAG, "Attempt ${job.attemptId} for track $trackId superseded; suppressed late failure publication")
            }
        } finally {
            activeStreams.remove(trackId)?.let { runCatching { it.close() } }
            activeOutputs.remove(trackId)?.let { runCatching { it.close() } }
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

    private fun notifyWaitingForWifi(trackId: Long) {
        listeners.toList().forEach { it.onDownloadWaitingForWifi(trackId) }
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

    fun sortDownloadedRecords(
        records: List<OfflineTrackRecord>,
        sortKey: String = getDownloadedSortKey(),
    ): List<OfflineTrackRecord> {
        val collator = java.text.Collator.getInstance(java.util.Locale("vi", "VN")).apply {
            strength = java.text.Collator.PRIMARY
        }
        val comparator = when (sortKey) {
            SORT_NAME_ASC -> Comparator<OfflineTrackRecord> { a, b ->
                val cmp = collator.compare(a.title.lowercase(), b.title.lowercase())
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
            SORT_DATE_DESC -> Comparator<OfflineTrackRecord> { a, b ->
                val cmp = b.downloadedAtMs.compareTo(a.downloadedAtMs)
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
            SORT_DATE_ASC -> Comparator<OfflineTrackRecord> { a, b ->
                val cmp = a.downloadedAtMs.compareTo(b.downloadedAtMs)
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
            SORT_SIZE_DESC -> Comparator<OfflineTrackRecord> { a, b ->
                val sizeA = getEffectiveRecordSizeBytes(a)
                val sizeB = getEffectiveRecordSizeBytes(b)
                val cmp = sizeB.compareTo(sizeA)
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
            SORT_SIZE_ASC -> Comparator<OfflineTrackRecord> { a, b ->
                val sizeA = getEffectiveRecordSizeBytes(a)
                val sizeB = getEffectiveRecordSizeBytes(b)
                val cmp = sizeA.compareTo(sizeB)
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
            else -> Comparator<OfflineTrackRecord> { a, b ->
                val cmp = b.downloadedAtMs.compareTo(a.downloadedAtMs)
                if (cmp != 0) cmp else a.trackId.compareTo(b.trackId)
            }
        }
        return records.sortedWith(comparator)
    }

    fun resolveLocalPlaybackFile(trackId: Long): File? {
        if (!isOfflineReady(trackId)) return null
        val record = store.getRecord(trackId) ?: return null
        return store.resolveSafeTrackFile(record)
    }

    fun resolvePlaybackMediaItem(
        track: CarTrack,
        apiClient: CarApiClient = lastApiClient,
    ): MediaItem {
        val trackId = track.id.toLong()
        if (isOfflineReady(trackId)) {
            val record = store.getRecord(trackId)
            if (record != null) {
                return toMediaItem(record)
            }
        }
        return RemoteCatalog.trackToRemoteMediaItem(track, apiClient)
    }

    fun toMediaItem(record: OfflineTrackRecord): MediaItem {
        val file = store.getTrackFile(record)
        val fileUri = Uri.fromFile(file)
        val effectiveSize = getEffectiveRecordSizeBytes(record).takeIf { it > 0L } ?: record.fileSizeBytes
        val extras = Bundle().apply {
            putInt("track_id", record.trackId.toInt())
            putLong("track_id_long", record.trackId)
            putLong("duration_ms", record.durationMs)
            putLong("size_bytes", effectiveSize)
            putLong("downloaded_at_ms", record.downloadedAtMs)
            putString("downloaded_date_str", formatDownloadDate(record.downloadedAtMs))
            putString("storage_target", record.storageTarget.name)
            record.storageVolumeUuid?.let { putString("storage_volume_uuid", it) }
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

    fun getAllDownloadedMediaItems(sortKey: String = getDownloadedSortKey()): List<MediaItem> {
        store.reconcile()
        val readyRecords = store.getAllRecords().filter { isOfflineReady(it.trackId) }
        return sortDownloadedRecords(readyRecords, sortKey).map { toMediaItem(it) }
    }

    fun getActiveJobMediaItems(): List<MediaItem> {
        return jobs.values
            .filter { it.state != DownloadState.OFFLINE_READY }
            .sortedWith(compareBy<DownloadJob> { it.queueSequence }.thenBy { it.enqueuedAtMs })
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

            val activeJobs = jobs.values
                .filter {
                    it.state == DownloadState.QUEUED ||
                    it.state == DownloadState.WAITING_FOR_WIFI ||
                    it.state == DownloadState.DOWNLOADING ||
                    it.state == DownloadState.FINALIZING
                }
                .sortedBy { it.queueSequence }

            if (activeJobs.isEmpty()) {
                if (stateFile.exists()) stateFile.delete()
                if (tmpFile.exists()) tmpFile.delete()
                return
            }

            val sb = StringBuilder()
            sb.append("{\n")
            sb.append("  \"network_policy\": \"${getDownloadNetworkPolicy().name}\",\n")
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
                fields.add("      \"state\": \"${j.state.name}\"")
                fields.add("      \"queue_sequence\": ${j.queueSequence}")
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
        lastApiClient = apiClient
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

            val policyMatch = Regex(""""network_policy"\s*:\s*"([^"]+)"""").find(content)
            if (policyMatch != null && context == null) {
                inMemoryNetworkPolicy = DownloadNetworkPolicy.fromString(policyMatch.groupValues[1])
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
            val restoredTrackIds = mutableListOf<Long>()
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

                        // Skip if already offline ready or already active in queue
                        if (isOfflineReady(trackId)) continue
                        val existingJob = jobs[trackId]
                        if (existingJob != null && (
                                existingJob.state == DownloadState.QUEUED ||
                                existingJob.state == DownloadState.WAITING_FOR_WIFI ||
                                existingJob.state == DownloadState.DOWNLOADING ||
                                existingJob.state == DownloadState.FINALIZING
                            )
                        ) {
                            continue
                        }

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
                        val enqAt = map["enqueued_at_ms"]?.toLongOrNull() ?: System.currentTimeMillis()
                        val newJob = DownloadJob(
                            track = track,
                            state = DownloadState.QUEUED,
                            totalBytes = track.sourceSizeBytes ?: 0L,
                            enqueuedAtMs = enqAt,
                            attemptId = attemptCounter.incrementAndGet(),
                            batchId = bId,
                            queueSequence = sequenceCounter.incrementAndGet(),
                        )
                        jobs[trackId] = newJob
                        restoredCount++
                        restoredTrackIds.add(trackId)
                    }
                }
            }

            reconcileWaitingQueueStates()
            for (trackId in restoredTrackIds) {
                notifyStarted(trackId)
            }

            if (restoredCount > 0) {
                serviceController?.onQueueActive()
            }
            persistQueueState()
            if (restoredCount > 0 && isNetworkAllowedForDownload()) {
                scheduleQueueProcessing(apiClient)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore queue state", e)
            try { stateFile.delete() } catch (ignored: Exception) {}
        }
        return restoredCount
    }
}
