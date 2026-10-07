package com.bsdung.musicassistantcar.lyrics

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer

data class ManualLyricsOverride(
    val provider: String,
    val recordId: String,
    val updatedAtMs: Long,
)

data class CachedLyricsEntry(
    val signature: String,
    val result: LyricsLookupResult,
    val cachedAtMs: Long,
    val lastAccessedAtMs: Long,
    val isSoftExpired: Boolean,
)

class LyricsCache(
    val cacheDir: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val positiveSoftTtlMs: Long = POSITIVE_SOFT_TTL_MS,
    private val negativeTtlMs: Long = NEGATIVE_TTL_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(
        context: Context,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
        clockMs: () -> Long = { System.currentTimeMillis() },
    ) : this(
        cacheDir = File(context.filesDir, CACHE_DIR_NAME),
        maxEntries = maxEntries,
        maxTotalBytes = maxTotalBytes,
        clockMs = clockMs,
    )

    private val lock = Any()

    init {
        synchronized(lock) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            cleanupTempFilesLocked()
        }
    }

    fun read(signature: String): CachedLyricsEntry? = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        val file = entryFile(safeSig)
        if (!file.exists() || !file.isFile) return null

        val map = runCatching {
            parseFlatJsonObject(file.readText(Charsets.UTF_8))
        }.getOrNull() ?: run {
            file.delete()
            return null
        }

        val statusStr = map["status"]
        if (statusStr.isNullOrBlank()) {
            file.delete()
            return null
        }
        val status = LyricsStatus.fromWire(statusStr)
        val cachedAtMs = map["cached_at_ms"]?.toLongOrNull() ?: 0L
        val now = clockMs()
        val ageMs = (now - cachedAtMs).coerceAtLeast(0L)

        when (status) {
            LyricsStatus.NOT_FOUND -> {
                if (ageMs > negativeTtlMs) {
                    file.delete()
                    return null
                }
            }
            LyricsStatus.SYNCED, LyricsStatus.PLAIN, LyricsStatus.INSTRUMENTAL -> {
                // Positive cache remains readable offline even after soft expiry
            }
            else -> {
                // TEMPORARY_ERROR or AMBIGUOUS must never live in disk cache
                file.delete()
                return null
            }
        }

        val provider = map["provider"]?.takeIf { it.isNotBlank() } ?: "lrclib"
        val providerRecordId = map["provider_record_id"]?.takeIf { it.isNotBlank() }
        val confidence = map["confidence"]?.toDoubleOrNull() ?: 0.0
        val plainLyrics = map["plain_lyrics"]?.takeIf { it.isNotBlank() }
        val syncedLyrics = map["synced_lyrics"]?.takeIf { it.isNotBlank() }
        val instrumental = map["instrumental"]?.toBooleanStrictOrNull() ?: (status == LyricsStatus.INSTRUMENTAL)

        val matchedTitle = map["matched_title"]
        val matchedArtist = map["matched_artist"]
        val matchedAlbum = map["matched_album"] ?: ""
        val matchedDurationMs = map["matched_duration_ms"]?.toLongOrNull()
        val matchedTrack = if (!matchedTitle.isNullOrBlank() || !matchedArtist.isNullOrBlank()) {
            LyricsMatchedTrack(
                title = matchedTitle.orEmpty(),
                artist = matchedArtist.orEmpty(),
                album = matchedAlbum,
                durationMs = matchedDurationMs,
            )
        } else {
            null
        }

        val syncedLines = if (status == LyricsStatus.SYNCED && !syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(syncedLyrics)
        } else {
            emptyList()
        }

        // Update file lastModified for LRU tracking
        runCatching { file.setLastModified(now) }

        val isSoftExpired = status in POSITIVE_STATUSES && ageMs > positiveSoftTtlMs
        return CachedLyricsEntry(
            signature = safeSig,
            result = LyricsLookupResult(
                status = status,
                provider = provider,
                providerRecordId = providerRecordId,
                confidence = confidence,
                matchedTrack = matchedTrack,
                plainLyrics = plainLyrics,
                syncedLyrics = syncedLyrics,
                syncedLines = syncedLines,
                instrumental = instrumental,
                candidates = emptyList(),
                diagnostic = null,
                fromCache = true,
            ),
            cachedAtMs = cachedAtMs,
            lastAccessedAtMs = now,
            isSoftExpired = isSoftExpired,
        )
    }

    fun write(
        signature: String,
        result: LyricsLookupResult,
    ): Boolean = synchronized(lock) {
        // Never cache TEMPORARY_ERROR or AMBIGUOUS
        if (result.status == LyricsStatus.TEMPORARY_ERROR || result.status == LyricsStatus.AMBIGUOUS) {
            return false
        }

        val safeSig = sanitizeSignature(signature)
        val now = clockMs()
        val fields = mutableListOf<String>()
        fields.add("  \"signature\": \"${escapeJson(safeSig)}\"")
        fields.add("  \"status\": \"${escapeJson(result.status.name)}\"")
        fields.add("  \"provider\": \"${escapeJson(result.provider)}\"")
        if (result.providerRecordId != null) {
            fields.add("  \"provider_record_id\": \"${escapeJson(result.providerRecordId)}\"")
        }
        fields.add("  \"confidence\": ${result.confidence}")
        if (result.matchedTrack != null) {
            fields.add("  \"matched_title\": \"${escapeJson(result.matchedTrack.title)}\"")
            fields.add("  \"matched_artist\": \"${escapeJson(result.matchedTrack.artist)}\"")
            fields.add("  \"matched_album\": \"${escapeJson(result.matchedTrack.album)}\"")
            if (result.matchedTrack.durationMs != null) {
                fields.add("  \"matched_duration_ms\": ${result.matchedTrack.durationMs}")
            }
        }
        if (!result.plainLyrics.isNullOrBlank()) {
            fields.add("  \"plain_lyrics\": \"${escapeJson(result.plainLyrics)}\"")
        }
        if (!result.syncedLyrics.isNullOrBlank()) {
            fields.add("  \"synced_lyrics\": \"${escapeJson(result.syncedLyrics)}\"")
        }
        fields.add("  \"instrumental\": ${result.instrumental}")
        fields.add("  \"cached_at_ms\": $now")

        val jsonText = "{\n" + fields.joinToString(",\n") + "\n}"
        val target = entryFile(safeSig)
        val written = atomicWriteLocked(target, jsonText)
        if (written) {
            runCatching { target.setLastModified(now) }
            pruneIfNeededLocked()
        }
        return written
    }

    fun saveManualOverride(
        signature: String,
        provider: String,
        recordId: String,
    ): Boolean = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        val now = clockMs()
        val jsonText = buildString {
            append("{\n")
            append("  \"provider\": \"${escapeJson(provider.trim().lowercase())}\",\n")
            append("  \"record_id\": \"${escapeJson(recordId.trim())}\",\n")
            append("  \"updated_at_ms\": $now\n")
            append("}")
        }
        val target = overrideFile(safeSig)
        val ok = atomicWriteLocked(target, jsonText)
        if (ok) {
            runCatching { target.setLastModified(now) }
            pruneIfNeededLocked()
        }
        return ok
    }

    fun getManualOverride(signature: String): ManualLyricsOverride? = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        val file = overrideFile(safeSig)
        if (!file.exists() || !file.isFile) return null
        val map = runCatching {
            parseFlatJsonObject(file.readText(Charsets.UTF_8))
        }.getOrNull() ?: run {
            file.delete()
            return null
        }
        val provider = map["provider"]?.trim().orEmpty()
        val recordId = map["record_id"]?.trim().orEmpty()
        if (provider.isEmpty() || recordId.isEmpty()) {
            file.delete()
            return null
        }
        return ManualLyricsOverride(
            provider = provider,
            recordId = recordId,
            updatedAtMs = map["updated_at_ms"]?.toLongOrNull() ?: 0L,
        )
    }

    fun clearManualOverride(signature: String) = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        overrideFile(safeSig).delete()
    }

    fun invalidateEntryOnly(signature: String) = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        entryFile(safeSig).delete()
    }

    fun invalidate(signature: String) = synchronized(lock) {
        val safeSig = sanitizeSignature(signature)
        entryFile(safeSig).delete()
        overrideFile(safeSig).delete()
    }

    fun pruneIfNeeded() = synchronized(lock) {
        pruneIfNeededLocked()
    }

    private fun entryFile(safeSig: String): File =
        File(cacheDir, "$safeSig.json")

    private fun overrideFile(safeSig: String): File =
        File(cacheDir, "$safeSig.override.json")

    private fun sanitizeSignature(signature: String): String {
        val trimmed = signature.trim().lowercase()
        return if (SAFE_HEX_REGEX.matches(trimmed)) {
            trimmed
        } else {
            sha256Hex(trimmed)
        }
    }

    private fun atomicWriteLocked(target: File, content: String): Boolean {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val tmpFile = File(cacheDir, "${target.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            try {
                Files.move(
                    tmpFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(
                    tmpFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
        } catch (_: Exception) {
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun cleanupTempFilesLocked() {
        val files = cacheDir.listFiles() ?: return
        for (file in files) {
            if (file.name.endsWith(".tmp")) {
                runCatching { file.delete() }
            }
        }
    }

    private fun pruneIfNeededLocked() {
        cleanupTempFilesLocked()
        val allFiles = (cacheDir.listFiles() ?: return)
            .filter { it.isFile && (it.name.endsWith(".json")) }
        val entryFiles = allFiles.filter { !it.name.endsWith(".override.json") }
        var totalBytes = allFiles.sumOf { it.length() }
        var entryCount = entryFiles.size

        if (entryCount <= maxEntries && totalBytes <= maxTotalBytes) {
            return
        }

        // Sort oldest first by lastModified timestamp
        val sortedOldestFirst = entryFiles.sortedBy { it.lastModified() }
        for (victim in sortedOldestFirst) {
            if (entryCount <= maxEntries && totalBytes <= maxTotalBytes) {
                break
            }
            val sig = victim.name.removeSuffix(".json")
            val associatedOverride = overrideFile(sig)
            val victimBytes = victim.length() + (if (associatedOverride.exists()) associatedOverride.length() else 0L)
            if (victim.delete()) {
                entryCount -= 1
                totalBytes = (totalBytes - victimBytes).coerceAtLeast(0L)
            }
            if (associatedOverride.exists()) {
                associatedOverride.delete()
            }
        }
    }

    companion object {
        const val CACHE_DIR_NAME = "lyrics_cache"
        const val DEFAULT_MAX_ENTRIES = 1000
        const val DEFAULT_MAX_TOTAL_BYTES = 20L * 1024L * 1024L
        const val POSITIVE_SOFT_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        const val NEGATIVE_TTL_MS = 24L * 60L * 60L * 1000L

        private val POSITIVE_STATUSES = setOf(
            LyricsStatus.SYNCED,
            LyricsStatus.PLAIN,
            LyricsStatus.INSTRUMENTAL,
        )

        private val SAFE_HEX_REGEX = Regex("^[0-9a-f]{64}$")

        private val NOISE_BRACKET_REGEX = Regex(
            """[\(\[\{]\s*(?:(?:official|full|hd|4k|hq|original|music|lyric|lyrics|audio|video|mv|visualizer|ver|version)\s*)+[\)\]\}]""",
            RegexOption.IGNORE_CASE,
        )
        private val PUNCT_REGEX = Regex("""['’‘`"“”\(\)\[\]\{\}\-–—_:;,.!?/\\|]+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun normalizeForSignature(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            var text = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC)
            text = NOISE_BRACKET_REGEX.replace(text, " ")
            text = text.lowercase()
            text = PUNCT_REGEX.replace(text, " ")
            return WHITESPACE_REGEX.replace(text, " ").trim()
        }

        /**
         * Computes a path-safe SHA-256 hex signature from normalized track metadata:
         * `title | artist | album | durationBucketSeconds`.
         *
         * This guarantees:
         * 1. Two different tracks named "Intro" with different artists/albums/durations never collide.
         * 2. The same track played via REMOTE_STREAM, OFFLINE_READY, or STREAM_CACHE shares the same signature.
         * 3. No unsafe path characters ever reach the filesystem.
         */
        fun computeMetadataSignature(
            title: String?,
            artist: String?,
            album: String? = "",
            durationMs: Long? = null,
        ): String {
            val normTitle = normalizeForSignature(title)
            val normArtist = normalizeForSignature(artist)
            val normAlbum = normalizeForSignature(album)
            val durationBucketSec = if (durationMs != null && durationMs > 0L) {
                // Round to nearest 2-second bucket so stream/local container rounding (±1s) matches
                ((durationMs + 1000L) / 2000L) * 2L
            } else {
                0L
            }
            val canonical = "$normTitle|$normArtist|$normAlbum|$durationBucketSec"
            return sha256Hex(canonical)
        }

        internal fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        }

        private val FLAT_JSON_PROP_REGEX = Regex(
            """"([^"]+)"\s*:\s*(?:"((?:\\.|[^"\\])*)"|(null)|(true|false)|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))""",
        )

        internal fun escapeJson(str: String): String {
            val sb = StringBuilder(str.length + 16)
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

        internal fun unescapeJson(str: String): String {
            val sb = StringBuilder(str.length)
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

        internal fun parseFlatJsonObject(raw: String): Map<String, String?> {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                throw IllegalArgumentException("Invalid JSON object")
            }
            val map = LinkedHashMap<String, String?>()
            for (prop in FLAT_JSON_PROP_REGEX.findAll(trimmed)) {
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
            if (map.isEmpty()) {
                throw IllegalArgumentException("Empty or malformed JSON object")
            }
            return map
        }
    }
}

