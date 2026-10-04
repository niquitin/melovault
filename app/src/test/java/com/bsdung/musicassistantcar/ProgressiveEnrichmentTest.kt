package com.bsdung.musicassistantcar

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsdung.musicassistantcar.data.MediaTechnicalSummary
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.controller.EnrichmentScheduler
import com.bsdung.musicassistantcar.ui.controller.ProgressiveEnrichmentController
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProgressiveEnrichmentTest {

    private class TestEnrichmentScheduler : EnrichmentScheduler {
        val postedRunnables = mutableListOf<Runnable>()
        val delayedRunnables = mutableListOf<Pair<Runnable, Long>>()
        val cancelledRunnables = mutableListOf<Runnable>()

        override fun post(runnable: Runnable) {
            postedRunnables.add(runnable)
            runnable.run()
        }

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            delayedRunnables.add(runnable to delayMs)
        }

        override fun removeCallbacks(runnable: Runnable) {
            cancelledRunnables.add(runnable)
            delayedRunnables.removeAll { it.first == runnable }
            postedRunnables.remove(runnable)
        }

        fun triggerPendingDelayed() {
            val list = delayedRunnables.map { it.first }.toList()
            delayedRunnables.clear()
            list.forEach { it.run() }
        }
    }

    private class DirectExecutor : ExecutorService by Executors.newSingleThreadExecutor() {
        override fun submit(task: Runnable): java.util.concurrent.Future<*> {
            task.run()
            val future = java.util.concurrent.CompletableFuture<Void>()
            future.complete(null)
            return future
        }
    }

    private class FakeFetcher {
        var batchResponse: Map<String, MediaTechnicalSummary>? = null
        var lastRequestedIds: List<String>? = null
        var callCount = 0

        fun fetch(mediaIds: List<String>): Map<String, MediaTechnicalSummary>? {
            callCount++
            lastRequestedIds = mediaIds
            return batchResponse
        }
    }

    private fun createTrack(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        format: String? = null,
        sampleRate: Int? = null,
        bitrate: Int? = null,
        channels: Int? = null,
        sizeBytes: Long? = null,
    ): LibraryItem.Track {
        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()
        return LibraryItem.Track(
            id = id,
            title = title,
            artist = artist,
            album = "Album $id",
            artworkUri = "https://example.com/art.jpg",
            durationMs = 180000L,
            isCurrent = false,
            isPlaying = false,
            mediaItem = mediaItem,
            format = format,
            sampleRate = sampleRate,
            bitrate = bitrate,
            channels = channels,
            sizeBytes = sizeBytes,
        )
    }

    // 1. visible range calculation
    @Test
    fun test_01_visible_range_calculation() {
        val items = (0 until 20).map { createTrack("t$it") }
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        val result = controller.computeCandidates(
            firstVisible = 5,
            lastVisible = 10,
            items = items,
            prefetchMargin = 0,
        )

        val expected = (5..10).map { "t$it" }
        assertEquals(expected, result.idsToFetch)
        assertTrue(result.cacheHits.isEmpty())
    }

    // 2. prefetch margin
    @Test
    fun test_02_prefetch_margin() {
        val items = (0 until 20).map { createTrack("t$it") }
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        // visible 5..10 with prefetch margin 2 => range 3..12
        val result = controller.computeCandidates(
            firstVisible = 5,
            lastVisible = 10,
            items = items,
            prefetchMargin = 2,
        )
        val expected = (3..12).map { "t$it" }
        assertEquals(expected, result.idsToFetch)

        // Boundary test: visible 1..3 with prefetch 5 => clamped to 0..8
        val boundaryResult = controller.computeCandidates(
            firstVisible = 1,
            lastVisible = 3,
            items = items,
            prefetchMargin = 5,
        )
        val boundaryExpected = (0..8).map { "t$it" }
        assertEquals(boundaryExpected, boundaryResult.idsToFetch)
    }

    // 3. duplicate IDs removed
    @Test
    fun test_03_duplicate_ids_removed() {
        val items = listOf(
            createTrack("dup1"),
            createTrack("dup2"),
            createTrack("dup1"),
            createTrack("dup3"),
            createTrack("dup2"),
        )
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        val result = controller.computeCandidates(0, 4, items, prefetchMargin = 0)
        assertEquals(listOf("dup1", "dup2", "dup3"), result.idsToFetch)
    }

    // 4. max batch 50
    @Test
    fun test_04_max_batch_50() {
        val items = (0 until 80).map { createTrack("t$it") }
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        val result = controller.computeCandidates(0, 79, items, prefetchMargin = 0, maxBatch = 50)
        assertEquals(50, result.idsToFetch.size)
        assertEquals((0 until 50).map { "t$it" }, result.idsToFetch)
    }

    // 5. cache hit avoids request
    @Test
    fun test_05_cache_hit_avoids_request() {
        val items = listOf(createTrack("t1"), createTrack("t2"))
        val scheduler = TestEnrichmentScheduler()
        val updatedMap = mutableMapOf<String, MediaTechnicalSummary>()

        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { id, summary -> updatedMap[id] = summary },
            getCurrentItems = { items },
        )

        val cachedSummary = MediaTechnicalSummary(format = "FLAC", sampleRate = 44100, bitrate = 912000)
        controller.putInCache("t1", cachedSummary)

        val candidates = controller.computeCandidates(0, 1, items, prefetchMargin = 0)
        assertEquals(1, candidates.cacheHits.size)
        assertEquals("t1", candidates.cacheHits[0].first)
        assertEquals(listOf("t2"), candidates.idsToFetch)

        // scheduleEnrichment should apply cache hit immediately
        controller.scheduleEnrichment(0, 1, items, prefetchMargin = 0)
        assertEquals(cachedSummary, updatedMap["t1"])
    }

    // 6. negative cache avoids repeated request
    @Test
    fun test_06_negative_cache_avoids_repeated_request() {
        val items = listOf(createTrack("t_no_info"), createTrack("t_regular"))
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        controller.putInNegativeCache("t_no_info")
        assertTrue(controller.isInNegativeCache("t_no_info"))

        val candidates = controller.computeCandidates(0, 1, items, prefetchMargin = 0)
        assertEquals(listOf("t_regular"), candidates.idsToFetch)
        assertTrue(candidates.cacheHits.isEmpty())
    }

    // 7. stale generation response ignored
    @Test
    fun test_07_stale_generation_response_ignored() {
        val currentItems = listOf(createTrack("t_new_screen"))
        val scheduler = TestEnrichmentScheduler()
        val updatedTracks = mutableListOf<String>()

        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { id, _ -> updatedTracks.add(id) },
            getCurrentItems = { currentItems },
        )

        // Request initiated during generation 0
        val requestGen = controller.getGeneration()
        // User navigated: generation increments to 1
        controller.nextGeneration()

        val response = mapOf(
            "t_old_screen" to MediaTechnicalSummary(format = "FLAC", sampleRate = 44100, bitrate = 900000)
        )

        // Response arrives for stale generation 0
        controller.handleBatchResponse(requestGen, listOf("t_old_screen"), response)

        // Track should NOT update UI for old screen
        assertFalse(updatedTracks.contains("t_old_screen"))
        // But cached for future visits
        assertNotNull(controller.getFromCache("t_old_screen"))
    }

    // 8. current dataset response applied
    @Test
    fun test_08_current_dataset_response_applied() {
        val track1 = createTrack("t1")
        val currentItems = listOf(track1)
        val scheduler = TestEnrichmentScheduler()
        val updatedTracks = mutableMapOf<String, MediaTechnicalSummary>()

        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { id, summary -> updatedTracks[id] = summary },
            getCurrentItems = { currentItems },
        )

        val currentGen = controller.getGeneration()
        val summary1 = MediaTechnicalSummary(format = "MP3", bitrate = 320000, sampleRate = 44100)
        controller.handleBatchResponse(currentGen, listOf("t1"), mapOf("t1" to summary1))

        assertEquals(summary1, updatedTracks["t1"])
        assertEquals(summary1, controller.getFromCache("t1"))

        // Also test: if generation changed but track is still in active dataset, it is updated
        controller.nextGeneration()
        val summaryUpdate = MediaTechnicalSummary(format = "FLAC", bitrate = 900000, sampleRate = 48000)
        controller.handleBatchResponse(currentGen, listOf("t1"), mapOf("t1" to summaryUpdate))
        assertEquals(summaryUpdate, updatedTracks["t1"])
    }

    // 9. technical info merge by mediaId
    @Test
    fun test_09_technical_info_merge_by_mediaId() {
        val initialTrack = createTrack("target_id", title = "Original Song", artist = "Original Artist")
        val otherTrack = createTrack("other_id", title = "Other Song", artist = "Other Artist")

        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = { _, _, _ -> },
        )
        adapter.submitItems(listOf(initialTrack, otherTrack))

        val summary = MediaTechnicalSummary(
            format = "FLAC",
            codec = "FLAC",
            sampleRate = 44100,
            bitrate = 912000,
            channels = 2,
            bitsPerSample = 16,
            sizeBytes = 30800000L,
        )

        val changed = adapter.updateTrackTechnicalInfo("target_id", summary)
        assertTrue(changed)

        val updatedItems = adapter.getItems()
        val updatedTrack = updatedItems.first { it.id == "target_id" } as LibraryItem.Track

        // Technical fields updated
        assertEquals("FLAC", updatedTrack.format)
        assertEquals("FLAC", updatedTrack.codec)
        assertEquals(44100, updatedTrack.sampleRate)
        assertEquals(912000, updatedTrack.bitrate)
        assertEquals(2, updatedTrack.channels)
        assertEquals(30800000L, updatedTrack.sizeBytes)

        // Metadata preserved
        assertEquals("Original Song", updatedTrack.title)
        assertEquals("Original Artist", updatedTrack.artist)
        assertEquals("Album target_id", updatedTrack.album)
        assertEquals("https://example.com/art.jpg", updatedTrack.artworkUri)
        assertEquals(180000L, updatedTrack.durationMs)

        // Other track untouched
        val untouched = updatedItems.first { it.id == "other_id" } as LibraryItem.Track
        assertNull(untouched.format)
    }

    // 10. null technical fields safely omitted
    @Test
    fun test_10_null_technical_fields_safely_omitted() {
        val initialTrack = createTrack("t_nulls", format = "MP3", sampleRate = 44100, bitrate = 128000)
        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = { _, _, _ -> },
        )
        adapter.submitItems(listOf(initialTrack))

        // Summary with partial nulls
        val partialSummary = MediaTechnicalSummary(format = null, sampleRate = 48000, bitrate = null)
        val changed = adapter.updateTrackTechnicalInfo("t_nulls", partialSummary)
        assertTrue(changed)

        val updated = adapter.getItems()[0] as LibraryItem.Track
        assertEquals("MP3", updated.format) // preserved
        assertEquals(48000, updated.sampleRate) // updated
        assertEquals(128000, updated.bitrate) // preserved

        // If summary has all nulls/zero, hasAnyTechnicalInfo should be false and put in negative cache
        val emptySummary = MediaTechnicalSummary()
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { listOf(initialTrack) },
        )

        assertFalse(controller.hasAnyTechnicalInfo(emptySummary))
        controller.handleBatchResponse(0L, listOf("t_empty"), mapOf("t_empty" to emptySummary))
        assertTrue(controller.isInNegativeCache("t_empty"))
        assertNull(controller.getFromCache("t_empty"))
    }

    // 11. cache bounded eviction
    @Test
    fun test_11_cache_bounded_eviction() {
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            maxCacheEntries = 3,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { emptyList() },
        )

        val s = MediaTechnicalSummary(format = "FLAC", sampleRate = 44100)
        controller.putInCache("c1", s)
        controller.putInCache("c2", s)
        controller.putInCache("c3", s)

        assertNotNull(controller.getFromCache("c1"))
        assertNotNull(controller.getFromCache("c2"))
        assertNotNull(controller.getFromCache("c3"))

        // Inserting 4th item should evict eldest entry ("c1")
        controller.putInCache("c4", s)
        assertNull(controller.getFromCache("c1"))
        assertNotNull(controller.getFromCache("c2"))
        assertNotNull(controller.getFromCache("c3"))
        assertNotNull(controller.getFromCache("c4"))
    }

    // 12. navigation increments generation
    @Test
    fun test_12_navigation_increments_generation() {
        val items = listOf(createTrack("t1"))
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { items },
        )

        assertEquals(0L, controller.getGeneration())
        controller.scheduleEnrichment(0, 0, items)
        assertEquals(1, scheduler.delayedRunnables.size)

        // Navigating calls nextGeneration()
        val gen1 = controller.nextGeneration()
        assertEquals(1L, gen1)
        assertEquals(1L, controller.getGeneration())

        // Debounce runnable was cancelled
        assertTrue(scheduler.delayedRunnables.isEmpty())
        assertTrue(scheduler.cancelledRunnables.isNotEmpty())

        val gen2 = controller.nextGeneration()
        assertEquals(2L, gen2)
    }

    // 13. search result enrichment occurs only after result render
    @Test
    fun test_13_search_result_enrichment_occurs_only_after_result_render() {
        var currentItems: List<LibraryItem> = emptyList()
        val scheduler = TestEnrichmentScheduler()
        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> },
            getCurrentItems = { currentItems },
        )

        // 1. Search started -> navigation increments generation
        val searchGen = controller.nextGeneration()
        assertEquals(1L, searchGen)

        // 2. Before results render, scheduleEnrichment with empty items does nothing
        controller.scheduleEnrichment(-1, -1, currentItems)
        assertTrue(scheduler.delayedRunnables.isEmpty())

        // 3. Search results arrive and are rendered into items
        val searchResults = listOf(createTrack("search_1"), createTrack("search_2"))
        currentItems = searchResults

        // 4. Enrichment scheduled after render
        controller.scheduleEnrichment(0, 1, currentItems)
        assertEquals(1, scheduler.delayedRunnables.size)
    }

    // 14. backend failure preserves base rows
    @Test
    fun test_14_backend_failure_preserves_base_rows() {
        val track = createTrack("t_fail", title = "Original Song")
        val currentItems = listOf(track)
        val scheduler = TestEnrichmentScheduler()
        var updateCalled = false

        val controller = ProgressiveEnrichmentController(
            executor = DirectExecutor(),
            scheduler = scheduler,
            onUpdateTrack = { _, _ -> updateCalled = true },
            getCurrentItems = { currentItems },
        )

        // Simulating null response (network failure / server 500)
        controller.handleBatchResponse(
            requestGeneration = controller.getGeneration(),
            requestedIds = listOf("t_fail"),
            response = null,
        )

        // No crash, callback not invoked, caches untouched
        assertFalse(updateCalled)
        assertNull(controller.getFromCache("t_fail"))
        assertFalse(controller.isInNegativeCache("t_fail"))
        assertEquals("Original Song", track.title)
    }
}
