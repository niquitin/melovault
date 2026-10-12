package com.bsdung.musicassistantcar.playback

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.bsdung.musicassistantcar.data.CompactIndexTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class WindowedQueueManager {

    companion object {
        private const val TAG = "WindowedQueueManager"
        const val WINDOW_SIZE = 25
        const val BATCH_APPEND_SIZE = 20
        const val REFILL_THRESHOLD = 5
    }

    private var masterPermutation: List<CompactIndexTrack> = emptyList()
    private var nextAppendPointer = 0
    private var activeSessionId = 0L
    private val isAppending = AtomicBoolean(false)

    /**
     * Initializes a windowed queue with a master list of tracks.
     * If shuffle is true, performs a Fisher-Yates shuffle on the entire list.
     * Returns the initial window of MediaItems (up to WINDOW_SIZE) to pass to playQueue.
     */
    fun setupQueue(
        tracks: List<CompactIndexTrack>,
        shuffle: Boolean,
        uiCatalog: RemoteCatalog,
        sessionId: Long = SystemClock.elapsedRealtime(),
    ): List<MediaItem> {
        activeSessionId = sessionId
        val list = if (shuffle) {
            val shuffled = ArrayList(tracks)
            Collections.shuffle(shuffled)
            shuffled
        } else {
            tracks
        }
        masterPermutation = list
        val initialSize = minOf(WINDOW_SIZE, list.size)
        val initialSlice = list.subList(0, initialSize)
        nextAppendPointer = initialSize

        Log.i(TAG, "[SHUFFLE_BENCHMARK] setupQueue: masterSize=${list.size}, initialWindow=$initialSize, shuffle=$shuffle, sessionId=$sessionId")

        return initialSlice.map { toMediaItem(it, uiCatalog) }
    }

    /**
     * Called on each media item transition.
     * If player's current index is approaching the end of the loaded window,
     * appends the next batch of items asynchronously.
     */
    fun onMediaItemTransition(player: Player, uiCatalog: RemoteCatalog) {
        val totalInPlayer = player.mediaItemCount
        val currentIndex = player.currentMediaItemIndex
        val remainingInPlayer = totalInPlayer - 1 - currentIndex

        if (masterPermutation.isEmpty() || nextAppendPointer >= masterPermutation.size) {
            return
        }

        if (remainingInPlayer <= REFILL_THRESHOLD) {
            if (isAppending.compareAndSet(false, true)) {
                try {
                    val batchEnd = minOf(nextAppendPointer + BATCH_APPEND_SIZE, masterPermutation.size)
                    val batchSlice = masterPermutation.subList(nextAppendPointer, batchEnd)
                    val mediaItems = batchSlice.map { toMediaItem(it, uiCatalog) }
                    player.addMediaItems(mediaItems)
                    val appendedCount = mediaItems.size
                    nextAppendPointer = batchEnd
                    Log.i(TAG, "[SHUFFLE_BENCHMARK] Appended batch of $appendedCount items to ExoPlayer (pointer: $nextAppendPointer / ${masterPermutation.size})")
                } catch (e: Exception) {
                    Log.e(TAG, "Error appending batch to windowed queue: ${e.message}", e)
                } finally {
                    isAppending.set(false)
                }
            }
        }
    }

    fun isWindowedQueueActive(): Boolean = masterPermutation.isNotEmpty()

    fun getMasterPermutationSize(): Int = masterPermutation.size

    fun getLoadedCount(): Int = nextAppendPointer

    fun getMasterPermutation(): List<CompactIndexTrack> = masterPermutation

    fun reset() {
        masterPermutation = emptyList()
        nextAppendPointer = 0
        activeSessionId = 0L
        isAppending.set(false)
    }

    private fun toMediaItem(item: CompactIndexTrack, uiCatalog: RemoteCatalog): MediaItem {
        val carTrack = item.toCarTrack()
        return uiCatalog.withCurrentQuality(uiCatalog.trackItem(carTrack))
    }
}
