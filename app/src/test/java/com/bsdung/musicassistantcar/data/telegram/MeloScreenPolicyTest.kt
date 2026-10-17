package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Emulation
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloScreenPolicyTest {

    @Test
    fun shanlingEmulationMatchesSpecNumbers() {
        val s = Emulation.SHANLING_M3_ULTRA.spec()!!
        assertEquals(768, s.widthPx)
        assertEquals(1280, s.heightPx)
        assertEquals(320, s.densityDpi)
        assertEquals(384, s.widthDp)
        assertEquals(640, s.heightDp)
        assertEquals(355f, s.physicalPpi, 1f)
        assertEquals(4.2f, s.diagonalInches, 0.01f)
        // Spec: 1dp is about 0.14mm on this panel, so 48dp is about 7mm.
        assertEquals(0.143f, s.dpInMm, 0.005f)
        assertEquals(Layout.COMPACT, MeloScreenPolicy.chooseLayout(s.widthDp, s.heightDp, false))
    }

    @Test
    fun fytEmulationsDifferOnlyInAndroidDensity() {
        val f160 = Emulation.FYT_7870_160.spec()!!
        val f240 = Emulation.FYT_7870_240.spec()!!
        assertEquals(1920, f160.widthDp)
        assertEquals(1200, f160.heightDp)
        assertEquals(1280, f240.widthDp)
        assertEquals(800, f240.heightDp)
        assertEquals(f160.physicalPpi, f240.physicalPpi, 0.001f)
        assertEquals(172.8f, f160.physicalPpi, 0.5f)
        assertEquals(282f, f160.widthMm, 2f)
        assertEquals(176f, f160.heightMm, 2f)
    }

    @Test
    fun offHasNoSpecAndUnknownKeyFallsBackToOff() {
        assertNull(Emulation.OFF.spec())
        assertFalse(Emulation.OFF.isActive)
        assertEquals(Emulation.OFF, Emulation.fromKey(null))
        assertEquals(Emulation.OFF, Emulation.fromKey("nonexistent"))
        assertEquals(Emulation.FYT_7870_240, Emulation.fromKey("fyt_7870_240"))
    }

    @Test
    fun layoutThresholdsFollowSpecTable() {
        assertEquals(Layout.CAR, MeloScreenPolicy.chooseLayout(384, 640, true))
        assertEquals(Layout.COMPACT, MeloScreenPolicy.chooseLayout(599, 699, false))
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(599, 700, false))
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(412, 915, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(600, 960, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(839, 1200, false))
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(840, 500, false))
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(1920, 1200, false))
    }

    @Test
    fun profileKeepsTabletForY700AndPhoneForSidewaysPhone() {
        assertEquals(Profile.TABLET, MeloScreenPolicy.profileFor(Layout.TWO_PANE, 800))
        assertEquals(Profile.PHONE, MeloScreenPolicy.profileFor(Layout.TWO_PANE, 412))
        assertEquals(Profile.COMPACT, MeloScreenPolicy.profileFor(Layout.COMPACT, 384))
        assertEquals(Profile.CAR, MeloScreenPolicy.profileFor(Layout.CAR, 800))
    }

    @Test
    fun carModeIsOfferedOnceOnWideLandscapeOnly() {
        assertTrue(MeloScreenPolicy.shouldOfferCarMode(1920, 1200, carMode = false, alreadyAsked = false))
        assertTrue(MeloScreenPolicy.shouldOfferCarMode(1280, 800, carMode = false, alreadyAsked = false))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1280, 800, carMode = false, alreadyAsked = true))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1280, 800, carMode = true, alreadyAsked = false))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1199, 700, carMode = false, alreadyAsked = false))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1200, 1920, carMode = false, alreadyAsked = false))
    }

    @Test
    fun implausibleFirmwareXdpiFallsBackToDensity() {
        assertEquals(240f, MeloScreenPolicy.physicalPpi(0f, 0f, 240), 0.001f)
        assertEquals(240f, MeloScreenPolicy.physicalPpi(10_000f, 5f, 240), 0.001f)
        assertEquals(173f, MeloScreenPolicy.physicalPpi(173f, 2f, 240), 0.001f)
        assertEquals(344f, MeloScreenPolicy.physicalPpi(343f, 345f, 400), 0.001f)
        assertFalse(DisplaySpec(1920, 1200, 160, 0f, 0f).physicalPpiReliable)
        assertTrue(DisplaySpec(1920, 1200, 160, 173f, 172f).physicalPpiReliable)
    }

    @Test
    fun densityBuckets() {
        assertEquals("mdpi", MeloScreenPolicy.densityBucket(160))
        assertEquals("hdpi", MeloScreenPolicy.densityBucket(240))
        assertEquals("xhdpi", MeloScreenPolicy.densityBucket(320))
        assertEquals("xxhdpi", MeloScreenPolicy.densityBucket(420))
    }

    @Test
    fun fitScaleIsExactWhenFrameFitsAndShrinksOtherwise() {
        assertEquals(1f, MeloScreenPolicy.fitScale(1920, 1200, 2560, 1500), 0.0001f)
        assertEquals(1f, MeloScreenPolicy.fitScale(768, 1280, 2560, 1500), 0.0001f)
        assertEquals(1600f / 1920f, MeloScreenPolicy.fitScale(1920, 1200, 1600, 2400), 0.0001f)
        assertEquals(1f, MeloScreenPolicy.fitScale(0, 0, 100, 100), 0.0001f)
    }

    @Test
    fun measuredS25UltraUsesPhonePortraitAndTwoPaneLandscape() {
        // Owner measurement 2026-10-17, SM-S938B: 1080 × 2340 px, 450 dpi, 375.8 × 376.2 ppi.
        val portrait = DisplaySpec(1080, 2340, 450, 375.8f, 376.2f)
        val landscape = DisplaySpec(2340, 1080, 450, 375.8f, 376.2f)
        assertEquals(384, portrait.widthDp)
        assertEquals(832, portrait.heightDp)
        assertEquals(0.190f, portrait.dpInMm, 0.002f)
        assertEquals(6.9f, portrait.diagonalInches, 0.05f)
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(384, 832, false))
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(landscape.widthDp, landscape.heightDp, false))
        assertEquals(Profile.PHONE, MeloScreenPolicy.profileFor(Layout.TWO_PANE, landscape.smallestWidthDp))
        assertFalse(MeloCompactPolicy.shouldUseCompact(MeloCompactPolicy.CompactSetting.AUTO, 384, 832, authorized = true))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(832, 384, carMode = false, alreadyAsked = false))
    }

    @Test
    fun landscapeBetween600And839IsTwoPanePortraitStaysTablet() {
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(700, 500, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(700, 1000, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(800, 800, false))
    }

    @Test
    fun y700LikeDisplayChoosesTwoPaneLandscapeAndTabletPortrait() {
        // [CẦN XÁC NHẬN] real Y700 density must come from the "Thông tin màn hình" screen.
        val landscape = DisplaySpec(2560, 1600, 400, 343f, 343f)
        val portrait = DisplaySpec(1600, 2560, 400, 343f, 343f)
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(landscape.widthDp, landscape.heightDp, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(portrait.widthDp, portrait.heightDp, false))
    }
}
