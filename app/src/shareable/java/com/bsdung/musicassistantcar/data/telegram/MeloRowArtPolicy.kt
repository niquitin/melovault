package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

/**
 * E98: pure rules for the small cover on every song row of the teal theme. Where the picture
 * comes from (TDLib message or chat JSON), how its cache key looks, how big it is decoded, and
 * how many requests may run. The Android side is [MeloRowArtLoader]. Never logs or keeps a song,
 * channel or account name: keys are numbers only.
 */
internal object MeloRowArtPolicy {

    /** TDLib requests running at once (getMessage / getChat / downloadFile together). */
    const val MAX_IN_FLIGHT = 3
    /** Rows below the last visible one that are prepared too. */
    const val PREFETCH_AHEAD = 4
    /** A decoded cover is at most this many pixels on a side. */
    const val MAX_SIDE_PX = 192
    /** An embedded picture bigger than this is skipped (same ceiling as the Studio Deck). */
    const val MAX_EMBEDDED_BYTES = 8 * 1024 * 1024
    /** A TDLib minithumbnail is ~40px and a few hundred bytes; anything bigger is not one. */
    const val MAX_MINITHUMB_BYTES = 16 * 1024
    const val CACHE_CAP_BYTES = 12 * 1024 * 1024
    const val NEGATIVE_CAP = 4000
    /** AbsListView.OnScrollListener.SCROLL_STATE_FLING; copied so the rule stays JVM-testable. */
    const val SCROLL_STATE_FLING = 2

    /**
     * What a message or chat offers as a picture: a ~40px [mini] JPEG that is right there, and/or a
     * real thumbnail file ([fileId]; [localPath] is set only once TDLib finished downloading it).
     */
    class Thumb(
        val mini: ByteArray?,
        val fileId: Int?,
        val localPath: String?,
        val width: Int,
        val height: Int
    ) {
        val usable: Boolean get() = mini != null || fileId != null
        val downloaded: Boolean get() = localPath != null
    }

    private val ACCEPTED_FORMATS = setOf("thumbnailFormatJpeg", "thumbnailFormatPng", "thumbnailFormatWebp")

    /**
     * The cover of an audio message (`album_cover_*`) or the thumbnail of a document message.
     * Null when the message has neither, or is neither an audio nor a document.
     */
    fun pickThumb(message: JSONObject?): Thumb? {
        val content = message?.optJSONObject("content") ?: return null
        val thumb = when (content.optString("@type")) {
            "messageAudio" -> content.optJSONObject("audio")?.let {
                thumbOf(it.optJSONObject("album_cover_minithumbnail"), it.optJSONObject("album_cover_thumbnail"))
            }
            "messageDocument" -> content.optJSONObject("document")?.let {
                thumbOf(it.optJSONObject("minithumbnail"), it.optJSONObject("thumbnail"))
            }
            else -> null
        }
        return thumb?.takeIf { it.usable }
    }

    /** The small profile photo of a channel / chat (`photo.small`). */
    fun pickChatPhoto(chat: JSONObject?): Thumb? {
        val photo = chat?.optJSONObject("photo") ?: return null
        val file = photo.optJSONObject("small")
        val mini = decodeMini(photo.optJSONObject("minithumbnail"))
        val fileId = file?.optInt("id", 0)?.takeIf { it > 0 }
        val thumb = Thumb(mini, fileId, completedPath(file), 0, 0)
        return thumb.takeIf { it.usable }
    }

    private fun thumbOf(miniJson: JSONObject?, thumbJson: JSONObject?): Thumb {
        val mini = decodeMini(miniJson)
        val format = thumbJson?.optJSONObject("format")?.optString("@type").orEmpty()
        val file = thumbJson?.optJSONObject("file").takeIf { format in ACCEPTED_FORMATS }
        val fileId = file?.optInt("id", 0)?.takeIf { it > 0 }
        return Thumb(
            mini = mini,
            fileId = fileId,
            localPath = completedPath(file),
            width = thumbJson?.optInt("width", 0) ?: miniJson?.optInt("width", 0) ?: 0,
            height = thumbJson?.optInt("height", 0) ?: miniJson?.optInt("height", 0) ?: 0
        )
    }

