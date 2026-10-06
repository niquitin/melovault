package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

enum class OfflineStorageTarget {
    INTERNAL,
    REMOVABLE_SD;

    companion object {
        fun fromString(value: String?): OfflineStorageTarget {
            return when (value?.trim()?.uppercase()) {
                "REMOVABLE_SD", "SD_CARD", "SD" -> REMOVABLE_SD
                "INTERNAL" -> INTERNAL
                else -> INTERNAL
            }
        }

        fun formatDisplayName(target: OfflineStorageTarget): String {
            return when (target) {
                INTERNAL -> "Bộ nhớ trong"
                REMOVABLE_SD -> "Thẻ nhớ SD"
            }
        }
    }
}

data class OfflineStorageVolumeInfo(
    val target: OfflineStorageTarget,
    val appFilesRootDir: File,
    val offlineDir: File,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    val isWritable: Boolean,
    val state: String,
    val volumeUuid: String? = null,
    val availableBytes: Long = 0L,
    val totalBytes: Long = 0L,
) {
    val isAvailable: Boolean
        get() = isMounted && isWritable
}

data class OfflineStorageDiscoveryResult(
    val internalVolume: OfflineStorageVolumeInfo?,
    val removableSdVolume: OfflineStorageVolumeInfo?,
    val allDiscoveredVolumes: List<OfflineStorageVolumeInfo>,
) {
    val isInternalAvailable: Boolean
        get() = internalVolume?.isAvailable == true

    val isRemovableSdAvailable: Boolean
        get() = removableSdVolume?.isAvailable == true
}

object OfflineStorageVolumeClassifier {
    const val OFFLINE_DIR_NAME = "melovault_offline"
    const val STATE_MOUNTED = "mounted"
    const val STATE_MOUNTED_READ_ONLY = "mounted_ro"
    const val STATE_UNMOUNTED = "unmounted"
    const val STATE_REMOVED = "removed"

