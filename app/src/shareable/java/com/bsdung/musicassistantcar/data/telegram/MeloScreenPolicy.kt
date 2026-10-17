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

    /** Spec "Cách app chọn bố cục": window size in dp; Car Mode overrides every other rule. */
    fun chooseLayout(widthDp: Int, heightDp: Int, carMode: Boolean): Layout = when {
        carMode -> Layout.CAR
        widthDp < COMPACT_MAX_WIDTH_DP && heightDp < COMPACT_MAX_HEIGHT_DP -> Layout.COMPACT
        widthDp < TABLET_MIN_WIDTH_DP -> Layout.PHONE_PORTRAIT
        widthDp >= TWO_PANE_MIN_WIDTH_DP -> Layout.TWO_PANE
        // Measured S25 Ultra turned sideways is 832 × 384dp: just under 840 but far too short
        // for the tablet column, so any landscape window from 600dp wide gets the two panes.
        widthDp > heightDp -> Layout.TWO_PANE
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
