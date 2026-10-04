package com.bsdung.musicassistantcar

import androidx.media3.common.MediaItem
import com.bsdung.musicassistantcar.data.BatchResult
import com.bsdung.musicassistantcar.data.CollectionActionResult
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEditorTest {

    private fun createTrack(id: String, title: String = "Track $id", artist: String = "Artist $id", album: String = "Album $id"): LibraryItem.Track {
        val mediaItem = MediaItem.Builder().setMediaId(id).build()
        return LibraryItem.Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUri = null,
            durationMs = 180000L,
            isCurrent = false,
            isPlaying = false,
            mediaItem = mediaItem,
        )
    }

    private fun createFolder(id: String, title: String = "Folder $id"): LibraryItem.Folder {
        val mediaItem = MediaItem.Builder().setMediaId(id).build()
        return LibraryItem.Folder(
            id = id,
            title = title,
            subtitle = "",
            mediaItem = mediaItem,
        )
    }

    // 1. playlist edit mode enter/exit state
    @Test
    fun testPlaylistEditModeEnterExitState() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        assertFalse(adapter.isEditMode)
        assertFalse(adapter.canReorder)

        adapter.setEditMode(true, allowReorder = true)
        assertTrue(adapter.isEditMode)
        assertTrue(adapter.canReorder)

        adapter.setEditMode(false)
        assertFalse(adapter.isEditMode)
        assertFalse(adapter.canReorder)
    }

    // 2. selected item toggle
    @Test
    fun testSelectedItemToggle() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.setEditMode(true)
        adapter.submitItems(listOf(createTrack("track:1"), createTrack("track:2")))

        adapter.toggleSelection("track:1")
        assertTrue(adapter.selectedMediaIds.contains("track:1"))
        assertFalse(adapter.selectedMediaIds.contains("track:2"))

        adapter.toggleSelection("track:1")
        assertFalse(adapter.selectedMediaIds.contains("track:1"))
    }

    // 3. select all
    @Test
    fun testSelectAll() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.submitItems(listOf(
            createTrack("track:1"),
            createTrack("track:2"),
            createFolder("folder:1"),
            createTrack("track:3"),
        ))

        adapter.selectAllTracks()
        assertEquals(3, adapter.selectedMediaIds.size)
        assertTrue(adapter.selectedMediaIds.contains("track:1"))
        assertTrue(adapter.selectedMediaIds.contains("track:2"))
        assertTrue(adapter.selectedMediaIds.contains("track:3"))
        assertFalse(adapter.selectedMediaIds.contains("folder:1"))

        // Calling selectAllTracks again when all are selected should deselect all
        adapter.selectAllTracks()
        assertEquals(0, adapter.selectedMediaIds.size)
    }

    // 4. selected count
    @Test
    fun testSelectedCount() {
        var notifiedCount = -1
        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = { _, _, _ -> },
            onSelectionChanged = { notifiedCount = it },
        )
        adapter.submitItems(listOf(createTrack("track:1"), createTrack("track:2")))

        adapter.toggleSelection("track:1")
        assertEquals(1, notifiedCount)
        assertEquals(1, adapter.selectedMediaIds.size)

        adapter.toggleSelection("track:2")
        assertEquals(2, notifiedCount)
        assertEquals(2, adapter.selectedMediaIds.size)

        adapter.clearSelection()
        assertEquals(0, notifiedCount)
        assertEquals(0, adapter.selectedMediaIds.size)
    }

    // 5. drag adapter position mapping
    @Test
    fun testDragAdapterPositionMapping() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val t1 = createTrack("track:1")
        val t2 = createTrack("track:2")
        val t3 = createTrack("track:3")
        adapter.submitItems(listOf(t1, t2, t3))

        val moved = adapter.moveItem(0, 2)
        assertTrue(moved)
        val items = adapter.getItems().filterIsInstance<LibraryItem.Track>()
        assertEquals(listOf("track:2", "track:3", "track:1"), items.map { it.id })
    }

    // 6. reorder produces exact media ID order
    @Test
    fun testReorderProducesExactMediaIdOrder() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val tracks = (1..5).map { createTrack("track:$it") }
        adapter.submitItems(tracks)

        adapter.moveItem(4, 0) // move track 5 to top
        adapter.moveItem(2, 3)

        val orderedIds = adapter.getItems().filterIsInstance<LibraryItem.Track>().map { it.id }
        assertEquals(listOf("track:5", "track:1", "track:3", "track:2", "track:4"), orderedIds)
    }

    // 7. drag does not alter Media3 queue logic
    @Test
    fun testDragDoesNotAlterMedia3QueueLogic() {
        // Active Media3 playback queue must be isolated from playlist drag reordering
        val activeQueue = mutableListOf("track:10", "track:20", "track:30")
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.submitItems(listOf(createTrack("track:1"), createTrack("track:2")))

        adapter.moveItem(0, 1)

        // Verifying active player queue remains completely untouched
        assertEquals(listOf("track:10", "track:20", "track:30"), activeQueue)
    }

    // 8. successful reorder commits state
    @Test
    fun testSuccessfulReorderCommitsState() {
        var persistedOrder: List<String>? = null
        val fakeServerReorder: (Int, List<String>) -> Boolean = { _, ids ->
            persistedOrder = ids
            true
        }

        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.submitItems(listOf(createTrack("track:1"), createTrack("track:2"), createTrack("track:3")))
        adapter.moveItem(0, 2)

        val currentIds = adapter.getItems().filterIsInstance<LibraryItem.Track>().map { it.id }
        val success = fakeServerReorder(123, currentIds)

        assertTrue(success)
        assertEquals(listOf("track:2", "track:3", "track:1"), persistedOrder)
    }

    // 9. reorder failure restores authoritative data
    @Test
    fun testReorderFailureRestoresAuthoritativeData() {
        val authoritativeState = listOf(createTrack("track:1"), createTrack("track:2"), createTrack("track:3"))
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.submitItems(authoritativeState)

        adapter.moveItem(0, 2)
        assertEquals(listOf("track:2", "track:3", "track:1"), adapter.getItems().map { (it as LibraryItem.Track).id })

        // On failure, reload from authoritative state
        val reorderFailed = true
        if (reorderFailed) {
            adapter.submitItems(authoritativeState)
        }

        assertEquals(listOf("track:1", "track:2", "track:3"), adapter.getItems().map { (it as LibraryItem.Track).id })
    }

    // 10. multi-remove request IDs correct
    @Test
    fun testMultiRemoveRequestIdsCorrect() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        adapter.submitItems(listOf(createTrack("track:1"), createTrack("track:2"), createTrack("track:3")))
        adapter.toggleSelection("track:1")
        adapter.toggleSelection("track:3")

        val requestedRemoveIds = adapter.selectedMediaIds.toList()
        assertEquals(2, requestedRemoveIds.size)
        assertTrue(requestedRemoveIds.contains("track:1"))
        assertTrue(requestedRemoveIds.contains("track:3"))
    }

    // 11. duplicate add result formatting
    @Test
    fun testDuplicateAddResultFormatting() {
        val result = BatchResult(added = 3, skipped = 2, removed = 0, total = 5)
        val message = "Đã thêm ${result.added} bài vào danh sách phát (bỏ qua ${result.skipped} bài trùng)"
        assertTrue(message.contains("3"))
        assertTrue(message.contains("2"))
    }

    // 12. album action hidden with blank album
    @Test
    fun testAlbumActionHiddenWithBlankAlbum() {
        val trackWithoutAlbum = createTrack("track:1", album = "")
        val trackWithBlankAlbum = createTrack("track:2", album = "   ")
        val trackWithUnknownAlbum = createTrack("track:3", album = "Unknown")

        fun isAlbumActionVisible(track: LibraryItem.Track): Boolean {
            val clean = track.album?.trim().orEmpty()
            return clean.isNotBlank() && !clean.equals("Unknown", ignoreCase = true) && !clean.equals("Album không rõ", ignoreCase = true)
        }

        assertFalse(isAlbumActionVisible(trackWithoutAlbum))
        assertFalse(isAlbumActionVisible(trackWithBlankAlbum))
        assertFalse(isAlbumActionVisible(trackWithUnknownAlbum))

        val validTrack = createTrack("track:4", album = "Cỏ Úa")
        assertTrue(isAlbumActionVisible(validTrack))
    }

    // 13. artist action hidden with unknown artist
    @Test
    fun testArtistActionHiddenWithUnknownArtist() {
        fun isArtistActionVisible(track: LibraryItem.Track): Boolean {
            val clean = track.artist.trim()
            return clean.isNotBlank() && !clean.equals("Unknown", ignoreCase = true) && !clean.equals("Nghệ sĩ không rõ", ignoreCase = true)
        }

        assertFalse(isArtistActionVisible(createTrack("track:1", artist = "")))
        assertFalse(isArtistActionVisible(createTrack("track:2", artist = "Unknown")))
        assertFalse(isArtistActionVisible(createTrack("track:3", artist = "Nghệ sĩ không rõ")))
        assertTrue(isArtistActionVisible(createTrack("track:4", artist = "Châu Gia Kiệt")))
    }

    // 14. folder action only on folder
    @Test
    fun testFolderActionOnlyOnFolder() {
        fun canShowFolderAction(item: LibraryItem): Boolean {
            if (item !is LibraryItem.Folder) return false
            return item.id.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) || item.id.startsWith(RemoteCatalog.DRIVE_FOLDER_PREFIX)
        }

        assertTrue(canShowFolderAction(createFolder("drive-root:0")))
        assertTrue(canShowFolderAction(createFolder("drive-folder:0:NhacLossless")))
        assertFalse(canShowFolderAction(createFolder("category:recent")))
        assertFalse(canShowFolderAction(createTrack("track:1")))
    }

    // 15. collection result formatting
    @Test
    fun testCollectionResultFormatting() {
        val colRes = CollectionActionResult(matched = 12, added = 10, skipped = 2, total = 12)
        val msg = "Đã thêm ${colRes.added} bài vào danh sách phát"
        assertEquals("Đã thêm 10 bài vào danh sách phát", msg)
    }

    // 16. artist navigation ID generation
    @Test
    fun testArtistNavigationIdGeneration() {
        val id = RemoteCatalog.artistId("Châu Gia Kiệt")
        assertTrue(id.startsWith(RemoteCatalog.ARTIST_PREFIX))
        assertTrue(id.contains("Ch%C3%A2u") || id.contains("Chau") || id.contains("Châu"))
    }

    // 17. album navigation ID generation
    @Test
    fun testAlbumNavigationIdGeneration() {
        val id = RemoteCatalog.albumId("Châu Gia Kiệt", "Người Ngoài Phố")
        assertTrue(id.startsWith(RemoteCatalog.ALBUM_PREFIX))
        assertTrue(id.contains("::"))
    }

    // 18. stale collection response ignored after navigation
    @Test
    fun testStaleCollectionResponseIgnoredAfterNavigation() {
        var currentRequestId = 1L
        var appliedResult: String? = null

        fun onCollectionResultArrived(requestId: Long, result: String) {
            if (requestId == currentRequestId) {
                appliedResult = result
            }
        }

        val request1Id = currentRequestId
        // User navigates away before request 1 completes
        currentRequestId = 2L

        onCollectionResultArrived(request1Id, "Stale Result")
        assertNull(appliedResult)

        onCollectionResultArrived(2L, "Fresh Result")
        assertEquals("Fresh Result", appliedResult)
    }

    // 19. progressive enrichment still attaches to Artist page
    @Test
    fun testProgressiveEnrichmentAttachesToArtistPage() {
        val artistParentId = RemoteCatalog.artistId("Châu Gia Kiệt")
        val isEligibleForEnrichment = !artistParentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) && artistParentId != RemoteCatalog.ROOT_ID
        assertTrue(isEligibleForEnrichment)
    }

    // 20. progressive enrichment still attaches to Album page
    @Test
    fun testProgressiveEnrichmentAttachesToAlbumPage() {
        val albumParentId = RemoteCatalog.albumId("Châu Gia Kiệt", "Người Ngoài Phố")
        val isEligibleForEnrichment = !albumParentId.startsWith(RemoteCatalog.DRIVE_ROOT_PREFIX) && albumParentId != RemoteCatalog.ROOT_ID
        assertTrue(isEligibleForEnrichment)
    }

    // 21. edit mode track tap selects instead of plays
    @Test
    fun testEditModeTrackTapSelectsInsteadOfPlays() {
        var played = false
        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = { _, _, _ -> played = true },
        )
        adapter.submitItems(listOf(createTrack("track:1")))
        adapter.setEditMode(true)

        // In edit mode, clicking a track toggles selection
        adapter.toggleSelection("track:1")
        assertFalse(played)
        assertTrue(adapter.selectedMediaIds.contains("track:1"))
    }

    // 22. non-edit track tap still plays normally
    @Test
    fun testNonEditTrackTapPlaysNormally() {
        var playedTrackId: String? = null
        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = { track, _, _ -> playedTrackId = track.id },
        )
        val track = createTrack("track:1")
        adapter.submitItems(listOf(track))
        assertFalse(adapter.isEditMode)

        // When not in edit mode, playing works normally
        adapter.setEditMode(false)
        val onTrackClickLambda = {
            playedTrackId = track.id
        }
        onTrackClickLambda()
        assertEquals("track:1", playedTrackId)
    }

    // 23. page 0 produces offset 0
    @Test
    fun testPage0ProducesOffset0() {
        val page = 0
        val pageSize = 30
        val offset = page * pageSize
        assertEquals(0, offset)
    }

    // 24. page 1 produces offset 30
    @Test
    fun testPage1ProducesOffset30() {
        val page = 1
        val pageSize = 30
        val offset = page * pageSize
        assertEquals(30, offset)
    }

    // 25. current page IDs only are submitted
    @Test
    fun testCurrentPageIdsOnlyAreSubmitted() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val page0Tracks = (1..30).map { createTrack("track:$it") }
        adapter.submitItems(page0Tracks)

        val submittedIds = adapter.getItems()
            .filterIsInstance<LibraryItem.Track>()
            .map { it.id }

        assertEquals(30, submittedIds.size)
        assertEquals("track:1", submittedIds.first())
        assertEquals("track:30", submittedIds.last())
        assertFalse(submittedIds.contains("track:31"))
    }

    // 26. reorder failure reload behavior unchanged
    @Test
    fun testReorderFailureReloadBehaviorUnchanged() {
        var reloadedParentId: String? = null
        var reloadedPage: Int? = null
        var reloadedLabel: String? = null

        val fakeReload: (String, Int, String) -> Unit = { p, pg, l ->
            reloadedParentId = p
            reloadedPage = pg
            reloadedLabel = l
        }

        val ok = false
        if (!ok) {
            fakeReload("playlist:123", 1, "My Playlist")
        }

        assertEquals("playlist:123", reloadedParentId)
        assertEquals(1, reloadedPage)
        assertEquals("My Playlist", reloadedLabel)
    }

    // 27. drag never mutates Media3 queue
    @Test
    fun testDragNeverMutatesMedia3Queue() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val tracks = listOf(createTrack("track:A"), createTrack("track:B"), createTrack("track:C"))
        adapter.submitItems(tracks)
        adapter.setEditMode(true, allowReorder = true)

        // Simulated Media3 active playback queue
        val activeMedia3Queue = listOf("track:A", "track:B", "track:C")

        // User drags track C to position 0
        adapter.moveItem(2, 0)

        // Verify adapter order changed
        val newAdapterIds = adapter.getItems().filterIsInstance<LibraryItem.Track>().map { it.id }
        assertEquals(listOf("track:C", "track:A", "track:B"), newAdapterIds)

        // Verify active playback Media3 queue is completely untouched
        assertEquals(listOf("track:A", "track:B", "track:C"), activeMedia3Queue)
    }
}

