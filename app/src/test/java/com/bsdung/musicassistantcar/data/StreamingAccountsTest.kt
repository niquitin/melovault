package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAccountsTest {

    @Test
    fun testProviderFromId() {
        assertEquals(AccountProvider.APPLE_MUSIC, AccountProvider.fromId("apple_music"))
        assertEquals(AccountProvider.APPLE_MUSIC, AccountProvider.fromId("APPLE_MUSIC"))
        assertEquals(AccountProvider.YOUTUBE, AccountProvider.fromId("youtube"))
        assertEquals(AccountProvider.YOUTUBE, AccountProvider.fromId("YouTube"))
        assertNull(AccountProvider.fromId("spotify"))
    }

    @Test
    fun testConnectionStateFromWire() {
        assertEquals(AccountConnectionState.NOT_CONFIGURED, AccountConnectionState.fromWire("NOT_CONFIGURED"))
        assertEquals(AccountConnectionState.NOT_CONNECTED, AccountConnectionState.fromWire("NOT_CONNECTED"))
        assertEquals(AccountConnectionState.CONNECTING, AccountConnectionState.fromWire("CONNECTING"))
        assertEquals(AccountConnectionState.CONNECTED, AccountConnectionState.fromWire("CONNECTED"))
        assertEquals(AccountConnectionState.TOKEN_EXPIRED, AccountConnectionState.fromWire("TOKEN_EXPIRED"))
        assertEquals(AccountConnectionState.REAUTH_REQUIRED, AccountConnectionState.fromWire("REAUTH_REQUIRED"))
        assertEquals(AccountConnectionState.ERROR, AccountConnectionState.fromWire("ERROR"))
        assertEquals(AccountConnectionState.VERIFICATION_UNAVAILABLE, AccountConnectionState.fromWire("VERIFICATION_UNAVAILABLE"))
        assertEquals(AccountConnectionState.ERROR, AccountConnectionState.fromWire("UNKNOWN_STATE"))
    }

    @Test
    fun testVietnameseLabels() {
        assertEquals("Chưa cấu hình", AccountConnectionState.NOT_CONFIGURED.vietnameseLabel)
        assertEquals("Chưa kết nối", AccountConnectionState.NOT_CONNECTED.vietnameseLabel)
        assertEquals("Đang kết nối", AccountConnectionState.CONNECTING.vietnameseLabel)
        assertEquals("Đã kết nối", AccountConnectionState.CONNECTED.vietnameseLabel)
        assertEquals("Token hết hạn", AccountConnectionState.TOKEN_EXPIRED.vietnameseLabel)
        assertEquals("Cần ủy quyền lại", AccountConnectionState.REAUTH_REQUIRED.vietnameseLabel)
        assertEquals("Lỗi kết nối", AccountConnectionState.ERROR.vietnameseLabel)
        assertEquals("Chưa thể xác thực", AccountConnectionState.VERIFICATION_UNAVAILABLE.vietnameseLabel)
    }

    @Test
    fun testProviderCapabilitiesModel() {
        val caps = ProviderCapabilities(
            accountAuthorizationSupported = true,
            catalogSearchAvailable = true,
            personalLibraryAvailable = false,
            playlistReadAvailable = true,
            playlistWriteAvailable = false,
            playbackAvailable = false,
            offlineDownloadPermitted = false,
        )
        assertTrue(caps.accountAuthorizationSupported)
        assertTrue(caps.catalogSearchAvailable)
        assertFalse(caps.personalLibraryAvailable)
        assertTrue(caps.playlistReadAvailable)
        assertFalse(caps.playlistWriteAvailable)
        assertFalse(caps.playbackAvailable)
        assertFalse(caps.offlineDownloadPermitted)
    }

    @Test
    fun testAccountStatusDtoModel() {
        val caps = ProviderCapabilities(
            accountAuthorizationSupported = true,
            catalogSearchAvailable = false,
            personalLibraryAvailable = false,
            playlistReadAvailable = false,
            playlistWriteAvailable = false,
            playbackAvailable = false,
            offlineDownloadPermitted = false,
        )
        val dto = AccountStatusDto(
            provider = "apple_music",
            state = AccountConnectionState.NOT_CONFIGURED,
            displayLabel = "Apple Music",
            capabilities = caps,
            lastVerifiedAt = "2026-10-08T00:00:00Z",
            errorCode = null,
            errorMessage = "Chưa cấu hình Apple Developer Token",
        )
        assertEquals("apple_music", dto.provider)
        assertEquals(AccountConnectionState.NOT_CONFIGURED, dto.state)
        assertEquals("Apple Music", dto.displayLabel)
        assertEquals("2026-10-08T00:00:00Z", dto.lastVerifiedAt)
        assertNull(dto.errorCode)
        assertEquals("Chưa cấu hình Apple Developer Token", dto.errorMessage)
        assertFalse(dto.capabilities.playbackAvailable)
    }

    @Test
    fun testExternalAccountsDoNotPolluteRootCatalog() {
        val catalog = RemoteCatalog()
        val rootItems = catalog.rootChildren()
        // Must maintain exactly 7 standard categories
        assertEquals(7, rootItems.size)
        val ids = rootItems.map { it.mediaId }
        assertFalse(ids.contains("apple_music"))
        assertFalse(ids.contains("youtube"))
        assertFalse(ids.contains("category:streaming_accounts"))
    }

    @Test
    fun testAccountStatusConnectedState() {
        val connectedCaps = ProviderCapabilities(
            accountAuthorizationSupported = true,
            catalogSearchAvailable = true,
            personalLibraryAvailable = true,
            playlistReadAvailable = true,
            playlistWriteAvailable = false,
            playbackAvailable = false,
            offlineDownloadPermitted = false,
        )
        val connectedDto = AccountStatusDto(
            provider = "apple_music",
            state = AccountConnectionState.CONNECTED,
            displayLabel = "Apple Music",
            capabilities = connectedCaps,
            lastVerifiedAt = "2026-10-08T00:00:00Z",
            errorCode = null,
            errorMessage = null,
        )
        assertEquals(AccountConnectionState.CONNECTED, connectedDto.state)
        assertTrue(connectedDto.capabilities.catalogSearchAvailable)
        assertFalse(connectedDto.capabilities.playbackAvailable)
    }

    @Test
    fun testYouTubePlaylistDtoModel() {
        val playlist = YouTubePlaylistDto(
            id = "PL1234567890",
            title = "My Rock Playlist",
            description = "Best tracks",
            itemCount = 42,
            privacyStatus = "public",
            thumbnailUrl = "https://i.ytimg.com/vi/xyz/default.jpg",
            uri = "youtube:playlist:PL1234567890",
        )
        assertEquals("PL1234567890", playlist.id)
        assertEquals("My Rock Playlist", playlist.title)
        assertEquals(42, playlist.itemCount)
        assertEquals("public", playlist.privacyStatus)
        assertEquals("https://i.ytimg.com/vi/xyz/default.jpg", playlist.thumbnailUrl)
    }

    @Test
    fun testYouTubePlaylistItemDtoModel() {
        val item1 = YouTubePlaylistItemDto(
            id = "item_1",
            playlistId = "PL1234567890",
            videoId = "vid_abc",
            title = "Bohemian Rhapsody",
            position = 0,
            channelTitle = "Queen Official",
            isAvailable = true,
        )
        val item2 = YouTubePlaylistItemDto(
            id = "item_2",
            playlistId = "PL1234567890",
            videoId = null,
            title = "Deleted video",
            position = 1,
            channelTitle = null,
            isAvailable = false,
        )
        assertEquals(0, item1.position)
        assertTrue(item1.isAvailable)
        assertEquals("vid_abc", item1.videoId)
        assertEquals("Queen Official", item1.channelTitle)

        assertEquals(1, item2.position)
        assertFalse(item2.isAvailable)
        assertNull(item2.videoId)
    }
}
