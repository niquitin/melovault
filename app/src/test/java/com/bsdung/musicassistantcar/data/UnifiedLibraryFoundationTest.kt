package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedLibraryFoundationTest {

    @Test
    fun testCarTrackUnifiedFieldsDefaults() {
        val track = CarTrack(
            id = 10,
            title = "Test Track",
            artist = "Test Artist",
            album = "Test Album",
            status = "ACTIVE",
            duration = 180.0,
        )
        assertNull(track.sourceBadge)
        assertNull(track.playbackAction)
        assertTrue(track.isPlayable)
        assertNull(track.unifiedId)
        assertNull(track.externalUrl)
    }

    @Test
    fun testParseTrackWithUnifiedEnrichment() {
        val payload: Map<String, Any?> = mapOf(
            "id" to 42,
            "title" to "Bài Ca Hy Vọng",
            "artist" to "Đoàn Chính",
            "album" to "Tuyển Tập",
            "status" to "ACTIVE",
            "duration" to 215.5,
            "source_type" to "LOCAL_CACHE",
            "source_badge" to "LOCAL",
            "playback_action" to "STREAM",
            "is_playable" to true,
            "unified_id" to "track:42",
            "external_url" to "https://example.com",
        )

        val client = CarApiClient()
        val track = client.parseTrack(payload)

        assertEquals(42, track.id)
        assertEquals("Bài Ca Hy Vọng", track.title)
        assertEquals("LOCAL", track.sourceBadge)
        assertEquals("STREAM", track.playbackAction)
        assertTrue(track.isPlayable)
        assertEquals("track:42", track.unifiedId)
        assertEquals("https://example.com", track.externalUrl)
    }

    @Test
    fun testParseUnifiedTrackPlayableStream() {
        val payload: Map<String, Any?> = mapOf(
            "id" to "track:100",
            "title" to "Lossless Master",
            "artist" to "Audiophile",
            "album" to "Studio 24bit",
            "duration" to 300.0,
            "primary_source" to "LOCAL_CACHE",
            "playback_action" to "STREAM",
            "is_playable" to true,
            "source_badge" to "LOCAL",
            "available_sources" to listOf("LOCAL_CACHE", "TELEGRAM", "GOOGLE_DRIVE"),
            "canonical_track_id" to 100,
            "stream_path" to "/v1/tracks/100/stream",
        )

        val client = CarApiClient()
        val unified = client.parseUnifiedTrack(payload)

        assertEquals("track:100", unified.id)
        assertEquals("STREAM", unified.playbackAction)
        assertTrue(unified.isPlayable)
        assertEquals("LOCAL", unified.sourceBadge)
        assertEquals(3, unified.availableSources.size)
        assertEquals(Integer.valueOf(100), unified.canonicalTrackId)
        assertEquals("/v1/tracks/100/stream", unified.streamPath)
        assertNull(unified.externalUrl)
    }

    @Test
    fun testParseUnifiedTrackYouTubeExternalOpenOnly() {
        val payload: Map<String, Any?> = mapOf(
            "id" to "youtube:dQw4w9WgXcQ",
            "title" to "Never Gonna Give You Up",
            "artist" to "Rick Astley",
            "album" to "YouTube Playlist",
            "duration" to 212.0,
            "primary_source" to "YOUTUBE",
            "playback_action" to "EXTERNAL_OPEN",
            "is_playable" to false,
            "source_badge" to "YT",
            "available_sources" to listOf("YOUTUBE"),
            "external_url" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )

        val client = CarApiClient()
        val unified = client.parseUnifiedTrack(payload)

        assertEquals("youtube:dQw4w9WgXcQ", unified.id)
        assertEquals("EXTERNAL_OPEN", unified.playbackAction)
        // CRITICAL: YouTube items must NEVER be marked playable in internal Media3 player
        assertFalse(unified.isPlayable)
        assertEquals("YT", unified.sourceBadge)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", unified.externalUrl)
        assertNull(unified.streamPath)
    }

    @Test
    fun testParseUnifiedTrackAppleMusicNotConnected() {
        val payload: Map<String, Any?> = mapOf(
            "id" to "apple_music:12345",
            "title" to "Bohemian Rhapsody",
            "artist" to "Queen",
            "album" to "A Night at the Opera",
            "primary_source" to "APPLE_MUSIC",
            "playback_action" to "NOT_CONNECTED",
            "is_playable" to false,
            "source_badge" to "APPLE",
            "available_sources" to listOf("APPLE_MUSIC"),
        )

        val client = CarApiClient()
        val unified = client.parseUnifiedTrack(payload)

        assertEquals("apple_music:12345", unified.id)
        assertEquals("NOT_CONNECTED", unified.playbackAction)
        assertFalse(unified.isPlayable)
        assertEquals("APPLE", unified.sourceBadge)
        assertNull(unified.streamPath)
    }
}
