package com.bsdung.musicassistantcar.ui.util

import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.UnifiedTrackDto

/**
 * Maps an explicitly playable Unified Library item to the canonical MeloVault
 * track used by the existing Media3/queue pipeline. Never trust externalUrl or
 * streamPath for playback; the regular authenticated CarApiClient builds the URI.
 */
object UnifiedPlaybackMapper {
    fun playableTrack(item: UnifiedTrackDto): CarTrack? {
        if (UnifiedActionHelper.resolveAction(item) != UnifiedItemAction.PLAY_IN_LIBRARY) return null
        val trackId = item.canonicalTrackId?.takeIf { it > 0 } ?: return null
        // No fuzzy title matching or external-provider IDs: require a canonical MeloVault item.
        if (item.id != "track:$trackId") return null
        return CarTrack(
            id = trackId,
            title = item.title,
            artist = item.artist,
            album = item.album,
            status = "ACTIVE",
            duration = item.duration?.takeIf { it.isFinite() && it > 0.0 },
            sourceType = item.primarySource,
            sourceBadge = item.sourceBadge,
            playbackAction = item.playbackAction,
            isPlayable = true,
            unifiedId = item.id,
        )
    }
}
