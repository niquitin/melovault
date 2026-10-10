package com.bsdung.musicassistantcar.playback

/**
 * The source cursor belongs to the playback session, not the RecyclerView.
 * Source pages are numbered by ALL browse rows (including folders); only playable rows
 * are added to the MediaSession queue. Duplicate track IDs are intentional in playlists.
 */
data class QueueBrowseSeed(
    val parentId: String,
    val sort: String,
    val query: String,
    val nextPage: Int,
    val pageSize: Int,
) {
    companion object {
        const val KEY_PARENT = "melovault.queue.parent"
        const val KEY_SORT = "melovault.queue.sort"
        const val KEY_QUERY = "melovault.queue.query"
        const val KEY_NEXT_PAGE = "melovault.queue.next_page"
        const val KEY_PAGE_SIZE = "melovault.queue.page_size"

        fun supports(parentId: String, query: String = ""): Boolean =
            (parentId == "search" && query.isNotBlank()) ||
                parentId == "source:telegram" ||
                parentId == "source:all" ||
                parentId == "category:recent" ||
                parentId.startsWith("drive-root:") ||
                parentId.startsWith("drive-folder:") ||
                parentId.startsWith("playlist:") ||
                parentId.startsWith("album:") ||
                parentId.startsWith("artist:")
    }
}

/**
 * Pure queue/identity state; all calls on the service's main looper.
 * We deliberately do NOT deduplicate mediaIds: a playlist can contain repeat occurrences.
 */
class QueuePageCursor(
    val seed: QueueBrowseSeed,
    initialMediaIds: List<String>,
) {
    private val expected = initialMediaIds.toMutableList()
    private var prefetched = false
    private var scanningFolderOnlyPages = false
    var nextPage: Int = seed.nextPage
        private set
    var loading: Boolean = false
        private set
    var endReached: Boolean = false
        private set
    var pausedOnError: Boolean = false
        private set

    val queueSize: Int get() = expected.size
    fun isUnchanged(actual: List<String>): Boolean = expected == actual

    fun shouldFetch(currentIndex: Int): Boolean =
        !loading && !endReached && !pausedOnError && currentIndex >= 0 &&
            (!prefetched || scanningFolderOnlyPages || currentIndex >= (expected.size - 12).coerceAtLeast(0))

    fun beginFetch(currentIndex: Int): Int? {
        if (!shouldFetch(currentIndex)) return null
        loading = true
        return nextPage
    }

    /** Return false for a response from a superseded request. */
    fun acceptPage(requestPage: Int, rawRowCount: Int, playableMediaIds: List<String>): Boolean {
        if (!loading || requestPage != nextPage) return false
        loading = false
        prefetched = true
        scanningFolderOnlyPages = rawRowCount == seed.pageSize && playableMediaIds.isEmpty()
        expected.addAll(playableMediaIds)
        nextPage++
        if (rawRowCount < seed.pageSize) endReached = true
        return true
    }

    fun fail(requestPage: Int) {
        if (loading && requestPage == nextPage) {
            loading = false
            pausedOnError = true // never retry failing servers on each track transition
        }
    }

    fun cancel() {
        endReached = true
        loading = false
    }
}
