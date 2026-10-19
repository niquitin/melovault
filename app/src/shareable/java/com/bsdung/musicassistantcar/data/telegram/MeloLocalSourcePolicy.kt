package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale

/**
 * Pure rules of the "Nhạc trên máy" source (docs/plans/E94-LOCAL-MUSIC.md): song identity,
 * display text, search/sort, queue window, permission decisions. No Android imports, so
 * everything here is JVM-testable. A local song is identified by its MediaStore `_ID` only;
 * nothing here is ever stored on disk and nothing is shared with the Telegram index.
 */
internal object MeloLocalSourcePolicy {
    const val MEDIA_ID_PREFIX = "local:"
    const val SOURCE_TITLE = "Nhạc trên máy"
    const val PAGE_SIZE = 200
    const val QUEUE_MAX = 500
    const val QUEUE_BEFORE = 100
    const val PREFS_NAME = "melovault_local_music"
    const val KEY_ASKED = "read_audio_asked"
    const val PERMISSION_AUDIO = "android.permission.READ_MEDIA_AUDIO"
    const val PERMISSION_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val RELOAD_AFTER_MS = 5 * 60_000L
    const val EXTRA_MIME = "local_mime"
    const val EXTRA_SIZE = "local_size"
    const val EXTRA_DURATION = "local_duration_ms"

    /** Bit 63 set, bit 62 clear: every local key is <= -2^62, never a Telegram key (>= 0). */
    private const val KEY_FLAG = Long.MIN_VALUE
    private const val ID_LIMIT = 1L shl 62
    private const val ID_MASK = ID_LIMIT - 1L

    private val UNKNOWN_ARTISTS = setOf("<unknown>", "nghệ sĩ không rõ")

    /** One song read from MediaStore; holds no Uri or Context. */
    data class LocalSong(
        val mediaStoreId: Long,
        val title: String,
        val artist: String?,
        val album: String?,
        val folder: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val mimeType: String?,
        val searchText: String,
        val alphaKey: String
    ) {
        val key: Long get() = keyOf(mediaStoreId)
    }

    // ---- Identity ------------------------------------------------------------------------

    fun keyOf(mediaStoreId: Long): Long {
        require(mediaStoreId in 1 until ID_LIMIT) { "invalid MediaStore id" }
        return KEY_FLAG or (mediaStoreId and ID_MASK)
    }

    fun isLocalKey(key: Long): Boolean = (key ushr 62) == 2L

    fun idOfKey(key: Long): Long? = if (isLocalKey(key)) (key and ID_MASK).takeIf { it > 0 } else null

    fun mediaIdOf(mediaStoreId: Long): String {
        require(mediaStoreId > 0) { "invalid MediaStore id" }
        return "$MEDIA_ID_PREFIX$mediaStoreId"
    }

    /** Only `local:<positive number>`; Telegram, device:, picked: and everything else give null. */
    fun idOfMediaId(mediaId: String?): Long? {
        if (mediaId == null || !mediaId.startsWith(MEDIA_ID_PREFIX)) return null
        val digits = mediaId.substring(MEDIA_ID_PREFIX.length)
        if (digits.isEmpty() || digits.length > 18 || !digits.all { it in '0'..'9' }) return null
        return digits.toLong().takeIf { it > 0 }
    }

    // ---- Display -------------------------------------------------------------------------

    /** Last folder of "Music/Vietnam/" -> "Vietnam"; blank when the song has no folder. */
    fun folderOf(relativePath: String?): String =
        relativePath.orEmpty().replace('\\', '/').trim('/').substringAfterLast('/').trim()

    /** Real artist, else the folder name, else null (never a "chưa biết" placeholder). */
    fun displayArtist(rawArtist: String?, relativePath: String?): String? {
        val artist = rawArtist?.trim().orEmpty()
        if (artist.isNotEmpty() && artist.lowercase(Locale.ROOT) !in UNKNOWN_ARTISTS) return artist
        return folderOf(relativePath).takeIf { it.isNotEmpty() }
    }

