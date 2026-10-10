package com.bsdung.musicassistantcar.playback

/**
 * Rate limits lightweight process memory diagnostics while music is actually playing.
 *
 * Sampling is non-invasive: no forced GC, cache clear, player reset or disk writes.
 * Uses elapsed realtime so changing device clock cannot cause diagnostic storms.
 */
class MemorySamplePolicy(private val intervalMs: Long = 5 * 60_000L) {
    init {
        require(intervalMs > 0L) { "Memory sampling interval must be positive" }
    }

    private var lastSampleAtMs: Long? = null

    fun shouldSample(nowElapsedMs: Long, isPlaying: Boolean): Boolean {
        if (!isPlaying || nowElapsedMs < 0L) return false
        val last = lastSampleAtMs
        if (last != null && nowElapsedMs >= last && nowElapsedMs - last < intervalMs) {
            return false
        }
        lastSampleAtMs = nowElapsedMs
        return true
    }

    fun reset() {
        lastSampleAtMs = null
    }
}
