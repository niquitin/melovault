package com.bsdung.musicassistantcar.data.telegram

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import com.bsdung.musicassistantcar.data.telegram.MeloRowArtPolicy.Scheduler
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * E98: the small cover on every song row of the teal theme. Order for one row: cache, the picture
 * embedded in a downloaded file, the device thumbnail (local songs), the real cover Telegram sent
 * with the audio message (a ~40px blur first, then the thumbnail file), the channel's photo, and
 * otherwise nothing (the row keeps its letter tile). Every rule that can be tested without Android
 * lives in [MeloRowArtPolicy].
 *
 * All state is touched on the main thread only. TDLib replies arrive on its own thread and are
 * posted here; decoding runs on one low-priority background thread. A reply or a decoded picture
 * counts only for the generation that asked, so a new account, a log out or a switched-off setting
 * never shows an old picture. Nothing is written to disk and nothing is deleted: TDLib keeps the
 * few-kilobyte thumbnail files in its own folder. The log tag "MeloRowArt" prints counts only.
 */
internal class MeloRowArtLoader(
    private val context: Context,
    /** (type, tag, parameters) to TDLib; may be called from the main thread. */
    private val send: (String, String, (JSONObject) -> Unit) -> Unit,
    /** The verified account, 0 when there is none. */
    private val account: () -> Long,
    private val isOwnedFile: (File) -> Boolean,
    /** False once the screen is destroyed. */
    private val alive: () -> Boolean
) {
    /** One row to cover. [key] comes from [MeloRowArtPolicy]. */
    class Item(val key: String, val chatId: Long, val rawMessageId: Long, val localFile: String?, val mediaStoreId: Long)

    private enum class Kind { MESSAGE, CHAT }

    private class Job(val key: String, val kind: Kind, val chatId: Long, val rawMessageId: Long, val account: Long, val generation: Long)

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "MeloVault-RowArt").apply { priority = Thread.MIN_PRIORITY + 1; isDaemon = true }
    }
    private val cache = object : LruCache<String, Bitmap>(cacheBytes(context)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val miss = MeloRowArtPolicy.MissCache()
    private val scheduler = Scheduler()
    private val jobs = HashMap<String, Job>()
    private val fileWaiters = HashMap<Int, MutableList<String>>()
    private val chatWaiters = HashMap<String, MutableList<String>>()
    private val provisional = HashSet<String>()
    private val decoding = HashSet<String>()
    private val watchdogs = HashMap<String, Runnable>()
    private var generation = 0L
    private var targetPx = 96
    private var listener: (() -> Unit)? = null
    private var notifyPosted = false
    @Volatile private var hasFileWaiters = false

    // Counts for the log line; no names, no ids.
    private var hits = 0
    private var sent = 0
    private var failed = 0
    private var decoded = 0

    fun setListener(listener: (() -> Unit)?) {
        this.listener = listener
    }

    /** The picture to show now (a blur counts), or null. */
    fun peek(key: String?): Bitmap? = key?.let { cache.get(it) }

    /** True once a row has its final picture or is known to have none, so the caller can count coverage. */
    fun settled(key: String?): Boolean = key != null && ((cache.get(key) != null && key !in provisional) || miss.contains(key))

    /** Rows that are visible or about to be; anything not listed and still waiting is dropped. */
    fun request(items: List<Item>, rowArtPx: Int) {
        if (!alive()) return
        val acct = account()
        if (acct <= 0L) return
        targetPx = MeloRowArtPolicy.targetPx(rowArtPx)
        val wanted = HashSet<String>()
        for (item in items) {
            val key = item.key
            if (cache.get(key) != null && key !in provisional) { hits++; continue }
            if (miss.contains(key)) continue
            wanted.add(key)
            when {
                item.localFile != null -> decodeEmbedded(item, acct)
                item.mediaStoreId > 0L -> decodeDeviceThumb(item)
                item.chatId != 0L && item.rawMessageId > 0L -> enqueue(Job(key, Kind.MESSAGE, item.chatId, item.rawMessageId, acct, generation))
            }
        }
        val keep = HashSet(wanted)
        chatWaiters.forEach { (chatKey, rows) -> if (rows.any { it in wanted }) keep.add(chatKey) }
        scheduler.retain(keep)
        pump()
    }

    /** A TDLib reply tagged "rowArt…" (any thread). */
    fun onTdlibEvent(event: JSONObject) {
        main.post { handleEvent(event) }
    }

    /** Any `updateFile` (any thread): only looked at while a thumbnail download is awaited. */
    fun onUpdateFile(event: JSONObject) {
        if (!hasFileWaiters) return
        main.post { MeloRowArtPolicy.fileOf(event)?.let(::onFile) }
    }

    /** New account, log out, setting switched off or screen gone: forget every picture and request. */
    fun reset() {
        generation++
        scheduler.clear()
        jobs.clear()
        fileWaiters.clear()
        chatWaiters.clear()
        provisional.clear()
        decoding.clear()
        miss.clear()
        watchdogs.values.forEach(main::removeCallbacks)
        watchdogs.clear()
        hasFileWaiters = false
        cache.evictAll()
    }

    fun shutdown() {
        reset()
        listener = null
        main.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    // ---- Scheduling ------------------------------------------------------------------------------

    private fun enqueue(job: Job) {
        jobs[job.key] = job
        scheduler.request(job.key)
    }

    private fun pump() {
        while (true) {
            val key = scheduler.next() ?: return
            val job = jobs[key]
            if (job == null || !isCurrent(job)) {
                scheduler.done(key)
                jobs.remove(key)
                continue
            }
            val tag = (if (job.kind == Kind.MESSAGE) "rowArtMsg:" else "rowArtChat:") + "${job.generation}:$key"
            val ok = runCatching {
                if (job.kind == Kind.MESSAGE) {
                    send("getMessage", tag) { it.put("chat_id", job.chatId); it.put("message_id", job.rawMessageId) }
                } else {
                    send("getChat", tag) { it.put("chat_id", job.chatId) }
                }
            }.isSuccess
            if (!ok) {
                scheduler.done(key)
                jobs.remove(key)
                continue
            }
            sent++
            val dog = Runnable { watchdogFired(key) }
            watchdogs[key] = dog
            main.postDelayed(dog, WATCHDOG_MS)
        }
    }

    /** A job that got no final answer in time gives its slot back; a late file still lands through [fileWaiters]. */
    private fun watchdogFired(key: String) {
        watchdogs.remove(key)
        if (jobs.remove(key) == null) return
        scheduler.done(key)
        pump()
    }

    private fun finishJob(key: String) {
        watchdogs.remove(key)?.let(main::removeCallbacks)
        if (jobs.remove(key) != null) scheduler.done(key)
        pump()
    }

    private fun isCurrent(job: Job): Boolean =
        alive() && MeloRowArtPolicy.isCurrent(job.generation, generation) && job.account == account()

    // ---- TDLib replies ---------------------------------------------------------------------------

    private fun handleEvent(event: JSONObject) {
        if (!alive()) return
        val tag = event.optString("@extra")
        val type = event.optString("@type")
        val firstColon = tag.indexOf(':')
        val secondColon = tag.indexOf(':', firstColon + 1)
        if (firstColon < 0 || secondColon < 0) return
        val prefix = tag.substring(0, firstColon)
        val gen = tag.substring(firstColon + 1, secondColon).toLongOrNull() ?: return
        if (!MeloRowArtPolicy.isCurrent(gen, generation)) return
        val rest = tag.substring(secondColon + 1)
        when (prefix) {
            "rowArtMsg" -> {
                val job = jobs[rest] ?: return
                when {
                    type == "error" -> fail(rest)
                    type == "message" && isCurrent(job) -> onMessage(job, event)
                }
            }
            "rowArtChat" -> {
                val job = jobs[rest] ?: return
                when {
                    type == "error" -> fail(rest)
                    type == "chat" && isCurrent(job) -> onChat(job, event)
                }
            }
            "rowArtFile" -> {
                val fileId = rest.toIntOrNull() ?: return
                if (type == "error") {
                    fileWaiters.remove(fileId)?.forEach(::fail)
                    updateFileWaiters()
                } else {
                    MeloRowArtPolicy.fileOf(event)?.let(::onFile)
                }
            }
        }
    }

    private fun onMessage(job: Job, message: JSONObject) {
        val thumb = MeloRowArtPolicy.pickThumb(message)
        if (thumb == null) {
            fallbackToChat(job)
            return
        }
        resolveThumb(job.key, thumb)
    }

    private fun onChat(job: Job, chat: JSONObject) {
        val thumb = MeloRowArtPolicy.pickChatPhoto(chat)
        if (thumb == null) {
            fail(job.key)
            return
        }
        resolveThumb(job.key, thumb)
    }

    /** No cover on the message: use the channel's photo (one request per channel), or give up. */
    private fun fallbackToChat(job: Job) {
        val chatKey = MeloRowArtPolicy.chatKey(job.account, job.chatId)
        val have = cache.get(chatKey)
        when {
            have != null && chatKey !in provisional -> {
                cache.put(job.key, have)
                provisional.remove(job.key)
                scheduleNotify()
            }
            miss.contains(chatKey) -> miss.add(job.key)
            else -> {
                chatWaiters.getOrPut(chatKey) { mutableListOf() }.add(job.key)
                if (chatKey !in jobs) enqueue(Job(chatKey, Kind.CHAT, job.chatId, 0L, job.account, generation))
            }
        }
        finishJob(job.key)
    }

    /** [thumb] belongs to [key]: show the blur at once, then the thumbnail from disk or after its download. */
    private fun resolveThumb(key: String, thumb: MeloRowArtPolicy.Thumb) {
        val path = thumb.localPath
        val fileId = thumb.fileId
        val mini = thumb.mini
        if (mini != null && cache.get(key) == null) decodeBytes(key, mini, final = path == null && fileId == null)
        when {
            path != null -> decodeFile(key, path)
            fileId != null -> {
                fileWaiters.getOrPut(fileId) { mutableListOf() }.let { if (key !in it) it.add(key) }
                updateFileWaiters()
                val g = generation
                runCatching {
                    send("downloadFile", "rowArtFile:$g:$fileId") {
                        it.put("file_id", fileId)
                        it.put("priority", 1)
                        it.put("offset", 0)
                        it.put("limit", 0)
                        it.put("synchronous", false)
                    }
                }.onFailure {
                    fileWaiters.remove(fileId)?.forEach(::fail)
                    updateFileWaiters()
                }
            }
            mini == null -> fail(key)
        }
    }

    private fun onFile(file: JSONObject) {
        val id = file.optInt("id", 0)
        if (id <= 0) return
        val path = MeloRowArtPolicy.completedPath(file) ?: return
        val keys = fileWaiters.remove(id) ?: return
        updateFileWaiters()
        keys.forEach { decodeFile(it, path) }
    }

    private fun updateFileWaiters() {
        hasFileWaiters = fileWaiters.isNotEmpty()
    }

    private fun fail(key: String) {
        failed++
        miss.add(key)
        chatWaiters.remove(key)?.forEach { row -> if (cache.get(row) == null) miss.add(row) }
        finishJob(key)
    }

    // ---- Decoding (background thread) ------------------------------------------------------------

    private fun decodeBytes(key: String, bytes: ByteArray, final: Boolean) {
        val g = generation
        val px = targetPx
        executor.execute {
            val bmp = decodeByteArray(bytes, px)
            main.post { deliver(key, g, bmp, final) }
        }
    }

    private fun decodeFile(key: String, path: String) {
        val g = generation
        val px = targetPx
        executor.execute {
            val bmp = runCatching {
                val file = File(path)
                if (!file.isFile) null else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = MeloRowArtPolicy.decodeSample(bounds.outWidth, bounds.outHeight, px)
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        BitmapFactory.decodeFile(path, opts)?.let { scaleDown(it, px) }
                    }
                }
            }.getOrNull()
            main.post { deliver(key, g, bmp, final = true) }
        }
    }

    /** A downloaded song: its embedded picture; with none, the Telegram cover. */
    private fun decodeEmbedded(item: Item, acct: Long) {
        val key = item.key
        if (!decoding.add(key)) return
        val g = generation
        val px = targetPx
        val path = item.localFile ?: return
        executor.execute {
            val bmp = runCatching {
                val file = File(path)
                if (!file.isFile || !isOwnedFile(file)) null else {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.path)
                        retriever.embeddedPicture?.takeIf { MeloRowArtPolicy.embeddedAllowed(it.size) }?.let { decodeByteArray(it, px) }
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }.getOrNull()
            main.post {
                decoding.remove(key)
                if (!MeloRowArtPolicy.isCurrent(g, generation) || !alive()) return@post
                if (bmp != null) {
                    deliver(key, g, bmp, final = true)
                } else if (item.chatId != 0L && item.rawMessageId > 0L) {
                    enqueue(Job(key, Kind.MESSAGE, item.chatId, item.rawMessageId, acct, generation))
                    pump()
                } else {
                    miss.add(key)
                }
            }
        }
    }

    /** A song on the device: the system's own thumbnail (Android 10 and up). */
    private fun decodeDeviceThumb(item: Item) {
        val key = item.key
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            miss.add(key)
            return
        }
        if (!decoding.add(key)) return
        val g = generation
        val px = targetPx
        executor.execute {
            val bmp = runCatching {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.mediaStoreId)
                context.contentResolver.loadThumbnail(uri, Size(px, px), null)
            }.getOrNull()
            main.post {
                decoding.remove(key)
                if (!MeloRowArtPolicy.isCurrent(g, generation) || !alive()) return@post
                if (bmp != null) deliver(key, g, bmp, final = true) else miss.add(key)
            }
        }
    }

    private fun deliver(key: String, askedGeneration: Long, bmp: Bitmap?, final: Boolean) {
        if (!MeloRowArtPolicy.isCurrent(askedGeneration, generation) || !alive()) return
        if (bmp == null) {
            if (final) fail(key)
            return
        }
        decoded++
        // A blur never replaces a real picture that arrived first.
        if (!final && cache.get(key) != null) return
        cache.put(key, bmp)
        if (final) provisional.remove(key) else provisional.add(key)
        if (final) {
            chatWaiters.remove(key)?.forEach { row ->
                if (cache.get(row) == null || row in provisional) {
                    cache.put(row, bmp)
                    provisional.remove(row)
                }
            }
            finishJob(key)
        }
        scheduleNotify()
    }

    private fun decodeByteArray(bytes: ByteArray, px: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = MeloRowArtPolicy.decodeSample(bounds.outWidth, bounds.outHeight, px)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { scaleDown(it, px) }
        }
    }.getOrNull()

    /** The shorter side lands on [px]; smaller pictures stay as they are. */
    private fun scaleDown(bmp: Bitmap, px: Int): Bitmap {
        val shorter = minOf(bmp.width, bmp.height)
        if (shorter <= px) return bmp
        val scale = px.toFloat() / shorter
        val scaled = Bitmap.createScaledBitmap(
            bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true
        )
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    // ---- Telling the screen ----------------------------------------------------------------------

    /** New pictures are announced at most every 120 ms, so a burst redraws the list once. */
    private fun scheduleNotify() {
        if (notifyPosted) return
        notifyPosted = true
        main.postDelayed({
            notifyPosted = false
            if (!alive()) return@postDelayed
            android.util.Log.i(
                "MeloRowArt",
                "sent=$sent hits=$hits decoded=$decoded failed=$failed cache=${cache.size()} miss=${miss.size} " +
                    "pending=${scheduler.pendingCount} inflight=${scheduler.inFlightCount}"
            )
            listener?.invoke()
        }, NOTIFY_MS)
    }

    private companion object {
        const val NOTIFY_MS = 120L
        const val WATCHDOG_MS = 20_000L

        /** A sixteenth of the app's heap budget, between 4 MB and the policy ceiling. */
        fun cacheBytes(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val budget = (am?.memoryClass ?: 64) * 1024 * 1024 / 16
            return budget.coerceIn(4 * 1024 * 1024, MeloRowArtPolicy.CACHE_CAP_BYTES)
        }
    }
}
