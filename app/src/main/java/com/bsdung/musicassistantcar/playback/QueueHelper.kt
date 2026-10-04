package com.bsdung.musicassistantcar.playback

import androidx.media3.common.MediaItem
import com.bsdung.musicassistantcar.ui.util.AudioInfoFormatter
import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper

sealed class PlayNextAction {
    object AlreadyCurrent : PlayNextAction()
    data class Move(val fromIndex: Int, val toIndex: Int) : PlayNextAction()
    data class Insert(val targetIndex: Int, val item: MediaItem) : PlayNextAction()
    data class StartNewQueue(val item: MediaItem) : PlayNextAction()
}

sealed class AddToQueueAction {
    object AlreadyExists : AddToQueueAction()
    data class Append(val item: MediaItem) : AddToQueueAction()
    data class StartNewQueue(val item: MediaItem) : AddToQueueAction()
}

sealed class QueueListItem {
    data class Header(val title: String) : QueueListItem()
    data class Empty(val message: String) : QueueListItem()
    data class Track(
        val mediaItem: MediaItem,
        val queueIndex: Int,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUri: String?,
        val audioInfo: String?,
        val isCurrent: Boolean,
        val isPlaying: Boolean,
        val isUpcoming: Boolean,
    ) : QueueListItem()
}

object QueueHelper {

    fun planPlayNext(
        queue: List<MediaItem>,
        currentIndex: Int,
        newItem: MediaItem,
    ): PlayNextAction {
        if (queue.isEmpty() || currentIndex !in queue.indices) {
            return PlayNextAction.StartNewQueue(newItem)
        }

        if (queue[currentIndex].mediaId == newItem.mediaId) {
            return PlayNextAction.AlreadyCurrent
        }

        val targetIndex = currentIndex + 1
        var foundIndex = -1
        for (i in targetIndex until queue.size) {
            if (queue[i].mediaId == newItem.mediaId) {
                foundIndex = i
                break
            }
        }

        return if (foundIndex != -1) {
            PlayNextAction.Move(fromIndex = foundIndex, toIndex = targetIndex)
        } else {
            PlayNextAction.Insert(targetIndex = targetIndex, item = newItem)
        }
    }

    fun applyPlayNext(
        queue: List<MediaItem>,
        currentIndex: Int,
        newItem: MediaItem,
    ): List<MediaItem> {
        val action = planPlayNext(queue, currentIndex, newItem)
        return when (action) {
            is PlayNextAction.StartNewQueue -> listOf(newItem)
            is PlayNextAction.AlreadyCurrent -> queue
            is PlayNextAction.Move -> {
                val mutable = queue.toMutableList()
                val item = mutable.removeAt(action.fromIndex)
                mutable.add(action.toIndex, item)
                mutable
            }
            is PlayNextAction.Insert -> {
                val mutable = queue.toMutableList()
                mutable.add(action.targetIndex, action.item)
                mutable
            }
        }
    }

    fun planAddToQueue(
        queue: List<MediaItem>,
        newItem: MediaItem,
    ): AddToQueueAction {
        if (queue.isEmpty()) {
            return AddToQueueAction.StartNewQueue(newItem)
        }

        if (queue.any { it.mediaId == newItem.mediaId }) {
            return AddToQueueAction.AlreadyExists
        }

        return AddToQueueAction.Append(newItem)
    }

    fun applyAddToQueue(
        queue: List<MediaItem>,
        newItem: MediaItem,
    ): List<MediaItem> {
        val action = planAddToQueue(queue, newItem)
        return when (action) {
            is AddToQueueAction.StartNewQueue -> listOf(newItem)
            is AddToQueueAction.AlreadyExists -> queue
            is AddToQueueAction.Append -> queue + newItem
        }
    }

    fun planRemoveUpcoming(
        queueSize: Int,
        currentIndex: Int,
        targetIndex: Int,
    ): Boolean {
        return targetIndex in (currentIndex + 1) until queueSize
    }

    fun applyRemoveUpcoming(
        queue: List<MediaItem>,
        currentIndex: Int,
        targetIndex: Int,
    ): List<MediaItem> {
        if (!planRemoveUpcoming(queue.size, currentIndex, targetIndex)) {
            return queue
        }
        val mutable = queue.toMutableList()
        mutable.removeAt(targetIndex)
        return mutable
    }

    fun planClearUpcoming(
        queueSize: Int,
        currentIndex: Int,
    ): IntRange? {
        val fromIndex = currentIndex + 1
        return if (fromIndex < queueSize) {
            fromIndex until queueSize
        } else {
            null
        }
    }

