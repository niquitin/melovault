package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

data class OfflineTrackRecord(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val relativeFileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val loudnessLufs: Double?,
    val truePeakDbtp: Double?,
    val loudnessRangeLu: Double?,
    val analysisVersion: Int?,
    val downloadedAtMs: Long,
    val format: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
)

class OfflineMetadataStore(private val storageDir: File) {

    companion object {
        private const val TAG = "OfflineMetadataStore"
        private const val METADATA_FILENAME = "offline_metadata.json"
        private const val BACKUP_FILENAME = "offline_metadata.json.bak"
        private const val TMP_FILENAME = "offline_metadata.json.tmp"
        private const val OFFLINE_DIR_NAME = "melovault_offline"

        @Volatile
        private var defaultInstance: OfflineMetadataStore? = null

        fun getDefault(context: Context): OfflineMetadataStore {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: run {
                    val baseDir = context.getExternalFilesDir(null)
                        ?.resolve(OFFLINE_DIR_NAME)
                        ?: File(context.filesDir, OFFLINE_DIR_NAME)
                    if (!baseDir.exists()) {
                        baseDir.mkdirs()
                    }
                    OfflineMetadataStore(baseDir).also { defaultInstance = it }
                }
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
    }

    private val metadataFile = File(storageDir, METADATA_FILENAME)
    private val records = mutableMapOf<Long, OfflineTrackRecord>()
    private var isLoaded = false

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        load()
    }

