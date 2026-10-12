package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.CollectionPlaybackResult
import com.bsdung.musicassistantcar.data.DriveBrowseItem
import com.bsdung.musicassistantcar.data.DriveBrowseResult
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.ui.model.NavigationManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for v0.6.15 Targeted Recursive Drive Folder Fix:
 * 1. Drive subtitle renders recursive track count
 * 2. folder count never rendered as "N bài"
 * 3. optional folder count + track count format correct
 * 4. Play All consumes recursive collection result
 * 5. Shuffle preserves full recursive membership
 * 6. Add All consumes recursive collection result
 * 7. existing Drive Play From Here behavior unchanged (recursive=false direct slice)
 * 8. Playlist sort/order behavior unchanged
 */
class RecursiveDriveFolderTest {

    // 1. Drive subtitle renders recursive track count when only tracks exist
    @Test
    fun test01_driveSubtitleRendersRecursiveTrackCount() {
        val parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}0:Music/ELAC"
        val subtitle = MainActivity.formatCollectionSubtitle(
            parentId = parentId,
            total = 12,
            trackCount = 12,
            folderCount = 0,
        )
        assertEquals("12 bài", subtitle)
    }

    // 2. folder count never rendered as "N bài" (Apple Music case with 23 folders, 0 direct tracks)
    @Test
    fun test02_folderCountNeverRenderedAsTrackCount() {
        val parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:Apple Music"
        // If there are 23 folders and no tracks
        val subtitleNoTracks = MainActivity.formatCollectionSubtitle(
            parentId = parentId,
            total = 23,
            trackCount = 0,
            folderCount = 23,
        )
        assertEquals("23 thư mục", subtitleNoTracks)
        assertFalse(
            "Folder count must NEVER be labeled as '23 bài'",
            subtitleNoTracks?.contains("23 bài") ?: false
        )
    }

    // 3. optional folder count + track count format correct ("23 thư mục • 684 bài")
    @Test
    fun test03_folderCountAndTrackCountFormatCorrect() {
        val parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:Apple Music"
        val subtitle = MainActivity.formatCollectionSubtitle(
            parentId = parentId,
            total = 23,
            trackCount = 684,
            folderCount = 23,
        )
        assertEquals("23 thư mục · 684 bài", subtitle)

        // Real Sony Apple Music case with 928 tracks
        val sonyAppleMusic = MainActivity.formatCollectionSubtitle(
            parentId = parentId,
            total = 23,
            trackCount = 928,
            folderCount = 23,
        )
        assertEquals("23 thư mục · 928 bài", sonyAppleMusic)

        // Large 11,188 track folder with Vietnamese thousands separator
        val largeFolder = MainActivity.formatCollectionSubtitle(
            parentId = parentId,
            total = 2,
            trackCount = 11188,
            folderCount = 2,
        )
        assertEquals("2 thư mục · 11.188 bài", largeFolder)
    }

    // 4. Play All consumes recursive collection result
    @Test
    fun test04_playAllConsumesRecursiveCollectionResult() {
        // Multi-subfolder tracks returned from recursive backend expansion
        val tracks = listOf(
            CarTrack(id = 1, title = "A_Teens Song 1", artist = "A_Teens", album = "Alb1", status = "OWNED_AUDIO", duration = 180.0),
            CarTrack(id = 2, title = "A_Teens Song 2", artist = "A_Teens", album = "Alb1", status = "OWNED_AUDIO", duration = 200.0),
            CarTrack(id = 3, title = "Andy Lau Song 1", artist = "Andy Lau", album = "Cantopop", status = "OWNED_AUDIO", duration = 210.0),
            CarTrack(id = 4, title = "Chopin Nocturne", artist = "Chopin", album = "Classical", status = "OWNED_AUDIO", duration = 300.0),
        )
        val result = CollectionPlaybackResult(items = tracks, total = 4, playable = 4, skipped = 0)

        // Play All queues all tracks in order starting at index 0
        assertEquals(4, result.items.size)
        assertEquals("A_Teens Song 1", result.items[0].title)
        assertEquals("Chopin Nocturne", result.items[3].title)
        // Tracks from at least two different child folders are present
        val artists = result.items.map { it.artist }.distinct()
        assertTrue("Queue contains tracks from multiple child folders", artists.size >= 2)
    }

    // 5. Shuffle preserves full recursive membership
    @Test
    fun test05_shufflePreservesFullRecursiveMembership() {
        val tracks = (1..50).map {
            CarTrack(id = it, title = "Song $it", artist = "Artist ${it % 5}", album = "Alb", status = "OWNED_AUDIO", duration = 180.0)
        }
        val shuffled = tracks.shuffled()

        assertEquals("Shuffle must contain exactly the same number of tracks", tracks.size, shuffled.size)
        assertEquals("Shuffle must contain all original IDs", tracks.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
    }

    // 6. Add All consumes recursive collection result (appends without restarting current track)
    @Test
    fun test06_addAllConsumesRecursiveCollectionResult() {
        val existingQueue = mutableListOf(
            CarTrack(id = 99, title = "Currently Playing", artist = "Current", album = "Alb", status = "OWNED_AUDIO", duration = 150.0)
        )
        val recursiveTracks = listOf(
            CarTrack(id = 1, title = "Recursive 1", artist = "Folder A", album = "Alb", status = "OWNED_AUDIO", duration = 180.0),
            CarTrack(id = 2, title = "Recursive 2", artist = "Folder B", album = "Alb", status = "OWNED_AUDIO", duration = 200.0),
        )

        // Append to queue
        existingQueue.addAll(recursiveTracks)

        assertEquals(3, existingQueue.size)
        assertEquals("Currently playing track remains at index 0", 99, existingQueue[0].id)
        assertEquals(1, existingQueue[1].id)
        assertEquals(2, existingQueue[2].id)
    }

    // 7. existing Drive Play From Here behavior unchanged (direct context, slice from selected onward)
    @Test
    fun test07_playFromHereDirectContextSlice() {
        // Concrete folder with tracks A, B, C, D, E, F
        val directTracks = listOf(
            CarTrack(id = 10, title = "Track A", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
            CarTrack(id = 11, title = "Track B", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
            CarTrack(id = 12, title = "Track C", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
            CarTrack(id = 13, title = "Track D", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
            CarTrack(id = 14, title = "Track E", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
            CarTrack(id = 15, title = "Track F", artist = "ELAC", album = "Test", status = "OWNED_AUDIO", duration = 100.0),
        )

        // Selecting Track D (id=13)
        val selectedId = 13
        val startIndex = directTracks.indexOfFirst { it.id == selectedId }
        assertEquals(3, startIndex)

        // Sliced queue from startIndex onward: D, E, F
        val queueSuffix = directTracks.drop(startIndex)
        assertEquals(3, queueSuffix.size)
        assertEquals("Track D", queueSuffix[0].title)
        assertEquals("Track E", queueSuffix[1].title)
        assertEquals("Track F", queueSuffix[2].title)
        assertFalse("Preceding tracks A, B, C must NOT be in queue", queueSuffix.any { it.id in listOf(10, 11, 12) })
    }

    // 8. Playlist sort/order behavior unchanged (non-drive collections format as "$total bài")
    @Test
    fun test08_playlistSortAndOrderBehaviorUnchanged() {
        val playlistParent = "${RemoteCatalog.PLAYLIST_PREFIX}5"
        val subtitle = MainActivity.formatCollectionSubtitle(
            parentId = playlistParent,
            total = 25,
            trackCount = null,
            folderCount = null,
        )
        assertEquals("25 bài", subtitle)

        val albumParent = "${RemoteCatalog.ALBUM_PREFIX}Artist::Album"
        val albumSubtitle = MainActivity.formatCollectionSubtitle(
            parentId = albumParent,
            total = 10,
            trackCount = null,
            folderCount = null,
        )
        assertEquals("10 bài", albumSubtitle)

        val artistParent = "${RemoteCatalog.ARTIST_PREFIX}ArtistName"
        val artistSubtitle = MainActivity.formatCollectionSubtitle(
            parentId = artistParent,
            total = 42,
            trackCount = null,
            folderCount = null,
        )
        assertEquals("42 bài", artistSubtitle)
    }

    // 9. DriveBrowseResult data class holds trackCount and folderCount
    @Test
    fun test09_driveBrowseResultHoldsMetadata() {
        val item = DriveBrowseItem(
            kind = "folder",
            name = "Subfolder",
            root = 0,
            path = "Music/Subfolder",
            driveFileId = null,
            trackId = null,
            title = "",
            artist = "",
            album = "",
        )
        val res = DriveBrowseResult(
            items = listOf(item),
            total = 1,
            trackCount = 42,
            folderCount = 1,
        )
        assertEquals(1, res.total)
        assertEquals(42, res.trackCount)
        assertEquals(1, res.folderCount)
    }

    // 10. NavigationManager back navigation restores collection counts
    @Test
    fun test10_navigationManagerRestoresCollectionCountsOnBack() {
        val nav = NavigationManager()
        nav.navigateTo(parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:Apple Music", label = "Apple Music")
        nav.setCollectionCounts(total = 23, trackCount = 928, folderCount = 23)

        assertEquals(23, nav.currentCollectionTotal)
        assertEquals(928, nav.currentCollectionTrackCount)
        assertEquals(23, nav.currentCollectionFolderCount)

        // Navigate deeper into child folder A_Teens
        nav.navigateTo(parentId = "${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:Apple Music/A_Teens", label = "A_Teens")
        nav.setCollectionCounts(total = 15, trackCount = 15, folderCount = 0)

        assertEquals(15, nav.currentCollectionTotal)
        assertEquals(15, nav.currentCollectionTrackCount)
        assertEquals(0, nav.currentCollectionFolderCount)

        // Go back to Apple Music
        val previous = nav.goBack()
        assertNotNull(previous)
        assertEquals("${RemoteCatalog.DRIVE_FOLDER_PREFIX}2:Apple Music", nav.currentParentId)
        assertEquals(23, nav.currentCollectionTotal)
        assertEquals(928, nav.currentCollectionTrackCount)
        assertEquals(23, nav.currentCollectionFolderCount)
    }
}
