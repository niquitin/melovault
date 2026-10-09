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
}
