package com.bsdung.musicassistantcar.data

/**
 * Page the complete Playlist catalog, including the "new playlist" row on page 0.
 * No duplicates across pages and no Int overflow for extreme page numbers.
 */
internal object PlaylistBrowsePaging {
    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || items.isEmpty()) return emptyList()
        val start = page.coerceAtLeast(0).toLong() * pageSize.toLong()
        if (start >= items.size) return emptyList()
        return items.subList(start.toInt(), (start + pageSize).coerceAtMost(items.size.toLong()).toInt())
    }
}
