package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MetadataDisplayHelperTest {

    // ==========================================
    // A. Raw filename display cleanup for list rows
    // ==========================================

    @Test
    fun testCleanTitleWithLeadingTrackNumbersAndUnderscores() {
        // Core filename cleanup cases
        assertEquals(
            "Host Nguyễn Khang Có Thứ Tình Yêu",
            MetadataDisplayHelper.cleanTitle("02_Host_Nguyễn_Khang_Có_Thứ_Tình_Yêu")
        )
        assertEquals(
            "Host Nguyễn Khang - Bản Tình Ca Chua Xót",
            MetadataDisplayHelper.cleanTitle("01 - Host Nguyễn Khang - Bản Tình Ca Chua Xót")
        )
        assertEquals(
            "Tình Đơn Phương",
            MetadataDisplayHelper.cleanTitle("04. Tình Đơn Phương.mp3")
        )
        assertEquals(
            "Track Name",
            MetadataDisplayHelper.cleanTitle("12_Track_Name.flac")
        )
        assertEquals(
            "Bản Tình Ca Chua Xót",
            MetadataDisplayHelper.cleanTitle("Bản Tình Ca Chua Xót")
        )
    }

    @Test
    fun testCleanTitleRawFileWithExtension() {
        // Task 10A: filename from fast driveBrowse (no tags) should clean for list display
        // "02_Host_Nguyễn_Khang_Có_Thứ_Tình_Yêu.flac" → "Host Nguyễn Khang Có Thứ Tình Yêu"
        assertEquals(
            "Host Nguyễn Khang Có Thứ Tình Yêu",
            MetadataDisplayHelper.cleanTitle("02_Host_Nguyễn_Khang_Có_Thứ_Tình_Yêu.flac")
        )
        assertEquals(
            "Host Nguyễn Khang Có Thứ Tình Yêu",
            MetadataDisplayHelper.cleanTitle("02_Host_Nguyễn_Khang_Có_Thứ_Tình_Yêu.mp3")
        )
    }

    // ==========================================
    // B. Authoritative semantic metadata — must be preserved exactly
    // ==========================================

    @Test
    fun testAuthoritativeTitleIsPreservedWithoutManglingWhenClean() {
        // Task 10B: authoritative tags from detail endpoint must pass through without corruption
        // "Bản Tình Ca Chua Xót" has no leading track number, no extension → stays intact
        assertEquals(
            "Bản Tình Ca Chua Xót",
            MetadataDisplayHelper.cleanTitle("Bản Tình Ca Chua Xót")
        )
        // Artist "Host Nguyễn Khang" is a real artist name and must never be hidden
        assertEquals(
            "Host Nguyễn Khang",
            MetadataDisplayHelper.cleanArtist("Host Nguyễn Khang")
        )
        // Album must be preserved
        assertEquals(
            "The Khang Show Remix",
            MetadataDisplayHelper.cleanAlbum("The Khang Show Remix")
        )
    }

    @Test
    fun testAuthoritativeMetadataNotMangledIntoCombinedTitle() {
        // Task 10B: The regression case.
        // Authoritative title "Bản Tình Ca Chua Xót" must NOT become the combined filename form.
        // cleanTitle must NOT join artist+title into one string.
        val authoritativeTitle = "Bản Tình Ca Chua Xót"
        val authoritativeArtist = "Host Nguyễn Khang"
        val authoritativeAlbum = "The Khang Show Remix"

        val cleanedTitle = MetadataDisplayHelper.cleanTitle(authoritativeTitle)
        val cleanedArtist = MetadataDisplayHelper.cleanArtist(authoritativeArtist)
        val cleanedAlbum = MetadataDisplayHelper.cleanAlbum(authoritativeAlbum)

        assertEquals(authoritativeTitle, cleanedTitle)
        assertEquals(authoritativeArtist, cleanedArtist)
        assertEquals(authoritativeAlbum, cleanedAlbum)

        // Confirm cleanTitle does NOT produce the combined regression value
        assertNotEquals(
            "Host Nguyễn Khang - Bản Tình Ca Chua Xót",
            cleanedTitle
        )
        assertNotEquals("", cleanedArtist)
        assertNotEquals("", cleanedAlbum)
    }

    // ==========================================
    // C. Junk artist / artist fallback filtering
    // ==========================================

    @Test
    fun testCleanArtistRemovesJunkAndUnknown() {
        assertEquals("", MetadataDisplayHelper.cleanArtist(null))
        assertEquals("", MetadataDisplayHelper.cleanArtist(""))
        assertEquals("", MetadataDisplayHelper.cleanArtist("01"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("08"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("10"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("Nghệ sĩ không rõ"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("nghệ sĩ không rõ"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("Unknown Artist"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("Unknown"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("Chưa xác định"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("null"))
        assertEquals("", MetadataDisplayHelper.cleanArtist("None"))
        assertEquals("Host Nguyễn Khang", MetadataDisplayHelper.cleanArtist("Host Nguyễn Khang"))
    }

    @Test
    fun testCleanAlbumFiltersJunk() {
        assertEquals("", MetadataDisplayHelper.cleanAlbum(null))
        assertEquals("", MetadataDisplayHelper.cleanAlbum(""))
        assertEquals("", MetadataDisplayHelper.cleanAlbum("null"))
        assertEquals("", MetadataDisplayHelper.cleanAlbum("none"))
        assertEquals("", MetadataDisplayHelper.cleanAlbum("Unknown Album"))
        assertEquals("", MetadataDisplayHelper.cleanAlbum("Unknown"))
        assertEquals("", MetadataDisplayHelper.cleanAlbum("Chưa xác định"))
        assertEquals("The Khang Show Remix", MetadataDisplayHelper.cleanAlbum("The Khang Show Remix"))
        assertEquals("Greatest Hits", MetadataDisplayHelper.cleanAlbum("Greatest Hits"))
    }

    // ==========================================
    // D. Playlist emoji cleanup
    // ==========================================

    @Test
    fun testCleanPlaylistNameStripsLeadingEmojis() {
        assertEquals("Favorites", MetadataDisplayHelper.cleanPlaylistName("❤️ Favorites"))
        assertEquals("TikTok Favorites", MetadataDisplayHelper.cleanPlaylistName("🎵 TikTok Favorites"))
        assertEquals("Driving", MetadataDisplayHelper.cleanPlaylistName("🚗 Driving"))
        assertEquals("Chill", MetadataDisplayHelper.cleanPlaylistName("🌙 Chill"))
        assertEquals("Workout", MetadataDisplayHelper.cleanPlaylistName("🏃 Workout"))
        assertEquals("My Playlist", MetadataDisplayHelper.cleanPlaylistName("My Playlist"))
    }
}
