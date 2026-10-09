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
            val verticalBound = (heightDp * 0.45f).roundToInt()
            val maxWidth = widthDp - 32
            val bounded = minOf(targetWidth, verticalBound, maxWidth, 320)
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
