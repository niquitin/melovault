package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCarPolicy.CarSetting
import com.bsdung.musicassistantcar.data.telegram.MeloCarPolicy.Nav
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloCarPolicyTest {

    @Test
    fun carOnlyAfterLoginAndNotWhenOff() {
        assertFalse(MeloCarPolicy.shouldUseCar(CarSetting.ON, authorized = false))
        assertFalse(MeloCarPolicy.shouldUseCar(CarSetting.OFF, authorized = true))
        for (s in listOf(CarSetting.ON, CarSetting.EMULATE_FYT_160, CarSetting.EMULATE_FYT_240)) {
            assertTrue(MeloCarPolicy.shouldUseCar(s, authorized = true))
        }
    }

    @Test
    fun settingKeysAndEmulations() {
        for (s in CarSetting.entries) assertEquals(s, CarSetting.fromKey(s.key))
        assertEquals(CarSetting.OFF, CarSetting.fromKey(null))
        assertEquals(CarSetting.OFF, CarSetting.fromKey("bogus"))
        assertEquals(MeloScreenPolicy.Emulation.FYT_7870_160, CarSetting.EMULATE_FYT_160.emulation)
        assertEquals(MeloScreenPolicy.Emulation.FYT_7870_240, CarSetting.EMULATE_FYT_240.emulation)
        assertNull(CarSetting.ON.emulation)
        assertNull(CarSetting.OFF.emulation)
    }

    @Test
    fun askOnceOnWideLandscapeDisplayOnly() {
        assertTrue(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, false, 1920, 1200, authorized = true))
        assertTrue(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, false, 1280, 800, authorized = true))
        assertFalse(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, true, 1920, 1200, authorized = true))
        assertFalse(MeloCarPolicy.shouldAskCarMode(CarSetting.ON, false, 1920, 1200, authorized = true))
        assertFalse(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, false, 1920, 1200, authorized = false))
        // Measured S25 Ultra landscape and a Y700-like tablet are not offered Car Mode.
        assertFalse(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, false, 832, 384, authorized = true))
        assertFalse(MeloCarPolicy.shouldAskCarMode(CarSetting.OFF, false, 1024, 640, authorized = true))
    }

    @Test
    fun scaleIsExactOnFytAndNeverOverflows() {
        assertEquals(1f, MeloCarPolicy.carScale(172.8f, 1920, 1200), 0.0001f)
        // Firmware reporting 240 "ppi" must not push columns off the 1920px panel.
        assertEquals(1f, MeloCarPolicy.carScale(240f, 1920, 1200), 0.0001f)
        // A denser real panel of the same pixel size still fits.
        assertEquals(1f, MeloCarPolicy.carScale(343f, 1920, 1200), 0.0001f)
        // A smaller window shrinks everything to fit.
        assertEquals(1280f / 1920f, MeloCarPolicy.carScale(172.8f, 1280, 800), 0.0001f)
        assertEquals(1f, MeloCarPolicy.carScale(0f, 1920, 1200), 0.0001f)
    }

    @Test
    fun carSizesFitTheFytPanelAtBothFirmwareDensities() {
        for (emulation in listOf(MeloScreenPolicy.Emulation.FYT_7870_160, MeloScreenPolicy.Emulation.FYT_7870_240)) {
            val spec = emulation.spec()!!
            val ppi = MeloCarPolicy.effectivePpi(spec.physicalPpi, spec.widthPx, spec.heightPx)
            val s = MeloDesignTokens.sizes(Profile.CAR, spec.density, spec.fontScale, ppi)
            val r = MeloDesignTokens.CarReferencePx
            val columns = s.navColumnWidth + MeloDesignTokens.carPx(r.PLAYER_PANE, ppi) + MeloDesignTokens.carPx(r.LIST_PANE, ppi)
            assertTrue("columns=$columns", columns <= spec.widthPx)
            val list = MeloCarPolicy.ROWS_PER_PAGE * s.rowHeight + (MeloCarPolicy.ROWS_PER_PAGE - 1) * s.gap +
                s.pageButtonHeight + 2 * s.screenMargin
            assertTrue("list=$list", list <= spec.heightPx)
            assertEquals(160, s.rowHeight, 2)
            assertEquals(240, s.playButtonWidth, 2)
        }
    }

    @Test
    fun paging() {
        assertEquals(1, MeloCarPolicy.pageCount(0, 5))
        assertEquals(1, MeloCarPolicy.pageCount(5, 5))
        assertEquals(2, MeloCarPolicy.pageCount(6, 5))
        assertEquals(listOf(6, 7), MeloCarPolicy.pageItems((1..7).toList(), 1, 5))
        assertEquals(listOf(6, 7), MeloCarPolicy.pageItems((1..7).toList(), 9, 5))
        assertEquals(listOf(1, 2, 3, 4, 5), MeloCarPolicy.pageItems((1..7).toList(), -1, 5))
        assertEquals(0, MeloCarPolicy.clampPage(3, 0, 5))
    }

    @Test
    fun loadMoreBeforeReachingTheEnd() {
        assertTrue(MeloCarPolicy.shouldLoadMore(page = 39, perPage = 5, loaded = 200, total = 25_085))
        assertFalse(MeloCarPolicy.shouldLoadMore(page = 0, perPage = 5, loaded = 200, total = 25_085))
        assertFalse(MeloCarPolicy.shouldLoadMore(page = 39, perPage = 5, loaded = 200, total = 200))
        assertTrue(MeloCarPolicy.shouldLoadMore(page = 39, perPage = 5, loaded = 200, total = null))
    }

    @Test
    fun seekIsClampedToTrack() {
        assertEquals(0L, MeloCarPolicy.seekTarget(10_000, 300_000, MeloCarPolicy.SEEK_BACK_MS))
        assertEquals(70_000L, MeloCarPolicy.seekTarget(10_000, 300_000, MeloCarPolicy.SEEK_FORWARD_MS))
        assertEquals(300_000L, MeloCarPolicy.seekTarget(280_000, 300_000, MeloCarPolicy.SEEK_FORWARD_MS))
        assertEquals(70_000L, MeloCarPolicy.seekTarget(10_000, 0, MeloCarPolicy.SEEK_FORWARD_MS))
        assertEquals(0L, MeloCarPolicy.seekTarget(10_000, 0, MeloCarPolicy.SEEK_BACK_MS))
    }

    @Test
    fun offlineDimsOnlyTelegramOnlySongs() {
        assertTrue(MeloCarPolicy.isOfflineDimmed(online = false, downloaded = false))
        assertFalse(MeloCarPolicy.isOfflineDimmed(online = false, downloaded = true))
        assertFalse(MeloCarPolicy.isOfflineDimmed(online = true, downloaded = false))
    }

    @Test
    fun activeNavFollowsState() {
        assertEquals(Nav.SEARCH, MeloCarPolicy.activeNav(Mode.ALL, showingSources = true, searchOpen = true))
        assertEquals(Nav.TELEGRAM, MeloCarPolicy.activeNav(Mode.ALL, showingSources = true, searchOpen = false))
        assertEquals(Nav.PLAYLIST, MeloCarPolicy.activeNav(Mode.PLAYLIST, showingSources = false, searchOpen = false))
        assertEquals(Nav.DOWNLOADED, MeloCarPolicy.activeNav(Mode.DOWNLOADED, showingSources = false, searchOpen = false))
        assertEquals(Nav.MUSIC, MeloCarPolicy.activeNav(Mode.ALL, showingSources = false, searchOpen = false))
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        assertTrue("expected $expected±$tolerance, was $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
