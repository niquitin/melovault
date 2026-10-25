package com.bsdung.musicassistantcar.data.telegram

import java.text.Normalizer
import java.util.Locale

/**
 * Pure rules behind the shared components (song row status, quality label,
 * search hint/matching, chips, mini player time). Views live in MeloComponents.
 */
internal object MeloComponentPolicy {

    enum class Status { PLAYING, PAUSED, DOWNLOADED, DOWNLOADING, TELEGRAM, ERROR, OPENING }

    enum class ColorRole { ACCENT, SECONDARY, WARNING }

    /** Status always carries an icon and words, never color alone. */
    data class StatusPresentation(
        val status: Status,
        val label: String,
        val color: ColorRole,
        val highlightRow: Boolean
    )

    /** [local] = a song of "Nhạc trên máy": it is on the device, not "đã tải" from Telegram. */
    fun presentStatus(
        status: Status,
        qualityLabel: String? = null,
        progressPercent: Int? = null,
        local: Boolean = false
    ): StatusPresentation =
        when (status) {
            Status.PLAYING -> StatusPresentation(status, "Đang phát", ColorRole.ACCENT, highlightRow = true)
            Status.PAUSED -> StatusPresentation(status, "Tạm dừng", ColorRole.ACCENT, highlightRow = true)
            Status.DOWNLOADED -> StatusPresentation(
                status,
                listOfNotNull(if (local) "Trên máy" else "Đã tải", qualityLabel?.takeIf { it.isNotBlank() }).joinToString(" · "),
                ColorRole.ACCENT, highlightRow = false
            )
            Status.DOWNLOADING -> StatusPresentation(
                status,
                progressPercent?.let { "Đang tải ${it.coerceIn(0, 100)}%" } ?: "Đang tải",
                ColorRole.ACCENT, highlightRow = false
            )
            Status.TELEGRAM -> StatusPresentation(status, "Telegram · Chưa tải", ColorRole.SECONDARY, highlightRow = false)
            Status.ERROR -> StatusPresentation(status, "Tải lỗi – bấm thử lại", ColorRole.WARNING, highlightRow = false)
            Status.OPENING -> StatusPresentation(status, TelegramPlayFeedbackPolicy.RESOLVING_TEXT, ColorRole.ACCENT, highlightRow = false)
        }

    private val LOSSLESS = setOf("FLAC", "WAV", "ALAC", "AIFF", "APE")

