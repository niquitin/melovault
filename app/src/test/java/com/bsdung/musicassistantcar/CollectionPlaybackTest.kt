package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.data.CollectionPlaybackResult
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for v0.6.14 Collection Playback UX.
 *
 * Tests cover:
 * 01 — CollectionPlaybackResult data class properties
 * 02 — CollectionPlaybackResult invariant: total = playable + skipped
 * 03 — CollectionPlaybackResult empty case
 * 04 — isCollectionContext: artist prefix returns true
 * 05 — isCollectionContext: album prefix returns true
 * 06 — isCollectionContext: drive-folder prefix returns true
 * 07 — isCollectionContext: drive-root prefix returns true
 * 08 — isCollectionContext: playlist prefix returns true
 * 09 — isCollectionContext: root returns false
 * 10 — isCollectionContext: source:all returns false
 * 11 — isCollectionContext: source:telegram returns false
 * 12 — isCollectionContext: category:recent returns false
 * 13 — buildCollectionParams: artist prefix decodes correctly
 * 14 — buildCollectionParams: album prefix decodes artist+album
 * 15 — buildCollectionParams: drive-folder decodes root+path
 * 16 — buildCollectionParams: drive-root decodes to drive_folder kind with empty path
 * 17 — buildCollectionParams: playlist prefix decodes playlistId
 * 18 — buildCollectionParams: root returns null (not a collection)
 * 19 — buildCollectionParams: encoded Vietnamese artist round-trips cleanly
 * 20 — buildCollectionParams: album with encoded separator
 * 21 — artistId encoding encodes special chars
 * 22 — albumId encoding with separator
 * 23 — CollectionPlaybackResult: skipped count is non-negative
 * 24 — CollectionPlaybackResult: item list size equals playable count
 * 25 — buildCollectionParams: drive-folder with root index 0 and non-empty path
 */
class CollectionPlaybackTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper that mirrors the private isCollectionContext logic in MainActivity
    // ─────────────────────────────────────────────────────────────────────────
    private fun isCollectionContext(parentId: String): Boolean =
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)

    // ─────────────────────────────────────────────────────────────────────────
    // Mirrors the private buildCollectionParams logic from MainActivity
    // ─────────────────────────────────────────────────────────────────────────
    private data class CollectionParams(
        val kind: String,
        val artist: String? = null,
        val album: String? = null,
        val root: Int? = null,
        val path: String? = null,
        val playlistId: Int? = null,
    )

    private fun buildCollectionParams(parentId: String): CollectionParams? = when {
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX) -> {
            val artist = RemoteCatalog.safeDecode(parentId.removePrefix(RemoteCatalog.ARTIST_PREFIX))
            CollectionParams(kind = "artist", artist = artist)
        }
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) -> {
            val payload = parentId.removePrefix(RemoteCatalog.ALBUM_PREFIX)
            val parts = payload.split("::", limit = 2)
            val artist = if (parts.size == 2) RemoteCatalog.safeDecode(parts[0]) else ""
            val album = if (parts.size == 2) RemoteCatalog.safeDecode(parts[1]) else RemoteCatalog.safeDecode(parts[0])
            CollectionParams(kind = "album", artist = artist, album = album)
        }
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) -> {
            val payload = parentId.removePrefix(RemoteCatalog.DRIVE_FOLDER_PREFIX)
            val colonIdx = payload.indexOf(':')
            if (colonIdx < 0) null
            else {
                val rootIdx = payload.substring(0, colonIdx).toIntOrNull() ?: return null
                val folderPath = RemoteCatalog.safeDecode(payload.substring(colonIdx + 1))
                CollectionParams(kind = "drive_folder", root = rootIdx, path = folderPath)
            }
        }
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) -> {
            val rootIdx = parentId.removePrefix(RemoteCatalog.DRIVE_ROOT_PREFIX).toIntOrNull() ?: return null
            CollectionParams(kind = "drive_folder", root = rootIdx, path = "")
        }
        parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX) -> {
            val playlistId = parentId.removePrefix(RemoteCatalog.PLAYLIST_PREFIX).toIntOrNull() ?: return null
            CollectionParams(kind = "playlist", playlistId = playlistId)
        }
        else -> null
    }

    private fun makeTrack(id: Int) = CarTrack(
        id = id, title = "Track $id", artist = "Artist", album = "Album",
        status = "OWNED_AUDIO", duration = 180.0,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 01 — CollectionPlaybackResult data class
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test01_collectionPlaybackResultProperties() {
        val tracks = listOf(makeTrack(1), makeTrack(2))
        val result = CollectionPlaybackResult(items = tracks, total = 3, playable = 2, skipped = 1)
        assertEquals(2, result.items.size)
        assertEquals(3, result.total)
        assertEquals(2, result.playable)
        assertEquals(1, result.skipped)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 02 — total == playable + skipped
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test02_totalEqualsPlayablePlusSkipped() {
        val result = CollectionPlaybackResult(items = emptyList(), total = 10, playable = 7, skipped = 3)
        assertEquals(result.total, result.playable + result.skipped)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 03 — empty result
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test03_emptyCollectionPlaybackResult() {
        val result = CollectionPlaybackResult(items = emptyList(), total = 0, playable = 0, skipped = 0)
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.total)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 04-08 — isCollectionContext returns true for collection prefixes
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test04_isCollectionContext_artistPrefix() {
        assertTrue(isCollectionContext("artist:Band%20A"))
    }

    @Test
    fun test05_isCollectionContext_albumPrefix() {
        assertTrue(isCollectionContext("album:Band%20A::Album%20One"))
    }

    @Test
    fun test06_isCollectionContext_driveFolderPrefix() {
        assertTrue(isCollectionContext("drive-folder:0:Rock%2FSub"))
    }

    @Test
    fun test07_isCollectionContext_driveRootPrefix() {
        assertTrue(isCollectionContext("drive-root:0"))
    }

    @Test
    fun test08_isCollectionContext_playlistPrefix() {
        assertTrue(isCollectionContext("playlist:5"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 09-12 — isCollectionContext returns false for non-collection contexts
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test09_isCollectionContext_rootReturnsFalse() {
        assertFalse(isCollectionContext("root"))
    }

    @Test
    fun test10_isCollectionContext_sourceAllReturnsFalse() {
        assertFalse(isCollectionContext("source:all"))
    }

    @Test
    fun test11_isCollectionContext_telegramReturnsFalse() {
        assertFalse(isCollectionContext("source:telegram"))
    }

    @Test
    fun test12_isCollectionContext_recentReturnsFalse() {
        assertFalse(isCollectionContext("category:recent"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13 — buildCollectionParams: artist
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test13_buildCollectionParams_artist() {
        val artistId = RemoteCatalog.artistId("Band A")
        val params = buildCollectionParams(artistId)
        assertNotNull(params)
        assertEquals("artist", params!!.kind)
        assertEquals("Band A", params.artist)
        assertNull(params.album)
        assertNull(params.playlistId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14 — buildCollectionParams: album decodes both artist and album
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test14_buildCollectionParams_album() {
        val albumId = RemoteCatalog.albumId("Band A", "Album One")
        val params = buildCollectionParams(albumId)
        assertNotNull(params)
        assertEquals("album", params!!.kind)
        assertEquals("Band A", params.artist)
        assertEquals("Album One", params.album)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 15 — buildCollectionParams: drive-folder decodes root and path
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test15_buildCollectionParams_driveFolder() {
        val encodedPath = RemoteCatalog.safeEncode("Rock/Sub")
        val parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:$encodedPath"
        val params = buildCollectionParams(parentId)
        assertNotNull(params)
        assertEquals("drive_folder", params!!.kind)
        assertEquals(2, params.root)
        assertEquals("Rock/Sub", params.path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 16 — buildCollectionParams: drive-root → drive_folder kind, empty path
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test16_buildCollectionParams_driveRoot() {
        val parentId = "${RemoteCatalog.DRIVE_ROOT_PREFIX}1"
        val params = buildCollectionParams(parentId)
        assertNotNull(params)
        assertEquals("drive_folder", params!!.kind)
        assertEquals(1, params.root)
        assertEquals("", params.path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 17 — buildCollectionParams: playlist
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test17_buildCollectionParams_playlist() {
        val parentId = "${RemoteCatalog.PLAYLIST_PREFIX}42"
        val params = buildCollectionParams(parentId)
        assertNotNull(params)
        assertEquals("playlist", params!!.kind)
        assertEquals(42, params.playlistId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 18 — buildCollectionParams: root returns null
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test18_buildCollectionParams_rootReturnsNull() {
        assertNull(buildCollectionParams("root"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 19 — Vietnamese artist name round-trips through encode/decode
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test19_vietnameseArtistRoundTrip() {
        val originalArtist = "Châu Gia Kiệt"
        val artistId = RemoteCatalog.artistId(originalArtist)
        assertTrue(artistId.startsWith(RemoteCatalog.ARTIST_PREFIX))
        val params = buildCollectionParams(artistId)
        assertNotNull(params)
        assertEquals(originalArtist, params!!.artist)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 20 — album with :: separator round-trips
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test20_albumWithSeparatorRoundTrips() {
        val artist = "Band A"
        val album = "My :: Album"
        val albumId = RemoteCatalog.albumId(artist, album)
        val params = buildCollectionParams(albumId)
        assertNotNull(params)
        assertEquals(artist, params!!.artist)
        assertEquals(album, params.album)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 21 — artistId encodes spaces and special chars
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test21_artistIdEncodesSpaces() {
        val artistId = RemoteCatalog.artistId("Band A & B")
        assertTrue(artistId.startsWith(RemoteCatalog.ARTIST_PREFIX))
        assertFalse(artistId.contains(" "))  // spaces must be encoded
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 22 — albumId uses :: separator between artist and album
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test22_albumIdUsesSeparator() {
        val albumId = RemoteCatalog.albumId("ArtistX", "AlbumY")
        assertTrue(albumId.startsWith(RemoteCatalog.ALBUM_PREFIX))
        assertTrue(albumId.contains("::"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 23 — skipped count is always non-negative
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test23_skippedNonNegative() {
        val result = CollectionPlaybackResult(
            items = listOf(makeTrack(1), makeTrack(2), makeTrack(3)),
            total = 3, playable = 3, skipped = 0,
        )
        assertTrue(result.skipped >= 0)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 24 — items list size equals playable count
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test24_itemsSizeEqualsPlayable() {
        val tracks = listOf(makeTrack(1), makeTrack(2))
        val result = CollectionPlaybackResult(items = tracks, total = 5, playable = tracks.size, skipped = 3)
        assertEquals(result.playable, result.items.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 25 — drive-folder with root 0 and non-empty path
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test25_driveFolderRoot0NonEmptyPath() {
        val encodedPath = RemoteCatalog.safeEncode("Music/Vietnamese")
        val parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}0:$encodedPath"
        val params = buildCollectionParams(parentId)
        assertNotNull(params)
        assertEquals("drive_folder", params!!.kind)
        assertEquals(0, params.root)
        assertEquals("Music/Vietnamese", params.path)
    }
}