    private fun decodeMini(json: JSONObject?): ByteArray? {
        val data = json?.optString("data").orEmpty()
        if (data.isEmpty() || data.length > MAX_MINITHUMB_BYTES * 2) return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(data) }.getOrNull() ?: return null
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_MINITHUMB_BYTES }
    }

    /** Local path of a TDLib `file` object, only when it is completely downloaded. */
    fun completedPath(file: JSONObject?): String? {
        val local = file?.optJSONObject("local") ?: return null
        if (!local.optBoolean("is_downloading_completed", false)) return null
        return local.optString("path").takeIf { it.isNotBlank() }
    }

    /** The `file` object inside an `updateFile` or a `file` reply, or null. */
    fun fileOf(event: JSONObject): JSONObject? = when (event.optString("@type")) {
        "updateFile" -> event.optJSONObject("file")
        "file" -> event
        else -> null
    }

    // ---- Keys ----------------------------------------------------------------------------------

    /** A Telegram song's cover. The account is part of the key so another account never sees it. */
    fun telegramKey(accountId: Long, chatId: Long, rawMessageId: Long): String = "tg:$accountId:$chatId:$rawMessageId"

    fun localKey(mediaStoreId: Long): String = "local:$mediaStoreId"

    /** A channel's profile photo, shared by its songs that have no cover of their own. */
    fun chatKey(accountId: Long, chatId: Long): String = "chat:$accountId:$chatId"

    // ---- Decoding ------------------------------------------------------------------------------

    /**
     * Largest power-of-two `inSampleSize` that keeps the shorter side at or above [targetPx]
     * (2000x2000 -> 192 gives 8). Sizes are the picture's real width and height, never its byte
     * count; an unknown or invalid size gives 1.
     */
    fun decodeSample(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        val shorter = minOf(width, height)
        var sample = 1
        while (shorter / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** Pixel size of a cover for a row cover of [rowArtPx]: never above [MAX_SIDE_PX]. */
    fun targetPx(rowArtPx: Int): Int = rowArtPx.coerceIn(1, MAX_SIDE_PX)

    fun embeddedAllowed(sizeBytes: Int): Boolean = sizeBytes in 1..MAX_EMBEDDED_BYTES

    // ---- What to load and when -----------------------------------------------------------------

    /** No loading while the list is being flung; the covers come once it slows down. */
    fun shouldLoad(scrollState: Int): Boolean = scrollState != SCROLL_STATE_FLING

    /**
     * Rows to prepare: the visible ones and [ahead] more, clamped to the list. Empty when nothing
     * is visible or the list is empty.
     */
    fun rowsToLoad(first: Int, visibleCount: Int, total: Int, ahead: Int = PREFETCH_AHEAD): IntRange {
        if (total <= 0 || visibleCount <= 0 || first < 0 || first >= total) return IntRange.EMPTY
        return first..minOf(total - 1, first + visibleCount - 1 + ahead)
    }

    /** A reply from TDLib or a decoder still counts only for the generation that asked. */
    fun isCurrent(askedGeneration: Long, currentGeneration: Long): Boolean = askedGeneration == currentGeneration

    /**
     * Request book-keeping: newest request first (the row that just came on screen wins), at most
     * [MAX_IN_FLIGHT] running, anything no longer wanted is dropped before it is sent.
     */
    class Scheduler(private val maxInFlight: Int = MAX_IN_FLIGHT) {
        private val pending = ArrayDeque<String>()
        private val inFlight = HashSet<String>()

        val pendingCount: Int get() = pending.size
        val inFlightCount: Int get() = inFlight.size

        /** False when [key] is already waiting or running. A key asked again moves to the front. */
        fun request(key: String): Boolean {
            if (key in inFlight) return false
            val known = pending.remove(key)
            pending.addLast(key)
            return !known
        }

        /** Drops waiting requests that are not in [wanted] (rows that left the screen). Running ones finish. */
        fun retain(wanted: Set<String>) {
            pending.removeAll { it !in wanted }
        }

        /** The next request to send, newest first, or null when none waits or the limit is reached. */
        fun next(): String? {
            if (inFlight.size >= maxInFlight) return null
            val key = pending.removeLastOrNull() ?: return null
            inFlight.add(key)
            return key
        }

        fun done(key: String) {
            inFlight.remove(key)
        }

        fun clear() {
            pending.clear()
            inFlight.clear()
        }
    }

    /** Keys known to have no picture, so they are not asked again; the oldest are forgotten past [cap]. */
    class MissCache(private val cap: Int = NEGATIVE_CAP) {
        private val keys = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > cap
        }

        val size: Int get() = keys.size
        fun add(key: String) { keys[key] = true }
        fun contains(key: String): Boolean = keys.containsKey(key)
        fun clear() = keys.clear()
    }
}
