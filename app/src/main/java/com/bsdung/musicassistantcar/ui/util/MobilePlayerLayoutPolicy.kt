package com.bsdung.musicassistantcar.ui.util

import kotlin.math.roundToInt

/**
 * Pure Kotlin layout policy helper for mobile player sizing and visibility decisions.
 * Contains no Android platform dependencies.
 */
internal object MobilePlayerLayoutPolicy {

    /**
     * Identifies whether the screen belongs to a portrait phone/mobile layout:
     * portrait orientation where height > width, and width under 700dp (below car widescreen qualifier).
     * Non-positive / invalid dimensions evaluate to false.
     */
    fun isPortraitPhone(widthDp: Int, heightDp: Int): Boolean {
        if (widthDp <= 0 || heightDp <= 0) return false
        return widthDp < 700 && heightDp > widthDp
    }

    /**
     * Computes artwork dimension in dp.
     * - For portrait phones: target ~80% of available width, bounded by vertical screen real estate (~45% of height),
     *   at least 140dp on regular phones (width >= 320dp), capped at 320dp, and never exceeding width minus 32dp.
     * - For landscape or car displays: bounded by available height and width, capped at 320dp.
     * - Returns 0 for non-positive / invalid dimensions.
     */
    fun artworkSizeDp(widthDp: Int, heightDp: Int): Int {
        if (widthDp <= 0 || heightDp <= 0) return 0

        return if (isPortraitPhone(widthDp, heightDp)) {
            val targetWidth = (widthDp * 0.80f).roundToInt()
            val maxVerticalFraction = if (heightDp <= 700) 0.40f else 0.45f
            val verticalBound = (heightDp * maxVerticalFraction).roundToInt()
            val maxWidth = widthDp - 32
            val maxCap = if (heightDp <= 700) 230 else 320
            val bounded = minOf(targetWidth, verticalBound, maxWidth, maxCap)
            val regularPhoneSize = if (widthDp >= 320) bounded.coerceAtLeast(140) else bounded
            regularPhoneSize.coerceIn(0, maxWidth)
        } else {
            val maxAllowedWidth = widthDp - 32
            val maxAllowedHeight = heightDp - 32
            val target = if (heightDp > widthDp) {
                (widthDp * 0.60f).roundToInt()
            } else {
                // Dual-panel car layout: Now Playing card is ~40% width.
                // Album cover comfortably fits within available vertical budget (~45-50% height).
                val cardWidth = (widthDp * 0.40f).roundToInt() - 32
                val cardHeight = (heightDp * 0.48f).roundToInt()
                minOf(cardWidth, cardHeight, 240)
            }
            minOf(target, maxAllowedWidth, maxAllowedHeight, 320).coerceAtLeast(0)
        }
    }

    /**
     * Shallow car-landscape windows (e.g. FYT 7870 in 2/3 split or full screen with system bars)
     * previously squeezed the album art to 46dp due to a redundant quick navigation row.
     * With navigation cleaned up, artwork expands to a comfortable ~170–195dp square
     * leaving ample space for transport and secondary controls.
     */
    fun shortExpandedArtworkSizeDp(widthDp: Int, heightDp: Int): Int? =
        if (widthDp >= 500 && widthDp > heightDp && heightDp in 340..430) {
            minOf(200, (heightDp * 0.45f).roundToInt()).coerceAtLeast(140)
        } else null

    /**
     * Landscape player can use tap-to-expand artwork on wide car windows.
     * Portrait phone retains its tap-to-open-Queue interaction.
     */
    fun supportsLandscapeArtworkExpansion(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 500 && heightDp > 0 && widthDp > heightDp

    /**
     * Generous artwork expansion on landscape car windows when requested by user.
     */
    fun expandedLandscapeArtworkSizeDp(widthDp: Int, heightDp: Int): Int? {
        if (!supportsLandscapeArtworkExpansion(widthDp, heightDp)) return null
        return if (heightDp <= 430) {
            minOf(220, (heightDp * 0.55f).roundToInt()).coerceAtLeast(160)
        } else {
            minOf(260, (heightDp * 0.47f).roundToInt())
        }
    }

    /**
     * Determines if the mini player should be shown.
     * Mini player is only shown when browsing on a portrait phone, unless the full player
     * has been explicitly opened by the user.
     */
    fun shouldShowMiniPlayer(
        isPortraitPhone: Boolean,
        isBrowsing: Boolean,
        userOpenedFullPlayer: Boolean
    ): Boolean {
        return isPortraitPhone && isBrowsing && !userOpenedFullPlayer
    }

    /**
     * Queue and inline lyrics are hosted inside libraryCard. On portrait phones,
     * they must keep the compact player visible even when the user previously
     * expanded the full player, or their entire parent panel becomes hidden.
     * Landscape/car UI retains its independent side-by-side full player.
     */
    fun shouldShowMiniPlayerForPanel(
        isPortraitPhone: Boolean,
        isBrowsing: Boolean,
        userOpenedFullPlayer: Boolean,
        isQueueVisible: Boolean,
        isLyricsVisible: Boolean,
    ): Boolean = isPortraitPhone && (
        isQueueVisible || isLyricsVisible ||
            shouldShowMiniPlayer(isPortraitPhone, isBrowsing, userOpenedFullPlayer)
    )

    /**
     * Boolean complement of [shouldShowMiniPlayer].
     */
    fun shouldShowFullPlayer(
        isPortraitPhone: Boolean,
        isBrowsing: Boolean,
        userOpenedFullPlayer: Boolean
    ): Boolean {
        return !shouldShowMiniPlayer(
            isPortraitPhone = isPortraitPhone,
            isBrowsing = isBrowsing,
            userOpenedFullPlayer = userOpenedFullPlayer
        )
    }
}
