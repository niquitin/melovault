package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import kotlin.math.sqrt

/**
 * Screen measurements, test-only device emulation and the layout rules of
 * docs/ui-spec.md. Pure JVM except the SharedPreferences helpers.
 * The thresholds 600/700/840/1200dp are the spec's estimates. Measured on S25 Ultra
 * (2026-10-17): 384 × 832dp portrait, 832 × 384dp landscape, 450 dpi, 376 ppi.
 * Y700 measurement was dropped by the owner; tablet thresholds stay at the spec estimates.
 */
internal object MeloScreenPolicy {

    const val PREFS_NAME = "melovault_ui_lab"
    const val KEY_EMULATION = "device_emulation"
    const val KEY_PREVIEW_CAR_MODE = "preview_car_mode"

    const val COMPACT_MAX_WIDTH_DP = 600
    const val COMPACT_MAX_HEIGHT_DP = 700
    const val TABLET_MIN_WIDTH_DP = 600
    const val TWO_PANE_MIN_WIDTH_DP = 840
    const val CAR_OFFER_MIN_WIDTH_DP = 1200

    /**
     * E95 window thresholds (D3, tuned after FYT QA): from this width the player and the library
     * sit side by side; a narrower window keeps one list and the bottom bar.
     */
    const val TWO_PANE_PLAYER_MIN_WIDTH_DP = 960

    /** A landscape window shorter than this is too low for a list with a bottom bar: two panes. */
    const val LIST_FIRST_MIN_HEIGHT_DP = 560

    /** A phone window narrower than 600dp and shorter than this stays on the old compact layout. */
    const val SPLIT_COMPACT_MAX_HEIGHT_DP = 480

    /** A panel qualifies for Gọn only when it is smaller than this on both sides (Shanling 384 × 640dp). */
    const val PANEL_COMPACT_MAX_SHORT_DP = 600
    const val PANEL_COMPACT_MAX_LONG_DP = 700

    /** A physical panel dimension outside 1..this (px) is firmware noise and is ignored. */
    private const val MAX_PLAUSIBLE_PANEL_PX = 20_000

    /** A plausible physical density; outside it xdpi/ydpi from firmware is treated as unknown. */
    private const val MIN_PLAUSIBLE_PPI = 80f
    private const val MAX_PLAUSIBLE_PPI = 800f

    data class DisplaySpec(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val xdpi: Float,
        val ydpi: Float,
        val fontScale: Float = 1f
    ) {
        init {
            require(widthPx > 0 && heightPx > 0 && densityDpi > 0 && fontScale > 0f)
        }

        val density: Float get() = densityDpi / 160f
        val widthDp: Int get() = (widthPx / density).toInt()
        val heightDp: Int get() = (heightPx / density).toInt()
        val smallestWidthDp: Int get() = minOf(widthDp, heightDp)
        val isLandscape: Boolean get() = widthPx > heightPx

        /** Firmware such as FYT may report any xdpi; fall back to densityDpi when it is implausible. */
        val physicalPpi: Float get() = physicalPpi(xdpi, ydpi, densityDpi)
        val physicalPpiReliable: Boolean get() = isPlausible(xdpi) && isPlausible(ydpi)
        val widthMm: Float get() = widthPx * 25.4f / physicalPpi
        val heightMm: Float get() = heightPx * 25.4f / physicalPpi
        val diagonalInches: Float get() =
            sqrt(widthPx.toFloat() * widthPx + heightPx.toFloat() * heightPx) / physicalPpi

        /** One dp in millimetres on this panel. */
        val dpInMm: Float get() = density * 25.4f / physicalPpi
    }

    fun isPlausible(ppi: Float): Boolean = ppi in MIN_PLAUSIBLE_PPI..MAX_PLAUSIBLE_PPI

    fun physicalPpi(xdpi: Float, ydpi: Float, densityDpi: Int): Float = when {
        isPlausible(xdpi) && isPlausible(ydpi) -> (xdpi + ydpi) / 2f
        isPlausible(xdpi) -> xdpi
        isPlausible(ydpi) -> ydpi
        else -> densityDpi.toFloat()
    }

    fun ppiFromDiagonal(widthPx: Int, heightPx: Int, diagonalInches: Float): Float =
        sqrt(widthPx.toFloat() * widthPx + heightPx.toFloat() * heightPx) / diagonalInches

    enum class Emulation(
        val key: String,
        val displayName: String,
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int,
        val diagonalInches: Float
    ) {
        OFF("off", "Tắt", 0, 0, 0, 0f),
        SHANLING_M3_ULTRA("shanling_m3_ultra", "Shanling M3 Ultra (768 × 1280, mật độ 320)", 768, 1280, 320, 4.2f),
        FYT_7870_160("fyt_7870_160", "FYT 7870 (1920 × 1200, mật độ 160)", 1920, 1200, 160, 13.1f),
        FYT_7870_240("fyt_7870_240", "FYT 7870 (1920 × 1200, mật độ 240)", 1920, 1200, 240, 13.1f);

        val isActive: Boolean get() = this != OFF

        /** Physical density is computed from the hardware diagonal, as in the spec table. */
        fun spec(): DisplaySpec? {
            if (!isActive) return null
            val ppi = ppiFromDiagonal(widthPx, heightPx, diagonalInches)
            return DisplaySpec(widthPx, heightPx, densityDpi, ppi, ppi, 1f)
        }

        companion object {
            fun fromKey(key: String?): Emulation = entries.firstOrNull { it.key == key } ?: OFF
        }
    }

