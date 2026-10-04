package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.media3.common.MediaItem
import java.util.Locale

data class ResumeEntry(
    val positionMs: Long,
    val updatedAtMs: Long,
)

object TrackResumePolicy {
    const val MIN_RESUME_POSITION_MS = 10_000L      // 10s
    const val MIN_REMAINING_DURATION_MS = 20_000L    // 20s
    const val MAX_ENTRIES = 500

    /**
     * Determines whether a position should be saved.
     * Position must be at least 10s into the track and have at least 20s remaining.
     */
    fun shouldSavePosition(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        if (positionMs < MIN_RESUME_POSITION_MS) return false
        return (durationMs - positionMs) >= MIN_REMAINING_DURATION_MS
    }

    /**
     * Checks if a track position is within the final 20 seconds of playback.
     */
    fun isNearCompletion(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        return (durationMs - positionMs) < MIN_REMAINING_DURATION_MS
    }

    /**
     * Computes the resume position to restore.
     * Returns valid positionMs or null if not eligible.
     */
    fun computeResumePosition(savedPositionMs: Long, durationMs: Long): Long? {
        if (durationMs <= 0L) return null
        if (savedPositionMs < MIN_RESUME_POSITION_MS) return null
        if (isNearCompletion(savedPositionMs, durationMs)) return null
        val maxAllowed = durationMs - MIN_REMAINING_DURATION_MS
        return savedPositionMs.coerceAtMost(maxAllowed)
    }

    /**
     * Formats milliseconds into "mm:ss" or "hh:mm:ss" for UI display.
     */
    fun formatDuration(positionMs: Long): String {
        val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, remMinutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}

interface ResumeStorageBackend {
    fun get(mediaId: String): String?
    fun put(mediaId: String, value: String)
    fun remove(mediaId: String)
    fun getAll(): Map<String, *>
    fun clear()
}

class SharedPrefsStorageBackend(
    private val prefs: SharedPreferences,
) : ResumeStorageBackend {
    override fun get(mediaId: String): String? = prefs.getString(mediaId, null)

    override fun put(mediaId: String, value: String) {
        prefs.edit().putString(mediaId, value).apply()
    }

    override fun remove(mediaId: String) {
        prefs.edit().remove(mediaId).apply()
    }

    override fun getAll(): Map<String, *> = prefs.all

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class InMemoryStorageBackend : ResumeStorageBackend {
    private val store = mutableMapOf<String, String>()

    override fun get(mediaId: String): String? = store[mediaId]

    override fun put(mediaId: String, value: String) {
        store[mediaId] = value
    }

    override fun remove(mediaId: String) {
        store.remove(mediaId)
    }

    override fun getAll(): Map<String, *> = HashMap(store)

    override fun clear() {
        store.clear()
    }
}

class TrackResumeStore(
    private val backend: ResumeStorageBackend,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "TrackResumeStore"
        const val PREFS_NAME = "melo_track_resume_positions"

        fun create(context: Context): TrackResumeStore {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return TrackResumeStore(SharedPrefsStorageBackend(prefs))
        }

        fun getMediaIdentity(mediaItem: MediaItem?): String {
            if (mediaItem == null) return ""
            val extras = mediaItem.mediaMetadata.extras
            val canonicalId = extras?.getString("canonical_id")
                ?: extras?.getInt("track_id")?.takeIf { it > 0 }?.let { "track:$it" }
            return canonicalId ?: mediaItem.mediaId
        }
    }

    /**
     * Saves playback position for [mediaId].
     * If near completion or < 10s: clears any existing saved entry.
     */
    fun save(mediaId: String, positionMs: Long, durationMs: Long) {
        if (mediaId.isBlank()) return

        // If track completed or within final 20s, clear any previous resume position
        if (durationMs > 0L && TrackResumePolicy.isNearCompletion(positionMs, durationMs)) {
            clear(mediaId)
            return
        }

        // If position is under 10s, treat as not meaningfully started (clear or do not save)
        if (positionMs < TrackResumePolicy.MIN_RESUME_POSITION_MS) {
            clear(mediaId)
            return
        }

        if (!TrackResumePolicy.shouldSavePosition(positionMs, durationMs)) {
            return
        }

        val now = clock()
        val value = "$positionMs,$now"
        backend.put(mediaId, value)
        pruneIfNeeded()
    }

    /**
     * Retrieves resume position for [mediaId] with given [durationMs].
     * Returns null if duration is unknown (<= 0), position < 10s, or < 20s remaining.
     * If within final 20s, also clears the stale entry.
     */
    fun get(mediaId: String, durationMs: Long): Long? {
        if (mediaId.isBlank() || durationMs <= 0L) return null

        val entry = getEntry(mediaId) ?: return null
        val validPos = TrackResumePolicy.computeResumePosition(entry.positionMs, durationMs)
        if (validPos == null) {
            if (TrackResumePolicy.isNearCompletion(entry.positionMs, durationMs)) {
                clear(mediaId)
            }
            return null
        }
        return validPos
    }

    /**
     * Clears resume position for [mediaId].
     */
    fun clear(mediaId: String) {
        if (mediaId.isBlank()) return
        backend.remove(mediaId)
    }

    /**
     * Clears all stored resume entries.
     */
    fun clearAll() {
        backend.clear()
    }

    fun getEntry(mediaId: String): ResumeEntry? {
        val raw = backend.get(mediaId) ?: return null
        return parseEntry(raw)
    }

    fun parseEntry(raw: String): ResumeEntry? {
        return runCatching {
            val parts = raw.split(",")
            if (parts.size >= 2) {
                val pos = parts[0].toLong()
                val time = parts[1].toLong()
                if (pos >= 0L) ResumeEntry(pos, time) else null
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Prunes oldest entries if total entries exceed MAX_ENTRIES.
     */
    fun pruneIfNeeded() {
        val all = backend.getAll()
        if (all.size <= TrackResumePolicy.MAX_ENTRIES) return

        val entries = all.mapNotNull { (key, value) ->
            if (value is String) {
                parseEntry(value)?.let { key to it.updatedAtMs }
            } else {
                null
            }
        }.sortedBy { it.second } // oldest first

        val removeCount = entries.size - TrackResumePolicy.MAX_ENTRIES
        if (removeCount > 0) {
            for (i in 0 until removeCount) {
                backend.remove(entries[i].first)
            }
            runCatching {
                Log.i(TAG, "Pruned $removeCount oldest resume entries, remaining: ${TrackResumePolicy.MAX_ENTRIES}")
            }
        }
    }
}
