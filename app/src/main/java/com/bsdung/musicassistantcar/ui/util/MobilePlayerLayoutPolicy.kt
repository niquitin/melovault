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
                (heightDp * 0.70f).roundToInt()
            }
            minOf(target, maxAllowedWidth, maxAllowedHeight, 320).coerceAtLeast(0)
        }
    }

    /**
     * Short car-landscape panels have little vertical space once seek, transport
     * and navigation controls are laid out. Preserve a *complete* small square
     * album cover rather than letting a 200dp cover be clipped to a thin banner.
     * Keep portrait phone and taller tablet artwork behavior unchanged.
     */
    fun shortExpandedArtworkSizeDp(widthDp: Int, heightDp: Int): Int? =
        if (widthDp >= 700 && heightDp in 340..430) 46 else null

    /**
     * Only the wide landscape player can use tap-to-expand artwork. The portrait
     * phone retains its original tap-to-open-Queue interaction.
     */
    fun supportsLandscapeArtworkExpansion(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 700 && heightDp > 0 && widthDp > heightDp

    /**
     * Increase the artwork by about 2x on FYT-class shallow landscape windows.
     * Hiding the quick navigation row recovers the necessary vertical space;
     * larger landscape screens can support a larger artwork size.
     */
    fun expandedLandscapeArtworkSizeDp(widthDp: Int, heightDp: Int): Int? {
        if (!supportsLandscapeArtworkExpansion(widthDp, heightDp)) return null
        return if (heightDp <= 430) {
            minOf(96, (heightDp * 0.26f).roundToInt()).coerceAtLeast(1)
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
