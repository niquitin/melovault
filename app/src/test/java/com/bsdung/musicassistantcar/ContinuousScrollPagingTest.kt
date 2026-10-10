package com.bsdung.musicassistantcar

import androidx.media3.common.MediaItem
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.controller.ProgressController
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import com.bsdung.musicassistantcar.ui.util.AudioInfoFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousScrollPagingTest {

    private fun createTrack(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        album: String = "Album $id",
        durationMs: Long? = 185000L,
    ): LibraryItem.Track {
        val mediaItem = MediaItem.Builder().setMediaId(id).build()
        return LibraryItem.Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            artworkUri = null,
            durationMs = durationMs,
            isCurrent = false,
            isPlaying = false,
            mediaItem = mediaItem,
        )
    }

    @Test
    fun testAppendItemsPreservesExistingAndOrdersSequentially() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val page0 = (0 until 30).map { createTrack("track:$it", "Song $it") }
        adapter.submitItems(page0)
        assertEquals(30, adapter.itemCount)
        assertEquals("track:0", adapter.getItems().first().id)
        assertEquals("track:29", adapter.getItems().last().id)

        val page1 = (30 until 60).map { createTrack("track:$it", "Song $it") }
        adapter.appendItems(page1)
        assertEquals(60, adapter.itemCount)
        assertEquals("track:0", adapter.getItems()[0].id)
        assertEquals("track:29", adapter.getItems()[29].id)
        assertEquals("track:30", adapter.getItems()[30].id)
        assertEquals("track:59", adapter.getItems()[59].id)
    }

    @Test
    fun testShowAndHideFooter() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val initialItems = (0 until 10).map { createTrack("track:$it") }
        adapter.submitItems(initialItems)
        assertEquals(10, adapter.itemCount)

        // Show loading footer
        adapter.showFooter(isError = false)
        assertEquals(11, adapter.itemCount)
        val footer1 = adapter.getItems().last() as LibraryItem.LoadingFooter
        assertFalse(footer1.isError)
        assertNull(footer1.errorMessage)

        // Update to error footer with retry
        var retried = false
        adapter.showFooter(isError = true, errorMessage = "Lỗi kết nối", onRetry = { retried = true })
        assertEquals(11, adapter.itemCount)
        val footer2 = adapter.getItems().last() as LibraryItem.LoadingFooter
        assertTrue(footer2.isError)
        assertEquals("Lỗi kết nối", footer2.errorMessage)
        footer2.onRetry?.invoke()
        assertTrue(retried)

        // Hide footer
        adapter.hideFooter()
        assertEquals(10, adapter.itemCount)
        assertTrue(adapter.getItems().none { it is LibraryItem.LoadingFooter })
    }

    @Test
    fun testAppendItemsRemovesFooterAutomatically() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val page0 = (0 until 30).map { createTrack("track:$it") }
        adapter.submitItems(page0)

        adapter.showFooter(isError = false)
        assertEquals(31, adapter.itemCount)
        assertTrue(adapter.getItems().last() is LibraryItem.LoadingFooter)

        val page1 = (30 until 60).map { createTrack("track:$it") }
        adapter.appendItems(page1)
        assertEquals(60, adapter.itemCount)
        assertTrue(adapter.getItems().none { it is LibraryItem.LoadingFooter })
        assertEquals("track:30", adapter.getItems()[30].id)
        assertEquals("track:59", adapter.getItems()[59].id)
    }

    @Test
    fun testLegitimateDuplicateTracksPreservedInPlaylist() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val page0 = listOf(
            createTrack("track:101", "Song A"),
            createTrack("track:102", "Song B"),
            createTrack("track:101", "Song A"), // Duplicate track in playlist
        )
        adapter.submitItems(page0)
        assertEquals(3, adapter.itemCount)

        val page1 = listOf(
            createTrack("track:103", "Song C"),
            createTrack("track:102", "Song B"), // Duplicate across pages
        )
        adapter.appendItems(page1)
        assertEquals(5, adapter.itemCount)
        val ids = adapter.getItems().map { it.id }
        assertEquals(listOf("track:101", "track:102", "track:101", "track:103", "track:102"), ids)
    }

    @Test
    fun testPlaylistReorderProtectionAgainstDuplicates() {
        fun canSafelyReorder(trackIds: List<String>): Boolean {
            return trackIds.size == trackIds.distinct().size
        }

        val uniqueList = listOf("track:1", "track:2", "track:3", "track:4")
        assertTrue("Unique tracks must allow safe reorder", canSafelyReorder(uniqueList))

        val duplicateList = listOf("track:1", "track:2", "track:1", "track:3")
        assertFalse("Duplicate tracks must be guarded against backend rejection/corruption", canSafelyReorder(duplicateList))
    }

    @Test
    fun testDurationFormattedIntoSecondaryMetadata() {
        val durationMs = 225000L // 03:45
        val durationStr = ProgressController.formatTimeMs(durationMs)
        assertEquals("03:45", durationStr)

        val sourceLabel = "Drive"
        val audioInfo = AudioInfoFormatter.formatAudioInfo(
            format = "flac",
            sizeBytes = 45000000L,
            codec = "flac",
            bitrate = 1411,
            sampleRate = 96000,
            channels = 2,
        )
        assertNotNull(audioInfo)

        val combinedInfo = listOfNotNull(
            durationStr.takeIf { it.isNotBlank() },
            sourceLabel.takeIf { it.isNotBlank() },
            audioInfo?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        assertTrue(combinedInfo.startsWith("03:45 · Drive"))
        assertTrue(combinedInfo.contains("FLAC"))
    }

    @Test
    fun testSyntheticLargeCollectionContinuousAppendSimulation() {
        val totalCollectionSize = 136
        val pageSize = 30
        val allTracks = (0 until totalCollectionSize).map { createTrack("drive-file:$it", "Tình Yêu Tôi Hát #$it") }

        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        var currentLoadedPages = 0
        var isPagingEndReached = false

        // Page 0
        val p0 = allTracks.drop(currentLoadedPages * pageSize).take(pageSize)
        adapter.submitItems(p0)
        currentLoadedPages = 1
        assertEquals(30, adapter.itemCount)
        assertFalse(isPagingEndReached)

        // Pages 1 to 4
        while (!isPagingEndReached) {
            val chunk = allTracks.drop(currentLoadedPages * pageSize).take(pageSize)
            if (chunk.isEmpty()) {
                isPagingEndReached = true
            } else {
                currentLoadedPages++
                adapter.appendItems(chunk)
                if (chunk.size < pageSize || adapter.itemCount >= totalCollectionSize) {
                    isPagingEndReached = true
                }
            }
        }

        assertEquals(136, adapter.itemCount)
        assertEquals(5, currentLoadedPages)
        assertTrue(isPagingEndReached)
        assertEquals("drive-file:0", adapter.getItems().first().id)
        assertEquals("drive-file:135", adapter.getItems().last().id)
    }

    @Test
    fun testPagingPolicyPreventsScrollStormOnError() {
        val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = 30)
        val gen = policy.reset()

        // Page 0 succeeds
        assertTrue(policy.startLoading(gen))
        assertTrue(policy.onPageSuccess(gen, newItemsCount = 30))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.IDLE, policy.state)

        // User scrolls near bottom -> start loading Page 1
        assertTrue(policy.shouldFetchOnScroll(dy = 10, lastVisiblePosition = 27, totalItemCount = 30))
        assertTrue(policy.startLoading(gen))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.LOADING, policy.state)

        // Backend returns error (e.g. 500 or timeout)
        assertTrue(policy.onPageFailure(gen, "500 Server Error"))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.ERROR, policy.state)
        assertEquals("500 Server Error", policy.errorMessage)

        // User continues scrolling down: MUST NOT trigger load or storm network!
        assertFalse("Scroll must be blocked in ERROR state", policy.shouldFetchOnScroll(dy = 15, lastVisiblePosition = 27, totalItemCount = 30))
        assertFalse("Direct startLoading without retry must be rejected", policy.startLoading(gen, isRetry = false))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.ERROR, policy.state)

        // User clicks retry button on footer: explicit retry allowed
        assertTrue("Explicit retry must be accepted", policy.startLoading(gen, isRetry = true))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.LOADING, policy.state)

        // Retry succeeds
        assertTrue(policy.onPageSuccess(gen, newItemsCount = 30))
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.IDLE, policy.state)
    }

    @Test
    fun testPagingPolicyRejectsStaleCallbacksFromOlderGenerations() {
        val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = 30)
        val oldGen = policy.reset()

        assertTrue(policy.startLoading(oldGen))

        // User navigates away to another folder
        val newGen = policy.reset()
        assertTrue(newGen > oldGen)

        // Stale response arrives from oldGen
        assertFalse("Stale success must be ignored", policy.onPageSuccess(oldGen, newItemsCount = 30))
        assertFalse("Stale failure must be ignored", policy.onPageFailure(oldGen, "Timeout"))
        assertEquals(0, policy.currentLoadedPages)
        assertEquals(com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy.State.IDLE, policy.state)
    }

    @Test
    fun testPagingPolicyEndReachedStopsFurtherFetches() {
        val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = 30)
        val gen = policy.reset()

        // Page returns fewer than pageSize items -> reached end
        assertTrue(policy.startLoading(gen))
        assertTrue(policy.onPageSuccess(gen, newItemsCount = 15, totalInCollection = 45, loadedItemsCount = 45))
        assertTrue(policy.isEndReached)

        // Further scroll events must never fetch
        assertFalse(policy.shouldFetchOnScroll(dy = 20, lastVisiblePosition = 44, totalItemCount = 45))
        assertFalse(policy.startLoading(gen))
    }

    @Test
    fun testPlaylistReorderGuardContractAndRollback() {
        val guard = com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard

        // 1. No-op drag (items dragged and returned to original order)
        val originalIds = listOf("track:101", "track:102", "track:103", "track:104")
        val noOpDecision = guard.evaluateReorder(
            playlistId = 42,
            initialTrackIds = originalIds,
            currentTrackIds = originalIds,
            isPagingLoading = false,
        )
        assertTrue(noOpDecision is com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard.Decision.NoOp)

        // 2. In-flight paging fetch reject
        val reorderedIds = listOf("track:102", "track:101", "track:103", "track:104")
        val inFlightDecision = guard.evaluateReorder(
            playlistId = 42,
            initialTrackIds = originalIds,
            currentTrackIds = reorderedIds,
            isPagingLoading = true,
        )
        assertTrue(inFlightDecision is com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard.Decision.RejectedInFlightPaging)

        // 3. Duplicate track IDs in playlist reject
        val duplicateIds = listOf("track:102", "track:101", "track:102", "track:104")
        val dupDecision = guard.evaluateReorder(
            playlistId = 42,
            initialTrackIds = originalIds,
            currentTrackIds = duplicateIds,
            isPagingLoading = false,
        )
        assertTrue(dupDecision is com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard.Decision.RejectedDuplicateTracks)

        // 4. Multi-page prefix reorder succeeds with offset = 0
        val multiPagePrefix = (1..60).map { "track:$it" }
        val multiPageReordered = listOf("track:2") + (1..60).filter { it != 2 }.map { "track:$it" }
        val proceedDecision = guard.evaluateReorder(
            playlistId = 42,
            initialTrackIds = multiPagePrefix,
            currentTrackIds = multiPageReordered,
            isPagingLoading = false,
        )
        assertTrue(proceedDecision is com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard.Decision.Proceed)
        val proceed = proceedDecision as com.bsdung.musicassistantcar.ui.util.PlaylistReorderGuard.Decision.Proceed
        assertEquals(42, proceed.playlistId)
        assertEquals(0, proceed.offset)
        assertEquals(multiPageReordered, proceed.trackIds)

        // 5. Non-track target rejection (e.g. footer)
        assertFalse(guard.canMove(isEditMode = true, canReorder = true, fromIsTrack = true, toIsTrack = false))
        assertTrue(guard.canMove(isEditMode = true, canReorder = true, fromIsTrack = true, toIsTrack = true))

        // 6. Rollback snapshot simulation on adapter
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val snapshot = (1..10).map { createTrack("track:$it", "Song $it") }
        adapter.submitItems(snapshot)

        // Simulate local drag mutation
        adapter.moveItem(0, 1)
        assertEquals("track:2", adapter.getItems()[0].id)

        // Rollback to snapshot on failure restores exact original state
        adapter.submitItems(snapshot)
        assertEquals("track:1", adapter.getItems()[0].id)
        assertEquals(10, adapter.itemCount)
    }

    @Test
    fun testContentItemCountExcludesFooterWhilePreservingContent() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val dummyMediaItem = MediaItem.Builder().setMediaId("dummy").build()

        val category = LibraryItem.Category("cat:1", "Category", "Sub", 0, dummyMediaItem)
        val folder = LibraryItem.Folder("folder:1", "Folder", "Sub", dummyMediaItem)
        val track1 = createTrack("track:1", "Song 1")
        val track2 = createTrack("track:2", "Song 2")
        val duplicateTrack1 = createTrack("track:1", "Song 1 (Dup)")

        adapter.submitItems(listOf(category, folder, track1, track2, duplicateTrack1))
        assertEquals(5, adapter.itemCount)
        assertEquals(5, adapter.getContentItemCount())
        assertFalse(adapter.hasFooter())
        assertEquals(5, adapter.getContentItems().size)

        // Add loading footer
        adapter.showFooter(isError = false)
        assertEquals(6, adapter.itemCount) // UI itemCount includes footer
        assertEquals(5, adapter.getContentItemCount()) // content count strictly excludes footer
        assertTrue(adapter.hasFooter())
        assertEquals(5, adapter.getContentItems().size)

        // Remove footer
        adapter.hideFooter()
        assertEquals(5, adapter.itemCount)
        assertEquals(5, adapter.getContentItemCount())
        assertFalse(adapter.hasFooter())
    }

    @Test
    fun testProductionPagingWithFooterCountBoundaryMilestones() {
        val milestones = listOf(30, 31, 60, 61, 90, 91, 136, 151)
        val pageSize = 30

        for (total in milestones) {
            val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
            val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = pageSize)
            val gen = policy.reset()

            // Page 0
            val page0Size = minOf(pageSize, total)
            val page0 = (0 until page0Size).map { createTrack("track:$it", "Song $it") }
            adapter.submitItems(page0)

            policy.onPageSuccess(
                callbackGeneration = gen,
                newItemsCount = page0.size,
                totalInCollection = total,
                loadedItemsCount = adapter.getContentItemCount(),
            )

            if (total <= pageSize) {
                assertTrue("Total $total <= pageSize should reach end on page 0", policy.isEndReached)
            } else {
                assertFalse("Total $total > pageSize should NOT reach end on page 0", policy.isEndReached)
            }

            // Continuous scroll loop
            var pageIndex = 1
            while (!policy.isEndReached) {
                assertTrue(
                    "Should fetch on scroll for page $pageIndex (total=$total)",
                    policy.shouldFetchOnScroll(dy = 10, lastVisiblePosition = adapter.itemCount - 1, totalItemCount = adapter.itemCount)
                )
                assertTrue(policy.startLoading(gen))

                // Production: show footer while in-flight
                adapter.showFooter(isError = false)
                assertTrue(adapter.hasFooter())
                assertEquals("itemCount must be content + 1 footer", adapter.getContentItemCount() + 1, adapter.itemCount)

                // Fetch next page
                val from = adapter.getContentItemCount()
                val to = minOf(from + pageSize, total)
                val batch = (from until to).map { createTrack("track:$it", "Song $it") }

                // Production sequence: appendItems removes footer and appends batch
                adapter.appendItems(batch)
                assertFalse("Footer must be removed after appendItems", adapter.hasFooter())

                val currentLoaded = adapter.getContentItemCount()
                policy.onPageSuccess(
                    callbackGeneration = gen,
                    newItemsCount = batch.size,
                    totalInCollection = total,
                    loadedItemsCount = currentLoaded,
                )
                adapter.hideFooter()
                pageIndex++
            }

            // Verify final state
            assertTrue("Policy must reach end for total $total", policy.isEndReached)
            assertEquals("Exact total items must be loaded for total $total", total, adapter.getContentItemCount())
            assertEquals("Adapter itemCount must match total for total $total", total, adapter.itemCount)
            assertFalse(policy.shouldFetchOnScroll(dy = 10, lastVisiblePosition = adapter.itemCount - 1, totalItemCount = adapter.itemCount))

            // Verify sequential ordering of all items from first to last
            val loadedItems = adapter.getItems().filterIsInstance<LibraryItem.Track>()
            assertEquals(total, loadedItems.size)
            assertEquals("track:0", loadedItems.first().id)
            assertEquals("track:${total - 1}", loadedItems.last().id)
        }
    }

    @Test
    fun testRegressionTotal61FooterOffByOneBugReproduction() {
        val total = 61
        val pageSize = 30

        // --- 1. REPRODUCE BUGGY BEHAVIOR (Old code) ---
        run {
            val buggyAdapter = LibraryAdapter({}, {}, { _, _, _ -> })
            val buggyPolicy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = pageSize)
            val gen = buggyPolicy.reset()

            // Page 0
            val page0 = (0 until 30).map { createTrack("track:$it") }
            buggyAdapter.submitItems(page0)
            buggyPolicy.onPageSuccess(gen, newItemsCount = 30, totalInCollection = total, loadedItemsCount = 30)
            assertFalse(buggyPolicy.isEndReached)

            // User scrolls -> show footer
            buggyAdapter.showFooter(isError = false)
            assertEquals(31, buggyAdapter.itemCount) // 30 tracks + 1 footer

            // Page 1 arrives (30 tracks)
            val page1 = (30 until 60).map { createTrack("track:$it") }

            // OLD BUGGY CALCULATION: loadedItemsCount = buggyAdapter.itemCount + page1.size = 31 + 30 = 61!
            val buggyLoadedCount = buggyAdapter.itemCount + page1.size
            assertEquals(61, buggyLoadedCount)

            // Buggy call BEFORE appendItems
            buggyPolicy.onPageSuccess(gen, newItemsCount = page1.size, totalInCollection = total, loadedItemsCount = buggyLoadedCount)
            assertTrue("BUG: Policy incorrectly marks END_REACHED at 60 items!", buggyPolicy.isEndReached)

            buggyAdapter.appendItems(page1)
            buggyAdapter.hideFooter()

            // Notice: Only 60 items loaded, but policy is at END_REACHED! Item 60 (track:60, the 61st song) is LOST!
            assertEquals(60, buggyAdapter.itemCount)
            assertFalse("BUG: Scroll is permanently blocked, 61st song is never reachable!",
                buggyPolicy.shouldFetchOnScroll(10, 59, 60))
        }

        // --- 2. VERIFY FIXED PRODUCTION BEHAVIOR ---
        run {
            val fixedAdapter = LibraryAdapter({}, {}, { _, _, _ -> })
            val fixedPolicy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = pageSize)
            val gen = fixedPolicy.reset()

            // Page 0
            val page0 = (0 until 30).map { createTrack("track:$it") }
            fixedAdapter.submitItems(page0)
            fixedPolicy.onPageSuccess(gen, newItemsCount = 30, totalInCollection = total, loadedItemsCount = fixedAdapter.getContentItemCount())
            assertFalse(fixedPolicy.isEndReached)

            // User scrolls -> show footer
            fixedAdapter.showFooter(isError = false)
            assertEquals(31, fixedAdapter.itemCount)
            assertEquals(30, fixedAdapter.getContentItemCount())

            // Page 1 arrives (30 tracks)
            val page1 = (30 until 60).map { createTrack("track:$it") }

            // FIXED PRODUCTION LOGIC: appendItems first, then count accurate content items
            fixedAdapter.appendItems(page1)
            val currentLoaded = fixedAdapter.getContentItemCount()
            assertEquals(60, currentLoaded)

            fixedPolicy.onPageSuccess(gen, newItemsCount = page1.size, totalInCollection = total, loadedItemsCount = currentLoaded)
            fixedAdapter.hideFooter()

            // FIX VERIFIED: Policy correctly stays IDLE so Page 2 can be fetched!
            assertFalse("FIX: Policy must NOT mark END_REACHED when only 60 of 61 items loaded", fixedPolicy.isEndReached)
            assertTrue(fixedPolicy.shouldFetchOnScroll(10, 59, 60))

            // Page 2 arrives (the 61st song!)
            assertTrue(fixedPolicy.startLoading(gen))
            fixedAdapter.showFooter(isError = false)
            val page2 = listOf(createTrack("track:60")) // 61st song

            fixedAdapter.appendItems(page2)
            val finalLoaded = fixedAdapter.getContentItemCount()
            assertEquals(61, finalLoaded)

            fixedPolicy.onPageSuccess(gen, newItemsCount = page2.size, totalInCollection = total, loadedItemsCount = finalLoaded)
            fixedAdapter.hideFooter()

            assertTrue("FIX: Policy marks END_REACHED once all 61 items are loaded", fixedPolicy.isEndReached)
            assertEquals(61, fixedAdapter.itemCount)
            assertEquals("track:60", fixedAdapter.getItems().last().id)
            assertFalse(fixedPolicy.shouldFetchOnScroll(10, 60, 61))
        }
    }

    @Test
    fun testLargeCollection11001ItemsReachesAllItems() {
        val total = 11001
        val pageSize = 30
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = pageSize)
        val gen = policy.reset()

        // Page 0
        val page0 = (0 until 30).map { createTrack("track:$it") }
        adapter.submitItems(page0)
        policy.onPageSuccess(gen, newItemsCount = 30, totalInCollection = total, loadedItemsCount = adapter.getContentItemCount())

        while (!policy.isEndReached) {
            policy.startLoading(gen)
            adapter.showFooter(isError = false)
            val from = adapter.getContentItemCount()
            val to = minOf(from + pageSize, total)
            val batch = (from until to).map { createTrack("track:$it") }
            adapter.appendItems(batch)
            policy.onPageSuccess(gen, newItemsCount = batch.size, totalInCollection = total, loadedItemsCount = adapter.getContentItemCount())
            adapter.hideFooter()
        }

        assertTrue(policy.isEndReached)
        assertEquals(11001, adapter.getContentItemCount())
        assertEquals("track:0", adapter.getItems().first().id)
        assertEquals("track:11000", adapter.getItems().last().id)
    }

    @Test
    fun testUnknownTotalAndShortPagePagination() {
        val adapter = LibraryAdapter({}, {}, { _, _, _ -> })
        val policy = com.bsdung.musicassistantcar.ui.util.ContinuousPagingPolicy(pageSize = 30)
        val gen = policy.reset()

        // Page 0 (30 items, unknown total = -1)
        adapter.submitItems((0 until 30).map { createTrack("track:$it") })
        policy.onPageSuccess(gen, newItemsCount = 30, totalInCollection = -1, loadedItemsCount = 30)
        assertFalse(policy.isEndReached)

        // Page 1 (30 items, unknown total = -1)
        policy.startLoading(gen)
        adapter.showFooter(isError = false)
        adapter.appendItems((30 until 60).map { createTrack("track:$it") })
        policy.onPageSuccess(gen, newItemsCount = 30, totalInCollection = -1, loadedItemsCount = adapter.getContentItemCount())
        adapter.hideFooter()
        assertFalse(policy.isEndReached)

        // Page 2 (14 items < 30 pageSize -> short page indicates end)
        policy.startLoading(gen)
        adapter.showFooter(isError = false)
        adapter.appendItems((60 until 74).map { createTrack("track:$it") })
        policy.onPageSuccess(gen, newItemsCount = 14, totalInCollection = -1, loadedItemsCount = adapter.getContentItemCount())
        adapter.hideFooter()

        assertTrue("Short page must automatically trigger END_REACHED", policy.isEndReached)
        assertEquals(74, adapter.getContentItemCount())
        assertEquals("track:73", adapter.getItems().last().id)
        assertFalse(policy.shouldFetchOnScroll(10, 73, 74))
    }

    @Test
    fun testUnifiedLibraryDialogPaginationContractWithTotal61() {
        val totalItems = 61
        val pageSize = 30
        val displayedItems = mutableListOf<String>()

        // Page 0
        var currentOffset = 0
        val page0 = (currentOffset until minOf(currentOffset + pageSize, totalItems)).map { "item:$it" }
        displayedItems.addAll(page0)
        assertEquals(30, displayedItems.size)

        // Continuous scroll condition
        fun canFetchNext(): Boolean = displayedItems.size < totalItems
        assertTrue(canFetchNext())

        // Page 1
        currentOffset = displayedItems.size
        val page1 = (currentOffset until minOf(currentOffset + pageSize, totalItems)).map { "item:$it" }
        displayedItems.addAll(page1)
        assertEquals(60, displayedItems.size)
        assertTrue(canFetchNext()) // Still 1 item left!

        // Page 2
        currentOffset = displayedItems.size
        val page2 = (currentOffset until minOf(currentOffset + pageSize, totalItems)).map { "item:$it" }
        displayedItems.addAll(page2)
        assertEquals(61, displayedItems.size)
        assertFalse(canFetchNext()) // Reached 61, no further request

        assertEquals("item:0", displayedItems.first())
        assertEquals("item:60", displayedItems.last())
    }
}
