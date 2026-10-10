package com.bsdung.musicassistantcar.playback

/**
 * Pure predicate for safe, delayed Drive metadata enrichment.
 * A network lookup can finish after dozens of Next presses. A stale replacement
 * must not churn ExoPlayer's timeline, nor replace the active item while buffering.
 */
object DriveMetadataUpdatePolicy {
    fun shouldApply(
        originalSlotStillMatches: Boolean,
        requestIndex: Int,
        currentIndex: Int,
        currentReady: Boolean,
    ): Boolean {
        if (!originalSlotStillMatches || requestIndex < 0 || currentIndex < 0) return false
        if (requestIndex != currentIndex && requestIndex != currentIndex + 1) return false
        return requestIndex != currentIndex || currentReady
    }
}
