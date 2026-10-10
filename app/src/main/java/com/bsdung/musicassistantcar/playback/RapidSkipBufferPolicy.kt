package com.bsdung.musicassistantcar.playback

import java.util.ArrayDeque

/**
 * Bounds recovery to ONE attempt after a genuine rapid-skip buffering stall.
 * Normal streaming on slow networks is never restarted by this policy.
 */
class RapidSkipBufferPolicy(
    private val windowMs: Long = 12_000,
    private val minimumSeeks: Int = 5,
    val timeoutMs: Long = 25_000,
) {
    private val seekTimes = ArrayDeque<Long>()
    private var recoveredMediaId: String? = null

    fun recordSeek(nowMs: Long) {
        seekTimes.addLast(nowMs)
        trim(nowMs)
    }

    fun canWatch(nowMs: Long, mediaId: String?): Boolean {
        if (mediaId.isNullOrBlank() || recoveredMediaId == mediaId) return false
        trim(nowMs)
        return seekTimes.size >= minimumSeeks
    }

    fun shouldRecover(
        nowMs: Long,
        armedAtMs: Long,
        mediaId: String?,
        currentMediaId: String?,
        isBuffering: Boolean,
        playWhenReady: Boolean,
        shuttingDown: Boolean,
    ): Boolean {
        if (mediaId.isNullOrBlank() || mediaId != currentMediaId ||
            shuttingDown || !isBuffering || !playWhenReady ||
            nowMs - armedAtMs < timeoutMs
        ) return false
        return recoveredMediaId != mediaId
    }

    fun markRecovery(mediaId: String) { recoveredMediaId = mediaId }

    private fun trim(nowMs: Long) {
        while (seekTimes.isNotEmpty() &&
            (nowMs < seekTimes.first || nowMs - seekTimes.first > windowMs)
        ) seekTimes.removeFirst()
    }
}