    /**
     * Classifies app-specific external storage directories returned by [Context.getExternalFilesDirs].
     *
     * Rules:
     * - Primary/emulated app external storage (!isRemovable) => [OfflineStorageTarget.INTERNAL]
     * - Removable app external storage (isRemovable) => [OfflineStorageTarget.REMOVABLE_SD]
     * - If multiple removable volumes exist, selects the first valid mounted & writable removable
     *   app-specific volume.
     * - Never hardcodes `/storage/XXXX-XXXX` paths or relies on a constant UUID path.
     */
    fun discoverVolumes(
        externalFilesDirs: Array<File?>?,
        fallbackInternalFilesDir: File? = null,
        isRemovableResolver: (File) -> Boolean,
        storageStateResolver: (File) -> String,
        isWritableResolver: (File) -> Boolean = { dir ->
            try {
                val offlineDir = File(dir, OFFLINE_DIR_NAME)
                if (!offlineDir.exists()) {
                    offlineDir.mkdirs()
                }
                offlineDir.exists() && offlineDir.isDirectory && offlineDir.canWrite()
            } catch (_: Exception) {
                false
            }
        },
        volumeUuidResolver: (File) -> String? = { null },
        availableBytesResolver: (File) -> Long = { dir ->
            try {
                val offlineDir = File(dir, OFFLINE_DIR_NAME)
                val probe = if (offlineDir.exists()) offlineDir else dir
                probe.usableSpace.coerceAtLeast(0L)
            } catch (_: Exception) {
                0L
            }
        },
        totalBytesResolver: (File) -> Long = { dir ->
            try {
                val offlineDir = File(dir, OFFLINE_DIR_NAME)
                val probe = if (offlineDir.exists()) offlineDir else dir
                probe.totalSpace.coerceAtLeast(0L)
            } catch (_: Exception) {
                0L
            }
        },
    ): OfflineStorageDiscoveryResult {
        val discovered = mutableListOf<OfflineStorageVolumeInfo>()

        if (externalFilesDirs != null) {
            for (rawDir in externalFilesDirs) {
                val dir = rawDir ?: continue
                val removable = try {
                    isRemovableResolver(dir)
                } catch (_: Exception) {
                    false
                }
                val state = try {
                    storageStateResolver(dir)
                } catch (_: Exception) {
                    STATE_UNMOUNTED
                }
                val mounted = (state == STATE_MOUNTED)
                val writable = if (mounted) {
                    try {
                        isWritableResolver(dir)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }
                val uuid = try {
                    volumeUuidResolver(dir)?.trim()?.takeIf { it.isNotEmpty() }
                } catch (_: Exception) {
                    null
                }
                val avail = if (mounted && writable) {
                    try {
                        availableBytesResolver(dir).coerceAtLeast(0L)
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    0L
                }
                val total = if (mounted) {
                    try {
                        totalBytesResolver(dir).coerceAtLeast(0L)
                    } catch (_: Exception) {
                        0L
                    }
                } else {
                    0L
                }

                val target = if (removable) {
                    OfflineStorageTarget.REMOVABLE_SD
                } else {
                    OfflineStorageTarget.INTERNAL
                }
                val offlineDir = File(dir, OFFLINE_DIR_NAME)
                discovered.add(
                    OfflineStorageVolumeInfo(
                        target = target,
                        appFilesRootDir = dir,
                        offlineDir = offlineDir,
                        isRemovable = removable,
                        isMounted = mounted,
                        isWritable = writable,
                        state = state,
                        volumeUuid = uuid,
                        availableBytes = avail,
                        totalBytes = total,
                    )
                )
            }
        }

        var internalVolume = discovered.firstOrNull { !it.isRemovable && it.isAvailable }
            ?: discovered.firstOrNull { !it.isRemovable }

        if (internalVolume == null && fallbackInternalFilesDir != null) {
            val offlineDir = File(fallbackInternalFilesDir, OFFLINE_DIR_NAME)
            if (!offlineDir.exists()) {
                runCatching { offlineDir.mkdirs() }
            }
            val avail = runCatching { offlineDir.usableSpace.coerceAtLeast(0L) }.getOrDefault(0L)
            val total = runCatching { offlineDir.totalSpace.coerceAtLeast(0L) }.getOrDefault(0L)
            internalVolume = OfflineStorageVolumeInfo(
                target = OfflineStorageTarget.INTERNAL,
                appFilesRootDir = fallbackInternalFilesDir,
                offlineDir = offlineDir,
                isRemovable = false,
                isMounted = true,
                isWritable = true,
                state = STATE_MOUNTED,
                volumeUuid = null,
                availableBytes = avail,
                totalBytes = total,
            )
            discovered.add(0, internalVolume)
        }

        // First valid writable removable app-specific volume
        val removableSdVolume = discovered.firstOrNull { it.isRemovable && it.isAvailable }

        return OfflineStorageDiscoveryResult(
            internalVolume = internalVolume,
            removableSdVolume = removableSdVolume,
            allDiscoveredVolumes = discovered,
        )
    }
}

interface OfflineStorageVolumeProvider {
    fun discover(): OfflineStorageDiscoveryResult
    fun discoverVolumes(): OfflineStorageDiscoveryResult = discover()
    fun getInternalOfflineDir(): File
    fun getRemovableSdOfflineDir(): File?
    fun getOfflineDirForTarget(target: OfflineStorageTarget): File?
    fun getStorageDirectory(target: OfflineStorageTarget): File? = getOfflineDirForTarget(target)
    fun isTargetAvailable(target: OfflineStorageTarget): Boolean
    fun getAvailableBytes(target: OfflineStorageTarget): Long
    fun getTotalBytes(target: OfflineStorageTarget): Long
    fun getVolumeUuid(target: OfflineStorageTarget): String?
}

class SingleDirOfflineStorageVolumeProvider(
    private val internalOfflineDir: File,
) : OfflineStorageVolumeProvider {

    override fun discover(): OfflineStorageDiscoveryResult {
        if (!internalOfflineDir.exists()) {
            runCatching { internalOfflineDir.mkdirs() }
        }
        val parent = internalOfflineDir.parentFile ?: internalOfflineDir
        val avail = runCatching { internalOfflineDir.usableSpace.coerceAtLeast(0L) }.getOrDefault(0L)
        val total = runCatching { internalOfflineDir.totalSpace.coerceAtLeast(0L) }.getOrDefault(0L)
        val internalInfo = OfflineStorageVolumeInfo(
            target = OfflineStorageTarget.INTERNAL,
            appFilesRootDir = parent,
            offlineDir = internalOfflineDir,
            isRemovable = false,
            isMounted = true,
            isWritable = true,
            state = OfflineStorageVolumeClassifier.STATE_MOUNTED,
            volumeUuid = null,
            availableBytes = avail,
            totalBytes = total,
        )
        return OfflineStorageDiscoveryResult(
            internalVolume = internalInfo,
            removableSdVolume = null,
            allDiscoveredVolumes = listOf(internalInfo),
        )
    }

    override fun getInternalOfflineDir(): File = internalOfflineDir

    override fun getRemovableSdOfflineDir(): File? = null

    override fun getOfflineDirForTarget(target: OfflineStorageTarget): File? {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> internalOfflineDir
            OfflineStorageTarget.REMOVABLE_SD -> null
        }
    }

    override fun isTargetAvailable(target: OfflineStorageTarget): Boolean {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> true
            OfflineStorageTarget.REMOVABLE_SD -> false
        }
    }