    @Synchronized
    private fun load() {
        records.clear()
        if (!metadataFile.exists() || metadataFile.length() == 0L) {
            isLoaded = true
            return
        }

        try {
            val content = metadataFile.readText(Charsets.UTF_8).trim()
            if (!content.startsWith("{") || !content.endsWith("}")) {
                throw IllegalArgumentException("Invalid root JSON")
            }

            val tracksStart = content.indexOf("\"tracks\"")
            if (tracksStart == -1) {
                isLoaded = true
                return
            }

            val arrayStart = content.indexOf('[', tracksStart)
            val arrayEnd = content.lastIndexOf(']')
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                isLoaded = true
                return
            }

            val tracksBlock = content.substring(arrayStart + 1, arrayEnd)
            val objRegex = Regex("""\{([^{}]+)\}""")
            val propRegex = Regex(""""([^"]+)"\s*:\s*(?:"((?:\\.|[^"\\])*)"|(null)|(true|false)|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))""")

            for (match in objRegex.findAll(tracksBlock)) {
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

                val record = OfflineTrackRecord(
                    trackId = trackId,
                    title = map["title"] ?: "",
                    artist = map["artist"] ?: "",
                    album = map["album"] ?: "",
                    durationMs = map["duration_ms"]?.toLongOrNull() ?: 0L,
                    relativeFileName = map["relative_file_name"] ?: "",
                    fileSizeBytes = map["file_size_bytes"]?.toLongOrNull() ?: 0L,
                    sha256 = map["sha256"] ?: "",
                    loudnessLufs = map["loudness_lufs"]?.toDoubleOrNull(),
                    truePeakDbtp = map["true_peak_dbtp"]?.toDoubleOrNull(),
                    loudnessRangeLu = map["loudness_range_lu"]?.toDoubleOrNull(),
                    analysisVersion = map["analysis_version"]?.toIntOrNull(),
                    downloadedAtMs = map["downloaded_at_ms"]?.toLongOrNull() ?: 0L,
                    format = map["format"]?.takeIf { it.isNotBlank() },
                    codec = map["codec"]?.takeIf { it.isNotBlank() },
                    bitrate = map["bitrate"]?.toIntOrNull(),
                    sampleRate = map["sample_rate"]?.toIntOrNull(),
                    channels = map["channels"]?.toIntOrNull(),
                )
                records[trackId] = record
            }
            isLoaded = true
            reconcile()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse offline metadata JSON: ${e.message}, creating backup", e)
            try {
                val backupFile = File(storageDir, BACKUP_FILENAME)
                metadataFile.copyTo(backupFile, overwrite = true)
            } catch (ignored: Exception) {}
            records.clear()
            isLoaded = true
        }
    }

    @Synchronized
    private fun persist() {
        val sb = StringBuilder()
        sb.append("{\n  \"version\": 1,\n  \"tracks\": [\n")
        val trackEntries = records.values.map { r ->
            val fields = mutableListOf<String>()
            fields.add("      \"track_id\": ${r.trackId}")
            fields.add("      \"title\": \"${escapeJson(r.title)}\"")
            fields.add("      \"artist\": \"${escapeJson(r.artist)}\"")
            fields.add("      \"album\": \"${escapeJson(r.album)}\"")
            fields.add("      \"duration_ms\": ${r.durationMs}")
            fields.add("      \"relative_file_name\": \"${escapeJson(r.relativeFileName)}\"")
            fields.add("      \"file_size_bytes\": ${r.fileSizeBytes}")
            fields.add("      \"sha256\": \"${escapeJson(r.sha256)}\"")
            r.loudnessLufs?.let { fields.add("      \"loudness_lufs\": $it") }
            r.truePeakDbtp?.let { fields.add("      \"true_peak_dbtp\": $it") }
            r.loudnessRangeLu?.let { fields.add("      \"loudness_range_lu\": $it") }
            r.analysisVersion?.let { fields.add("      \"analysis_version\": $it") }
            fields.add("      \"downloaded_at_ms\": ${r.downloadedAtMs}")
            r.format?.let { fields.add("      \"format\": \"${escapeJson(it)}\"") }
            r.codec?.let { fields.add("      \"codec\": \"${escapeJson(it)}\"") }
            r.bitrate?.let { fields.add("      \"bitrate\": $it") }
            r.sampleRate?.let { fields.add("      \"sample_rate\": $it") }
            r.channels?.let { fields.add("      \"channels\": $it") }
            "    {\n" + fields.joinToString(",\n") + "\n    }"
        }
        sb.append(trackEntries.joinToString(",\n"))
        sb.append("\n  ]\n}")

        val tmpFile = File(storageDir, TMP_FILENAME)
        tmpFile.writeText(sb.toString(), Charsets.UTF_8)
        if (!tmpFile.renameTo(metadataFile)) {
            tmpFile.copyTo(metadataFile, overwrite = true)
            tmpFile.delete()
        }
    }

    @Synchronized
    fun saveRecord(record: OfflineTrackRecord) {
        records[record.trackId] = record
        persist()
    }

    @Synchronized
    fun getRecord(trackId: Long): OfflineTrackRecord? {
        return records[trackId]
    }

    fun isSafeRelativeFileName(fileName: String): Boolean {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("..") || trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains(':')) {
            return false
        }
        val lower = trimmed.lowercase()
        if (lower.startsWith(".") ||
            lower.endsWith(".part") ||
            lower.endsWith(".json") ||
            lower.endsWith(".tmp") ||
            lower.endsWith(".bak")
        ) {
            return false
        }
        return try {
            val rootCanonical = storageDir.canonicalFile
            val targetCanonical = File(storageDir, trimmed).canonicalFile
            targetCanonical.parentFile == rootCanonical
        } catch (e: Exception) {
            false
        }
    }

    fun resolveSafeTrackFile(fileName: String): File? {
        if (!isSafeRelativeFileName(fileName)) return null
        return File(storageDir, fileName.trim())
    }

    @Synchronized
    fun getEffectiveFileSizeBytes(record: OfflineTrackRecord): Long {
        if (record.sha256.isBlank()) return 0L
        val partFile = File(storageDir, "track_${record.trackId}.part")
        if (partFile.exists()) return 0L
        val safeFile = resolveSafeTrackFile(record.relativeFileName) ?: return 0L
        if (!safeFile.exists() || !safeFile.isFile) return 0L
        val actualLen = safeFile.length()
        return if (actualLen > 0L) actualLen else 0L
    }

    @Synchronized
    fun reconcileMissingAndUnsafe(): List<Long> {
        val removedIds = mutableListOf<Long>()
        val iterator = records.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val record = entry.value
            val file = resolveSafeTrackFile(record.relativeFileName)
            if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
                removedIds.add(entry.key)
                iterator.remove()
            }
        }
        if (removedIds.isNotEmpty()) {
            Log.w(TAG, "Removed ${removedIds.size} missing/unsafe offline records: $removedIds")
            persist()
        }
        return removedIds
    }

    @Synchronized
    fun repairMismatchedSizes(): Int {
        var repairedCount = 0
        for ((trackId, record) in records.entries.toList()) {
            val safeFile = resolveSafeTrackFile(record.relativeFileName) ?: continue
            if (safeFile.exists() && safeFile.isFile) {
                val actualLen = safeFile.length()
                if (actualLen > 0L && actualLen != record.fileSizeBytes) {
                    records[trackId] = record.copy(fileSizeBytes = actualLen)
                    repairedCount++
                }
            }
        }
        if (repairedCount > 0) {
            persist()
        }
        return repairedCount
    }

    @Synchronized
    fun reconcile(): List<Long> {
        val staleIds = mutableListOf<Long>()
        val iterator = records.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val record = entry.value
            val file = resolveSafeTrackFile(record.relativeFileName)
            if (file == null || !file.exists() || !file.isFile || file.length() <= 0L || file.length() != record.fileSizeBytes) {
                staleIds.add(entry.key)
                iterator.remove()
            }
        }
        if (staleIds.isNotEmpty()) {
            Log.w(TAG, "Reconciled metadata: removed ${staleIds.size} stale/mismatched records: $staleIds")
            persist()
        }
        return staleIds
    }

    @Synchronized
    fun hasTrack(trackId: Long): Boolean {
        val rec = records[trackId] ?: return false
        val file = resolveSafeTrackFile(rec.relativeFileName) ?: return false
        return file.exists() && file.isFile && file.length() > 0L && file.length() == rec.fileSizeBytes
    }

    @Synchronized
    fun getAllRecords(): List<OfflineTrackRecord> {
        return records.values.toList()
    }

    @Synchronized
    fun removeRecord(trackId: Long): Boolean {
        val existing = records.remove(trackId)
        if (existing != null) {
            val safeFile = resolveSafeTrackFile(existing.relativeFileName)
            if (safeFile != null && safeFile.exists() && safeFile.isFile) {
                safeFile.delete()
            }
        }
        // Ensure stale .part and other files for this track inside storageDir are also cleaned safely
        storageDir.listFiles { _, name ->
            name.startsWith("track_${trackId}.") && !name.contains("..") && !name.contains('/') && !name.contains('\\')
        }?.forEach { candidate ->
            try {
                if (candidate.canonicalFile.parentFile == storageDir.canonicalFile) {
                    candidate.delete()
                }
            } catch (ignored: Exception) {}
        }

        if (existing != null) {
            persist()
            return true
        }
        return false
    }

    fun getTrackFile(record: OfflineTrackRecord): File {
        return resolveSafeTrackFile(record.relativeFileName)
            ?: File(storageDir, "invalid_track_${record.trackId}")
    }

    fun getStorageDirectory(): File = storageDir
}
