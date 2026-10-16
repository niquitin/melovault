package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale

/**
 * Layout and formatting policies for MeloVault Theme 02: Cockpit 2-Pane Split.
 *
 * Implements:
 * - 42% : 58% split pane eligibility and dimensions.
 * - Minimum pane boundaries (Left >= 240dp, Right >= 310dp).
 * - Square artwork sizing capped by available width and height.
 * - Display-only track title cleaning (strips terminal extensions, replaces underscores).
 * - Duration formatting (mm:ss or h:mm:ss; null/placeholder for unknown).
 * - Real audio format badge generation without fabrication.
 */
internal object CockpitLayoutPolicy {

    const val LEFT_PANE_WEIGHT = 0.42f
    const val RIGHT_PANE_WEIGHT = 0.58f

    const val MIN_LEFT_PANE_WIDTH_DP = 240
    const val MIN_RIGHT_PANE_WIDTH_DP = 310
    const val MIN_SPLIT_TOTAL_WIDTH_DP = 580
    const val MIN_SPLIT_HEIGHT_DP = 320

    /**
     * Determines whether the current window dimensions in dp qualify for
     * the Cockpit 2-Pane side-by-side split.
     *
     * Requires:
     * 1. Window width >= 580 dp (ensuring Left >= 243 dp and Right >= 336 dp).
     * 2. Window is landscape or wide-aspect (width >= height).
     * 3. Window height >= 320 dp (sufficient vertical room for Left Now Playing controls).
     */
    fun isEligibleForCockpitTwoPane(widthDp: Int, heightDp: Int): Boolean {
        if (widthDp <= 0 || heightDp <= 0) return false
        return widthDp >= MIN_SPLIT_TOTAL_WIDTH_DP &&
                widthDp >= heightDp &&
                heightDp >= MIN_SPLIT_HEIGHT_DP
    }

    /**
     * Calculates the square artwork container size in dp for the Left Cockpit pane.
     *
     * Capped by both the Left pane width (42% minus margins) and the available
     * vertical height minus title, badge, seekbar, and transport controls.
     * Guaranteed >= 96 dp and never stretched.
     */
    fun cockpitCoverSizeDp(widthDp: Int, heightDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 160
        val leftPaneWidth = (widthDp * LEFT_PANE_WEIGHT).toInt()
        val maxFromWidth = (leftPaneWidth - 36).coerceAtLeast(96)
        // Reserved vertical space in Left Pane:
        // Title (~36dp), Subtitle (~18dp), Format (~20dp), Seek (~26dp),
        // Transport (~50dp), Secondary grid (~38dp), Margins/Padding (~20dp) = ~280dp
        val maxFromHeight = (heightDp - 280).coerceAtLeast(96)
        return minOf(maxFromWidth, maxFromHeight, 360).coerceAtLeast(96)
    }

    /**
     * Display-only cleaner for track names.
     * Strips terminal audio extensions (.mp3, .flac, .m4a, .wav, .aac, .ogg, .opus)
     * case-insensitively, and replaces consecutive underscores with spaces.
     *
     * IMPORTANT: Original filename, title, Telegram IDs and metadata are preserved
     * in the underlying data model for search, playback, cache, and queue.
     */
    fun cleanTrackTitle(raw: String): String {
        if (raw.isBlank()) return ""
        val withoutExt = raw.replace(
            Regex("""\.(mp3|flac|m4a|wav|aac|ogg|opus)$""", RegexOption.IGNORE_CASE),
            ""
        )
        val withSpaces = withoutExt.replace(Regex("""_+"""), " ").trim()
        return withSpaces.ifEmpty { raw.trim() }
    }

    /**
     * Formats duration in seconds truthfully:
     * - Under 1 hour: "mm:ss" (e.g. "05:12")
     * - 1 hour or more: "h:mm:ss" (e.g. "1:03:58")
     * - Unknown or non-positive: null
     */
    fun formatDuration(durationSeconds: Long): String? {
        if (durationSeconds <= 0L) return null
        val hours = durationSeconds / 3600L
        val minutes = (durationSeconds % 3600L) / 60L
        val seconds = durationSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats duration in milliseconds for seekbar labels.
     */
    fun formatDurationMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        return formatDuration(durationMs / 1000L) ?: "00:00"
    }

    /**
     * Generates a truthful audio format badge string from actual track format metadata.
     * Never fabricates bitrate, sample rate, or channels.
     *
     * Example outputs:
     * - "MP3 · 44.1 kHz · 320 kbps"
     * - "FLAC · 48.0 kHz · 2 kênh"
     * - "AAC · 256 kbps"
     * - null if no information available
     */
    fun formatAudioBadge(
        sampleMimeType: String?,
        sampleRate: Int,
        channelCount: Int,
        bitrate: Int
    ): String? {
        val codec = when (sampleMimeType) {
            "audio/mpeg" -> "MP3"
            "audio/flac" -> "FLAC"
            "audio/raw" -> "PCM/WAV"
            "audio/mp4a-latm" -> "AAC"
            "audio/opus" -> "OPUS"
            "audio/vorbis" -> "VORBIS"
            null -> null
            else -> sampleMimeType.removePrefix("audio/").uppercase(Locale.ROOT)
        }
        val parts = mutableListOf<String>()
        if (!codec.isNullOrBlank()) {
            parts.add(codec)
        }
        if (sampleRate > 0) {
            parts.add(String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0))
        }
        if (channelCount > 0) {
            parts.add(if (channelCount == 2) "Stereo" else "$channelCount kênh")
        }
        if (bitrate > 0) {
            parts.add("${bitrate / 1000} kbps")
        }
        return if (parts.isNotEmpty()) parts.joinToString(" · ") else null
    }
}
