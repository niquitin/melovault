package com.bsdung.musicassistantcar.ui.model

import com.bsdung.musicassistantcar.ui.util.MetadataDisplayHelper
import java.io.Serializable
import java.util.ArrayDeque

data class LibraryLocation(
    val parentId: String,
    val label: String,
    val page: Int,
    val firstVisiblePosition: Int = 0,
    val topOffsetPx: Int = 0,
    val sort: String = "",
    val collectionTotal: Int? = null,
    val collectionTrackCount: Int? = null,
    val collectionFolderCount: Int? = null,
) : Serializable

class NavigationManager {
    companion object {
        const val ROOT_ID = "root"
        const val ALL_ID = "source:all"
        const val DOWNLOADED_ID = "category:downloaded"
        const val DEVICE_LOCAL_ID = "category:device_local"
        const val DRIVE_ID = "source:drive"
        const val PLAYLISTS_ID = "category:playlists"

        val QUICK_FILTER_IDS = setOf(
            ROOT_ID,
            ALL_ID,
            DOWNLOADED_ID,
            DEVICE_LOCAL_ID,
            DRIVE_ID,
            PLAYLISTS_ID,
        )

        fun normalizeSearchQuery(raw: String): String {
            return raw.trim().replace(Regex("\\s+"), " ")
        }
    }

    private val stack = ArrayDeque<LibraryLocation>()

    var currentParentId: String = "root"
        private set

    var currentLabel: String = "Thư viện"
        private set

    var currentPage: Int = 0
        private set

    var currentSort: String = ""
        private set

    var currentFirstVisiblePosition: Int = 0
        private set

    var currentTopOffsetPx: Int = 0
        private set

    var currentCollectionTotal: Int? = null
        private set

    var currentCollectionTrackCount: Int? = null
        private set

    var currentCollectionFolderCount: Int? = null
        private set

    var searchQuery: String = ""
        private set

    val isAtRoot: Boolean
        get() = currentParentId == "root" && searchQuery.isBlank()

    fun activeQuickFilterId(): String? {
        if (searchQuery.isNotBlank() || currentParentId == "search") return null
        if (currentParentId in QUICK_FILTER_IDS) return currentParentId
        val ancestor = stack.toList().asReversed().firstOrNull {
            it.parentId in QUICK_FILTER_IDS && it.parentId != ROOT_ID
        }
        if (ancestor != null) return ancestor.parentId
        return when {
            currentParentId.startsWith("playlist:") -> PLAYLISTS_ID
            currentParentId.startsWith("device_folder:") ||
                currentParentId.startsWith("category:device_local") -> DEVICE_LOCAL_ID
            currentParentId.startsWith("drive-folder:") ||
                currentParentId.startsWith("drive-root:") ||
                currentParentId.startsWith("drive_folder:") ||
                currentParentId.startsWith("folder:") -> DRIVE_ID
            else -> ROOT_ID
        }
    }

    fun selectQuickFilter(parentId: String, label: String): Boolean {
        val cleanLabel = cleanEmoji(label)
        if (parentId == ROOT_ID) {
            if (isAtRoot && currentPage == 0) return false
            resetToRoot()
            return true
        }
        if (currentParentId == parentId && searchQuery.isBlank() && currentPage == 0) {
            return false
        }
        stack.clear()
        stack.addLast(
            LibraryLocation(
                parentId = ROOT_ID,
                label = "Thư viện",
                page = 0,
            )
        )
        currentParentId = parentId
        currentLabel = cleanLabel
        currentPage = 0
        currentFirstVisiblePosition = 0
        currentTopOffsetPx = 0
        currentSort = ""
        currentCollectionTotal = null
        currentCollectionTrackCount = null
        currentCollectionFolderCount = null
        searchQuery = ""
        return true
    }

    fun shouldExecuteSearch(rawQuery: String, page: Int = 0): Boolean {
        val normalized = normalizeSearchQuery(rawQuery)
        if (normalized.isBlank()) return false
        if (currentParentId == "search" && normalizeSearchQuery(searchQuery) == normalized && currentPage == page) {
            return false
        }
        return true
    }

    fun setCollectionTotal(total: Int?) {
        currentCollectionTotal = total
        currentCollectionTrackCount = null
        currentCollectionFolderCount = null
    }

    fun setCollectionCounts(total: Int?, trackCount: Int? = null, folderCount: Int? = null) {
        currentCollectionTotal = total
        currentCollectionTrackCount = trackCount
        currentCollectionFolderCount = folderCount
    }

    fun saveCurrentScroll(firstVisiblePosition: Int, topOffsetPx: Int) {
        currentFirstVisiblePosition = firstVisiblePosition
        currentTopOffsetPx = topOffsetPx
    }

    fun setSort(sort: String) {
        currentSort = sort
    }