    fun applyClearUpcoming(
        queue: List<MediaItem>,
        currentIndex: Int,
    ): List<MediaItem> {
        val range = planClearUpcoming(queue.size, currentIndex) ?: return queue
        return queue.take(range.first)
    }

    fun canMoveUpcoming(
        currentIndex: Int,
        fromIndex: Int,
        toIndex: Int,
        queueSize: Int,
    ): Boolean {
        if (queueSize <= 0) return false
        if (currentIndex !in 0 until queueSize) return false
        val upcomingStart = currentIndex + 1
        if (upcomingStart >= queueSize) return false
        val upcomingRange = upcomingStart until queueSize
        if (fromIndex !in upcomingRange || toIndex !in upcomingRange) return false
        if (fromIndex == toIndex) return false
        return true
    }

    fun applyMove(
        queue: List<MediaItem>,
        currentIndex: Int,
        fromIndex: Int,
        toIndex: Int,
    ): List<MediaItem> {
        if (!canMoveUpcoming(currentIndex, fromIndex, toIndex, queue.size)) {
            return queue
        }
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }

    fun getUpcomingCount(
        currentIndex: Int,
        queueSize: Int,
    ): Int {
        if (queueSize <= 0 || currentIndex !in 0 until queueSize) return 0
        return (queueSize - 1 - currentIndex).coerceAtLeast(0)
    }

    fun applyQualityRebuild(
        queue: List<MediaItem>,
        transform: (MediaItem) -> MediaItem,
    ): List<MediaItem> {
        return queue.map(transform)
    }

    fun buildQueueList(
        queue: List<MediaItem>,
        currentIndex: Int,
        isPlaying: Boolean,
    ): List<QueueListItem> {
        if (queue.isEmpty()) {
            return listOf(QueueListItem.Empty("Chưa có bài trong hàng đợi"))
        }

        val safeCurrentIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val result = mutableListOf<QueueListItem>()

        // 1. Current track
        result.add(QueueListItem.Header("Đang phát"))
        result.add(
            createTrackItem(
                item = queue[safeCurrentIndex],
                index = safeCurrentIndex,
                isCurrent = true,
                isPlaying = isPlaying,
                isUpcoming = false,
            )
        )

        // 2. Upcoming tracks with count
        val upcomingCount = getUpcomingCount(safeCurrentIndex, queue.size)
        result.add(QueueListItem.Header("Tiếp theo · $upcomingCount"))
        val upcomingStartIndex = safeCurrentIndex + 1
        if (upcomingStartIndex >= queue.size) {
            result.add(QueueListItem.Empty("Không còn bài tiếp theo"))
        } else {
            for (i in upcomingStartIndex until queue.size) {
                result.add(
                    createTrackItem(
                        item = queue[i],
                        index = i,
                        isCurrent = false,
                        isPlaying = false,
                        isUpcoming = true,
                    )
                )
            }
        }

        return result
    }

    private fun createTrackItem(
        item: MediaItem,
        index: Int,
        isCurrent: Boolean,
        isPlaying: Boolean,
        isUpcoming: Boolean,
    ): QueueListItem.Track {
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty().ifBlank { "Chưa xác định" }
        val artist = metadata.artist?.toString().orEmpty()
        val album = metadata.albumTitle?.toString().orEmpty()

        val extras = metadata.extras
        val format = extras?.getString("format")
        val sizeBytes = if (extras != null && extras.containsKey("size_bytes")) extras.getLong("size_bytes") else null
        val codec = extras?.getString("codec")
        val bitrate = if (extras != null && extras.containsKey("bitrate")) extras.getInt("bitrate") else null
        val sampleRate = if (extras != null && extras.containsKey("sample_rate")) extras.getInt("sample_rate") else null
        val channels = if (extras != null && extras.containsKey("channels")) extras.getInt("channels") else null

        val audioInfo = AudioInfoFormatter.formatAudioInfo(
            format = format,
            sizeBytes = sizeBytes,
            codec = codec,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
        )

        return QueueListItem.Track(
            mediaItem = item,
            queueIndex = index,
            title = MetadataDisplayHelper.cleanTitle(title),
            artist = MetadataDisplayHelper.cleanArtist(artist),
            album = MetadataDisplayHelper.cleanAlbum(album),
            artworkUri = metadata.artworkUri?.toString(),
            audioInfo = audioInfo,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            isUpcoming = isUpcoming,
        )
    }
}
