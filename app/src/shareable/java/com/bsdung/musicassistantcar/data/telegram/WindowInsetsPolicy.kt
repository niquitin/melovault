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
     */
    fun computeMainRootPadding(
        systemBars: Insets,
        ime: Insets,
        displayCutout: Insets,
        baseHorizontal: Int,
        baseTop: Int,
        baseBottom: Int
    ): Insets {
        val leftInset = maxOf(systemBars.left, displayCutout.left)
        val rightInset = maxOf(systemBars.right, displayCutout.right)
        val topInset = maxOf(systemBars.top, displayCutout.top)
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
        baseBottom: Int
    ): Insets {
        val leftInset = maxOf(systemBars.left, displayCutout.left)
        val rightInset = maxOf(systemBars.right, displayCutout.right)
        val topInset = maxOf(systemBars.top, displayCutout.top)
        val bottomInset = systemBars.bottom

        return Insets(
            left = leftInset + baseHorizontal,
            top = topInset + baseTop,
            right = rightInset + baseHorizontal,
            bottom = bottomInset + baseBottom
        )
    }
}
