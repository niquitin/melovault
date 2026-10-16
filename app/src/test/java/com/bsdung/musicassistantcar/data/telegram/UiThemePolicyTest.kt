package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.test.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiThemePolicyTest {

    @Test
    fun defaultPresetIsPortraitEdgePro() {
        assertEquals(
            UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
            UiThemePolicy.ThemePreset.DEFAULT
        )
    }

    @Test
    fun fromKeyResolvesCorrectly() {
        assertEquals(
            UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
            UiThemePolicy.ThemePreset.fromKey("portrait_edge_pro")
        )
        assertEquals(
            UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE,
            UiThemePolicy.ThemePreset.fromKey("cockpit_two_pane")
        )
        assertEquals(
            UiThemePolicy.ThemePreset.CLASSIC,
            UiThemePolicy.ThemePreset.fromKey("classic")
        )
        // Unknown or null key safely falls back to default
        assertEquals(
            UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
            UiThemePolicy.ThemePreset.fromKey(null)
        )
        assertEquals(
            UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO,
            UiThemePolicy.ThemePreset.fromKey("unknown_future_theme")
        )
    }

    @Test
    fun preferencesPersistenceAndMigration() {
        val prefs = FakeSharedPreferences()

        // Uninitialized prefs defaults to PORTRAIT_EDGE_PRO
        val initial = UiThemePolicy.getSavedPreset(prefs)
        assertEquals(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO, initial)

        // Save COCKPIT_TWO_PANE
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE)
        assertEquals(UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE, UiThemePolicy.getSavedPreset(prefs))

        // Save CLASSIC
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.CLASSIC)
        assertEquals(UiThemePolicy.ThemePreset.CLASSIC, UiThemePolicy.getSavedPreset(prefs))

        // Save back to PORTRAIT_EDGE_PRO
        UiThemePolicy.savePreset(prefs, UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO)
        assertEquals(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO, UiThemePolicy.getSavedPreset(prefs))
    }

    @Test
    fun portraitEdgeProTokensMatchSpecification() {
        val tokens = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO)

        assertEquals(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO, tokens.preset)
        // Teal #3DDC97
        assertEquals(0xFF3DDC97.toInt(), tokens.accent)
        // Near-black dark navy #0B1420
        assertEquals(0xFF0B1420.toInt(), tokens.background)
        // Surface #152536
        assertEquals(0xFF152536.toInt(), tokens.surface)
        // Card #1B2E42
        assertEquals(0xFF1B2E42.toInt(), tokens.card)
        // Slate-50 Text #F1F5F9
        assertEquals(0xFFF1F5F9.toInt(), tokens.text)
        // Slate-400 Subtle #94A3B8
        assertEquals(0xFF94A3B8.toInt(), tokens.subtle)
        // Border #20364F
        assertEquals(0xFF20364F.toInt(), tokens.border)
        // Accent contrast color is dark for contrast with teal button
        assertEquals(0xFF0B1420.toInt(), tokens.accentContrast)
        // Playing card background #163832
        assertEquals(0xFF163832.toInt(), tokens.cardPlaying)
    }

    @Test
    fun cockpitTwoPaneTokensMatchSpecification() {
        val tokens = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE)

        assertEquals(UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE, tokens.preset)
        // Teal #3DDC97
        assertEquals(0xFF3DDC97.toInt(), tokens.accent)
        // Dark navy #0B1420
        assertEquals(0xFF0B1420.toInt(), tokens.background)
        // Surface #111C28
        assertEquals(0xFF111C28.toInt(), tokens.surface)
        // Card #111924
        assertEquals(0xFF111924.toInt(), tokens.card)
        // Slate-50 Text #F1F5F9
        assertEquals(0xFFF1F5F9.toInt(), tokens.text)
        // Slate-400 Subtle #94A3B8
        assertEquals(0xFF94A3B8.toInt(), tokens.subtle)
        // Border #20364F
        assertEquals(0xFF20364F.toInt(), tokens.border)
        // Accent contrast #0B1420
        assertEquals(0xFF0B1420.toInt(), tokens.accentContrast)
        // Active playing card background #163832
        assertEquals(0xFF163832.toInt(), tokens.cardPlaying)
    }

    @Test
    fun classicTokensPreserveLegacyColors() {
        val tokens = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.CLASSIC)

        assertEquals(UiThemePolicy.ThemePreset.CLASSIC, tokens.preset)
        // Mint #43D4AE
        assertEquals(0xFF43D4AE.toInt(), tokens.accent)
        // Midnight #0C131F
        assertEquals(0xFF0C131F.toInt(), tokens.background)
        // Panel #192535
        assertEquals(0xFF192535.toInt(), tokens.surface)
        // Text #E9F1FA
        assertEquals(0xFFE9F1FA.toInt(), tokens.text)
        // Subtle #9EB0C3
        assertEquals(0xFF9EB0C3.toInt(), tokens.subtle)
    }

    @Test
    fun tokensAreDistinctBetweenPresets() {
        val pro = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.PORTRAIT_EDGE_PRO)
        val cockpit = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.COCKPIT_TWO_PANE)
        val classic = UiThemePolicy.tokensFor(UiThemePolicy.ThemePreset.CLASSIC)

        assertNotEquals(pro.surface, cockpit.surface)
        assertNotEquals(pro.card, cockpit.card)
        assertNotEquals(cockpit.background, classic.background)
        assertNotEquals(cockpit.accent, classic.accent)
    }
}
