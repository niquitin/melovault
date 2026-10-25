package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer
import com.bsdung.musicassistantcar.data.telegram.MeloThemePolicy.ColorTheme
import com.bsdung.musicassistantcar.data.telegram.MeloThemePolicy.SyncDot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloThemePolicyTest {

    @Test
    fun defaultIsCurrentAndKeysRoundTrip() {
        assertEquals(ColorTheme.CURRENT, ColorTheme.DEFAULT)
        ColorTheme.entries.forEach { assertEquals(it, ColorTheme.fromKey(it.key)) }
    }

    @Test
    fun missingOrUnknownKeyKeepsTheCurrentLook() {
        assertEquals(ColorTheme.CURRENT, ColorTheme.fromKey(null))
        assertEquals(ColorTheme.CURRENT, ColorTheme.fromKey(""))
        assertEquals(ColorTheme.CURRENT, ColorTheme.fromKey("neon_pink"))
    }

    @Test
    fun tealAppliesOnlyToThePhoneAndTabletLibraryLayers() {
        NewLayer.entries.forEach { layer ->
            assertTrue(MeloThemePolicy.appliesTo(ColorTheme.TEAL_BRIGHT, layer, Profile.PHONE))
            assertTrue(MeloThemePolicy.appliesTo(ColorTheme.TEAL_BRIGHT, layer, Profile.TABLET))
            assertFalse(MeloThemePolicy.appliesTo(ColorTheme.TEAL_BRIGHT, layer, Profile.COMPACT))
            assertFalse(MeloThemePolicy.appliesTo(ColorTheme.TEAL_BRIGHT, layer, Profile.CAR))
        }
    }

    @Test
    fun noLayerMeansNoTheme() {
        // Gọn, Car Mode, the legacy library and a signed-out app have no new layer.
        assertFalse(MeloThemePolicy.appliesTo(ColorTheme.TEAL_BRIGHT, null, Profile.PHONE))
        assertEquals(ColorTheme.CURRENT, MeloThemePolicy.effective(ColorTheme.TEAL_BRIGHT, null, Profile.TABLET))
    }

    @Test
    fun currentThemeNeverAppliesAnywhere() {
        NewLayer.entries.forEach { layer ->
            Profile.entries.forEach { profile ->
                assertFalse(MeloThemePolicy.appliesTo(ColorTheme.CURRENT, layer, profile))
                assertEquals(ColorTheme.CURRENT, MeloThemePolicy.effective(ColorTheme.CURRENT, layer, profile))
            }
        }
    }

    @Test
    fun effectiveThemeIsTealOnlyWhereItApplies() {
        assertEquals(ColorTheme.TEAL_BRIGHT, MeloThemePolicy.effective(ColorTheme.TEAL_BRIGHT, NewLayer.TWO_PANE, Profile.TABLET))
        assertEquals(ColorTheme.CURRENT, MeloThemePolicy.effective(ColorTheme.TEAL_BRIGHT, NewLayer.TWO_PANE, Profile.CAR))
    }

    @Test
    fun rowArtNeedsTealAndTheSwitch() {
        assertTrue(MeloThemePolicy.rowArtActive(ColorTheme.TEAL_BRIGHT, true))
        assertFalse(MeloThemePolicy.rowArtActive(ColorTheme.TEAL_BRIGHT, false))
        assertFalse(MeloThemePolicy.rowArtActive(ColorTheme.CURRENT, true))
        assertFalse(MeloThemePolicy.rowArtActive(ColorTheme.CURRENT, false))
    }

    @Test
    fun syncDotFollowsTheRealState() {
        val updated = "27.971 bài · ${TelegramLibrarySyncDisplayPolicy.LIBRARY_UPDATED}"
        assertEquals(SyncDot.OK, MeloThemePolicy.syncDot(true, updated))
        assertEquals(SyncDot.OK, MeloThemePolicy.syncDot(true, null))
        assertEquals(SyncDot.SYNCING, MeloThemePolicy.syncDot(true, "27.971 bài · ${TelegramLibrarySyncDisplayPolicy.LIBRARY_UPDATING}"))
        assertEquals(SyncDot.SYNCING, MeloThemePolicy.syncDot(true, TelegramLibrarySyncDisplayPolicy.LIBRARY_PAUSED))
        assertEquals(SyncDot.OFFLINE, MeloThemePolicy.syncDot(false, updated))
    }

    @Test
    fun syncHeadlineUsesTheRealCount() {
        assertEquals("Đồng bộ: 27.971 bài", MeloThemePolicy.syncHeadline(27971))
        assertEquals("Đồng bộ: chưa có bài", MeloThemePolicy.syncHeadline(null))
        assertEquals("Đồng bộ: chưa có bài", MeloThemePolicy.syncHeadline(0))
        assertNotEquals("Đồng bộ: 0 bài", MeloThemePolicy.syncHeadline(0))
    }
}
