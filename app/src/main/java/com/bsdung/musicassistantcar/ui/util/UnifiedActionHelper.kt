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

        if (action == "EXTERNAL_OPEN") {
            return if (hasValidUrl) {
                UnifiedItemAction.OPEN_EXTERNAL
            } else {
                UnifiedItemAction.READ_ONLY
            }
        }

        if (item.isPlayable && item.canonicalTrackId != null && item.canonicalTrackId > 0) {
            return UnifiedItemAction.PLAY_IN_LIBRARY
        }

        if (hasValidUrl) {
            return UnifiedItemAction.OPEN_EXTERNAL
        }

        return UnifiedItemAction.READ_ONLY
    }

    fun getActionButtonText(action: UnifiedItemAction): String {
        return when (action) {
            UnifiedItemAction.PLAY_IN_LIBRARY -> "Phát trong thư viện"
            UnifiedItemAction.OPEN_EXTERNAL -> "Mở liên kết"
            UnifiedItemAction.READ_ONLY -> "Chỉ xem"
        }
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
