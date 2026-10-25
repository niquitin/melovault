package com.bsdung.musicassistantcar.data.telegram

/**
 * Deterministic window insets calculations for edge-to-edge layout.
 *
 * Ensures proper padding for system bars (status bar, navigation bar, taskbar),
 * display cutouts, and IME keyboard across devices (including Sony Xperia 21:9
 * and Lenovo Legion Y700 Gen 3 with persistent taskbar), preventing both
 * taskbar occlusion and double-padding.
 */
object WindowInsetsPolicy {

    /** Height of the strip kept free for the head unit's own "•••" card handle (E95 F04), in dp. */
    const val HANDLE_BAND_DP = 24

    /** A window whose top edge sits lower than this on the screen has a bar or app above it, not the handle. */
    const val HANDLE_BAND_MAX_TOP_DP = 80

    const val KEY_HANDLE_BAND = "handle_band"

    /** "Chừa chỗ nút ••• hệ thống": the owner's override when the automatic rule guesses wrong on the head unit. */
    enum class HandleBand(val key: String, val displayName: String) {
        AUTO("auto", "Tự động"),
        ALWAYS("always", "Luôn chừa"),
        NEVER("never", "Không chừa");

        companion object {
            val DEFAULT = AUTO
            fun fromKey(key: String?): HandleBand = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    fun loadHandleBand(prefs: android.content.SharedPreferences): HandleBand =
        HandleBand.fromKey(prefs.getString(KEY_HANDLE_BAND, null))

    fun saveHandleBand(prefs: android.content.SharedPreferences, setting: HandleBand) {
        prefs.edit().putString(KEY_HANDLE_BAND, setting.key).apply()
    }

    /**
     * Pixels to keep free at the top of the window for the card handle. Automatic: only for a
     * window of a shared screen ([inMultiWindow]) whose top edge sits within the first
     * [HANDLE_BAND_MAX_TOP_DP] of the screen ([topDp], > 0) and where Android reports neither a
     * status bar ([statusTopPx]) nor a caption bar ([captionTopPx]) there; a phone's full screen,
     * Shanling (top 0) and the lower window of a top/bottom split are untouched. No device names.
     */
    fun handleBandPx(
        topDp: Int,
        inMultiWindow: Boolean,
        statusTopPx: Int,
        captionTopPx: Int,
        setting: HandleBand,
        bandPx: Int
    ): Int = when (setting) {
        HandleBand.NEVER -> 0
        HandleBand.ALWAYS -> bandPx.coerceAtLeast(0)
        HandleBand.AUTO ->
            if (inMultiWindow && topDp in 1..HANDLE_BAND_MAX_TOP_DP && statusTopPx <= 0 && captionTopPx <= 0) bandPx.coerceAtLeast(0)
            else 0
    }

    data class Insets(
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0
    ) {
        companion object {
            val ZERO = Insets(0, 0, 0, 0)
        }
    }

    /**
     * Computes the root view padding for [TelegramPreviewActivity].
     *
     * @param systemBars insets for status/navigation/caption bars
     * @param ime insets for soft keyboard
     * @param displayCutout insets for display notch or camera cutout
     * @param baseHorizontal base layout horizontal padding in px
     * @param baseTop base layout top padding in px
     * @param baseBottom base layout bottom padding in px
     * @param handleBand extra top room for the head unit's card handle ([handleBandPx]), 0 elsewhere
     */
    fun computeMainRootPadding(
        systemBars: Insets,
        ime: Insets,
        displayCutout: Insets,
        baseHorizontal: Int,
        baseTop: Int,
        baseBottom: Int,
        handleBand: Int = 0
    ): Insets {
        val leftInset = maxOf(systemBars.left, displayCutout.left)
        val rightInset = maxOf(systemBars.right, displayCutout.right)
        val topInset = maxOf(systemBars.top, displayCutout.top) + handleBand.coerceAtLeast(0)
        // If keyboard (IME) is visible, its bottom inset takes precedence over nav bar/taskbar.
        val bottomInset = maxOf(systemBars.bottom, ime.bottom)

        return Insets(
            left = leftInset + baseHorizontal,
            top = topInset + baseTop,
            right = rightInset + baseHorizontal,
            bottom = bottomInset + baseBottom
        )
    }

    /**
     * Computes safe dialog padding for full-screen dialogs like NowPlaying and Queue.
     */
    fun computeDialogRootPadding(
        systemBars: Insets,
        displayCutout: Insets,
        baseHorizontal: Int,
        baseTop: Int,
        baseBottom: Int,
        handleBand: Int = 0
    ): Insets {
        val leftInset = maxOf(systemBars.left, displayCutout.left)
        val rightInset = maxOf(systemBars.right, displayCutout.right)
        val topInset = maxOf(systemBars.top, displayCutout.top) + handleBand.coerceAtLeast(0)
        val bottomInset = systemBars.bottom

        return Insets(
            left = leftInset + baseHorizontal,
            top = topInset + baseTop,
            right = rightInset + baseHorizontal,
            bottom = bottomInset + baseBottom
        )
    }
}
