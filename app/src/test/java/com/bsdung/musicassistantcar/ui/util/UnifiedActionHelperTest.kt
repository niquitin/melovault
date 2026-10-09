package com.bsdung.musicassistantcar.ui.util

import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedActionHelperTest {

    @Test
    fun testMapSourceLabel() {
        assertEquals("LOCAL", UnifiedActionHelper.mapSourceLabel("LOCAL_CACHE", "LOCAL"))
        assertEquals("CUSTOM", UnifiedActionHelper.mapSourceLabel("YOUTUBE", "CUSTOM"))
        assertEquals("TG", UnifiedActionHelper.mapSourceLabel("TELEGRAM", null))
        assertEquals("GDRIVE", UnifiedActionHelper.mapSourceLabel("GOOGLE_DRIVE", ""))
        assertEquals("YT", UnifiedActionHelper.mapSourceLabel("YOUTUBE", null))
        assertEquals("APPLE", UnifiedActionHelper.mapSourceLabel("APPLE_MUSIC", null))
        assertEquals("Chưa rõ", UnifiedActionHelper.mapSourceLabel(null, null))
        assertEquals("OTHER_SRC", UnifiedActionHelper.mapSourceLabel("OTHER_SRC", null))
    }

    @Test
    fun testValidExternalUrls() {
        assertTrue(UnifiedActionHelper.isValidExternalUrl("https://www.youtube.com/watch?v=12345"))
        assertTrue(UnifiedActionHelper.isValidExternalUrl("https://youtu.be/12345"))
        assertTrue(UnifiedActionHelper.isValidExternalUrl("https://music.apple.com/us/album/test/123"))
        assertTrue(UnifiedActionHelper.isValidExternalUrl("https://t.me/c/123/456"))
        assertTrue(UnifiedActionHelper.isValidExternalUrl("https://drive.google.com/file/d/abc/view"))
    }

    @Test
    fun testInvalidOrInsecureExternalUrls() {
        // HTTP instead of HTTPS (Insecure)
        assertFalse(UnifiedActionHelper.isValidExternalUrl("http://www.youtube.com/watch?v=12345"))
        // Disallowed domains (Phishing / URL injection prevention)
        assertFalse(UnifiedActionHelper.isValidExternalUrl("https://malicious-site.com/track"))
        assertFalse(UnifiedActionHelper.isValidExternalUrl("https://youtube.com.attacker.com/evil"))
        // Invalid protocols
        assertFalse(UnifiedActionHelper.isValidExternalUrl("javascript:alert(1)"))
        assertFalse(UnifiedActionHelper.isValidExternalUrl("file:///data/secret"))
        // Null or blank
        assertFalse(UnifiedActionHelper.isValidExternalUrl(null))
        assertFalse(UnifiedActionHelper.isValidExternalUrl(""))
        assertFalse(UnifiedActionHelper.isValidExternalUrl("   "))
    }

    @Test
    fun testResolveActionForPlayableInternalTrack() {
        val track = UnifiedTrackDto(
            id = "track:101",
            title = "Test Track",
            artist = "Artist",
            album = "Album",
            duration = 180.0,
            primarySource = "LOCAL_CACHE",
            playbackAction = "STREAM",
            isPlayable = true,
            sourceBadge = "LOCAL",
            availableSources = listOf("LOCAL_CACHE"),
            canonicalTrackId = 101,
        )

        val action = UnifiedActionHelper.resolveAction(track)
        assertEquals(UnifiedItemAction.PLAY_IN_LIBRARY, action)
        assertEquals("Phát trong thư viện", UnifiedActionHelper.getActionButtonText(action))
    }

    @Test
    fun testResolveActionForExternalOpenWithValidUrl() {
        val track = UnifiedTrackDto(
            id = "youtube:abc",
            title = "YouTube Video",
            artist = "Artist",
            album = "Album",
            duration = 200.0,
            primarySource = "YOUTUBE",
            playbackAction = "EXTERNAL_OPEN",
            isPlayable = false,
            sourceBadge = "YT",
            availableSources = listOf("YOUTUBE"),
            externalUrl = "https://www.youtube.com/watch?v=abc",
        )

        val action = UnifiedActionHelper.resolveAction(track)
        assertEquals(UnifiedItemAction.OPEN_EXTERNAL, action)
        assertEquals("Mở liên kết", UnifiedActionHelper.getActionButtonText(action))
    }

    @Test
    fun testResolveActionForExternalOpenWithUnsafeOrMissingUrlFallsBackToReadOnly() {
        val track = UnifiedTrackDto(
            id = "youtube:bad",
            title = "Phishing Track",
            artist = "Artist",
            album = "Album",
            duration = 200.0,
            primarySource = "YOUTUBE",
            playbackAction = "EXTERNAL_OPEN",
            isPlayable = false,
            sourceBadge = "YT",
            availableSources = listOf("YOUTUBE"),
            externalUrl = "http://badsite.com/video", // HTTP not HTTPS
        )

        val action = UnifiedActionHelper.resolveAction(track)
        assertEquals(UnifiedItemAction.READ_ONLY, action)
        assertEquals("Chỉ xem", UnifiedActionHelper.getActionButtonText(action))
    }

    @Test
    fun testResolveActionForUnplayableItemWithoutUrlIsReadOnly() {
        val track = UnifiedTrackDto(
            id = "apple:456",
            title = "Apple Music Item",
            artist = "Artist",
            album = "Album",
            duration = null,
            primarySource = "APPLE_MUSIC",
            playbackAction = "NOT_CONNECTED",
            isPlayable = false,
            sourceBadge = "APPLE",
            availableSources = listOf("APPLE_MUSIC"),
            canonicalTrackId = null,
            externalUrl = null,
        )

        val action = UnifiedActionHelper.resolveAction(track)
        assertEquals(UnifiedItemAction.READ_ONLY, action)
    }

    @Test
    fun testPaginationCalculations() {
        assertEquals(1, UnifiedActionHelper.calculateTotalPages(0, 30))
        assertEquals(1, UnifiedActionHelper.calculateTotalPages(25, 30))
        assertEquals(1, UnifiedActionHelper.calculateTotalPages(30, 30))
        assertEquals(2, UnifiedActionHelper.calculateTotalPages(31, 30))
        assertEquals(4, UnifiedActionHelper.calculateTotalPages(100, 30))

        assertEquals(1, UnifiedActionHelper.calculateCurrentPage(0, 30))
        assertEquals(2, UnifiedActionHelper.calculateCurrentPage(30, 30))
        assertEquals(3, UnifiedActionHelper.calculateCurrentPage(60, 30))
    }

    @Test
    fun testSourceFilterEnumMappings() {
        assertEquals("ALL", UnifiedSourceFilter.ALL.serverKey)
        assertEquals("Tất cả", UnifiedSourceFilter.ALL.displayLabel)
        assertEquals("LOCAL_CACHE", UnifiedSourceFilter.LOCAL_CACHE.serverKey)
        assertEquals("TELEGRAM", UnifiedSourceFilter.TELEGRAM.serverKey)
        assertEquals("GOOGLE_DRIVE", UnifiedSourceFilter.GOOGLE_DRIVE.serverKey)
        assertEquals("YOUTUBE", UnifiedSourceFilter.YOUTUBE.serverKey)
        assertEquals("APPLE_MUSIC", UnifiedSourceFilter.APPLE_MUSIC.serverKey)
    }
}