    enum class Layout(val displayName: String) {
        CAR("Car Mode"),
        COMPACT("Gọn (máy nhỏ)"),
        PHONE_PORTRAIT("Điện thoại dọc"),
        TABLET_PORTRAIT("Tablet dọc"),
        TWO_PANE("Hai khung ngang")
    }

    /**
     * The largest plausible physical panel among [candidates] (width × height in px, any
     * orientation) as (short side, long side), or null when none is usable. 0, negative and absurd
     * values from firmware are skipped, so the remaining numbers still decide.
     */
    fun panelSizePx(candidates: List<Pair<Int, Int>>): Pair<Int, Int>? =
        candidates
            .filter { (w, h) -> w in 1..MAX_PLAUSIBLE_PANEL_PX && h in 1..MAX_PLAUSIBLE_PANEL_PX }
            .maxByOrNull { (w, h) -> w.toLong() * h }
            ?.let { (w, h) -> minOf(w, h) to maxOf(w, h) }

    /**
     * Gọn is for a small panel that is NOT shared with another app. [inMultiWindow] is the safety
     * net for a launcher that gives every app card its own display (the "panel" is then the card).
     */
    fun panelCompact(panelShortDp: Int, panelLongDp: Int, inMultiWindow: Boolean): Boolean =
        !inMultiWindow && panelShortDp in 1 until PANEL_COMPACT_MAX_SHORT_DP && panelLongDp in 1 until PANEL_COMPACT_MAX_LONG_DP

    /**
     * Spec "Cách app chọn bố cục" (E95): window size in dp; Car Mode overrides every other rule.
     * [panelCompact] tells whether the PANEL is small (Shanling): only then does a narrow window
     * mean the Gọn layout; a narrow window of a big panel (a split screen on the head unit) is
     * the Điện thoại list with the bottom bar. Callers pass it explicitly, there is no default.
     */
    fun chooseLayout(widthDp: Int, heightDp: Int, carMode: Boolean, panelCompact: Boolean): Layout = when {
        carMode -> Layout.CAR
        panelCompact && widthDp < COMPACT_MAX_WIDTH_DP && heightDp < COMPACT_MAX_HEIGHT_DP -> Layout.COMPACT
        // A phone cut to a very low split window keeps the old layout, as before E95.
        widthDp < COMPACT_MAX_WIDTH_DP && heightDp < SPLIT_COMPACT_MAX_HEIGHT_DP -> Layout.COMPACT
        widthDp < TABLET_MIN_WIDTH_DP -> Layout.PHONE_PORTRAIT
        widthDp >= TWO_PANE_PLAYER_MIN_WIDTH_DP -> Layout.TWO_PANE
        // Measured S25 Ultra turned sideways is 832 × 384dp: far too short for the tablet column,
        // so a low landscape window gets the two panes; a taller one (Y700 split 800 × 620, the
        // head unit's 782 × 642) keeps one wide list with the bottom bar.
        widthDp > heightDp && heightDp < LIST_FIRST_MIN_HEIGHT_DP -> Layout.TWO_PANE
        else -> Layout.TABLET_PORTRAIT
    }

    /** Y700 uses Tablet in both orientations; a phone turned sideways keeps the Phone sizes. */
    fun profileFor(layout: Layout, smallestWidthDp: Int): Profile = when (layout) {
        Layout.CAR -> Profile.CAR
        Layout.COMPACT -> Profile.COMPACT
        Layout.PHONE_PORTRAIT -> Profile.PHONE
        Layout.TABLET_PORTRAIT -> Profile.TABLET
        Layout.TWO_PANE -> if (smallestWidthDp >= TABLET_MIN_WIDTH_DP) Profile.TABLET else Profile.PHONE
    }

    /** First launch on a wide landscape screen asks "Bật Car Mode?" instead of enabling it. */
    fun shouldOfferCarMode(widthDp: Int, heightDp: Int, carMode: Boolean, alreadyAsked: Boolean): Boolean =
        !carMode && !alreadyAsked && widthDp > heightDp && widthDp >= CAR_OFFER_MIN_WIDTH_DP

    fun densityBucket(densityDpi: Int): String = when {
        densityDpi <= 120 -> "ldpi"
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 213 -> "tvdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    /** Scale applied to an emulated frame that does not fit the real window (1 = exact pixels). */
    fun fitScale(frameW: Int, frameH: Int, availableW: Int, availableH: Int): Float {
        if (frameW <= 0 || frameH <= 0 || availableW <= 0 || availableH <= 0) return 1f
        return minOf(1f, availableW.toFloat() / frameW, availableH.toFloat() / frameH)
    }

    fun loadEmulation(prefs: SharedPreferences): Emulation = Emulation.fromKey(prefs.getString(KEY_EMULATION, null))

    fun saveEmulation(prefs: SharedPreferences, emulation: Emulation) {
        prefs.edit().putString(KEY_EMULATION, emulation.key).apply()
    }

    fun loadPreviewCarMode(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_PREVIEW_CAR_MODE, false)

    fun savePreviewCarMode(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_CAR_MODE, enabled).apply()
    }
}