    fun songOf(
        id: Long,
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        relativePath: String?,
        durationMs: Long,
        sizeBytes: Long,
        mimeType: String?
    ): LocalSong? {
        if (id !in 1 until ID_LIMIT) return null
        val title = rawTitle?.trim().orEmpty().ifEmpty { "Nhạc thiết bị #$id" }
        val artist = displayArtist(rawArtist, relativePath)
        val album = rawAlbum?.trim()?.takeIf { it.isNotEmpty() }
        val folder = folderOf(relativePath)
        return LocalSong(
            mediaStoreId = id,
            title = title,
            artist = artist,
            album = album,
            folder = folder,
            durationMs = durationMs.coerceAtLeast(0L),
            sizeBytes = sizeBytes.coerceAtLeast(0L),
            mimeType = mimeType?.takeIf { it.isNotBlank() },
            searchText = MeloComponentPolicy.searchKey(listOfNotNull(title, artist, album, folder).joinToString(" ")),
            alphaKey = MeloAlphabetPolicy.keyOf(title, artist, null)
        )
    }

    // ---- Sort, search, queue --------------------------------------------------------------

    /** NEWEST = MediaStore id descending (scan order, the repository has no DATE_ADDED). */
    fun sorted(songs: List<LocalSong>, sort: SongSort): List<LocalSong> = when (sort) {
        SongSort.NEWEST -> songs.sortedByDescending { it.mediaStoreId }
        SongSort.NAME -> songs.sortedWith(compareBy<LocalSong> { it.alphaKey }.thenBy { it.mediaStoreId })
    }

    /** Accent-insensitive AND match; no query words = the whole list unchanged. */
    fun filtered(songs: List<LocalSong>, query: String?): List<LocalSong> {
        val terms = TelegramSearchPolicy.queryTerms(query)
        if (terms.isEmpty()) return songs
        return songs.filter { song -> terms.all { it in song.searchText } }
    }

    /** Indexes of the songs handed to the player: at most [max], [before] of them before [index]. */
    fun queueWindow(size: Int, index: Int, max: Int = QUEUE_MAX, before: Int = QUEUE_BEFORE): IntRange {
        if (size <= 0 || max <= 0) return IntRange.EMPTY
        if (size <= max) return 0 until size
        val at = index.coerceIn(0, size - 1)
        var start = (at - before.coerceIn(0, max - 1)).coerceAtLeast(0)
        var end = start + max - 1
        if (end > size - 1) {
            end = size - 1
            start = end - max + 1
        }
        return start..end
    }

    // ---- Permission ------------------------------------------------------------------------

    fun permissionFor(sdkInt: Int): String = if (sdkInt >= 33) PERMISSION_AUDIO else PERMISSION_STORAGE

    enum class PermissionStep { OPEN, EXPLAIN_THEN_ASK, OPEN_SETTINGS }

    /**
     * Granted -> open. Never asked or Android says explain -> our dialog, then the system one.
     * Asked before and Android will not show the box again -> send the user to Settings.
     */
    fun permissionStep(granted: Boolean, askedBefore: Boolean, showRationale: Boolean): PermissionStep = when {
        granted -> PermissionStep.OPEN
        !askedBefore || showRationale -> PermissionStep.EXPLAIN_THEN_ASK
        else -> PermissionStep.OPEN_SETTINGS
    }

    // ---- Card, note, queue rules -------------------------------------------------------------

    /** The LOCAL source card: only with permission and at least one song, never "0 bài". */
    fun cardSource(granted: Boolean, count: Int?): MeloSourceCardPolicy.Source? {
        if (!granted || count == null || count <= 0) return null
        return MeloSourceCardPolicy.Source(MeloSourceCardPolicy.Kind.LOCAL, count, null)
    }

    fun note(loading: Boolean, total: Int, shown: Int, query: String?): String? = when {
        total <= 0 && loading -> "Đang đọc nhạc trên máy…"
        total <= 0 -> "Không tìm thấy bài nhạc nào trên máy."
        shown <= 0 && TelegramSearchPolicy.queryTerms(query).isNotEmpty() -> "Không có bài khớp."
        else -> null
    }

    /** "Phát tiếp theo" only inside one source: local with local, Telegram with Telegram. */
    fun canQueueNext(currentIsLocal: Boolean, songIsLocal: Boolean): Boolean = currentIsLocal == songIsLocal

    /** "audio/flac=3 audio/mpeg=2" for logs: MIME types and counts only, no names or paths. */
    fun formatCounts(mimes: List<String?>): String {
        val counts = HashMap<String, Int>()
        for (mime in mimes) {
            val label = mime?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotEmpty() && it.length <= 40 && it.all { c -> c in 'a'..'z' || c in '0'..'9' || c in "/.+-" } }
                ?: "other"
            counts[label] = (counts[label] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .joinToString(" ") { "${it.key}=${it.value}" }
    }
}
