package com.bsdung.musicassistantcar.lyrics

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class LyricsOverrideEntry(
    val trackKey: String,
    val metadataSignature: String,
    val provider: String = "lrclib",
    val providerRecordId: String,
    val selectedAtMs: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long? = null,
) {
    val selectedTitle: String
        get() = title

    val selectedArtist: String
        get() = artist

    val selectedDurationMs: Long?
        get() = durationMs
}

class LyricsOverrideStore(
    val overrideFile: File,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(
        context: Context,
        clockMs: () -> Long = { System.currentTimeMillis() },
    ) : this(
        overrideFile = File(context.filesDir, DEFAULT_FILE_NAME),
        clockMs = clockMs,
    )

    private val lock = Any()

    fun saveOverride(
        metadata: LyricsTrackMetadata,
        provider: String = "lrclib",
        providerRecordId: String,
        selectedTitle: String = metadata.title,
        selectedArtist: String = metadata.artist,
        selectedAlbum: String = metadata.album,
        selectedDurationMs: Long? = metadata.durationMs,
    ): LyricsOverrideEntry = saveOverride(
        trackKey = metadata.trackKey,
        metadataSignature = metadata.cacheSignature(),
        provider = provider,
        providerRecordId = providerRecordId,
        selectedTitle = selectedTitle.ifBlank { metadata.title },
        selectedArtist = selectedArtist.ifBlank { metadata.artist },
        selectedAlbum = selectedAlbum.ifBlank { metadata.album },
        selectedDurationMs = selectedDurationMs ?: metadata.durationMs,
    )

    fun saveOverride(
        trackKey: String,
        metadataSignature: String,
        provider: String = "lrclib",
        providerRecordId: String,
        selectedTitle: String = "",
        selectedArtist: String = "",
        selectedAlbum: String = "",
        selectedDurationMs: Long? = null,
    ): LyricsOverrideEntry = synchronized(lock) {
        val cleanTrackKey = trackKey.trim()
        val cleanRecordId = providerRecordId.trim()
        val cleanSignature = metadataSignature.trim()
        require(cleanTrackKey.isNotEmpty()) { "trackKey must not be blank" }
        require(cleanSignature.isNotEmpty()) { "metadataSignature must not be blank" }
        require(cleanRecordId.isNotEmpty()) { "providerRecordId must not be blank" }

        val cleanProvider = provider.trim().ifEmpty { "lrclib" }.lowercase()
        val now = clockMs()

        val entry = LyricsOverrideEntry(
            trackKey = cleanTrackKey,
            metadataSignature = cleanSignature,
            provider = cleanProvider,
            providerRecordId = cleanRecordId,
            selectedAtMs = now,
            title = selectedTitle.trim(),
            artist = selectedArtist.trim(),
            album = selectedAlbum.trim(),
            durationMs = selectedDurationMs,
        )

        val current = readAllLocked().toMutableMap()
        current[cleanTrackKey] = entry
        writeAllLocked(current)
        return entry
    }

    /**
     * Returns the manual override for [metadata] ONLY IF both [LyricsTrackMetadata.trackKey]
     * and [LyricsTrackMetadata.cacheSignature] match the stored override entry.
     *
     * If track identity differs or metadata has materially changed, returns null.
     */
    fun getValidOverride(metadata: LyricsTrackMetadata): LyricsOverrideEntry? =
        getOverride(metadata.trackKey, metadata.cacheSignature())

    fun getOverride(trackKey: String, metadataSignature: String): LyricsOverrideEntry? = synchronized(lock) {
        val cleanTrackKey = trackKey.trim()
        val cleanSig = metadataSignature.trim()
        if (cleanTrackKey.isEmpty() || cleanSig.isEmpty()) return null
        val entry = readAllLocked()[cleanTrackKey] ?: return null
        if (entry.trackKey != cleanTrackKey || entry.metadataSignature != cleanSig) {
            return null
        }
        if (entry.providerRecordId.isBlank()) {
            return null
        }
        return entry
    }

    fun getOverrideByTrackKey(trackKey: String): LyricsOverrideEntry? = synchronized(lock) {
        val cleanTrackKey = trackKey.trim()
        if (cleanTrackKey.isEmpty()) return null
        return readAllLocked()[cleanTrackKey]
    }

    /**
     * Removes ONLY the override for [trackKey], preserving all other track overrides.
     */
    fun clearOverride(trackKey: String): Boolean = synchronized(lock) {
        val cleanTrackKey = trackKey.trim()
        if (cleanTrackKey.isEmpty()) return false
        val current = readAllLocked().toMutableMap()
        val removed = current.remove(cleanTrackKey) != null
        if (removed) {
            if (current.isEmpty()) {
                runCatching { overrideFile.delete() }
            } else {
                writeAllLocked(current)
            }
        }
        return removed
    }

    fun allOverrides(): Map<String, LyricsOverrideEntry> = synchronized(lock) {
        readAllLocked()
    }

    private fun readAllLocked(): LinkedHashMap<String, LyricsOverrideEntry> {
        val map = LinkedHashMap<String, LyricsOverrideEntry>()
        if (!overrideFile.exists() || !overrideFile.isFile) {
            return map
        }
        val raw = runCatching { overrideFile.readText(Charsets.UTF_8) }.getOrNull()
        if (raw == null) {
            runCatching { overrideFile.delete() }
            return map
        }
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{") || !trimmed.endsWith("}") || !trimmed.contains("\"overrides\"")) {
            runCatching { overrideFile.delete() }
            return map
        }
        for (objMatch in ENTRY_OBJECT_REGEX.findAll(raw)) {
            val block = objMatch.value
            val props = runCatching { LyricsCache.parseFlatJsonObject(block) }.getOrNull() ?: continue
            val trackKey = props["track_key"]?.trim().orEmpty()
            val sig = props["metadata_signature"]?.trim().orEmpty()
            val provider = props["provider"]?.trim().orEmpty().ifEmpty { "lrclib" }.lowercase()
            val recordId = props["provider_record_id"]?.trim().orEmpty()
            val selectedAt = props["selected_at"]?.toLongOrNull() ?: 0L
            if (trackKey.isEmpty() || sig.isEmpty() || recordId.isEmpty()) continue
            val title = props["title"]?.trim().orEmpty()
            val artist = props["artist"]?.trim().orEmpty()
            val album = props["album"]?.trim().orEmpty()
            val durationMs = props["duration_ms"]?.toLongOrNull()
            map[trackKey] = LyricsOverrideEntry(
                trackKey = trackKey,
                metadataSignature = sig,
                provider = provider,
                providerRecordId = recordId,
                selectedAtMs = selectedAt,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
            )
        }
        return map
    }

    private fun writeAllLocked(entries: Map<String, LyricsOverrideEntry>): Boolean {
        val parent = overrideFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val itemStrings = entries.values.map { entry ->
            val fields = mutableListOf<String>()
            fields.add("      \"track_key\": \"${LyricsCache.escapeJson(entry.trackKey)}\"")
            fields.add("      \"metadata_signature\": \"${LyricsCache.escapeJson(entry.metadataSignature)}\"")
            fields.add("      \"provider\": \"${LyricsCache.escapeJson(entry.provider)}\"")
            fields.add("      \"provider_record_id\": \"${LyricsCache.escapeJson(entry.providerRecordId)}\"")
            fields.add("      \"selected_at\": ${entry.selectedAtMs}")
            if (entry.title.isNotEmpty()) {
                fields.add("      \"title\": \"${LyricsCache.escapeJson(entry.title)}\"")
            }
            if (entry.artist.isNotEmpty()) {
                fields.add("      \"artist\": \"${LyricsCache.escapeJson(entry.artist)}\"")
            }
            if (entry.album.isNotEmpty()) {
                fields.add("      \"album\": \"${LyricsCache.escapeJson(entry.album)}\"")
            }
            if (entry.durationMs != null) {
                fields.add("      \"duration_ms\": ${entry.durationMs}")
            }
            "    {\n" + fields.joinToString(",\n") + "\n    }"
        }
        val jsonText = buildString {
            append("{\n")
            append("  \"version\": 1,\n")
            append("  \"overrides\": [\n")
            append(itemStrings.joinToString(",\n"))
            if (itemStrings.isNotEmpty()) {
                append("\n")
            }
            append("  ]\n")
            append("}")
        }
        val tmpFile = File(overrideFile.parentFile ?: File("."), "${overrideFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                fos.write(jsonText.toByteArray(Charsets.UTF_8))
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            try {
                Files.move(
                    tmpFile.toPath(),
                    overrideFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(
                    tmpFile.toPath(),
                    overrideFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            true
        } catch (_: Exception) {
            runCatching { tmpFile.delete() }
            false
        }
    }

    companion object {
        const val DEFAULT_FILE_NAME = "lyrics_overrides.json"

        private val ENTRY_OBJECT_REGEX = Regex("""\{[^{}]*"track_key"[^{}]*\}""")
    }
}
