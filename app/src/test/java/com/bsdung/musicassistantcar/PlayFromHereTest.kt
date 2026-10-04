package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.data.RemoteCatalog
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for v0.6.14 Play From Here patch.
 *
 * Tests use [RemoteCatalog.resolveCanonicalTrackMediaId] (pure, no Android Bundle needed)
 * for all drive-file / canonical-ID cases, and mirror the collection-slice logic
 * from [MainActivity.playFromHere] for queue-semantics tests.
 *
 * Tests cover:
 * 01 — drive-file mediaId with trackId=456 → "track:456"
 * 02 — "track:789" mediaId with any trackId → "track:789" (track: wins)
 * 03 — drive-folder mediaId → null (not a canonical track)
 * 04 — drive-file mediaId with trackId=null → null (no ID available)
 * 05 — drive-file with trackId=0 → null (0 is sentinel for "not set")
 * 06 — selected Drive item (mediaId=drive-file:123, trackId=456) finds track:456 in collection
 * 07 — selected middle index 2 in [A,B,C,D] → suffix [C,D]; A,B excluded
 * 08 — selected index 0 → suffix is the full list
 * 09 — selected last item → one-item suffix
 * 10 — missing ID (null canonical) → no suffix returned (no fallback to index 0)
 * 11 — >30 collection: suffix from index 37 has size 38
 */
class PlayFromHereTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Thin wrappers around the pure logic from MainActivity.playFromHere
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mirrors the canonical-ID match + suffix-slice from playFromHere.
     * Returns the suffix list starting at [selectedCanonicalId], or null if not found.
     * Uses [RemoteCatalog.resolveCanonicalTrackMediaId] with (mediaId, null) since
     * collection items are always "track:<id>" mediaIds.
     */
    private fun playFromHereSuffix(
        selectedCanonicalId: String?,
        collection: List<Pair<String, Int?>>,   // (mediaId, trackId) pairs
    ): List<String>? {
        if (selectedCanonicalId == null) return null
        val startIndex = collection.indexOfFirst { (mediaId, trackId) ->
            RemoteCatalog.resolveCanonicalTrackMediaId(mediaId, trackId) == selectedCanonicalId
        }
        if (startIndex < 0) return null
        return collection.drop(startIndex).map { it.first }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 01 — drive-file with trackId → "track:<id>"
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test01_driveFileWithTrackId_mapsToCanonicalTrackId() {
        val canonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FILE_PREFIX}123",
            trackId = 456,
        )
        assertEquals("track:456", canonical)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 02 — "track:<id>" mediaId maps to itself regardless of trackId param
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test02_trackMediaId_mapsToItself() {
        val canonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "track:789",
            trackId = 999,   // trackId param is irrelevant when mediaId is already "track:"
        )
        assertEquals("track:789", canonical)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 03 — drive-folder mediaId produces null
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test03_driveFolderMediaId_returnsNull() {
        val canonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}0:Rock",
            trackId = null,
        )
        assertNull(canonical)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 04 — drive-file with trackId=null → null (cannot identify)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test04_driveFileWithoutTrackId_returnsNull() {
        val canonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FILE_PREFIX}200",
            trackId = null,
        )
        assertNull(canonical)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 05 — drive-file with trackId=0 (sentinel) → null
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test05_driveFileWithZeroTrackId_returnsNull() {
        val canonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FILE_PREFIX}200",
            trackId = 0,
        )
        assertNull("trackId=0 is the Bundle default sentinel and must not produce a match", canonical)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 06 — Drive item (drive-file:123, trackId=456) finds track:456 in collection
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test06_driveItemFindsMatchingCollectionTrack() {
        // Selected row: drive-file browse item with canonical track_id=456
        val selectedCanonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FILE_PREFIX}123",
            trackId = 456,
        )   // → "track:456"

        // Collection from fetchCollectionPlayback always returns "track:<id>" items
        val collection = listOf(
            Pair("track:100", null),   // A
            Pair("track:200", null),   // B
            Pair("track:456", null),   // C ← match
            Pair("track:300", null),   // D
        )

        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNotNull("Suffix must not be null when track is found", suffix)
        assertEquals(2, suffix!!.size)
        assertEquals("track:456", suffix[0])
        assertEquals("track:300", suffix[1])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 07 — middle index 2 in [A,B,C,D] → suffix [C,D]; A,B excluded
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test07_middleIndex_returnsSuffixFromSelectedOnward() {
        val selectedCanonical = "track:30"
        val collection = listOf(
            Pair("track:10", null),   // A
            Pair("track:20", null),   // B
            Pair("track:30", null),   // C ← selected
            Pair("track:40", null),   // D
        )

        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNotNull(suffix)
        assertEquals(2, suffix!!.size)
        assertEquals("track:30", suffix[0])
        assertEquals("track:40", suffix[1])
        assertTrue("A and B must not appear before selected", suffix.none { it == "track:10" || it == "track:20" })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 08 — selected index 0 → full list returned
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test08_firstItem_returnsFullList() {
        val selectedCanonical = "track:10"
        val collection = listOf(
            Pair("track:10", null),
            Pair("track:20", null),
            Pair("track:30", null),
        )

        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNotNull(suffix)
        assertEquals(3, suffix!!.size)
        assertEquals("track:10", suffix[0])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 09 — selected last item → one-item suffix
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test09_lastItem_returnsOneItemSuffix() {
        val selectedCanonical = "track:30"
        val collection = listOf(
            Pair("track:10", null),
            Pair("track:20", null),
            Pair("track:30", null),
        )

        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNotNull(suffix)
        assertEquals(1, suffix!!.size)
        assertEquals("track:30", suffix[0])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10 — null canonical ID → null suffix; no fallback to index 0
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test10_missingCanonicalId_returnsNullNotIndex0() {
        // drive-file without track_id → null canonical
        val selectedCanonical = RemoteCatalog.resolveCanonicalTrackMediaId(
            mediaId = "${RemoteCatalog.DRIVE_FILE_PREFIX}999",
            trackId = null,
        )
        assertNull("No track_id must produce null canonical ID", selectedCanonical)

        val collection = listOf(
            Pair("track:10", null),
            Pair("track:20", null),
        )
        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNull("Null canonical ID must return null suffix — no fallback to 0", suffix)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11 — >30 collection: suffix from index 37 has correct size (75 - 37 = 38)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun test11_largeCollectionOver30_suffixSizeCorrect() {
        val totalTracks = 75
        val selectedIndex = 37
        val collection = (1..totalTracks).map { Pair("track:$it", null) }

        val selectedCanonical = "track:${selectedIndex + 1}"   // 1-indexed IDs
        val suffix = playFromHereSuffix(selectedCanonical, collection)
        assertNotNull(suffix)
        val expectedSize = totalTracks - selectedIndex   // 38
        assertEquals(expectedSize, suffix!!.size)
        assertEquals(selectedCanonical, suffix[0])
    }
}
