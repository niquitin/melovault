package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status

/**
 * Rules for the Studio Deck Now Playing screen, docs/ui-spec.md step 6.
 * Pure JVM except the SharedPreferences helpers.
 */
internal object MeloNowPlayingPolicy {

    const val KEY_STYLE = "now_playing_style"
    /** Lives with the playback preferences so ShareablePlaybackService can read it too. */
    const val PLAYBACK_PREFS = UiThemePolicy.PREFS_NAME
    const val KEY_PREFETCH_NEXT = "prefetch_next"
    const val SEEK_BACK_MS = -30_000L
    const val SEEK_FORWARD_MS = 60_000L

    enum class NowPlayingStyle(val key: String, val displayName: String) {
        STUDIO_DECK("studio_deck", "Studio Deck (mới)"),
        LEGACY("legacy", "Màn cũ");

        companion object {
            val DEFAULT = STUDIO_DECK
            fun fromKey(key: String?): NowPlayingStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    enum class DeckLayout { PORTRAIT, LANDSCAPE }

    /**
     * Player on the left and queue on the right in every window that is wider than tall and at
     * least 600dp wide (E95: the head unit's 782 × 642 stays two columns); narrower or taller
     * windows get one column. It no longer follows the library's layout, which switches to the
     * list with a bottom bar at those sizes.
     */
    fun deckLayout(widthDp: Int, heightDp: Int): DeckLayout =
        if (widthDp > heightDp && widthDp >= MeloScreenPolicy.TABLET_MIN_WIDTH_DP) DeckLayout.LANDSCAPE
        else DeckLayout.PORTRAIT

    enum class SleepOption(val minutes: Int, val label: String) {
        OFF(0, "Tắt hẹn giờ"),
        M15(15, "15 phút"),
        M30(30, "30 phút"),
        M45(45, "45 phút"),
        M60(60, "60 phút"),
        END_OF_TRACK(0, "Hết bài này");

        val isTimed: Boolean get() = minutes > 0
    }

    /** Deadline in elapsed-realtime millis for a timed option, null otherwise. */
    fun sleepDeadline(option: SleepOption, nowElapsedMs: Long): Long? =
        if (option.isTimed) nowElapsedMs + option.minutes * 60_000L else null

    /** "Hẹn giờ 14:59" while counting, "Dừng hết bài" at the end of the track, "Hẹn giờ" otherwise. Plain text: the moon glyph is missing from some Android fonts (Sony Android 12). */
    fun sleepLabel(deadlineElapsedMs: Long?, endOfTrack: Boolean, nowElapsedMs: Long): String = when {
        endOfTrack -> "Dừng hết bài"
        deadlineElapsedMs != null && deadlineElapsedMs > nowElapsedMs -> {
            val left = (deadlineElapsedMs - nowElapsedMs + 999) / 1000
            "Hẹn giờ " + String.format(java.util.Locale.US, "%d:%02d", left / 60, left % 60)
        }
        else -> "Hẹn giờ"
    }

    fun seekTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long =
        MeloCarPolicy.seekTarget(positionMs, durationMs, deltaMs)

    /** Transport buttons (−30s / Lùi / Tiến / +60s) are 56dp in a window tall enough for it, else 48dp (touch minimum). */
    fun transportHeightDp(windowHeightDp: Int): Int = if (windowHeightDp >= TALL_WINDOW_DP) 56 else 48

    private const val TALL_WINDOW_DP = 600

    /**
     * The bit depth sits in the header of a downloaded lossless file only; lossy files never get
     * a Hi-Res claim, so they are not probed at all.
     */
    fun shouldProbeBitDepth(filename: String?): Boolean =
        MeloComponentPolicy.isLossless(MeloComponentPolicy.formatOf(null, filename))

    /**
     * Side of the square cover: what is left of the column height after everything else
     * ([restHeightPx]), never wider than the column, never below [minSidePx] (a very low
     * screen then scrolls instead of shrinking the cover to nothing).
     */
    fun artSide(availableWidthPx: Int, availableHeightPx: Int, restHeightPx: Int, minSidePx: Int): Int {
        val room = availableHeightPx - restHeightPx.coerceAtLeast(0)
        return minOf(room, availableWidthPx).coerceAtLeast(minSidePx.coerceAtLeast(0))
    }

    /** Not even the smallest cover fits above the controls: the column falls back to its compact form. */
    fun playerTight(availableHeightPx: Int, restHeightPx: Int, minSidePx: Int): Boolean =
        availableHeightPx > 0 && availableHeightPx - restHeightPx.coerceAtLeast(0) < minSidePx

    /** Short label so four chips fit one row: the repeat state shows by colour, only "một bài" adds a digit. */
    fun repeatChipLabel(repeatLabel: String): String = if (repeatLabel == "một bài") "↻ Lặp 1" else "↻ Lặp"

    /**
     * The Đảo / Lặp / Lưu / Hàng đợi chips share the width equally; two rows of two when the
     * widest label would not fit in a quarter of the column.
     */
    fun extrasStacked(availableWidthPx: Int, chipWidthsPx: List<Int>, gapPx: Int): Boolean {
        if (chipWidthsPx.isEmpty()) return false
        val cell = (availableWidthPx - gapPx * (chipWidthsPx.size - 1)) / chipWidthsPx.size
        return chipWidthsPx.max() > cell
    }

    /**
     * Quality label from the real decoder format. ExoPlayer may report "audio/raw" after
     * decoding; the file name then gives the source format. Missing numbers are omitted.
     */
    fun qualityLabel(sampleMimeType: String?, filename: String?, sampleRateHz: Int, bitrateBps: Int, bitDepth: Int?): String? {
        val mime = sampleMimeType?.takeUnless { it == "audio/raw" }
        val format = MeloComponentPolicy.formatOf(mime, filename)
        return MeloComponentPolicy.qualityLabel(
            format,
            sampleRateHz.takeIf { it > 0 },
            bitDepth?.takeIf { it > 0 },
            (bitrateBps / 1000).takeIf { bitrateBps > 0 }
        )
    }

    /**
     * Pills for the Now Playing quality row from the same raw inputs as [qualityLabel] plus the
     * file size. Zero or negative numbers (what ExoPlayer reports when unknown) drop their pill.
     */
    fun qualityChips(
        sampleMimeType: String?,
        filename: String?,
        sampleRateHz: Int,
        bitrateBps: Int,
        bitDepth: Int?,
        sizeBytes: Long
    ): List<MeloQualityChipPolicy.Chip> {
        val mime = sampleMimeType?.takeUnless { it == "audio/raw" }
        return MeloQualityChipPolicy.chips(
            MeloComponentPolicy.formatOf(mime, filename),
            bitDepth?.takeIf { it > 0 },
            sampleRateHz.takeIf { it > 0 },
            (bitrateBps / 1000).takeIf { bitrateBps > 0 },
            sizeBytes.takeIf { it > 0L }
        )
    }

    fun queueStatus(isCurrent: Boolean, downloaded: Boolean, downloadingPercent: Int?): Status =
        MeloCompactPolicy.rowStatus(downloaded, downloadingPercent, isCurrent)

    fun loadStyle(prefs: SharedPreferences): NowPlayingStyle = NowPlayingStyle.fromKey(prefs.getString(KEY_STYLE, null))

    fun saveStyle(prefs: SharedPreferences, style: NowPlayingStyle) {
        prefs.edit().putString(KEY_STYLE, style.key).apply()
    }

    /** Spec: "Tải trước bài kế" is on unless the owner turns it off. */
    fun prefetchEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_PREFETCH_NEXT, true)

    fun setPrefetchEnabled(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFETCH_NEXT, enabled).apply()
    }
}
