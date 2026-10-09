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

sealed class RemoveQueueResult {
    object InvalidIndex : RemoveQueueResult()
    data class RemovedBeforeCurrent(
        val removedIndex: Int,
        val newCurrentIndex: Int,
    ) : RemoveQueueResult()
    data class RemovedAfterCurrent(
        val removedIndex: Int,
        val newCurrentIndex: Int,
    ) : RemoveQueueResult()
    data class RemovedCurrentAdvanceNext(
        val removedIndex: Int,
        val newCurrentIndex: Int,
    ) : RemoveQueueResult()
    data class RemovedCurrentMovePrevious(
        val removedIndex: Int,
        val newCurrentIndex: Int,
    ) : RemoveQueueResult()
    data class RemovedLastStopEmpty(
        val removedIndex: Int,
    ) : RemoveQueueResult()
}

data class AppliedRemoveResult(
    val updatedQueue: List<MediaItem>,
    val newCurrentIndex: Int,
    val outcome: RemoveQueueResult,
)

sealed class QueueListItem {
    data class Header(val title: String) : QueueListItem()
    data class Empty(
        val message: String,
        val isFullQueueEmpty: Boolean = false,
    ) : QueueListItem()
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
        val sourceBadge: String? = null,
    ) : QueueListItem()
}

object QueueHelper {

    fun formatQueueTitle(currentIndex: Int, totalCount: Int): String {
        if (totalCount <= 0 || currentIndex !in 0 until totalCount) {
            return "Hàng đợi · 0 bài"
        }
        return "Hàng đợi · ${currentIndex + 1}/$totalCount"
    }

    /** Short contextual hint: clarify which songs can be played or reordered. */
    fun formatQueueSummary(currentIndex: Int, totalCount: Int): String {
        if (totalCount <= 0 || currentIndex !in 0 until totalCount) {
            return "Mở Thư viện để chọn bài"
        }
        val upcoming = totalCount - currentIndex - 1
        return if (upcoming == 0) {
            "Không còn bài tiếp theo · Chạm bài để phát lại"
        } else {
            "$upcoming bài tiếp theo · Chạm bài để phát · Kéo để sắp xếp"
        }
    }

    fun currentTrackBadge(isPlaying: Boolean): String =
        if (isPlaying) "Đang phát" else "Bài hiện tại"

    fun deriveSourceBadge(
        item: MediaItem,
        isOfflineDownloaded: Boolean = false,
    ): String? {
        if (isOfflineDownloaded) return "✓ Offline"
        val mediaId = item.mediaId
        val uriStr = item.localConfiguration?.uri?.toString().orEmpty()
        return when {
            mediaId.startsWith("device:") ||
                mediaId.startsWith("device_track:") ||
                uriStr.startsWith("content://") -> "Thiết bị"
            uriStr.startsWith("file://") -> "✓ Offline"
            mediaId.startsWith("drive-file:") || mediaId.startsWith("drive_file:") -> "Drive"
            else -> {
                val extras = item.mediaMetadata.extras
                val source = (extras?.getString("source_type") ?: extras?.getString("source"))
                    ?.lowercase()
                    .orEmpty()
                when (source) {
                    "drive", "google_drive" -> "Drive"
                    "telegram" -> "Telegram"
                    "local", "device" -> "Thiết bị"
                    else -> null
                }
            }
        }
    }

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

    fun planRemoveQueueItem(
        queueSize: Int,
        currentIndex: Int,
        targetIndex: Int,
    ): RemoveQueueResult {
        if (queueSize <= 0 || currentIndex !in 0 until queueSize || targetIndex !in 0 until queueSize) {
            return RemoveQueueResult.InvalidIndex
        }
        return when {
            targetIndex > currentIndex -> RemoveQueueResult.RemovedAfterCurrent(
                removedIndex = targetIndex,
                newCurrentIndex = currentIndex,
            )
            targetIndex < currentIndex -> RemoveQueueResult.RemovedBeforeCurrent(
                removedIndex = targetIndex,
                newCurrentIndex = currentIndex - 1,
            )
            queueSize == 1 -> RemoveQueueResult.RemovedLastStopEmpty(
                removedIndex = targetIndex,
            )
            currentIndex < queueSize - 1 -> RemoveQueueResult.RemovedCurrentAdvanceNext(
                removedIndex = targetIndex,
                newCurrentIndex = currentIndex,
            )
            else -> RemoveQueueResult.RemovedCurrentMovePrevious(
                removedIndex = targetIndex,
                newCurrentIndex = currentIndex - 1,
            )
        }
    }

