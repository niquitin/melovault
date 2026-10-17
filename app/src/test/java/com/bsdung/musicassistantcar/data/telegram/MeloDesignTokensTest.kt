package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeloDesignTokensTest {

    @Test
    fun paletteMatchesSpecTable() {
        val p = MeloDesignTokens.PALETTE
        assertEquals(0xFF0B1622.toInt(), p.background)
        assertEquals(0xFF12212E.toInt(), p.surface)
        assertEquals(0xFF1A2C3A.toInt(), p.surfaceRaised)
        assertEquals(0xFF24394A.toInt(), p.border)
        assertEquals(0xFF2FD3B0.toInt(), p.accent)
        assertEquals(0xFF14463F.toInt(), p.accentSoft)
        assertEquals(0xFFEAF2F5.toInt(), p.text)
        assertEquals(0xFF9FB3BF.toInt(), p.textSecondary)
        assertEquals(0xFF062019.toInt(), p.onAccent)
        assertEquals(0xFFFF7A6B.toInt(), p.warning)
    }

    @Test
    fun carPaletteOnlyRaisesSecondaryContrast() {
        val car = MeloDesignTokens.palette(Profile.CAR)
        assertEquals(0xFFC4D3DB.toInt(), car.textSecondary)
        assertEquals(MeloDesignTokens.PALETTE.copy(textSecondary = car.textSecondary), car)
        assertEquals(MeloDesignTokens.PALETTE, MeloDesignTokens.palette(Profile.TABLET))
    }

    @Test
    fun dpProfilesFollowSpecTableAtDensityOne() {
        val compact = MeloDesignTokens.sizes(Profile.COMPACT, 1f, 1f, 160f)
        assertEquals(16f, compact.titleText, 0.01f)
        assertEquals(13f, compact.secondaryText, 0.01f)
        assertEquals(40, compact.rowArt)
        assertEquals(56, compact.rowHeight)
        assertEquals(56, compact.playButtonWidth)
        assertEquals(56, compact.miniPlayerHeight)
        assertEquals(16, compact.screenMargin)

        val phone = MeloDesignTokens.sizes(Profile.PHONE, 1f, 1f, 160f)
        assertEquals(56, phone.rowArt)
        assertEquals(72, phone.rowHeight)
        assertEquals(64, phone.playButtonHeight)

        val tablet = MeloDesignTokens.sizes(Profile.TABLET, 1f, 1f, 160f)
        assertEquals(17f, tablet.titleText, 0.01f)
        assertEquals(14f, tablet.secondaryText, 0.01f)
        assertEquals(64, tablet.rowArt)
        assertEquals(80, tablet.rowHeight)
        assertEquals(72, tablet.playButtonWidth)
        assertEquals(24, tablet.screenMargin)
        assertEquals(80, tablet.navColumnWidth)
    }

    @Test
    fun dpProfilesScaleWithDensityAndTextWithFontScale() {
        val s = MeloDesignTokens.sizes(Profile.COMPACT, 2f, 1.3f, 355f)
        assertEquals(80, s.rowArt)
        assertEquals(112, s.rowHeight)
        assertEquals(96, s.minTouch)
        assertEquals(16f * 2f * 1.3f, s.titleText, 0.01f)
    }

    @Test
    fun touchTargetsNeverBelow48dp() {
        for (profile in listOf(Profile.COMPACT, Profile.PHONE, Profile.TABLET)) {
            val s = MeloDesignTokens.sizes(profile, 2f, 1f, 320f)
            assertEquals(96, s.minTouch)
            assertTrue(s.playButtonWidth >= s.minTouch)
            assertTrue(s.transportHeight >= s.minTouch)
            assertTrue(s.miniPlayerHeight >= s.minTouch)
        }
    }

    @Test
    fun carModeMatchesSpecPixelsOnRealFytPanel() {
        val s = MeloDesignTokens.sizes(Profile.CAR, 1f, 1f, MeloDesignTokens.CAR_REFERENCE_PPI)
        assertEquals(40f, s.titleText, 0.01f)
        assertEquals(48f, s.playerTitleText, 0.01f)
        assertEquals(30f, s.secondaryText, 0.01f)
        assertEquals(128, s.rowArt)
        assertEquals(160, s.rowHeight)
        assertEquals(240, s.playButtonWidth)
        assertEquals(160, s.playButtonHeight)
        assertEquals(100, s.minTouch)
        assertEquals(160, s.navColumnWidth)
        assertEquals(360, s.playerArt)
        assertEquals(140, s.pageButtonHeight)
        assertEquals(16, s.progressHeight)
        assertEquals(48, s.progressThumb)
    }

    @Test
    fun carModeIgnoresFirmwareDensityAndFontScale() {
        val at160 = MeloDesignTokens.sizes(Profile.CAR, 1f, 1f, 172.8f)
        val at240 = MeloDesignTokens.sizes(Profile.CAR, 1.5f, 1.3f, 172.8f)
        assertEquals(at160, at240)
    }

    @Test
    fun carModeKeepsMillimetresOnDenserPanel() {
        val ppi = 343f
        val s = MeloDesignTokens.sizes(Profile.CAR, 2.5f, 1f, ppi)
        val rowMm = MeloDesignTokens.pxToMm(s.rowHeight.toFloat(), ppi)
        val refMm = MeloDesignTokens.pxToMm(160f, MeloDesignTokens.CAR_REFERENCE_PPI)
        assertTrue(abs(rowMm - refMm) < 0.2f)
        assertTrue(abs(refMm - 23.5f) < 0.2f)
    }

    @Test
    fun minCarTouchIsAboutFifteenMillimetres() {
        val mm = MeloDesignTokens.pxToMm(100f, MeloDesignTokens.CAR_REFERENCE_PPI)
        assertTrue(mm in 14.5f..15.0f)
        assertEquals(100f, MeloDesignTokens.mmToPx(mm, MeloDesignTokens.CAR_REFERENCE_PPI), 0.01f)
    }

    @Test
    fun carListFitsFiveRowsInPanelHeight() {
        val s = MeloDesignTokens.sizes(Profile.CAR, 1f, 1f, MeloDesignTokens.CAR_REFERENCE_PPI)
        val rows = MeloDesignTokens.CarReferencePx.ROWS_PER_PAGE
        val used = rows * s.rowHeight + (rows - 1) * s.gap + s.pageButtonHeight + 2 * s.screenMargin
        assertTrue("used=$used", used <= 1200)
    }

    @Test
    fun carPanesFitFytWidth() {
        val r = MeloDesignTokens.CarReferencePx
        assertTrue(r.NAV_COLUMN + r.PLAYER_PANE + r.LIST_PANE <= 1920)
    }
}
