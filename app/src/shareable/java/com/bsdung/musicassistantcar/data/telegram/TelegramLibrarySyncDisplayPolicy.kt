package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale

/**
 * Pure policy for formatting library sync and pagination status labels truthfully.
 * Clearly separates currently displayed count (pagination), total indexed songs in database,
 * and background synchronization state without confusing partial pages with sync completion.
 */
object TelegramLibrarySyncDisplayPolicy {

    /**
     * Formats the header sync subtitle.
     *
     * @param displayedCount number of songs currently loaded in memory / displayed in list
     * @param totalIndexedCount total number of indexed songs matching the current filter in SQLite DB
     * @param isSyncing whether background sync / scan is currently active
     * @param isAuthenticated whether the user has a valid active Telegram session
     * @param isSourcesMode whether the user is browsing sources (channels/groups)
     * @param sourceCount number of discovered sources
     * @param selectedSourceTitle optional title of filtered source
     */
    fun formatSyncSubtitle(
        displayedCount: Int,
        totalIndexedCount: Int,
        isSyncing: Boolean,
        isAuthenticated: Boolean,
        isSourcesMode: Boolean = false,
        sourceCount: Int = 0,
        selectedSourceTitle: String? = null
    ): String {
        val stateLabel = when {
            isSyncing -> "Đang đồng bộ…"
            isAuthenticated -> "Đã đồng bộ"
            else -> "Chờ kết nối"
        }
        if (!isAuthenticated) return "Telegram · $stateLabel"

        if (isSourcesMode) {
            val srcStr = String.format(Locale.US, "%,d nguồn", sourceCount)
            val totalStr = String.format(Locale.US, "%,d bài", totalIndexedCount)
            return "$srcStr · $totalStr · $stateLabel"
        }

        if (totalIndexedCount <= 0) {
            return "Chưa có bài · $stateLabel"
        }

        val totalStr = String.format(Locale.US, "%,d", totalIndexedCount)
        val countLabel = if (displayedCount in 1 until totalIndexedCount) {
            val dispStr = String.format(Locale.US, "%,d", displayedCount)
            "Hiển thị $dispStr / $totalStr bài"
        } else {
            "$totalStr bài"
        }

        return if (!selectedSourceTitle.isNullOrBlank()) {
            "$selectedSourceTitle · $countLabel · $stateLabel"
        } else {
            "$countLabel · $stateLabel"
        }
    }
}
