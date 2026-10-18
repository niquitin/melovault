package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import kotlin.math.abs

/**
 * Rules for the Studio Deck Now Playing screen, docs/ui-spec.md step 6.
 * Pure JVM except the SharedPreferences helpers.
 */
internal object MeloNowPlayingPolicy {

    const val KEY_STYLE = "now_playing_style"
    /** Lives with the playback preferences so ShareablePlaybackService can read it too. */
    const val PLAYBACK_PREFS = UiThemePolicy.PREFS_NAME
    const val KEY_PREFETCH_NEXT = "prefetch_next"
    const val WAVEFORM_BUCKETS = 120
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

    /** Two panes (player left, queue right) wherever the library would use two panes. */
    fun deckLayout(widthDp: Int, heightDp: Int): DeckLayout =
        if (MeloScreenPolicy.chooseLayout(widthDp, heightDp, carMode = false) == MeloScreenPolicy.Layout.TWO_PANE) {
            DeckLayout.LANDSCAPE
        } else DeckLayout.PORTRAIT

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

    /**
     * Normalises real sample peaks to [buckets] bars in 0..1. Fewer samples than bars
     * keeps the samples' count (bars are never invented); an all-silent file stays flat.
     */
    fun bucketPeaks(samples: FloatArray, buckets: Int): FloatArray {
        if (samples.isEmpty() || buckets <= 0) return FloatArray(0)
        val count = minOf(buckets, samples.size)
        val out = FloatArray(count)
        for (i in 0 until count) {
            val from = (i.toLong() * samples.size / count).toInt()
            val to = maxOf(from + 1, ((i + 1).toLong() * samples.size / count).toInt())
            var peak = 0f
            for (j in from until to) peak = maxOf(peak, abs(samples[j]))
            out[i] = peak
        }
        val max = out.maxOrNull() ?: 0f
        if (max <= 0f) return out
        for (i in out.indices) out[i] = out[i] / max
        return out
    }

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

    /** The Đảo / Lặp / Lưu / Hàng đợi chips go on two rows when one row would overflow the column. */
    fun extrasStacked(availableWidthPx: Int, chipWidthsPx: List<Int>, gapPx: Int): Boolean =
        chipWidthsPx.isNotEmpty() && chipWidthsPx.sum() + gapPx * chipWidthsPx.size > availableWidthPx

    /** Touch position to 0..1 over the bar, which starts and ends [inset] px inside the view. */
    fun seekFraction(x: Float, width: Float, inset: Float = 0f): Float {
        val span = width - 2f * inset.coerceAtLeast(0f)
        if (span <= 0f) return 0f
        return ((x - inset.coerceAtLeast(0f)) / span).coerceIn(0f, 1f)
    }

    /** Knob centre for [fraction]: [inset] at 0, width - [inset] at 1, so the knob is never clipped. */
    fun knobCenter(fraction: Float, width: Float, inset: Float): Float {
        val i = inset.coerceAtLeast(0f)
        val span = (width - 2f * i).coerceAtLeast(0f)
        return i + span * fraction.coerceIn(0f, 1f)
    }

    /** Derived data only; scoped by account so a different login never reuses another's file. */
    fun waveformCacheName(accountId: Long, songKey: Long, sizeBytes: Long): String =
        "wf_${accountId}_${songKey}_${sizeBytes}.bin"

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
