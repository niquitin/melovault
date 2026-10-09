package com.bsdung.musicassistantcar.ui.util

import com.bsdung.musicassistantcar.data.UnifiedTrackDto
import java.net.URI

enum class UnifiedSourceFilter(val serverKey: String, val displayLabel: String) {
    ALL("ALL", "Tất cả"),
    LOCAL_CACHE("LOCAL_CACHE", "Local"),
    TELEGRAM("TELEGRAM", "Telegram"),
    GOOGLE_DRIVE("GOOGLE_DRIVE", "GDrive"),
    YOUTUBE("YOUTUBE", "YouTube"),
    APPLE_MUSIC("APPLE_MUSIC", "Apple"),
}

enum class UnifiedItemAction {
    PLAY_IN_LIBRARY,
    OPEN_EXTERNAL,
    READ_ONLY,
}

object UnifiedActionHelper {

    private val ALLOWED_EXTERNAL_HOST_SUFFIXES = listOf(
        "youtube.com",
        "youtu.be",
        "music.apple.com",
        "apple.com",
        "t.me",
        "telegram.me",
        "drive.google.com",
    )

    fun mapSourceLabel(primarySource: String?, sourceBadge: String?): String {
        val badge = sourceBadge?.trim()
        if (!badge.isNullOrBlank()) {
            return badge
        }
        return when (primarySource?.trim()?.uppercase()) {
            "LOCAL_CACHE", "LOCAL" -> "LOCAL"
            "TELEGRAM" -> "TG"
            "GOOGLE_DRIVE", "DRIVE", "GDRIVE" -> "GDRIVE"
            "YOUTUBE", "YT" -> "YT"
            "APPLE_MUSIC", "APPLE" -> "APPLE"
            null, "" -> "Chưa rõ"
            else -> primarySource.trim()
        }
    }

    fun isValidExternalUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        return try {
            val uri = URI(trimmed)
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return false
            }
            val host = uri.host?.lowercase() ?: return false
            if (host.isBlank()) return false
            ALLOWED_EXTERNAL_HOST_SUFFIXES.any { suffix ->
                host == suffix || host.endsWith(".$suffix")
            }
        } catch (_: Exception) {
            false
        }
    }

    fun resolveAction(item: UnifiedTrackDto): UnifiedItemAction {
        val action = item.playbackAction.trim().uppercase()
        val hasValidUrl = isValidExternalUrl(item.externalUrl)

        return when (action) {
            "EXTERNAL_OPEN" -> {
                if (hasValidUrl) UnifiedItemAction.OPEN_EXTERNAL else UnifiedItemAction.READ_ONLY
            }
            "STREAM" -> {
                val ownedSource = item.primarySource.trim().uppercase() in setOf(
                    "LOCAL_CACHE", "TELEGRAM", "GOOGLE_DRIVE"
                )
                if (ownedSource && item.isPlayable && (item.canonicalTrackId ?: 0) > 0) {
                    UnifiedItemAction.PLAY_IN_LIBRARY
                } else {
                    UnifiedItemAction.READ_ONLY
                }
            }
            // NOT_CONNECTED, DISABLED and unknown actions must not fall back to Media3
            // or a misleading external-open button solely because a URL is present.
            else -> UnifiedItemAction.READ_ONLY
        }
    }

    const val PLAY_IN_LIBRARY_UNWIRED_TOAST = "Đóng cửa sổ và vào Thư viện chính để chọn phát bài này"

    fun getActionButtonText(action: UnifiedItemAction): String {
        return when (action) {
            UnifiedItemAction.PLAY_IN_LIBRARY -> "Phát"
            UnifiedItemAction.OPEN_EXTERNAL -> "Mở liên kết"
            UnifiedItemAction.READ_ONLY -> "Chỉ xem"
        }
    }

    data class DialogSearchFilterState(
        val filter: UnifiedSourceFilter = UnifiedSourceFilter.ALL,
        val query: String = "",
        val offset: Int = 0,
    )

    fun onUserApplySearch(currentState: DialogSearchFilterState, newQuery: String): DialogSearchFilterState {
        val trimmed = newQuery.trim()
        return if (trimmed.isNotBlank()) {
            // When user starts searching, automatically switch filter to ALL
            currentState.copy(
                filter = UnifiedSourceFilter.ALL,
                query = trimmed,
                offset = 0
            )
        } else {
            currentState.copy(
                query = "",
                offset = 0
            )
        }
    }

    fun onUserSelectFilter(currentState: DialogSearchFilterState, selectedFilter: UnifiedSourceFilter): DialogSearchFilterState {
        return currentState.copy(
            filter = selectedFilter,
            query = "", // Clear active search query when tapping a source chip
            offset = 0
        )
    }

    fun calculateTotalPages(total: Int, limit: Int): Int {
        if (limit <= 0) return 1
        if (total <= 0) return 1
        return (total + limit - 1) / limit
    }

    fun calculateCurrentPage(offset: Int, limit: Int): Int {
        if (limit <= 0) return 1
        return (offset / limit) + 1
    }
}
