package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * E96 P4-01/05: layout MATH for the Car Save row. These are pure-JVM calculations from the same [MeloDesignTokens.Sizes]
 * the screen uses - a simulation of the device windows, NOT screenshots of a Sony, Shanling, Lenovo or FYT head unit.
 */
class MeloCarLayoutPolicyTest {

    private class Device(val name: String, val widthPx: Int, val heightPx: Int, val ppi: Float)

    private val devices = listOf(
        Device("FYT 7870 1920x1200 (spec ppi)", 1920, 1200, 172.8f),
        Device("FYT 7870 firmware reporting 240 ppi", 1920, 1200, 240f),
        Device("Sony Xperia 5 II landscape", 2520, 1080, 449f),
        Device("Sony Xperia 5 II portrait", 1080, 2520, 449f),
        Device("Shanling M3 Ultra portrait", 768, 1280, 320f),
        Device("Lenovo Y700 landscape", 2560, 1600, 343f),
        Device("Lenovo Y700 portrait", 1600, 2560, 343f),
    )

    private fun sizesOf(d: Device): Pair<MeloDesignTokens.Sizes, Float> {
        val scale = MeloCarPolicy.carScale(d.ppi, d.widthPx, d.heightPx)
        val eff = MeloCarPolicy.effectivePpi(d.ppi, d.widthPx, d.heightPx)
        return MeloDesignTokens.sizes(MeloDesignTokens.Profile.CAR, d.ppi / 160f, 1f, eff) to scale
    }

    @Test fun l01_theSaveRowAndTheStatusLineFitEveryWindowTheCarScreenCanBeDrawnIn() {
        for (d in devices) {
            val (s, scale) = sizesOf(d)
            val available = MeloCarLayoutPolicy.availableHeightPx(d.heightPx, s, MeloCarLayoutPolicy.barsBudgetPx(scale))
            assertTrue("${d.name}: player pane ${MeloCarLayoutPolicy.playerPaneHeightPx(s)} > $available", MeloCarLayoutPolicy.playerPaneHeightPx(s) <= available)
            assertTrue("${d.name}: list pane ${MeloCarLayoutPolicy.listPaneHeightPx(s, true)} > $available", MeloCarLayoutPolicy.listPaneHeightPx(s, true) <= available)
            assertTrue(d.name, MeloCarLayoutPolicy.fits(d.heightPx, s, scale))
        }
    }

    @Test fun l02_onTheFytPanelTheSaveRowIsTheSizeOfAPageButtonAndLeavesRoomForTheBars() {
        val d = devices.first()
        val (s, _) = sizesOf(d)
        assertEquals(1.0f, MeloCarPolicy.carScale(d.ppi, d.widthPx, d.heightPx))
        assertEquals(140, MeloSavePolicy.carButtonHeightPx(s))
        // 1200 - padding - bars budget must exceed the taller pane by a visible margin, not by one pixel.
        val spare = MeloCarLayoutPolicy.availableHeightPx(d.heightPx, s, MeloCarLayoutPolicy.SYSTEM_BARS_REFERENCE_PX) - MeloCarLayoutPolicy.playerPaneHeightPx(s)
        assertTrue("spare=$spare", spare >= 50)
    }

    @Test fun l03_theSaveTargetIsNeverSmallerThanTheMinimumTouchTargetOnAnyWindow() {
        for (d in devices) {
            val (s, _) = sizesOf(d)
            assertTrue(d.name, MeloCarLayoutPolicy.saveTargetOk(s))
            assertTrue(d.name, MeloSavePolicy.carButtonHeightPx(s) >= s.minTouch)
            assertTrue(d.name, MeloSavePolicy.carButtonHeightPx(s) >= s.pageButtonHeight)
        }
    }

    @Test fun l04_thePhysicalHeightOfTheSaveTargetIsAboutTwoCentimetresEverywhere() {
        for (d in devices) {
            val (s, _) = sizesOf(d)
            val eff = MeloCarPolicy.effectivePpi(d.ppi, d.widthPx, d.heightPx)
            val mm = MeloDesignTokens.pxToMm(MeloSavePolicy.carButtonHeightPx(s).toFloat(), eff)
            // Android's own guidance is 9 mm; a driver should have far more.
            assertTrue("${d.name}: $mm mm", mm >= 15f)
        }
    }

    @Test fun l05_theStatusLineAddsOneSecondaryLineToTheListHeaderAndNothingMore() {
        val (s, _) = sizesOf(devices.first())
        val without = MeloCarLayoutPolicy.listHeaderHeightPx(s, statusLine = false)
        val with = MeloCarLayoutPolicy.listHeaderHeightPx(s, statusLine = true)
        assertTrue(with > without)
        assertTrue("one extra line, not a block: ${with - without}", with - without <= s.secondaryText * 1.5f)
        assertEquals(with - without, MeloCarLayoutPolicy.listPaneHeightPx(s, true) - MeloCarLayoutPolicy.listPaneHeightPx(s, false))
    }

    @Test fun l06_theSaveRowDoesNotOverlapTheTransportOrSeekRowsItSitsBelow() {
        val (s, _) = sizesOf(devices.first())
        val withoutSave = MeloCarLayoutPolicy.playerPaneHeightPx(s) - (s.gap * 2 + MeloSavePolicy.carButtonHeightPx(s))
        // The rows above are unchanged by P4: art 360 + seek + time + transport + seek row.
        assertTrue(withoutSave in 700..860)
        assertTrue(MeloCarLayoutPolicy.playerPaneHeightPx(s) > withoutSave)
    }

    @Test fun l07_textColoursOnTheSaveButtonKeepWcagContrast() {
        val p = MeloDesignTokens.CAR_PALETTE
        fun lum(c: Int): Double {
            fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
        }
        fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }
        assertTrue("saved: accent on accentSoft ${ratio(p.accent, p.accentSoft)}", ratio(p.accent, p.accentSoft) >= 4.5)
        assertTrue("unsaved: text on raised ${ratio(p.text, p.surfaceRaised)}", ratio(p.text, p.surfaceRaised) >= 4.5)
        assertTrue("hint/status: secondary on background ${ratio(p.textSecondary, p.background)}", ratio(p.textSecondary, p.background) >= 4.5)
        assertTrue("status warning on background ${ratio(p.warning, p.background)}", ratio(p.warning, p.background) >= 4.5)
    }

    @Test fun l08_theOtherScreensKeepTheirMinimumTouchTargetForTheSaveChip() {
        for (profile in listOf(MeloDesignTokens.Profile.COMPACT, MeloDesignTokens.Profile.PHONE, MeloDesignTokens.Profile.TABLET)) {
            for (density in listOf(2.0f, 2.625f, 3.0f)) {
                val s = MeloDesignTokens.sizes(profile, density, 1f, 160f * density)
                // MeloComponents.chip is minTouch tall; minTouch is 48 dp.
                assertEquals(profile.name, (48 * density).toInt(), s.minTouch)
            }
        }
    }
}