    override fun getAvailableBytes(target: OfflineStorageTarget): Long {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> {
                if (!internalOfflineDir.exists()) runCatching { internalOfflineDir.mkdirs() }
                runCatching { internalOfflineDir.usableSpace.coerceAtLeast(0L) }.getOrDefault(0L)
            }
            OfflineStorageTarget.REMOVABLE_SD -> 0L
        }
    }

    override fun getTotalBytes(target: OfflineStorageTarget): Long {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> {
                if (!internalOfflineDir.exists()) runCatching { internalOfflineDir.mkdirs() }
                runCatching { internalOfflineDir.totalSpace.coerceAtLeast(0L) }.getOrDefault(0L)
            }
            OfflineStorageTarget.REMOVABLE_SD -> 0L
        }
    }

    override fun getVolumeUuid(target: OfflineStorageTarget): String? = null
}

class ConfigurableOfflineStorageVolumeProvider(
    @Volatile private var internalOfflineDir: File,
    @Volatile private var removableSdOfflineDir: File? = null,
    @Volatile private var removableSdMounted: Boolean = removableSdOfflineDir != null,
    @Volatile private var removableSdWritable: Boolean = removableSdOfflineDir != null,
    @Volatile private var removableSdUuid: String? = "SD-TEST-UUID",
    @Volatile private var internalAvailableBytesOverride: Long? = null,
    @Volatile private var removableSdAvailableBytesOverride: Long? = null,
    @Volatile private var internalTotalBytesOverride: Long? = null,
    @Volatile private var removableSdTotalBytesOverride: Long? = null,
) : OfflineStorageVolumeProvider {

    fun setRemovableSdState(
        offlineDir: File? = this.removableSdOfflineDir,
        mounted: Boolean,
        writable: Boolean = mounted,
        uuid: String? = this.removableSdUuid,
    ) {
        this.removableSdOfflineDir = offlineDir
        this.removableSdMounted = mounted
        this.removableSdWritable = writable
        this.removableSdUuid = uuid
    }

    fun setInternalAvailableBytes(bytes: Long?) {
        this.internalAvailableBytesOverride = bytes
    }

    fun setRemovableSdAvailableBytes(bytes: Long?) {
        this.removableSdAvailableBytesOverride = bytes
    }

    override fun discover(): OfflineStorageDiscoveryResult {
        if (!internalOfflineDir.exists()) {
            runCatching { internalOfflineDir.mkdirs() }
        }
        val intAvail = getAvailableBytes(OfflineStorageTarget.INTERNAL)
        val intTotal = getTotalBytes(OfflineStorageTarget.INTERNAL)
        val intInfo = OfflineStorageVolumeInfo(
            target = OfflineStorageTarget.INTERNAL,
            appFilesRootDir = internalOfflineDir.parentFile ?: internalOfflineDir,
            offlineDir = internalOfflineDir,
            isRemovable = false,
            isMounted = true,
            isWritable = true,
            state = OfflineStorageVolumeClassifier.STATE_MOUNTED,
            volumeUuid = null,
            availableBytes = intAvail,
            totalBytes = intTotal,
        )

        val sdDir = removableSdOfflineDir
        val sdAvailable = sdDir != null && removableSdMounted && removableSdWritable
        val sdInfo = if (sdDir != null) {
            if (sdAvailable && !sdDir.exists()) {
                runCatching { sdDir.mkdirs() }
            }
            OfflineStorageVolumeInfo(
                target = OfflineStorageTarget.REMOVABLE_SD,
                appFilesRootDir = sdDir.parentFile ?: sdDir,
                offlineDir = sdDir,
                isRemovable = true,
                isMounted = removableSdMounted,
                isWritable = removableSdWritable,
                state = when {
                    !removableSdMounted -> OfflineStorageVolumeClassifier.STATE_UNMOUNTED
                    !removableSdWritable -> OfflineStorageVolumeClassifier.STATE_MOUNTED_READ_ONLY
                    else -> OfflineStorageVolumeClassifier.STATE_MOUNTED
                },
                volumeUuid = removableSdUuid,
                availableBytes = if (sdAvailable) getAvailableBytes(OfflineStorageTarget.REMOVABLE_SD) else 0L,
                totalBytes = if (removableSdMounted) getTotalBytes(OfflineStorageTarget.REMOVABLE_SD) else 0L,
            )
        } else {
            null
        }

        val all = buildList {
            add(intInfo)
            if (sdInfo != null) add(sdInfo)
        }
        return OfflineStorageDiscoveryResult(
            internalVolume = intInfo,
            removableSdVolume = sdInfo?.takeIf { it.isAvailable },
            allDiscoveredVolumes = all,
        )
    }

    override fun getInternalOfflineDir(): File = internalOfflineDir

    override fun getRemovableSdOfflineDir(): File? {
        if (!isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)) return null
        val dir = removableSdOfflineDir ?: return null
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return dir
    }

    override fun getOfflineDirForTarget(target: OfflineStorageTarget): File? {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> {
                if (!internalOfflineDir.exists()) runCatching { internalOfflineDir.mkdirs() }
                internalOfflineDir
            }
            OfflineStorageTarget.REMOVABLE_SD -> getRemovableSdOfflineDir()
        }
    }

    override fun isTargetAvailable(target: OfflineStorageTarget): Boolean {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> true
            OfflineStorageTarget.REMOVABLE_SD -> removableSdOfflineDir != null && removableSdMounted && removableSdWritable
        }
    }

    override fun getAvailableBytes(target: OfflineStorageTarget): Long {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> {
                internalAvailableBytesOverride ?: run {
                    if (!internalOfflineDir.exists()) runCatching { internalOfflineDir.mkdirs() }
                    runCatching { internalOfflineDir.usableSpace.coerceAtLeast(0L) }.getOrDefault(0L)
                }
            }
            OfflineStorageTarget.REMOVABLE_SD -> {
                if (!isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)) return 0L
                removableSdAvailableBytesOverride ?: run {
                    val dir = removableSdOfflineDir ?: return 0L
                    if (!dir.exists()) runCatching { dir.mkdirs() }
                    runCatching { dir.usableSpace.coerceAtLeast(0L) }.getOrDefault(0L)
                }
            }
        }
    }

    override fun getTotalBytes(target: OfflineStorageTarget): Long {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> {
                internalTotalBytesOverride ?: run {
                    if (!internalOfflineDir.exists()) runCatching { internalOfflineDir.mkdirs() }
                    runCatching { internalOfflineDir.totalSpace.coerceAtLeast(0L) }.getOrDefault(0L)
                }
            }
            OfflineStorageTarget.REMOVABLE_SD -> {
                if (!removableSdMounted) return 0L
                removableSdTotalBytesOverride ?: run {
                    val dir = removableSdOfflineDir ?: return 0L
                    if (!dir.exists()) runCatching { dir.mkdirs() }
                    runCatching { dir.totalSpace.coerceAtLeast(0L) }.getOrDefault(0L)
                }
            }
        }
    }

    override fun getVolumeUuid(target: OfflineStorageTarget): String? {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> null
            OfflineStorageTarget.REMOVABLE_SD -> if (isTargetAvailable(OfflineStorageTarget.REMOVABLE_SD)) removableSdUuid else null
        }
    }
}

