package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.ui.model.LibraryLocation
import com.bsdung.musicassistantcar.ui.model.NavigationManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for v0.6.15 Library UX Polish:
 * - Back navigation state restoration (parentId, page, scroll position, offset, sort)
 * - Sort state management and sortable collections
 * - Collection total count retention
 * - Effective sort determination for collections (playlist ignores sort)
 */
class LibraryPolishTest {

    private fun isSortableCollection(parentId: String): Boolean =
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX)

    private fun isCollectionContext(parentId: String): Boolean =
        parentId.startsWith(RemoteCatalog.ARTIST_PREFIX) ||
        parentId.startsWith(RemoteCatalog.ALBUM_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX) ||
        parentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) ||
        parentId.startsWith(RemoteCatalog.PLAYLIST_PREFIX)

    @Test
    fun test01_libraryLocationDefaults() {
        val loc = LibraryLocation(parentId = "folder:1", label = "Test Folder", page = 1)
        assertEquals("folder:1", loc.parentId)
        assertEquals("Test Folder", loc.label)
        assertEquals(1, loc.page)
        assertEquals(0, loc.firstVisiblePosition)
        assertEquals(0, loc.topOffsetPx)
        assertEquals("", loc.sort)
    }

    @Test
    fun test02_navigationManagerSaveScrollAndNavigateTo() {
        val nav = NavigationManager()
        assertEquals("root", nav.currentParentId)
        assertEquals(0, nav.currentPage)

        // Save scroll position for root
        nav.saveCurrentScroll(firstVisiblePosition = 5, topOffsetPx = -42)
        assertEquals(5, nav.currentFirstVisiblePosition)
        assertEquals(-42, nav.currentTopOffsetPx)

        // Navigate to folder
        nav.navigateTo(parentId = "drive-folder:0:Jazz", label = "Jazz", page = 0, sort = "name_asc")
        assertEquals("drive-folder:0:Jazz", nav.currentParentId)
        assertEquals("Jazz", nav.currentLabel)
        assertEquals(0, nav.currentPage)
        assertEquals("name_asc", nav.currentSort)
        // Current scroll resets for the new page
        assertEquals(0, nav.currentFirstVisiblePosition)
        assertEquals(0, nav.currentTopOffsetPx)

        // Backstack has root entry with the saved scroll position
        val snapshot = nav.backStackSnapshot()
        assertEquals(1, snapshot.size)
        val rootLoc = snapshot[0]
        assertEquals("root", rootLoc.parentId)
        assertEquals(5, rootLoc.firstVisiblePosition)
        assertEquals(-42, rootLoc.topOffsetPx)
        assertEquals("", rootLoc.sort)
    }

    @Test
    fun test03_navigationManagerGoBackRestoresScrollAndSort() {
        val nav = NavigationManager()
        nav.saveCurrentScroll(firstVisiblePosition = 12, topOffsetPx = -100)
        nav.navigateTo(parentId = "drive-folder:0:Rock", label = "Rock", page = 2, sort = "name_asc")

        // Now inside Rock, user navigates to subfolder
        nav.saveCurrentScroll(firstVisiblePosition = 8, topOffsetPx = -15)
        nav.navigateTo(parentId = "drive-folder:0:Rock/Sub", label = "Sub", page = 0, sort = "name_desc")
        assertEquals("drive-folder:0:Rock/Sub", nav.currentParentId)
        assertEquals("name_desc", nav.currentSort)

        // Go back to Rock
        val previous = nav.goBack()
        assertNotNull(previous)
        assertEquals("drive-folder:0:Rock", previous!!.parentId)
        assertEquals(2, previous.page)
        assertEquals(8, previous.firstVisiblePosition)
        assertEquals(-15, previous.topOffsetPx)
        assertEquals("name_asc", previous.sort)

        // NavManager state is restored to Rock
        assertEquals("drive-folder:0:Rock", nav.currentParentId)
        assertEquals(2, nav.currentPage)
        assertEquals(8, nav.currentFirstVisiblePosition)
        assertEquals(-15, nav.currentTopOffsetPx)
        assertEquals("name_asc", nav.currentSort)

        // Go back again to root
        val rootPrev = nav.goBack()
        assertNotNull(rootPrev)
        assertEquals("root", rootPrev!!.parentId)
        assertEquals(0, rootPrev.page)
        assertEquals(12, rootPrev.firstVisiblePosition)
        assertEquals(-100, rootPrev.topOffsetPx)
        assertEquals("", rootPrev.sort)
    }

    @Test
    fun test04_navigationManagerResetToRoot() {
        val nav = NavigationManager()
        nav.saveCurrentScroll(10, -20)
        nav.navigateTo("artist:TheBand", "The Band", page = 1, sort = "name_asc")
        nav.setCollectionTotal(55)

        nav.resetToRoot()
        assertEquals("root", nav.currentParentId)
        assertEquals("Thư viện", nav.currentLabel)
        assertEquals(0, nav.currentPage)
        assertEquals(0, nav.currentFirstVisiblePosition)
        assertEquals(0, nav.currentTopOffsetPx)
        assertEquals("", nav.currentSort)
        assertNull(nav.currentCollectionTotal)
        assertTrue(nav.isAtRoot)
    }

    @Test
    fun test05_navigationManagerSearchPushesScrollState() {
        val nav = NavigationManager()
        nav.saveCurrentScroll(14, -50)
        nav.navigateTo("drive-folder:0:Album", "Album", page = 1)

        nav.saveCurrentScroll(7, -30)
        nav.startSearch("queen")
        assertEquals("search", nav.currentParentId)
        assertEquals("queen", nav.searchQuery)
        assertEquals(0, nav.currentPage)

        // Go back from search restores the Album screen and its scroll position
        val prev = nav.goBack()
        assertNotNull(prev)
        assertEquals("drive-folder:0:Album", prev!!.parentId)
        assertEquals(1, prev.page)
        assertEquals(7, prev.firstVisiblePosition)
        assertEquals(-30, prev.topOffsetPx)
    }

    @Test
    fun test06_isSortableCollection() {
        // Sortable
        assertTrue(isSortableCollection("drive-folder:0:Music/Rock"))
        assertTrue(isSortableCollection("drive-root:0"))
        assertTrue(isSortableCollection("album:Band::AlbumName"))
        assertTrue(isSortableCollection("artist:TheBand"))

        // Strictly NOT sortable
        assertFalse(isSortableCollection("playlist:1"))
        assertFalse(isSortableCollection("root"))
        assertFalse(isSortableCollection("search"))
        assertFalse(isSortableCollection("category:recent"))
        assertFalse(isSortableCollection("source:all"))
        assertFalse(isSortableCollection("source:telegram"))
    }

    @Test
    fun test07_isCollectionContext() {
        assertTrue(isCollectionContext("drive-folder:0:Music"))
        assertTrue(isCollectionContext("drive-root:0"))
        assertTrue(isCollectionContext("album:Artist::Album"))
        assertTrue(isCollectionContext("artist:Artist"))
        assertTrue(isCollectionContext("playlist:42"))

        assertFalse(isCollectionContext("root"))
        assertFalse(isCollectionContext("search"))
        assertFalse(isCollectionContext("source:all"))
        assertFalse(isCollectionContext("category:recent"))
    }

    @Test
    fun test08_effectiveSortLogic() {
        val nav = NavigationManager()
        nav.setSort("name_asc")

        // Folder respects current sort
        val folderKind = "drive_folder"
        val folderSort = if (folderKind == "playlist") "" else nav.currentSort
        assertEquals("name_asc", folderSort)

        // Album respects current sort
        val albumKind = "album"
        val albumSort = if (albumKind == "playlist") "" else nav.currentSort
        assertEquals("name_asc", albumSort)

        // Artist respects current sort
        val artistKind = "artist"
        val artistSort = if (artistKind == "playlist") "" else nav.currentSort
        assertEquals("name_asc", artistSort)

        // Playlist strictly ignores sort
        val playlistKind = "playlist"
        val playlistSort = if (playlistKind == "playlist") "" else nav.currentSort
        assertEquals("", playlistSort)
    }

    @Test
    fun test09_collectionTotalResetOnNavigate() {
        val nav = NavigationManager()
        nav.navigateTo("artist:Artist1", "Artist 1")
        nav.setCollectionTotal(42)
        assertEquals(42, nav.currentCollectionTotal)

        // Navigating to a different parentId resets collection total
        nav.navigateTo("artist:Artist2", "Artist 2")
        assertNull(nav.currentCollectionTotal)

        // Setting total for new parent works
        nav.setCollectionTotal(15)
        assertEquals(15, nav.currentCollectionTotal)

        // Page change within same parent preserves collection total
        nav.navigateTo("artist:Artist2", "Artist 2", page = 1)
        assertEquals(15, nav.currentCollectionTotal)
    }

    @Test
    fun test10_restoreStateWithSort() {
        val nav = NavigationManager()
        nav.restoreState(
            parentId = "drive-folder:0:Rock",
            label = "Rock",
            page = 2,
            search = "",
            backStack = listOf(LibraryLocation("root", "Thư viện", 0, firstVisiblePosition = 4, topOffsetPx = -10)),
            sort = "name_desc",
        )

        assertEquals("drive-folder:0:Rock", nav.currentParentId)
        assertEquals(2, nav.currentPage)
        assertEquals("name_desc", nav.currentSort)
        assertEquals(1, nav.backStackSnapshot().size)
        assertEquals(4, nav.backStackSnapshot()[0].firstVisiblePosition)
        assertEquals(-10, nav.backStackSnapshot()[0].topOffsetPx)
    }
}
