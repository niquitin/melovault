package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale

/** Song-list display never duplicates Telegram's title and its filename, and formats metadata truthfully. */
internal object TelegramSongDisplayPolicy {
    fun label(title: String, filename: String): String {
        val t = title.trim()
        val f = filename.trim()
        if (t.isNotBlank() && t != "audio") return t
        return f.ifBlank { "Bài hát không tên" }
    }

    fun formatDuration(durationSeconds: Int): String? {
        if (durationSeconds <= 0) return null
        val totalSec = durationSeconds.toLong()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(fileSizeBytes: Long): String? {
        if (fileSizeBytes <= 0L) return null
        return String.format(Locale.US, "%.1f MB", fileSizeBytes / 1_000_000.0)
    }

    fun subtitle(
        source: String?,
        isDownloaded: Boolean,
        fileSizeBytes: Long = -1L,
        durationSeconds: Int = 0,
        performer: String? = null
    ): String {
        val parts = mutableListOf<String>()
        if (!performer.isNullOrBlank()) {
            parts.add(performer.trim())
        }
        parts.add(source?.takeIf { it.isNotBlank() } ?: "Telegram")
        val dur = formatDuration(durationSeconds)
        if (dur != null) {
            parts.add(dur)
        }
        val size = formatFileSize(fileSizeBytes)
        if (size != null) {
            parts.add(size)
        }
        parts.add(if (isDownloaded) "Đã tải" else "Chưa tải")
        return parts.joinToString(" · ")
    }

    fun computeSample(size: Int, targetW: Int, targetH: Int): Int {
        var s = 1
        while (size / (s * s) > targetW * targetH * 4 && s < 16) s *= 2
        return s
    }
}
