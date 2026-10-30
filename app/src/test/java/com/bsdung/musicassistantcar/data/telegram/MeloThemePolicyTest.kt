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
    fun rowArtLayerAlwaysCoversTheCompactScreenWhateverTheTheme() {
        // Gọn has no new layer (null) and shows covers with every colour theme; Car Mode never does.
        for (theme in ColorTheme.entries) {
            assertTrue(MeloThemePolicy.rowArtLayer(theme, null, Profile.COMPACT))
            assertFalse(MeloThemePolicy.rowArtLayer(theme, null, Profile.CAR))
            NewLayer.entries.forEach { assertFalse(MeloThemePolicy.rowArtLayer(theme, it, Profile.CAR)) }
        }
        assertTrue(MeloThemePolicy.rowArtLayer(ColorTheme.CURRENT, null, Profile.COMPACT))
        assertTrue(MeloThemePolicy.rowArtLayer(ColorTheme.TEAL_BRIGHT, null, Profile.COMPACT))
        // No new layer (signed-out / legacy library) never shows row covers, whatever the profile or theme.
        assertFalse(MeloThemePolicy.rowArtLayer(ColorTheme.TEAL_BRIGHT, null, Profile.PHONE))
        assertFalse(MeloThemePolicy.rowArtLayer(ColorTheme.CURRENT, null, Profile.PHONE))
    }

    /** P2-A (30/10/2026): row covers on Phone/Tablet no longer depend on the colour theme; only the switch does. */
    @Test
    fun rowArtLayerCoversPhoneAndTabletOnEveryColourTheme() {
        for (theme in ColorTheme.entries) for (layer in NewLayer.entries) {
            assertTrue(MeloThemePolicy.rowArtLayer(theme, layer, Profile.PHONE))
            assertTrue(MeloThemePolicy.rowArtLayer(theme, layer, Profile.TABLET))
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

    /** P2-A: fetching needs the layer shown (any colour theme) AND the switch; either alone is not enough. */
    @Test
    fun rowArtActiveNeedsTheLayerShownAndTheSwitch() {
        assertTrue(MeloThemePolicy.rowArtActive(true, true))
        assertFalse(MeloThemePolicy.rowArtActive(true, false))
        assertFalse(MeloThemePolicy.rowArtActive(false, true))
        assertFalse(MeloThemePolicy.rowArtActive(false, false))
    }

    /** Wired through [MeloThemePolicy.rowArtLayer]: Phone/Tablet CURRENT now fetches too, only the switch gates it. */
    @Test
    fun rowArtActiveFollowsRowArtLayerNotTheColourTheme() {
        for (theme in ColorTheme.entries) for (layer in NewLayer.entries) for (switchOn in listOf(true, false)) {
            val layerShown = MeloThemePolicy.rowArtLayer(theme, layer, Profile.PHONE)
            assertEquals(switchOn, MeloThemePolicy.rowArtActive(layerShown, switchOn))
        }
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
