package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer

/**
 * Source cards row above the Melo song list (docs/plans/the-nguon-nhac.md): one card per
 * connected source with real numbers. A source with no known song count has no card, so the
 * row never shows "0 bài". Pure JVM, no Android.
 */
internal object MeloSourceCardPolicy {

    /** Display order = enum order. LOCAL and DRIVE have no data in Shareable yet. */
    enum class Kind { TELEGRAM, LOCAL, DRIVE }

    /** Real numbers from the host; null total = not connected / not known. */
    data class Source(val kind: Kind, val total: Int?, val downloaded: Int?)

    data class Card(
        val kind: Kind,
        val title: String,
        val detail: String,
        val selected: Boolean,
        val contentDescription: String
    )

    /** Two-pane right pane shows the row only when the window is at least this tall. */
    const val TWO_PANE_MIN_HEIGHT_DP = 560

    fun cards(sources: List<Source>, selected: Kind?): List<Card> =
        sources
            .filter { (it.total ?: 0) > 0 }
            .distinctBy { it.kind }
            .sortedBy { it.kind.ordinal }
            .map { source ->
                val total = source.total ?: 0
                val downloaded = source.downloaded?.takeIf { it >= 0 }
                val countText = "${MeloComponentPolicy.formatCount(total)} bài"
                val downloadedText = downloaded?.let { "đã tải ${MeloComponentPolicy.formatCount(it)}" }
                val isSelected = source.kind == selected
                val title = titleOf(source.kind)
                Card(
                    kind = source.kind,
                    title = title,
                    detail = listOfNotNull(countText, downloadedText).joinToString(" · "),
                    selected = isSelected,
                    contentDescription = "Nguồn $title, " +
                        listOfNotNull(countText, downloadedText).joinToString(", ") +
                        if (isSelected) ", đang chọn" else ""
                )
            }

    fun titleOf(kind: Kind): String = when (kind) {
        Kind.TELEGRAM -> "Telegram"
        Kind.LOCAL -> "Nhạc trên máy"
        Kind.DRIVE -> "Google Drive"
    }

    /** One letter for the round badge (no emoji: they break on Android 12 fonts). */
    fun badgeOf(kind: Kind): String = when (kind) {
        Kind.TELEGRAM -> "T"
        Kind.LOCAL -> "♪"
        Kind.DRIVE -> "D"
    }

    fun rowVisible(
        layer: NewLayer,
        showingSources: Boolean,
        mode: Mode,
        windowHeightDp: Int,
        cardCount: Int
    ): Boolean {
        if (cardCount <= 0 || showingSources || mode == Mode.PLAYLIST) return false
        return when (layer) {
            NewLayer.PHONE_PORTRAIT, NewLayer.TABLET_PORTRAIT -> true
            NewLayer.TWO_PANE -> windowHeightDp >= TWO_PANE_MIN_HEIGHT_DP
        }
    }

    /**
     * Width of one card. Everything fits: share the pane evenly (a single card fills it).
     * Otherwise the widest card (>= [minCardPx]) that leaves the next card exactly half
     * visible, so no card is ever cut at a random place.
     */
    fun cardWidthPx(paneWidthPx: Int, count: Int, minCardPx: Int, gapPx: Int): Int {
        if (count <= 0 || paneWidthPx <= 0) return 0
        if (count * minCardPx + (count - 1) * gapPx <= paneWidthPx) {
            return (paneWidthPx - (count - 1) * gapPx) / count
        }
        for (k in (count - 1) downTo 1) {
            val width = ((paneWidthPx - k * gapPx) / (k + 0.5f)).toInt()
            if (width >= minCardPx) return width
        }
        return minOf(minCardPx, paneWidthPx)
    }

    /** Telegram is selected while no specific channel is open; null when a channel is open. */
    fun selectedKind(selectedChatId: Long?, showingSources: Boolean): Kind? =
        if (!showingSources && selectedChatId == null) Kind.TELEGRAM else null
}