    fun navigateTo(parentId: String, label: String, page: Int = 0, sort: String = currentSort) {
        val cleanLabel = cleanEmoji(label)
        if (parentId == ROOT_ID && page == 0) {
            resetToRoot()
            return
        }
        if (currentParentId != parentId || currentPage != page || searchQuery.isNotBlank() || currentSort != sort) {
            val existingInStack = stack.any { it.parentId == parentId }
            if (existingInStack) {
                while (stack.isNotEmpty() && stack.peekLast()?.parentId != parentId) {
                    stack.pollLast()
                }
                val target = stack.pollLast()
                currentParentId = parentId
                currentLabel = target?.label?.let { cleanEmoji(it) } ?: cleanLabel
                currentPage = page.coerceAtLeast(0)
                currentFirstVisiblePosition = target?.firstVisiblePosition ?: 0
                currentTopOffsetPx = target?.topOffsetPx ?: 0
                currentSort = sort
                currentCollectionTotal = target?.collectionTotal
                currentCollectionTrackCount = target?.collectionTrackCount
                currentCollectionFolderCount = target?.collectionFolderCount
                searchQuery = ""
                return
            }

            if (currentParentId != "search" && currentParentId != parentId) {
                if (stack.peekLast()?.parentId != currentParentId) {
                    stack.addLast(
                        LibraryLocation(
                            parentId = currentParentId,
                            label = currentLabel,
                            page = currentPage,
                            firstVisiblePosition = currentFirstVisiblePosition,
                            topOffsetPx = currentTopOffsetPx,
                            sort = currentSort,
                            collectionTotal = currentCollectionTotal,
                            collectionTrackCount = currentCollectionTrackCount,
                            collectionFolderCount = currentCollectionFolderCount,
                        )
                    )
                }
            }
            if (currentParentId != parentId) {
                currentCollectionTotal = null
                currentCollectionTrackCount = null
                currentCollectionFolderCount = null
            }
            currentParentId = parentId
            currentLabel = cleanLabel
            currentPage = page.coerceAtLeast(0)
            currentFirstVisiblePosition = 0
            currentTopOffsetPx = 0
            currentSort = sort
            searchQuery = ""
        }
    }

    fun restoreState(
        parentId: String,
        label: String,
        page: Int,
        search: String,
        backStack: List<LibraryLocation> = emptyList(),
        sort: String = "",
    ) {
        stack.clear()
        backStack.forEach {
            if (stack.peekLast()?.parentId != it.parentId) {
                stack.addLast(it.copy(label = cleanEmoji(it.label)))
            }
        }
        currentParentId = parentId
        currentLabel = cleanEmoji(label)
        currentPage = page.coerceAtLeast(0)
        currentSort = sort
        currentFirstVisiblePosition = 0
        currentTopOffsetPx = 0
        currentCollectionTotal = null
        searchQuery = search
    }

    fun backStackSnapshot(): ArrayList<LibraryLocation> =
        ArrayList(stack.toList())

    fun startSearch(query: String) {
        val normalized = normalizeSearchQuery(query)
        if (currentParentId != "search") {
            if (stack.peekLast()?.parentId != currentParentId) {
                stack.addLast(
                    LibraryLocation(
                        parentId = currentParentId,
                        label = currentLabel,
                        page = currentPage,
                        firstVisiblePosition = currentFirstVisiblePosition,
                        topOffsetPx = currentTopOffsetPx,
                        sort = currentSort,
                        collectionTotal = currentCollectionTotal,
                        collectionTrackCount = currentCollectionTrackCount,
                        collectionFolderCount = currentCollectionFolderCount,
                    )
                )
            }
        }
        currentParentId = "search"
        currentLabel = "Tìm: $normalized"
        currentPage = 0
        currentFirstVisiblePosition = 0
        currentTopOffsetPx = 0
        currentSort = ""
        currentCollectionTotal = null
        searchQuery = normalized
    }

    fun setPage(page: Int) {
        currentPage = page.coerceAtLeast(0)
    }

    fun goBack(): LibraryLocation? {
        val previous = stack.pollLast()
        if (previous != null) {
            currentParentId = previous.parentId
            currentLabel = cleanEmoji(previous.label)
            currentPage = previous.page
            currentFirstVisiblePosition = previous.firstVisiblePosition
            currentTopOffsetPx = previous.topOffsetPx
            currentSort = previous.sort
            currentCollectionTotal = previous.collectionTotal
            currentCollectionTrackCount = previous.collectionTrackCount
            currentCollectionFolderCount = previous.collectionFolderCount
            searchQuery = ""
        } else {
            resetToRoot()
        }
        return previous
    }

    fun resetToRoot() {
        stack.clear()
        currentParentId = "root"
        currentLabel = "Thư viện"
        currentPage = 0
        currentFirstVisiblePosition = 0
        currentTopOffsetPx = 0
        currentSort = ""
        currentCollectionTotal = null
        currentCollectionTrackCount = null
        currentCollectionFolderCount = null
        searchQuery = ""
    }

    fun breadcrumbTrail(): String {
        if (searchQuery.isNotBlank()) {
            return "Tìm kiếm: $searchQuery"
        }
        val path = stack
            .filter { it.parentId != "root" && it.parentId != "search" }
            .map { cleanEmoji(it.label) }
        val current = cleanEmoji(currentLabel)
        val allLabels = if (currentParentId == "root") {
            listOf(current)
        } else {
            path + current
        }
        return allLabels.joinToString(" › ")
    }

    fun displayTitle(): String {
        return if (searchQuery.isNotBlank()) {
            "Tìm kiếm: $searchQuery"
        } else {
            cleanEmoji(currentLabel)
        }
    }

    private fun cleanEmoji(raw: String): String {
        val withoutFolder = raw.removePrefix("📁 ").trim()
        return MetadataDisplayHelper.cleanPlaylistName(withoutFolder)
    }
}
