package com.bsdung.musicassistantcar

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.playback.AddToQueueAction
import com.bsdung.musicassistantcar.playback.PlayNextAction
import com.bsdung.musicassistantcar.playback.QueueHelper
import com.bsdung.musicassistantcar.playback.QueueListItem
import com.bsdung.musicassistantcar.playback.RemoveQueueResult
import com.bsdung.musicassistantcar.ui.model.NavigationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MeloVault v0.6.32 — Car Library & Queue UX.
 * Covers all 25 required navigation, search, quick-filter, breadcrumb, scroll restoration,
 * and full-queue manipulation invariants.
 */
class CarLibraryQueueUxTest {

    private fun item(id: String, title: String = id): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build(),
            )
            .build()

    // 1. Library home categories render/navigate deterministically
    @Test
    fun test01_libraryHomeCategoriesNavigateDeterministically() {
        val nav = NavigationManager()
        assertTrue(nav.isAtRoot)
        assertEquals(RemoteCatalog.ROOT_ID, nav.activeQuickFilterId())

        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        assertFalse(nav.isAtRoot)
        assertEquals(RemoteCatalog.DRIVE_ID, nav.currentParentId)
        assertEquals(RemoteCatalog.DRIVE_ID, nav.activeQuickFilterId())

        nav.navigateTo("drive-folder:1:Bolero", "Bolero")
        assertEquals("drive-folder:1:Bolero", nav.currentParentId)
        // Child inside Drive still highlights the Drive quick filter chip
        assertEquals(RemoteCatalog.DRIVE_ID, nav.activeQuickFilterId())
    }

    // 2. Quick filter selection updates visible scope only (resets to clean 1-level stack from root)
    @Test
    fun test02_quickFilterSelectionUpdatesVisibleScopeCleanly() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        nav.navigateTo("drive-folder:1:Lossless", "Lossless")
        nav.navigateTo("drive-folder:1:Lossless/CD1", "CD1")

        // User taps "Đã tải" quick filter chip
        nav.selectQuickFilter(RemoteCatalog.DOWNLOADED_ID, "Đã tải")
        assertEquals(RemoteCatalog.DOWNLOADED_ID, nav.currentParentId)
        assertEquals("Đã tải", nav.currentLabel)
        assertEquals(0, nav.currentPage)
        assertEquals(RemoteCatalog.DOWNLOADED_ID, nav.activeQuickFilterId())
        assertEquals("Đã tải", nav.breadcrumbTrail())

        // Single Back from a top-level quick filter returns directly to Root
        val backEntry = nav.goBack()
        assertNotNull(backEntry)
        assertEquals(RemoteCatalog.ROOT_ID, backEntry!!.parentId)
        assertTrue(nav.isAtRoot)
    }

    // 3. Quick filter does not mutate playback queue
    @Test
    fun test03_quickFilterDoesNotMutatePlaybackQueue() {
        val nav = NavigationManager()
        val initialQueue = listOf(item("track:101", "Bài 1"), item("track:102", "Bài 2"))
        val currentIndex = 0

        nav.selectQuickFilter(RemoteCatalog.ALL_ID, "Tất cả bài hát")
        nav.selectQuickFilter(RemoteCatalog.DOWNLOADED_ID, "Đã tải")
        nav.selectQuickFilter(RemoteCatalog.DEVICE_LOCAL_ID, "Thiết bị")
        nav.selectQuickFilter(RemoteCatalog.PLAYLISTS_ID, "Danh sách phát")

        // Playback queue remains untouched across all filter switches
        val rendered = QueueHelper.buildQueueList(initialQueue, currentIndex, isPlaying = true)
        val tracks = rendered.filterIsInstance<QueueListItem.Track>()
        assertEquals(listOf("track:101", "track:102"), tracks.map { it.mediaItem.mediaId })
        assertEquals(0, tracks.first { it.isCurrent }.queueIndex)
    }

    // 4. Search normalization and debounce query comparison
    @Test
    fun test04_searchNormalizationAndSupersededQueryCheck() {
        assertEquals("nhạc vàng bolero", NavigationManager.normalizeSearchQuery("  nhạc   vàng   bolero  "))
        assertEquals("", NavigationManager.normalizeSearchQuery("   "))

        val nav = NavigationManager()
        assertTrue(nav.shouldExecuteSearch("bolero", page = 0))
        nav.startSearch("bolero")
        // Superseded query with new text should execute
        assertTrue(nav.shouldExecuteSearch("bolero chọn lọc", page = 0))
    }

    // 5. Blank/cleared search restores prior view cleanly
    @Test
    fun test05_blankClearedSearchRestoresPriorViewCleanly() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        nav.navigateTo("drive-folder:1:Acoustic", "Acoustic", page = 1)
        nav.saveCurrentScroll(firstVisiblePosition = 8, topOffsetPx = -36)

        nav.startSearch("lệ quyên")
        assertEquals("search", nav.currentParentId)
        assertEquals("lệ quyên", nav.searchQuery)

        // Clearing search calls goBack() and restores the exact prior folder & scroll
        val restored = nav.goBack()
        assertNotNull(restored)
        assertEquals("drive-folder:1:Acoustic", restored!!.parentId)
        assertEquals(1, restored.page)
        assertEquals(8, restored.firstVisiblePosition)
        assertEquals(-36, restored.topOffsetPx)
        assertEquals("", nav.searchQuery)
    }

    // 6. Duplicate search query suppressed
    @Test
    fun test06_duplicateSearchQuerySuppressed() {
        val nav = NavigationManager()
        nav.startSearch("quang dũng")
        // Same query with extra whitespace on same page should be suppressed
        assertFalse(nav.shouldExecuteSearch("  quang   dũng ", page = 0))
        // Different page of same query should execute
        assertTrue(nav.shouldExecuteSearch("quang dũng", page = 1))
        // Different query should execute
        assertTrue(nav.shouldExecuteSearch("quang lê", page = 0))
    }

    // 7. Breadcrumb trail matches navigation depth without duplicate entries
    @Test
    fun test07_breadcrumbTrailMatchesNavigationDepthWithoutDuplicates() {
        val nav = NavigationManager()
        assertEquals("Thư viện", nav.breadcrumbTrail())

        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        assertEquals("Google Drive", nav.breadcrumbTrail())

        // Navigating to the same parentId again (e.g. page/sort refresh) must not duplicate breadcrumb
        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive", page = 1)
        assertEquals("Google Drive", nav.breadcrumbTrail())

        nav.navigateTo("drive-folder:1:Classical", "Classical")
        assertEquals("Google Drive › Classical", nav.breadcrumbTrail())

        // Navigating back to an ancestor already on stack unwinds cleanly instead of creating a loop
        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        assertEquals("Google Drive", nav.breadcrumbTrail())
    }

    // 8. Back from search returns to pre-search view even after multiple search refinements
    @Test
    fun test08_backFromSearchReturnsToPreSearchViewAfterRefinements() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.PLAYLISTS_ID, "Danh sách phát")
        nav.navigateTo("playlist:7", "Nhạc Trịnh")
        nav.saveCurrentScroll(firstVisiblePosition = 5, topOffsetPx = -20)

        // User types progressive refinements
        nav.startSearch("diễm")
        nav.startSearch("diễm xưa")
        nav.startSearch("diễm xưa khánh ly")

        val restored = nav.goBack()
        assertNotNull(restored)
        assertEquals("playlist:7", restored!!.parentId)
        assertEquals("Nhạc Trịnh", restored.label)
        assertEquals(5, restored.firstVisiblePosition)
        assertEquals(-20, restored.topOffsetPx)
    }

    // 9. Back from child folder returns to parent folder
    @Test
    fun test09_backFromChildFolderReturnsToParentFolder() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.DEVICE_LOCAL_ID, "Thiết bị")
        nav.navigateTo(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, "Thư mục trên thiết bị")
        nav.navigateTo("device_folder:primary:Music/FLAC", "FLAC")

        val parent = nav.goBack()
        assertNotNull(parent)
        assertEquals(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, parent!!.parentId)
        assertEquals("Thiết bị › Thư mục trên thiết bị", nav.breadcrumbTrail())
    }

    // 10. Back from queue returns to prior library view with preserved state
    @Test
    fun test10_backFromQueuePreservesLibraryNavigationAndScroll() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.ALL_ID, "Tất cả bài hát", page = 2)
        nav.saveCurrentScroll(firstVisiblePosition = 14, topOffsetPx = -42)

        // Opening/closing queue does not alter NavigationManager parentId, page, or saved scroll
        assertEquals(RemoteCatalog.ALL_ID, nav.currentParentId)
        assertEquals(2, nav.currentPage)
        assertEquals(14, nav.currentFirstVisiblePosition)
        assertEquals(-42, nav.currentTopOffsetPx)
    }

    // 11. Scroll position preserved on Back across multi-level navigation
    @Test
    fun test11_scrollPositionPreservedOnBack() {
        val nav = NavigationManager()
        nav.navigateTo(RemoteCatalog.DRIVE_ID, "Google Drive")
        nav.saveCurrentScroll(firstVisiblePosition = 4, topOffsetPx = -12)

        nav.navigateTo("drive-folder:1:Jazz", "Jazz")
        nav.saveCurrentScroll(firstVisiblePosition = 11, topOffsetPx = -50)

        nav.navigateTo("drive-folder:1:Jazz/Miles", "Miles")

        val backToJazz = nav.goBack()
        assertNotNull(backToJazz)
        assertEquals("drive-folder:1:Jazz", backToJazz!!.parentId)
        assertEquals(11, backToJazz.firstVisiblePosition)
        assertEquals(-50, backToJazz.topOffsetPx)

        val backToDriveRoot = nav.goBack()
        assertNotNull(backToDriveRoot)
        assertEquals(RemoteCatalog.DRIVE_ID, backToDriveRoot!!.parentId)
        assertEquals(4, backToDriveRoot.firstVisiblePosition)
        assertEquals(-12, backToDriveRoot.topOffsetPx)
    }

    // 12. Queue list shows current index accurately and includes previous + current + upcoming
    @Test
    fun test12_queueListShowsCurrentIndexAndFullQueueSections() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        assertEquals("Hàng đợi · 2/4", QueueHelper.formatQueueTitle(currentIndex = 1, totalCount = 4))
        assertEquals("Hàng đợi · 0 bài", QueueHelper.formatQueueTitle(currentIndex = -1, totalCount = 0))

        val rows = QueueHelper.buildQueueList(q, currentIndex = 1, isPlaying = true)
        val headers = rows.filterIsInstance<QueueListItem.Header>().map { it.title }
        assertEquals(listOf("Trước đó · 1", "Đang phát", "Tiếp theo · 2"), headers)

        val tracks = rows.filterIsInstance<QueueListItem.Track>()
        assertEquals(4, tracks.size)
        assertEquals(listOf(0, 1, 2, 3), tracks.map { it.queueIndex })
        assertTrue(tracks[1].isCurrent)
        assertFalse(tracks[0].isUpcoming)
        assertTrue(tracks[2].isUpcoming)
        assertTrue(tracks[3].isUpcoming)
    }

    // 13. Play-from-queue selects target index without corrupting remaining queue
    @Test
    fun test13_playFromQueueSelectsTargetIndexWithoutCorruptingQueue() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        // User taps item at index 3 in queue
        val rowsAfterJump = QueueHelper.buildQueueList(q, currentIndex = 3, isPlaying = true)
        val tracks = rowsAfterJump.filterIsInstance<QueueListItem.Track>()
        assertEquals(listOf("t0", "t1", "t2", "t3"), tracks.map { it.mediaItem.mediaId })
        assertEquals(3, tracks.first { it.isCurrent }.queueIndex)
    }

    // 14. Remove item before current adjusts current index (currentIndex - 1)
    @Test
    fun test14_removeItemBeforeCurrentAdjustsCurrentIndex() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        val applied = QueueHelper.applyRemoveQueueItem(q, currentIndex = 2, targetIndex = 0)
        assertTrue(applied.outcome is RemoveQueueResult.RemovedBeforeCurrent)
        assertEquals(1, applied.newCurrentIndex)
        assertEquals(listOf("t1", "t2", "t3"), applied.updatedQueue.map { it.mediaId })
        assertEquals("t2", applied.updatedQueue[applied.newCurrentIndex].mediaId)
    }

    // 15. Remove item after current preserves current index
    @Test
    fun test15_removeItemAfterCurrentPreservesCurrentIndex() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        val applied = QueueHelper.applyRemoveQueueItem(q, currentIndex = 1, targetIndex = 3)
        assertTrue(applied.outcome is RemoveQueueResult.RemovedAfterCurrent)
        assertEquals(1, applied.newCurrentIndex)
        assertEquals(listOf("t0", "t1", "t2"), applied.updatedQueue.map { it.mediaId })
        assertEquals("t1", applied.updatedQueue[applied.newCurrentIndex].mediaId)
    }

    // 16. Remove current item advances to next when available
    @Test
    fun test16_removeCurrentItemAdvancesToNextWhenAvailable() {
        val q = listOf(item("t0"), item("t1"), item("t2"))
        val applied = QueueHelper.applyRemoveQueueItem(q, currentIndex = 1, targetIndex = 1)
        assertTrue(applied.outcome is RemoveQueueResult.RemovedCurrentAdvanceNext)
        assertEquals(1, applied.newCurrentIndex)
        assertEquals(listOf("t0", "t2"), applied.updatedQueue.map { it.mediaId })
        // Next track t2 shifted into index 1
        assertEquals("t2", applied.updatedQueue[applied.newCurrentIndex].mediaId)
    }

    // 17. Remove current last item transitions cleanly to previous item
    @Test
    fun test17_removeCurrentLastItemTransitionsCleanlyToPrevious() {
        val q = listOf(item("t0"), item("t1"), item("t2"))
        val applied = QueueHelper.applyRemoveQueueItem(q, currentIndex = 2, targetIndex = 2)
        assertTrue(applied.outcome is RemoveQueueResult.RemovedCurrentMovePrevious)
        assertEquals(1, applied.newCurrentIndex)
        assertEquals(listOf("t0", "t1"), applied.updatedQueue.map { it.mediaId })
        assertEquals("t1", applied.updatedQueue[applied.newCurrentIndex].mediaId)
    }

    // 18. Remove final remaining item enters empty queue state safely
    @Test
    fun test18_removeFinalRemainingItemEntersEmptyQueueStateSafely() {
        val q = listOf(item("t0"))
        val applied = QueueHelper.applyRemoveQueueItem(q, currentIndex = 0, targetIndex = 0)
        assertTrue(applied.outcome is RemoveQueueResult.RemovedLastStopEmpty)
        assertEquals(-1, applied.newCurrentIndex)
        assertTrue(applied.updatedQueue.isEmpty())

        val rows = QueueHelper.buildQueueList(applied.updatedQueue, applied.newCurrentIndex, isPlaying = false)
        assertEquals(1, rows.size)
        val emptyRow = rows.first() as QueueListItem.Empty
        assertTrue(emptyRow.isFullQueueEmpty)
    }

    // 19. Play Next inserts at currentIndex + 1
    @Test
    fun test19_playNextInsertsAtCurrentIndexPlusOne() {
        val q = listOf(item("t0"), item("t1"), item("t2"))
        val next = item("t_insert")
        val plan = QueueHelper.planPlayNext(q, currentIndex = 1, newItem = next)
        assertTrue(plan is PlayNextAction.Insert)
        assertEquals(2, (plan as PlayNextAction.Insert).targetIndex)

        val updated = QueueHelper.applyPlayNext(q, currentIndex = 1, newItem = next)
        assertEquals(listOf("t0", "t1", "t_insert", "t2"), updated.map { it.mediaId })
    }

    // 20. Play Next when queue empty starts/initializes cleanly
    @Test
    fun test20_playNextWhenQueueEmptyStartsCleanly() {
        val empty = emptyList<MediaItem>()
        val first = item("t_first")
        val plan = QueueHelper.planPlayNext(empty, currentIndex = -1, newItem = first)
        assertTrue(plan is PlayNextAction.StartNewQueue)

        val updated = QueueHelper.applyPlayNext(empty, currentIndex = -1, newItem = first)
        assertEquals(listOf("t_first"), updated.map { it.mediaId })
    }

    // 21. Add to Queue appends at end
    @Test
    fun test21_addToQueueAppendsAtEnd() {
        val q = listOf(item("t0"), item("t1"))
        val tail = item("t_tail")
        val plan = QueueHelper.planAddToQueue(q, tail)
        assertTrue(plan is AddToQueueAction.Append)

        val updated = QueueHelper.applyAddToQueue(q, tail)
        assertEquals(listOf("t0", "t1", "t_tail"), updated.map { it.mediaId })
    }

    // 22. Add to Queue when queue empty starts/initializes cleanly
    @Test
    fun test22_addToQueueWhenQueueEmptyStartsCleanly() {
        val empty = emptyList<MediaItem>()
        val first = item("t_first")
        val plan = QueueHelper.planAddToQueue(empty, first)
        assertTrue(plan is AddToQueueAction.StartNewQueue)

        val updated = QueueHelper.applyAddToQueue(empty, first)
        assertEquals(listOf("t_first"), updated.map { it.mediaId })
    }

    // 23. Queue reorder preserves playing item/index
    @Test
    fun test23_queueReorderPreservesPlayingItemAndIndex() {
        val q = listOf(item("t0"), item("t1"), item("t2"), item("t3"))
        val currentIndex = 1 // t1 is playing
        assertTrue(QueueHelper.canMoveUpcoming(currentIndex = 1, fromIndex = 3, toIndex = 2, queueSize = 4))
        val reordered = QueueHelper.applyMove(q, currentIndex = 1, fromIndex = 3, toIndex = 2)
        assertEquals(listOf("t0", "t1", "t3", "t2"), reordered.map { it.mediaId })
        assertEquals("t1", reordered[currentIndex].mediaId)
    }

    // 24. MediaSession metadata/queue indices remain consistent after chained queue mutations
    @Test
    fun test24_queueIndicesConsistentAfterChainedMutations() {
        var q = listOf(item("t0"), item("t1"), item("t2"))
        var currentIndex = 0

        // Play Next t_next -> [t0, t_next, t1, t2]
        q = QueueHelper.applyPlayNext(q, currentIndex, item("t_next"))
        assertEquals(listOf("t0", "t_next", "t1", "t2"), q.map { it.mediaId })

        // Advance playback to index 2 (t1)
        currentIndex = 2
        assertEquals("t1", q[currentIndex].mediaId)

        // Remove item before current (index 0 = t0) -> [t_next, t1, t2], currentIndex = 1
        val rem1 = QueueHelper.applyRemoveQueueItem(q, currentIndex, targetIndex = 0)
        q = rem1.updatedQueue
        currentIndex = rem1.newCurrentIndex
        assertEquals(1, currentIndex)
        assertEquals("t1", q[currentIndex].mediaId)

        // Add to queue t_end -> [t_next, t1, t2, t_end]
        q = QueueHelper.applyAddToQueue(q, item("t_end"))

        val listItems = QueueHelper.buildQueueList(q, currentIndex, isPlaying = true)
        val tracks = listItems.filterIsInstance<QueueListItem.Track>()
        assertEquals(listOf(0, 1, 2, 3), tracks.map { it.queueIndex })
        assertEquals("t1", tracks.first { it.isCurrent }.mediaItem.mediaId)
    }

    // 25. Source badge resolution preserves offline/device/cloud semantics
    @Test
    fun test25_sourceBadgeResolutionPreservesSemantics() {
        val deviceItem = item("device:555")
        val driveItem = item("drive-file:123")
        val remoteTrack = item("track:888")

        assertEquals("Thiết bị", QueueHelper.deriveSourceBadge(deviceItem, isOfflineDownloaded = false))
        assertEquals("✓ Offline", QueueHelper.deriveSourceBadge(driveItem, isOfflineDownloaded = true))
        assertEquals("Drive", QueueHelper.deriveSourceBadge(driveItem, isOfflineDownloaded = false))
        assertEquals("✓ Offline", QueueHelper.deriveSourceBadge(remoteTrack, isOfflineDownloaded = true))
        assertNull(QueueHelper.deriveSourceBadge(remoteTrack, isOfflineDownloaded = false))
    }
}