    /** Format from MIME type first, then file extension; null when unknown (never guessed). */
    fun formatOf(mimeType: String?, filename: String?): String? {
        val mime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        val fromMime = when {
            "flac" in mime -> "FLAC"
            "mpeg" in mime || "mp3" in mime -> "MP3"
            "wav" in mime || "wave" in mime -> "WAV"
            "aiff" in mime -> "AIFF"
            "opus" in mime -> "OPUS"
            "ogg" in mime || "vorbis" in mime -> "OGG"
            "alac" in mime -> "ALAC"
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "M4A"
            else -> null
        }
        if (fromMime != null) return fromMime
        return when (filename?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)) {
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "wav" -> "WAV"
            "aif", "aiff" -> "AIFF"
            "opus" -> "OPUS"
            "ogg" -> "OGG"
            "ape" -> "APE"
            "m4a", "aac", "mp4" -> "M4A"
            else -> null
        }
    }

    /**
     * "MP3 · 44.1 kHz · 320 kbps"; only known parts are shown. Hi-Res only for
     * lossless files from 24-bit / 48 kHz up.
     */
    fun qualityLabel(format: String?, sampleRateHz: Int?, bitDepth: Int?, bitrateKbps: Int?): String? {
        val parts = mutableListOf<String>()
        format?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        bitDepth?.takeIf { it > 0 }?.let { parts.add("${it}bit") }
        sampleRateHz?.takeIf { it > 0 }?.let { parts.add(formatKhz(it)) }
        if (!isLossless(format)) bitrateKbps?.takeIf { it > 0 }?.let { parts.add("$it kbps") }
        if (isHiRes(format, sampleRateHz, bitDepth)) parts.add("Hi-Res")
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun isLossless(format: String?): Boolean = format != null && format.uppercase(Locale.ROOT) in LOSSLESS

    fun isHiRes(format: String?, sampleRateHz: Int?, bitDepth: Int?): Boolean =
        isLossless(format) && (bitDepth ?: 0) >= 24 && (sampleRateHz ?: 0) >= 48_000

    fun formatKhz(sampleRateHz: Int): String {
        val khz = sampleRateHz / 1000.0
        return if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
        else String.format(Locale.US, "%.1f kHz", khz)
    }

    /** Vietnamese thousands separator: 27971 -> "27.971". */
    fun formatCount(count: Int): String = String.format(Locale.US, "%,d", count).replace(',', '.')

    /**
     * Short count for a tab label: 999, 9,5K, 27K, 1,2M. Cut down, never rounded up, so "999,9K"
     * can not turn into "1000K". Null when the number is not known (or negative): no brackets then.
     */
    fun shortCount(count: Int?): String? {
        if (count == null || count < 0) return null
        fun oneDecimal(tenths: Int, unit: String) =
            if (tenths % 10 == 0) "${tenths / 10}$unit" else "${tenths / 10},${tenths % 10}$unit"
        return when {
            count < 1_000 -> count.toString()
            count < 10_000 -> oneDecimal(count / 100, "K")
            count < 1_000_000 -> "${count / 1_000}K"
            count < 10_000_000 -> oneDecimal(count / 100_000, "M")
            else -> "${count / 1_000_000}M"
        }
    }

    /** Straight ARGB mix of [from] and [to]; [t] is clamped to 0..1. Used for the playing row's gradient end. */
    fun blend(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * f + 0.5f).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * Words in front of the channel on a teal-theme row's second line. A plain "Telegram · Chưa tải"
     * or "Đã tải" row shows only its source (the icon on the right says the rest); every state that
     * needs words (playing, paused, loading, error) keeps them.
     */
    fun themedSecondLine(status: Status, label: String, source: String?): String {
        val prefix = when (status) {
            Status.TELEGRAM, Status.DOWNLOADED -> null
            else -> label
        }
        return listOfNotNull(prefix, source?.takeIf { it.isNotBlank() }).joinToString(" · ")
            .ifEmpty { label }
    }

    fun searchHint(totalSongs: Int?): String =
        if (totalSongs != null && totalSongs > 0) "Tìm trong ${formatCount(totalSongs)} bài hát…" else "Tìm bài hát…"

    /** Accent-insensitive key: "Sử Thanh Hóa" and "su thanh hoa" both become "su thanh hoa". */
    fun searchKey(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
            .replace('đ', 'd')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun matchesSearch(query: String, vararg fields: String?): Boolean {
        val q = searchKey(query)
        if (q.isEmpty()) return true
        val words = q.split(' ')
        val haystack = fields.filterNotNull().joinToString(" ") { searchKey(it) }
        return words.all { it in haystack }
    }

    fun chipLabel(name: String, count: Int?): String =
        if (count != null && count >= 0) "$name (${formatCount(count)})" else name

    /** Mini player time "01:23 / 04:56"; total omitted when unknown. */
    fun progressText(positionSeconds: Int, durationSeconds: Int): String {
        val pos = TelegramSongDisplayPolicy.formatDuration(positionSeconds.coerceAtLeast(0)) ?: "00:00"
        val total = TelegramSongDisplayPolicy.formatDuration(durationSeconds)
        return if (total != null) "$pos / $total" else pos
    }

    /** Placeholder art: first letter of the channel (or title) when there is no cover. */
    fun initialOf(sourceTitle: String?, title: String): String {
        val base = sourceTitle?.takeIf { it.isNotBlank() } ?: title
        val ch = base.trim().firstOrNull { it.isLetterOrDigit() } ?: return "♪"
        return ch.uppercaseChar().toString()
    }

    /** Stable placeholder tint per channel so the same source keeps its color. */
    fun placeholderColor(seed: String?, palette: MeloDesignTokens.Palette): Int {
        val tints = intArrayOf(palette.accentSoft, palette.surfaceRaised, 0xFF1D3B52.toInt(), 0xFF2A3346.toInt())
        val index = Math.floorMod((seed ?: "").hashCode(), tints.size)
        return tints[index]
    }
}
