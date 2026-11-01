package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Per-account lyrics cache (LD7): `<root>/<owner>/<signature>.json`, root = `noBackupFilesDir/melo_lyrics_v1`.
 * It never touches the TDLib database or cache. Files are written to a temp name and then moved, so a
 * crash leaves either the old entry or the new one. Entries expire (found 90 days, no match 3 days,
 * a manual choice never) and each account keeps at most [maxEntries] entries and [maxBytes] bytes,
 * dropping the least recently used first. Every failure here is swallowed: no cache must never stop playback.
 */
internal class ShareableLyricsCache(
    private val root: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxBytes: Long = MAX_BYTES
) {

    /** [noMatch] entries remember a definite "LRCLIB has nothing" for [NO_MATCH_TTL_MS]. */
    data class Entry(
        val lrclibId: Long,
        val synced: String?,
        val plain: String?,
        val instrumental: Boolean,
        val fetchedAtMs: Long,
        val manual: Boolean,
        val noMatch: Boolean = false
    )

    fun get(ownerId: String, signature: String): Entry? {
        val file = entryFile(ownerId, signature) ?: return null
        if (!file.isFile) return null
        val entry = try {
            parse(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        }
        if (entry == null || expired(entry)) {
            file.delete()
            return null
        }
        file.setLastModified(nowMs())
        return entry
    }

    fun put(ownerId: String, signature: String, entry: Entry) {
        val dir = ownerDir(ownerId) ?: return
        val target = File(dir, "$signature.json")
        if (!isSafeName(signature)) return
        try {
            dir.mkdirs()
            val temp = File(dir, "$signature.json.part")
            temp.writeText(toJson(entry).toString(), Charsets.UTF_8)
            move(temp, target)
            target.setLastModified(nowMs())
            trim(dir)
        } catch (e: IOException) {
            // The cache is an optimisation only.
        }
    }

    fun putNoMatch(ownerId: String, signature: String) {
        put(ownerId, signature, Entry(0L, null, null, false, nowMs(), manual = false, noMatch = true))
    }

    /** "Bỏ lời đã chọn": forget one song, including a manual choice. */
    fun remove(ownerId: String, signature: String) {
        entryFile(ownerId, signature)?.delete()
    }

    /** Logout of one account removes only that account's folder (LD7). */
    fun clearOwner(ownerId: String) {
        val dir = ownerDir(ownerId) ?: return
        dir.listFiles()?.forEach { it.delete() }
        dir.delete()
    }

    internal fun entryCount(ownerId: String): Int = entries(ownerDir(ownerId)).size

    private fun entryFile(ownerId: String, signature: String): File? {
        if (!isSafeName(signature)) return null
        return ownerDir(ownerId)?.let { File(it, "$signature.json") }
    }

    private fun ownerDir(ownerId: String): File? {
        val name = ownerFolderName(ownerId) ?: return null
        return File(root, name)
    }

    private fun isSafeName(value: String) = value.isNotEmpty() && value.length <= 128 && value.all { it.isLetterOrDigit() && it.code < 128 }

    /** Plain ids are used as they are; anything else (empty, dots, slashes, odd characters) becomes a hash. */
    private fun ownerFolderName(ownerId: String): String? {
        if (ownerId.isBlank()) return null
        if (ownerId.length <= 64 && ownerId.all { (it.isLetterOrDigit() && it.code < 128) || it == '-' || it == '_' }) return ownerId
        val hash = MessageDigest.getInstance("SHA-256").digest(ownerId.toByteArray(Charsets.UTF_8))
        return "h" + hash.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
    }

    private fun expired(entry: Entry): Boolean {
        if (entry.manual) return false
        val ttl = if (entry.noMatch) NO_MATCH_TTL_MS else FOUND_TTL_MS
        return nowMs() - entry.fetchedAtMs > ttl
    }

    private fun entries(dir: File?): List<File> =
        dir?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList() ?: emptyList()

    /** Oldest-used first goes until both limits hold. */
    private fun trim(dir: File) {
        val files = entries(dir).sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        while (files.isNotEmpty() && (files.size > maxEntries || total > maxBytes)) {
            val oldest = files.removeAt(0)
            total -= oldest.length()
            oldest.delete()
        }
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun toJson(entry: Entry): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("lrclibId", entry.lrclibId)
        put("synced", entry.synced ?: JSONObject.NULL)
        put("plain", entry.plain ?: JSONObject.NULL)
        put("instrumental", entry.instrumental)
        put("fetchedAtMs", entry.fetchedAtMs)
        put("manual", entry.manual)
        put("noMatch", entry.noMatch)
    }

    private fun parse(text: String): Entry? {
        val json = JSONObject(text)
        if (json.optInt("v", 0) != VERSION) return null
        return Entry(
            lrclibId = json.optLong("lrclibId", 0L),
            synced = if (json.isNull("synced")) null else json.optString("synced", "").takeIf { it.isNotEmpty() },
            plain = if (json.isNull("plain")) null else json.optString("plain", "").takeIf { it.isNotEmpty() },
            instrumental = json.optBoolean("instrumental", false),
            fetchedAtMs = json.optLong("fetchedAtMs", 0L),
            manual = json.optBoolean("manual", false),
            noMatch = json.optBoolean("noMatch", false)
        )
    }

    companion object {
        const val VERSION = 1
        const val MAX_ENTRIES = 3_000
        const val MAX_BYTES = 20L * 1024 * 1024
        const val FOUND_TTL_MS = 90L * 24 * 60 * 60 * 1000
        const val NO_MATCH_TTL_MS = 3L * 24 * 60 * 60 * 1000
    }
}
