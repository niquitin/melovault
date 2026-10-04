package com.bsdung.musicassistantcar.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationManagerTest {

    @Test
    fun initialStateIsRoot() {
        val nav = NavigationManager()
        assertTrue(nav.isAtRoot)
        assertEquals("root", nav.currentParentId)
        assertEquals("Thư viện", nav.currentLabel)
        assertEquals(0, nav.currentPage)
    }

    @Test
    fun navigationStackPushesAndPopsCorrectly() {
        val nav = NavigationManager()
        nav.navigateTo("source:drive", "Google Drive")
        assertFalse(nav.isAtRoot)
        assertEquals("source:drive", nav.currentParentId)

        nav.navigateTo("drive-folder:123", "Nhạc Việt", 1)
        assertEquals("drive-folder:123", nav.currentParentId)
        assertEquals(1, nav.currentPage)

        // Go back to Google Drive
        val loc1 = nav.goBack()
        assertEquals("source:drive", loc1?.parentId)
        assertEquals("source:drive", nav.currentParentId)
        assertEquals(0, nav.currentPage)

        // Go back to root
        val loc2 = nav.goBack()
        assertEquals("root", loc2?.parentId)
        assertTrue(nav.isAtRoot)
    }

    @Test
    fun searchStateAllowsBackToPrevious() {
        val nav = NavigationManager()
        nav.navigateTo("source:all", "Tất cả bài hát", 2)
        nav.startSearch("Trịnh Công Sơn")

        assertEquals("search", nav.currentParentId)
        assertEquals("Trịnh Công Sơn", nav.searchQuery)

        nav.goBack()
        assertEquals("source:all", nav.currentParentId)
        assertEquals(2, nav.currentPage)
        assertEquals("", nav.searchQuery)
    }

    @Test
    fun restoredBackStackSurvivesActivityRecreation() {
        val before = NavigationManager()
        before.navigateTo("source:drive", "Google Drive")
        before.navigateTo("drive-root:2", "Apple Music & Tidal")
        before.navigateTo("drive-folder:2:Tidal", "Tidal")

        val restored = NavigationManager()
        restored.restoreState(
            before.currentParentId,
            before.currentLabel,
            before.currentPage,
            before.searchQuery,
            before.backStackSnapshot(),
        )

        assertEquals("drive-folder:2:Tidal", restored.currentParentId)

        restored.goBack()
        assertEquals("drive-root:2", restored.currentParentId)

        restored.goBack()
        assertEquals("source:drive", restored.currentParentId)

        restored.goBack()
        assertTrue(restored.isAtRoot)
    }

    @Test
    fun searchPaginationUpdatesCurrentPageAndKeepsQuery() {
        val nav = NavigationManager()
        nav.startSearch("Nguyễn Khang")
        assertEquals(0, nav.currentPage)
        assertEquals("Nguyễn Khang", nav.searchQuery)
        assertEquals("Tìm kiếm: Nguyễn Khang", nav.breadcrumbTrail())

        nav.setPage(1)
        assertEquals(1, nav.currentPage)
        assertEquals("Nguyễn Khang", nav.searchQuery)

        nav.setPage(2)
        assertEquals(2, nav.currentPage)

        // Back restores root
        nav.goBack()
        assertTrue(nav.isAtRoot)
        assertEquals(0, nav.currentPage)
        assertEquals("", nav.searchQuery)
    }
}
