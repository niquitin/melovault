package com.bsdung.musicassistantcar.ui.util

import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import org.junit.Assert.*
import org.junit.Test

class UnifiedPlaybackMapperTest {
    private fun track(
        id: String = "track:11200",
        source: String = "LOCAL_CACHE",
        action: String = "STREAM",
        playable: Boolean = true,
        canonicalId: Int? = 11200,
        duration: Double? = 210.0,
    ) = UnifiedTrackDto(
        id = id,
        title = "Duyên Kiếp Hoa Ngàn Mây",
        artist = "Ca sĩ",
        album = "Album",
        duration = duration,
        primarySource = source,
        playbackAction = action,
        isPlayable = playable,
        sourceBadge = "LOCAL",
        availableSources = listOf("LOCAL_CACHE", "TELEGRAM"),
        canonicalTrackId = canonicalId,
        externalUrl = "https://www.youtube.com/watch?v=abc",
        streamPath = "/v1/tracks/11200/stream",
    )

    @Test fun permitsCanonicalLibraryStreamFromOwnedSources() {
        for (source in listOf("LOCAL_CACHE", "TELEGRAM", "GOOGLE_DRIVE")) {
            val result = UnifiedPlaybackMapper.playableTrack(track(source = source))
            assertNotNull(source, result)
            assertEquals(11200, result!!.id)
            assertEquals("Duyên Kiếp Hoa Ngàn Mây", result.title)
            assertEquals("STREAM", result.playbackAction)
            assertEquals(source, result.sourceType)
            assertEquals(210.0, result.duration!!, 0.001)
            assertNull("external URL must never be forwarded into Media3", result.externalUrl)
        }
    }

    @Test fun rejectsExternalProvidersAndRestrictedCapabilities() {
        assertNull(UnifiedPlaybackMapper.playableTrack(track(id = "youtube:abc", source = "YOUTUBE", action = "EXTERNAL_OPEN", playable = false)))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(source = "APPLE_MUSIC", action = "NOT_CONNECTED", playable = false)))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(source = "TELEGRAM", action = "DISABLED")))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(source = "TELEGRAM", playable = false)))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(source = "YOUTUBE")))
    }

    @Test fun rejectsSpoofedOrInvalidCanonicalIdentity() {
        assertNull(UnifiedPlaybackMapper.playableTrack(track(id = "track:11199")))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(id = "drive-file:11200")))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(canonicalId = null)))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(canonicalId = -1)))
        assertNull(UnifiedPlaybackMapper.playableTrack(track(canonicalId = 0)))
    }

    @Test fun invalidDurationCannotLeakToMediaMetadata() {
        assertNull(UnifiedPlaybackMapper.playableTrack(track(duration = Double.POSITIVE_INFINITY))!!.duration)
        assertNull(UnifiedPlaybackMapper.playableTrack(track(duration = -1.0))!!.duration)
    }
}