    fun applyRemoveQueueItem(
        queue: List<MediaItem>,
        currentIndex: Int,
        targetIndex: Int,
    ): AppliedRemoveResult {
        val outcome = planRemoveQueueItem(queue.size, currentIndex, targetIndex)
        if (outcome is RemoveQueueResult.InvalidIndex) {
            return AppliedRemoveResult(
                updatedQueue = queue,
                newCurrentIndex = currentIndex,
                outcome = outcome,
            )
        }
        val mutable = queue.toMutableList()
        mutable.removeAt(targetIndex)
        val newCurrentIndex = when (outcome) {
            is RemoveQueueResult.RemovedAfterCurrent -> outcome.newCurrentIndex
            is RemoveQueueResult.RemovedBeforeCurrent -> outcome.newCurrentIndex
            is RemoveQueueResult.RemovedCurrentAdvanceNext -> outcome.newCurrentIndex
            is RemoveQueueResult.RemovedCurrentMovePrevious -> outcome.newCurrentIndex
            is RemoveQueueResult.RemovedLastStopEmpty -> -1
            is RemoveQueueResult.InvalidIndex -> currentIndex
        }
        return AppliedRemoveResult(
            updatedQueue = mutable,
            newCurrentIndex = newCurrentIndex,
            outcome = outcome,
        )
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
        sourceBadgeResolver: ((MediaItem) -> String?)? = null,
    ): List<QueueListItem> {
        if (queue.isEmpty()) {
            return listOf(QueueListItem.Empty("Chưa có bài trong hàng đợi", isFullQueueEmpty = true))
        }

        val safeCurrentIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val result = mutableListOf<QueueListItem>()

        // 0. Previous tracks in queue (when safeCurrentIndex > 0)
        if (safeCurrentIndex > 0) {
            result.add(QueueListItem.Header("Trước đó · $safeCurrentIndex"))
            for (i in 0 until safeCurrentIndex) {
                result.add(
                    createTrackItem(
                        item = queue[i],
                        index = i,
                        isCurrent = false,
                        isPlaying = false,
                        isUpcoming = false,
                        sourceBadge = sourceBadgeResolver?.invoke(queue[i]) ?: deriveSourceBadge(queue[i]),
                    )
                )
            }
        }

        // 1. Current track
        result.add(QueueListItem.Header("Đang phát"))
        result.add(
            createTrackItem(
                item = queue[safeCurrentIndex],
                index = safeCurrentIndex,
                isCurrent = true,
                isPlaying = isPlaying,
                isUpcoming = false,
                sourceBadge = sourceBadgeResolver?.invoke(queue[safeCurrentIndex]) ?: deriveSourceBadge(queue[safeCurrentIndex]),
            )
        )

        // 2. Upcoming tracks with count
        val upcomingCount = getUpcomingCount(safeCurrentIndex, queue.size)
        result.add(QueueListItem.Header("Tiếp theo · $upcomingCount"))
        val upcomingStartIndex = safeCurrentIndex + 1
        if (upcomingStartIndex >= queue.size) {
            result.add(QueueListItem.Empty("Không còn bài tiếp theo", isFullQueueEmpty = false))
        } else {
            for (i in upcomingStartIndex until queue.size) {
                result.add(
                    createTrackItem(
                        item = queue[i],
                        index = i,
                        isCurrent = false,
                        isPlaying = false,
                        isUpcoming = true,
                        sourceBadge = sourceBadgeResolver?.invoke(queue[i]) ?: deriveSourceBadge(queue[i]),
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
        sourceBadge: String? = null,
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
            sourceBadge = sourceBadge,
        )
    }
}

