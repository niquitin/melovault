package com.bsdung.musicassistantcar.data.telegram

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Song-list display never duplicates Telegram's title and its filename, and formats metadata truthfully. */
internal object TelegramSongDisplayPolicy {
    fun label(title: String, filename: String): String {
        // E96 P5B3: display text only (see DisplayTextPolicy); the stored title/filename and every key stay as they are.
        val t = DisplayTextPolicy.clean(title.trim())
        val f = DisplayTextPolicy.clean(filename.trim())
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

    /** Telegram message date is Unix seconds; 0 or negative means unknown. */
    fun formatMessageDate(dateSeconds: Long, zone: TimeZone = TimeZone.getDefault()): String? {
        if (dateSeconds <= 0L) return null
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            .apply { timeZone = zone }
            .format(Date(dateSeconds * 1000L))
    }

    /** One chooser row per copy; date and size tell apart same-named copies from one chat. */
    fun versionLabel(
        source: String?,
        filename: String,
        dateSeconds: Long,
        fileSizeBytes: Long,
        playing: Boolean = false,
        zone: TimeZone = TimeZone.getDefault()
    ): String {
        val parts = mutableListOf(DisplayTextPolicy.cleanOrNull(source)?.takeIf { it.isNotBlank() } ?: "Telegram", DisplayTextPolicy.clean(filename))
        formatMessageDate(dateSeconds, zone)?.let { parts.add(it) }
        formatFileSize(fileSizeBytes)?.let { parts.add(it) }
        return (if (playing) "▶ " else "") + parts.joinToString(" · ")
    }

    fun subtitle(
        source: String?,
        isDownloaded: Boolean,
        fileSizeBytes: Long = -1L,
        durationSeconds: Int = 0,
        performer: String? = null
    ): String {
        val parts = mutableListOf<String>()
        DisplayTextPolicy.cleanOrNull(performer)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        parts.add(DisplayTextPolicy.cleanOrNull(source)?.takeIf { it.isNotBlank() } ?: "Telegram")
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