class AndroidOfflineStorageVolumeProvider(
    private val context: Context,
) : OfflineStorageVolumeProvider {

    private val appContext = context.applicationContext ?: context

    override fun discover(): OfflineStorageDiscoveryResult {
        val externalDirs = runCatching { appContext.getExternalFilesDirs(null) }.getOrNull()
        val storageManager = runCatching {
            appContext.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        }.getOrNull()

        return OfflineStorageVolumeClassifier.discoverVolumes(
            externalFilesDirs = externalDirs,
            fallbackInternalFilesDir = appContext.filesDir,
            isRemovableResolver = { dir ->
                Environment.isExternalStorageRemovable(dir)
            },
            storageStateResolver = { dir ->
                Environment.getExternalStorageState(dir)
            },
            volumeUuidResolver = { dir ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    storageManager?.getStorageVolume(dir)?.uuid
                } else {
                    null
                }
            },
        )
    }

    override fun getInternalOfflineDir(): File {
        val discovery = discover()
        val dir = discovery.internalVolume?.offlineDir
            ?: appContext.getExternalFilesDir(null)?.resolve(OfflineStorageVolumeClassifier.OFFLINE_DIR_NAME)
            ?: File(appContext.filesDir, OfflineStorageVolumeClassifier.OFFLINE_DIR_NAME)
        if (!dir.exists()) {
            runCatching { dir.mkdirs() }
        }
        return dir
    }

    override fun getRemovableSdOfflineDir(): File? {
        val discovery = discover()
        val sdVol = discovery.removableSdVolume ?: return null
        if (!sdVol.isAvailable) return null
        if (!sdVol.offlineDir.exists()) {
            runCatching { sdVol.offlineDir.mkdirs() }
        }
        return sdVol.offlineDir
    }

    override fun getOfflineDirForTarget(target: OfflineStorageTarget): File? {
        return when (target) {
            OfflineStorageTarget.INTERNAL -> getInternalOfflineDir()
            OfflineStorageTarget.REMOVABLE_SD -> getRemovableSdOfflineDir()
        }
    }

    override fun isTargetAvailable(target: OfflineStorageTarget): Boolean {
        val discovery = discover()
        return when (target) {
            OfflineStorageTarget.INTERNAL -> discovery.isInternalAvailable
            OfflineStorageTarget.REMOVABLE_SD -> discovery.isRemovableSdAvailable
        }
    }

    override fun getAvailableBytes(target: OfflineStorageTarget): Long {
        val discovery = discover()
        return when (target) {
            OfflineStorageTarget.INTERNAL -> discovery.internalVolume?.availableBytes ?: 0L
            OfflineStorageTarget.REMOVABLE_SD -> {
                val sd = discovery.removableSdVolume
                if (sd != null && sd.isAvailable) sd.availableBytes else 0L
            }
        }
    }

    override fun getTotalBytes(target: OfflineStorageTarget): Long {
        val discovery = discover()
        return when (target) {
            OfflineStorageTarget.INTERNAL -> discovery.internalVolume?.totalBytes ?: 0L
            OfflineStorageTarget.REMOVABLE_SD -> {
                val sd = discovery.removableSdVolume
                if (sd != null && sd.isAvailable) sd.totalBytes else 0L
            }
        }
    }

    override fun getVolumeUuid(target: OfflineStorageTarget): String? {
        val discovery = discover()
        return when (target) {
            OfflineStorageTarget.INTERNAL -> discovery.internalVolume?.volumeUuid
            OfflineStorageTarget.REMOVABLE_SD -> discovery.removableSdVolume?.volumeUuid
        }
    }
}

class StorageTargetUnavailableException(
    val target: OfflineStorageTarget = OfflineStorageTarget.REMOVABLE_SD,
    message: String = "Thẻ nhớ SD không khả dụng",
) : java.io.IOException(message) {
    constructor(message: String) : this(OfflineStorageTarget.REMOVABLE_SD, message)
}
