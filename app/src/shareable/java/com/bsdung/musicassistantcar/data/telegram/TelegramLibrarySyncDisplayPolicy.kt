package com.bsdung.musicassistantcar.data.telegram


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
            val srcStr = "${MeloComponentPolicy.formatCount(sourceCount)} nguồn"
            val totalStr = "${MeloComponentPolicy.formatCount(totalIndexedCount)} bài"
            return "$srcStr · $totalStr · $stateLabel"
        }

        if (totalIndexedCount <= 0) {
            return "Chưa có bài · $stateLabel"
        }

        val totalStr = MeloComponentPolicy.formatCount(totalIndexedCount)
        val countLabel = if (displayedCount in 1 until totalIndexedCount) {
            val dispStr = MeloComponentPolicy.formatCount(displayedCount)
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
