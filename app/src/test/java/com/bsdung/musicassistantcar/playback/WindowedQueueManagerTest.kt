package com.bsdung.musicassistantcar.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CompactIndexTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class WindowedQueueManagerTest {

    private val fakeApi = object : CarApiClient() {
        override fun hasValidConfiguration(): Boolean = true
        override fun probe(): String = "http://fake.local:8095"
    }

    private val uiCatalog = RemoteCatalog(
        api = fakeApi,
        localRepoSupplier = { null },
        downloadManagerSupplier = { null },
        playlistSyncCoordinatorSupplier = { null },
    )

    private fun generateTracks(count: Int): List<CompactIndexTrack> {
        return (1..count).map { id ->
            CompactIndexTrack(
                id = id,
                title = "Track $id",
                artist = "Artist ${id % 50}",
                album = "Album ${id % 100}",
                duration = 180.0 + (id % 60),
            )
        }
    }

    @Test
    fun test11188TracksFisherYatesShuffleIntegrity() {
        val manager = WindowedQueueManager()
        val original = generateTracks(11_188)
        assertEquals(11_188, original.size)

        val initialWindow = manager.setupQueue(
            tracks = original,
            shuffle = true,
            uiCatalog = uiCatalog,
        )

        // 1. Initial window size must be exactly WINDOW_SIZE (25)
        assertEquals(WindowedQueueManager.WINDOW_SIZE, initialWindow.size)
        assertEquals(25, manager.getLoadedCount())

        // 2. Master list must contain all 11,188 tracks
        val master = manager.getMasterPermutation()
        assertEquals(11_188, master.size)
        assertEquals(11_188, manager.getMasterPermutationSize())

        // 3. True permutation guarantee: zero duplicates, set of IDs identical to original
        val originalIds = original.map { it.id }.toSet()
        val shuffledIds = master.map { it.id }.toSet()
        assertEquals(11_188, shuffledIds.size)
        assertEquals(originalIds, shuffledIds)

        // 4. Randomness guarantee: order is not identical to 1..11188
        val originalOrder = original.map { it.id }
        val masterOrder = master.map { it.id }
        assertNotEquals(originalOrder, masterOrder)

        // 5. Initial window mediaItems must match the first 25 of the master permutation
        for (i in 0 until 25) {
            val expectedMediaId = "${RemoteCatalog.TRACK_PREFIX}${master[i].id}"
            assertEquals(expectedMediaId, initialWindow[i].mediaId)
        }
    }

    @Test
    fun testSequentialOrderWhenShuffleIsFalse() {
        val manager = WindowedQueueManager()
        val original = generateTracks(100)

        val initialWindow = manager.setupQueue(
            tracks = original,
            shuffle = false,
            uiCatalog = uiCatalog,
        )

        assertEquals(25, initialWindow.size)
        val master = manager.getMasterPermutation()
        assertEquals(100, master.size)

        // Order must match original exactly
        for (i in 0 until 100) {
            assertEquals(original[i].id, master[i].id)
        }
        for (i in 0 until 25) {
            assertEquals("${RemoteCatalog.TRACK_PREFIX}${original[i].id}", initialWindow[i].mediaId)
        }
    }

    @Test
    fun testSmallCollectionUnderWindowSize() {
        val manager = WindowedQueueManager()
        val original = generateTracks(10)

        val initialWindow = manager.setupQueue(
            tracks = original,
            shuffle = true,
            uiCatalog = uiCatalog,
        )

        // For 10 tracks, initial window has all 10 tracks
        assertEquals(10, initialWindow.size)
        assertEquals(10, manager.getLoadedCount())
        assertEquals(10, manager.getMasterPermutationSize())
    }

    @Test
    fun testWindowedQueueRefillTrigger() {
        val manager = WindowedQueueManager()
        val original = generateTracks(100)

        manager.setupQueue(
            tracks = original,
            shuffle = false,
            uiCatalog = uiCatalog,
        )
        assertEquals(25, manager.getLoadedCount())

        val appendedItems = mutableListOf<MediaItem>()
        var mediaItemCount = 25
        var currentMediaItemIndex = 0

        val fakePlayer = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                    return when (method.name) {
                        "getMediaItemCount" -> mediaItemCount
                        "getCurrentMediaItemIndex" -> currentMediaItemIndex
                        "addMediaItems" -> {
                            val items = args?.get(0) as? List<*> ?: emptyList<Any>()
                            @Suppress("UNCHECKED_CAST")
                            appendedItems.addAll(items as List<MediaItem>)
                            mediaItemCount += items.size
                            null
                        }
                        else -> null
                    }
                }
            }
        ) as Player

        // At index 0 (remaining: 25 - 1 - 0 = 24 > 5), no refill should trigger
        manager.onMediaItemTransition(fakePlayer, uiCatalog)
        assertEquals(0, appendedItems.size)
        assertEquals(25, manager.getLoadedCount())

        // Move to index 19 (remaining: 25 - 1 - 19 = 5 <= REFILL_THRESHOLD)
        currentMediaItemIndex = 19
        manager.onMediaItemTransition(fakePlayer, uiCatalog)

        // Refill should trigger with BATCH_APPEND_SIZE (20) items
        assertEquals(20, appendedItems.size)
        assertEquals(45, manager.getLoadedCount())
        assertEquals(45, mediaItemCount)

        // Verify the 20 appended items correspond to tracks 25..44
        for (i in 0 until 20) {
            val expectedId = original[25 + i].id
            assertEquals("${RemoteCatalog.TRACK_PREFIX}$expectedId", appendedItems[i].mediaId)
        }
    }

    @Test
    fun testResetClearsPermutationAndLoadedCount() {
        val manager = WindowedQueueManager()
        val original = generateTracks(50)
        manager.setupQueue(original, shuffle = true, uiCatalog = uiCatalog)
        assertTrue(manager.isWindowedQueueActive())

        manager.reset()
        assertFalse(manager.isWindowedQueueActive())
        assertEquals(0, manager.getMasterPermutationSize())
        assertEquals(0, manager.getLoadedCount())
    }
}
