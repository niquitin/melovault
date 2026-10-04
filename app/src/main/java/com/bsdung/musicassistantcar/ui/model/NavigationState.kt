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
        if (currentParentId != parentId || currentPage != page || searchQuery.isNotBlank() || currentSort != sort) {
            if (currentParentId != "search" && currentParentId != parentId) {
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
            stack.addLast(it.copy(label = cleanEmoji(it.label)))
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
        if (currentParentId != "search") {
            stack.addLast(
                LibraryLocation(
                    parentId = currentParentId,
                    label = currentLabel,
                    page = currentPage,
                    firstVisiblePosition = currentFirstVisiblePosition,
                    topOffsetPx = currentTopOffsetPx,
                    sort = currentSort,
                )
            )
        }
        currentParentId = "search"
        currentLabel = "Tìm: $query"
        currentPage = 0
        currentFirstVisiblePosition = 0
        currentTopOffsetPx = 0
        currentSort = ""
        currentCollectionTotal = null
        searchQuery = query
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
