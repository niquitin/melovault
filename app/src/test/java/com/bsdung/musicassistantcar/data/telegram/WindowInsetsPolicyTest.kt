package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowInsetsPolicyTest {

    @Test
    fun standardPhonePortraitInsets() {
        // System status bar = 38px, gesture bar = 24px, base H = 12px, base Top = 6px, base Bottom = 3px
        val systemBars = WindowInsetsPolicy.Insets(left = 0, top = 38, right = 0, bottom = 24)
        val ime = WindowInsetsPolicy.Insets.ZERO
        val cutout = WindowInsetsPolicy.Insets.ZERO

        val padding = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = systemBars,
            ime = ime,
            displayCutout = cutout,
            baseHorizontal = 12,
            baseTop = 6,
            baseBottom = 3
        )

        assertEquals(12, padding.left)
        assertEquals(44, padding.top)     // 38 + 6
        assertEquals(12, padding.right)
        assertEquals(27, padding.bottom)  // 24 + 3
    }

    @Test
    fun persistentTaskbarOcclusionPrevention() {
        // Lenovo Legion Y700 Gen 3 persistent taskbar = 80px
        val systemBars = WindowInsetsPolicy.Insets(left = 0, top = 30, right = 0, bottom = 80)
        val ime = WindowInsetsPolicy.Insets.ZERO
        val cutout = WindowInsetsPolicy.Insets.ZERO

        val padding = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = systemBars,
            ime = ime,
            displayCutout = cutout,
            baseHorizontal = 12,
            baseTop = 6,
            baseBottom = 3
        )

        // Bottom padding must include the full taskbar height (80 + 3 = 83px),
        // preventing bottom mini player or navigation tabs from being occluded.
        assertEquals(83, padding.bottom)
    }

    @Test
    fun softKeyboardImeTakesPrecedenceOverNavigationBar() {
        // Soft keyboard open: IME bottom = 320px, while nav bar = 24px
        val systemBars = WindowInsetsPolicy.Insets(left = 0, top = 38, right = 0, bottom = 24)
        val ime = WindowInsetsPolicy.Insets(left = 0, top = 0, right = 0, bottom = 320)
        val cutout = WindowInsetsPolicy.Insets.ZERO

        val padding = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = systemBars,
            ime = ime,
            displayCutout = cutout,
            baseHorizontal = 12,
            baseTop = 6,
            baseBottom = 3
        )

        // Bottom padding must expand to accommodate the IME keyboard (320 + 3 = 323px)
        assertEquals(323, padding.bottom)
    }

    @Test
    fun displayCutoutPrecedenceOverStatusBar() {
        // Deep camera notch / punch hole: cutout top = 54px, status bar = 38px
        val systemBars = WindowInsetsPolicy.Insets(left = 0, top = 38, right = 0, bottom = 24)
        val ime = WindowInsetsPolicy.Insets.ZERO
        val cutout = WindowInsetsPolicy.Insets(left = 0, top = 54, right = 0, bottom = 0)

        val padding = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = systemBars,
            ime = ime,
            displayCutout = cutout,
            baseHorizontal = 12,
            baseTop = 6,
            baseBottom = 3
        )

        // Top padding must accommodate the deeper camera notch (54 + 6 = 60px)
        assertEquals(60, padding.top)
    }

    @Test
    fun noDoublePaddingApplied() {
        // Base padding is applied exactly once on top of system bars
        val systemBars = WindowInsetsPolicy.Insets(left = 10, top = 40, right = 10, bottom = 30)
        val ime = WindowInsetsPolicy.Insets.ZERO
        val cutout = WindowInsetsPolicy.Insets.ZERO

        val padding = WindowInsetsPolicy.computeMainRootPadding(
            systemBars = systemBars,
            ime = ime,
            displayCutout = cutout,
            baseHorizontal = 8,
            baseTop = 5,
            baseBottom = 4
        )

        assertEquals(18, padding.left)    // 10 + 8
        assertEquals(45, padding.top)     // 40 + 5
        assertEquals(18, padding.right)   // 10 + 8
        assertEquals(34, padding.bottom)  // 30 + 4
    }

    @Test
    fun dialogRootPaddingCalculation() {
        val systemBars = WindowInsetsPolicy.Insets(left = 0, top = 38, right = 0, bottom = 24)
        val cutout = WindowInsetsPolicy.Insets.ZERO

        val padding = WindowInsetsPolicy.computeDialogRootPadding(
            systemBars = systemBars,
            displayCutout = cutout,
            baseHorizontal = 22,
            baseTop = 20,
            baseBottom = 20
        )

        assertEquals(22, padding.left)
        assertEquals(58, padding.top)     // 38 + 20
        assertEquals(22, padding.right)
        assertEquals(44, padding.bottom)  // 24 + 20
    }
}
