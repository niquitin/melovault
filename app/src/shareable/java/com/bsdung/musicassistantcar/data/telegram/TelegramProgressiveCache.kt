package com.bsdung.musicassistantcar.data.telegram

import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Authorized owner-only progressive TDLib file bridge.
 *
 * TDLib owns the download. Media3 reads only contiguous, already-downloaded
 * bytes from TDLib's private file and waits for more bytes when needed.
 * No file deletion, public URL, proxy server, bot token or cloud credential.
 */
internal object TelegramProgressiveCache {
    private class Entry(
        val account: Long, val root: File, val fileId: Int
    ) {
        @Volatile var path: File? = null
        @Volatile var downloaded: Long = 0L
        @Volatile var size: Long = -1L
        @Volatile var complete: Boolean = false
        @Volatile var active: Boolean = true
        val wake = Object()
        fun signal() = synchronized(wake) { wake.notifyAll() }
    }
    private val active = ConcurrentHashMap<Int, Entry>()
    const val SCHEME = "melovault-tdlib"

    fun makeUri(fileId: Int, extension: String): Uri =
        Uri.Builder().scheme(SCHEME).authority("authorized-audio")
            .appendPath(fileId.toString())
            .appendPath("track." + extension.lowercase().take(5)).build()

    fun fileId(uri: Uri): Int? =
        if (uri.scheme == SCHEME && uri.authority == "authorized-audio") {
            uri.pathSegments.firstOrNull()?.toIntOrNull()?.takeIf { it > 0 }
        } else null

    fun begin(account: Long, root: File, fileId: Int, initialSize: Long): Boolean {
        if (account <= 0 || fileId <= 0 ||
            ShareableTdlibOwner.authorizedAccount() != account) return false
        val ownerRoot = runCatching { root.canonicalFile }.getOrNull() ?: return false
        if (!ownerRoot.isDirectory) return false
        val previous = active.remove(fileId)
        if (previous != null) {
            previous.active = false
            previous.signal()
        }
        // Cap concurrent progressive streams; prefetch remains separate.
        if (active.size >= 3) clear()
        active[fileId] = Entry(account, ownerRoot, fileId).apply {
            if (initialSize > 0) size = initialSize
        }
        return true
    }

    fun observe(file: JSONObject) {
        val fileId = file.optInt("id")
        val entry = active[fileId] ?: return
        if (!entry.active || entry.account != ShareableTdlibOwner.authorizedAccount()) {
            stop(fileId)
            return
        }
        val local = file.optJSONObject("local") ?: return
        val path = local.optString("path")
        if (path.isNotBlank()) {
            val target = File(path)
            if (TelegramAccountStoragePolicy.isOwnedFile(entry.root, target)) {
                entry.path = target
            }
        }
        val size = file.optLong("size").takeIf { it > 0 }
            ?: file.optLong("expected_size").takeIf { it > 0 }
        if (size != null) entry.size = size
        entry.complete = local.optBoolean("is_downloading_completed")
        val offset = local.optLong("download_offset")
        val prefix = local.optLong("downloaded_prefix_size")
        if (offset == 0L && prefix > entry.downloaded) {
            entry.downloaded = prefix
        }
        if (entry.complete) {
            val length = entry.path?.length() ?: 0L
            entry.downloaded = maxOf(entry.downloaded, length)
            if (entry.size <= 0) entry.size = length
        }
        entry.signal()
    }

    fun coverCandidate(fileId: Int): Uri? {
        val item = active[fileId] ?: return null
        if (ShareableTdlibOwner.authorizedAccount() != item.account) return null
        val target = item.path ?: return null
        if (!item.complete && item.downloaded < 384L * 1024L) return null
        if (!target.isFile || !TelegramAccountStoragePolicy.isOwnedFile(item.root, target)) {
            return null
        }
        return Uri.fromFile(target)
    }

    fun readyBytes(fileId: Int): Long = active[fileId]?.downloaded ?: 0L
    fun totalBytes(fileId: Int): Long = active[fileId]?.size ?: -1L
    fun isComplete(fileId: Int): Boolean = active[fileId]?.complete == true

    fun stop(fileId: Int) {
        active.remove(fileId)?.let { it.active = false; it.signal() }
    }

    fun clear() {
        val entries = active.values.toList()
        active.clear()
        for (entry in entries) { entry.active = false; entry.signal() }
    }

    class Reader(private val fileId: Int, private val start: Long) {
        @Volatile private var closed = false
        private val entry = active[fileId]
            ?: throw IOException("Telegram stream is not registered")
        private var stream: RandomAccessFile? = null
        private var offset = start
        fun totalRemaining(): Long =
            if (entry.size > 0) (entry.size - start).coerceAtLeast(0)
            else -1L
        @Throws(IOException::class)
        fun read(target: ByteArray, bufferOffset: Int, count: Int): Int {
            var waitedMs = 0L
            while (true) {
                if (closed) throw IOException("Telegram stream reader closed")
                if (!entry.active ||
                    ShareableTdlibOwner.authorizedAccount() != entry.account) {
                    throw IOException("Telegram account stream closed")
                }
                val file = entry.path
                val available = minOf(entry.downloaded, file?.length() ?: 0L)
                if (file != null && offset < available) {
                    if (stream == null) stream = RandomAccessFile(file, "r")
                    val amount = minOf(count.toLong(), available - offset).toInt()
                    stream!!.seek(offset)
                    val read = stream!!.read(target, bufferOffset, amount)
                    if (read > 0) {
                        offset += read
                        return read
                    }
                }
                if (entry.complete && (file == null || offset >= file.length())) {
                    return -1
                }
                if (waitedMs >= 45_000L) {
                    throw IOException("Telegram download stalled, retry on a stable network")
                }
                synchronized(entry.wake) {
                    try { entry.wake.wait(200L) }
                    catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Telegram playback interrupted", e)
                    }
                }
                waitedMs += 200L
            }
        }
        fun close() {
            closed = true
            entry.signal()
            stream?.close()
            stream = null
        }
    }
}
