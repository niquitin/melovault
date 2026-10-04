package com.bsdung.musicassistantcar.playback

/**
 * Tracks playback duration for the currently playing track and triggers history reporting
 * once the track has accumulated at least [REQUIRED_PLAYBACK_MS] (10 seconds) of actual playback.
 *
 * Rules:
 * - Only genuine playback time counts (pauses pause accumulation).
 * - Rapid skips (<10 sec) do NOT get recorded.
 * - Only reports once per track playback occurrence (no spam on pause/resume/rotation).
 * - Moving to a new track resets accumulation.
 */
class PlayHistoryTracker(
    private val onEligibleForHistory: (String) -> Unit,
) {
    companion object {
        const val REQUIRED_PLAYBACK_MS = 10_000L
    }

    var currentMediaId: String? = null
        private set

    private var playingSinceRealtime: Long = 0L
    private var accumulatedPlayingMs: Long = 0L
    private var reportedForCurrentOccurrence: Boolean = false

    fun onMediaItemTransition(mediaId: String?, isPlaying: Boolean, nowRealtimeMs: Long) {
        if (currentMediaId != mediaId) {
            currentMediaId = mediaId
            accumulatedPlayingMs = 0L
            reportedForCurrentOccurrence = false
            playingSinceRealtime = if (isPlaying && mediaId != null) nowRealtimeMs else 0L
        } else {
            // Same mediaId (e.g. metadata refresh / replaceMediaItem) -> do not reset!
            if (isPlaying && playingSinceRealtime == 0L) {
                playingSinceRealtime = nowRealtimeMs
            }
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean, nowRealtimeMs: Long) {
        val mediaId = currentMediaId ?: return
        if (isPlaying) {
            if (playingSinceRealtime == 0L) {
                playingSinceRealtime = nowRealtimeMs
            }
        } else {
            if (playingSinceRealtime > 0L) {
                accumulatedPlayingMs += (nowRealtimeMs - playingSinceRealtime).coerceAtLeast(0L)
                playingSinceRealtime = 0L
            }
        }
        checkEligibility(mediaId, isPlaying, nowRealtimeMs)
    }

    fun onPeriodicTick(isPlaying: Boolean, nowRealtimeMs: Long) {
        val mediaId = currentMediaId ?: return
        checkEligibility(mediaId, isPlaying, nowRealtimeMs)
    }

    fun getAccumulatedMs(isPlaying: Boolean, nowRealtimeMs: Long): Long {
        var total = accumulatedPlayingMs
        if (isPlaying && playingSinceRealtime > 0L) {
            total += (nowRealtimeMs - playingSinceRealtime).coerceAtLeast(0L)
        }
        return total
    }

    fun isReported(): Boolean = reportedForCurrentOccurrence

    private fun checkEligibility(mediaId: String, isPlaying: Boolean, nowRealtimeMs: Long) {
        if (reportedForCurrentOccurrence) return
        val totalMs = getAccumulatedMs(isPlaying, nowRealtimeMs)
        if (totalMs >= REQUIRED_PLAYBACK_MS) {
            reportedForCurrentOccurrence = true
            onEligibleForHistory(mediaId)
        }
    }
}
