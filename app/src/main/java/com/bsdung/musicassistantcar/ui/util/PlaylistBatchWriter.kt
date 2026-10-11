package com.bsdung.musicassistantcar.ui.util

/**
 * Serializes backend playlist writes with its 100-ID request limit.
 * Safe for callers to run on a background executor; no Android dependencies.
 * Stops at the first failed request instead of silently dropping the rest.
 */
object PlaylistBatchWriter {
    data class Counts(val added: Int, val skipped: Int)
    data class Outcome(
        val requested: Int,
        val added: Int,
        val skipped: Int,
        val completedBatches: Int,
        val failed: Boolean,
    )

    fun write(
        mediaIds: List<String>,
        writeBatch: (List<String>) -> Counts,
    ): Outcome {
        var added = 0
        var skipped = 0
        var completed = 0
        for (batch in mediaIds.chunked(QueuePlaylistExportPolicy.BATCH_SIZE)) {
            val result = runCatching { writeBatch(batch) }
                .getOrElse {
                    return Outcome(mediaIds.size, added, skipped, completed, failed = true)
                }
            added += result.added
            skipped += result.skipped
            completed++
        }
        return Outcome(mediaIds.size, added, skipped, completed, failed = false)
    }
}
