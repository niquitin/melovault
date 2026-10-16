package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences

/**
 * Theme policy and tokens for MeloVault UI.
 *
 * Gate 1 implements Theme 01: Portrait Edge-to-Edge Pro (Teal #3DDC97) as the
 * modern default, while preserving Legacy Classic as a fallback.
 * Uses ARGB integer literals so colors are deterministic in both Android runtime
 * and JVM unit test environments without android.graphics.Color stubbing.
 */
object UiThemePolicy {
    const val PREFS_NAME = "melovault_ui_preferences"
    const val KEY_THEME_PRESET = "theme_preset"

    enum class ThemePreset(val key: String, val displayName: String) {
        PORTRAIT_EDGE_PRO("portrait_edge_pro", "Portrait Edge-to-Edge Pro"),
        CLASSIC("classic", "Cổ điển (Classic)");

        companion object {
            val DEFAULT = PORTRAIT_EDGE_PRO

            fun fromKey(key: String?): ThemePreset =
                entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    data class ThemeTokens(
        val preset: ThemePreset,
        val accent: Int,
        val background: Int,
        val surface: Int,
        val card: Int,
        val text: Int,
        val subtle: Int,
        val border: Int,
        val accentContrast: Int
    )

    val TOKENS_PORTRAIT_EDGE_PRO = ThemeTokens(
        preset = ThemePreset.PORTRAIT_EDGE_PRO,
        accent = 0xFF3DDC97.toInt(),        // #3DDC97 Teal (61, 220, 151)
        background = 0xFF0B1420.toInt(),    // #0B1420 Near-black dark navy (11, 20, 32)
        surface = 0xFF152536.toInt(),       // #152536 Dark slate surface (21, 37, 54)
        card = 0xFF1B2E42.toInt(),          // #1B2E42 Elevated card (27, 46, 66)
        text = 0xFFF1F5F9.toInt(),          // #F1F5F9 Slate-50 primary (241, 245, 249)
        subtle = 0xFF94A3B8.toInt(),        // #94A3B8 Slate-400 secondary (148, 163, 184)
        border = 0xFF20364F.toInt(),        // #20364F Border stroke (32, 54, 79)
        accentContrast = 0xFF0B1420.toInt() // #0B1420 Dark text on teal button
    )

    val TOKENS_CLASSIC = ThemeTokens(
        preset = ThemePreset.CLASSIC,
        accent = 0xFF43D4AE.toInt(),        // #43D4AE Mint (67, 212, 174)
        background = 0xFF0C131F.toInt(),    // #0C131F Midnight (12, 19, 31)
        surface = 0xFF192535.toInt(),       // #192535 Panel (25, 37, 53)
        card = 0xFF192535.toInt(),
        text = 0xFFE9F1FA.toInt(),          // #E9F1FA Ink (233, 241, 250)
        subtle = 0xFF9EB0C3.toInt(),        // #9EB0C3 Subtle (158, 176, 195)
        border = 0xFF23344B.toInt(),
        accentContrast = 0xFF0C131F.toInt()
    )

    fun tokensFor(preset: ThemePreset): ThemeTokens = when (preset) {
        ThemePreset.PORTRAIT_EDGE_PRO -> TOKENS_PORTRAIT_EDGE_PRO
        ThemePreset.CLASSIC -> TOKENS_CLASSIC
    }

    fun getSavedPreset(prefs: SharedPreferences): ThemePreset {
        val key = prefs.getString(KEY_THEME_PRESET, null)
        return ThemePreset.fromKey(key)
    }

    fun savePreset(prefs: SharedPreferences, preset: ThemePreset) {
        prefs.edit().putString(KEY_THEME_PRESET, preset.key).apply()
    }
}
