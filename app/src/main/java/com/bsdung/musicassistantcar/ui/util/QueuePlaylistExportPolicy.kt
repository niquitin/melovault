package com.bsdung.musicassistantcar.ui.util

/**
 * A queue snapshot can contain currently playing, previously played, and upcoming
 * Media3 items. Playlist storage is server-side and accepts canonical tracks
 * and Drive-file references; device-only files are intentionally not exported.
 *
 * The backend accepts <=100 media IDs per request. Never silently truncate a
 * long queue: chunk the complete snapshot or refuse extremely large exports.
 */
sealed class QueuePlaylistExport {
    object Empty : QueuePlaylistExport()
    data class TooLarge(val queueSize: Int, val limit: Int) : QueuePlaylistExport()
    data class Ready(
        val mediaIds: List<String>,
        val sourceCount: Int,
        val ignoredUnsupported: Int,
        val ignoredDuplicates: Int,
    ) : QueuePlaylistExport() {
        val batches: List<List<String>> get() = mediaIds.chunked(QueuePlaylistExportPolicy.BATCH_SIZE)
    }
}

object QueuePlaylistExportPolicy {
    const val BATCH_SIZE = 100
    const val MAX_QUEUE_ITEMS = 2000

    private fun supported(id: String): Boolean {
        val number = when {
            id.startsWith("track:") -> id.removePrefix("track:")
            id.startsWith("drive-file:") -> id.removePrefix("drive-file:")
            else -> return false
        }
        return number.isNotEmpty() &&
            number.all { it in '0'..'9' } &&
            number.any { it != '0' }
    }

    fun plan(queueMediaIds: List<String>): QueuePlaylistExport {
        if (queueMediaIds.isEmpty()) return QueuePlaylistExport.Empty
        if (queueMediaIds.size > MAX_QUEUE_ITEMS) {
            return QueuePlaylistExport.TooLarge(queueMediaIds.size, MAX_QUEUE_ITEMS)
        }
        val unique = LinkedHashSet<String>()
        var unsupported = 0
        var duplicates = 0
        queueMediaIds.forEach { raw ->
            val id = raw.trim()
            if (!supported(id)) {
                unsupported++
            } else if (!unique.add(id)) {
                duplicates++
            }
        }
        if (unique.isEmpty()) return QueuePlaylistExport.Empty
        return QueuePlaylistExport.Ready(unique.toList(), queueMediaIds.size, unsupported, duplicates)
    }
}
