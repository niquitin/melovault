package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer

/**
 * E98: the optional "Kiểu màu" of the library screens. [ColorTheme.CURRENT] is the look MeloVault
 * always had and stays the default; [ColorTheme.TEAL_BRIGHT] is the brighter teal look with a cover
 * on every row. The theme only reaches Điện thoại dọc, Tablet dọc and Hai khung; Gọn keeps its colours and (E99) always has the
 * row covers, with every colour theme, and Car Mode never changes. Pure JVM except the preference helpers.
 */
internal object MeloThemePolicy {

    const val KEY = "color_theme"
    const val KEY_ROW_ART = "row_art_telegram"

    enum class ColorTheme(val key: String, val displayName: String) {
        CURRENT("current", "Hiện tại"),
        TEAL_BRIGHT("teal_bright", "Teal sáng · ảnh bìa");

        companion object {
            val DEFAULT = CURRENT
            /** An unknown or missing key keeps the look the owner already has. */
            fun fromKey(key: String?): ColorTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    /** Only the three new library layers with a Phone or Tablet profile can wear the teal theme. */
    fun appliesTo(theme: ColorTheme, layer: NewLayer?, profile: Profile): Boolean =
        theme == ColorTheme.TEAL_BRIGHT && layer != null && (profile == Profile.PHONE || profile == Profile.TABLET)

    /** The theme a screen really draws with: the chosen one where it applies, otherwise [ColorTheme.CURRENT]. */
    fun effective(theme: ColorTheme, layer: NewLayer?, profile: Profile): ColorTheme =
        if (appliesTo(theme, layer, profile)) ColorTheme.TEAL_BRIGHT else ColorTheme.CURRENT

    /**
     * E99/P2-A: whether a screen shows row covers, for either colour theme. The Phone/Tablet new
     * library layers and Gọn always do (any theme, independent of [theme]); Car Mode never.
     */
    fun rowArtLayer(theme: ColorTheme, layer: NewLayer?, profile: Profile): Boolean =
        (layer != null && (profile == Profile.PHONE || profile == Profile.TABLET)) || profile == Profile.COMPACT

    /** P2-A: row covers are fetched only where [rowArtLayer] shows them and the switch (default on) is not turned off. */
    fun rowArtActive(layerShown: Boolean, switchOn: Boolean): Boolean = layerShown && switchOn

    /** Colour of the dot after "Đồng bộ: N bài". */
    enum class SyncDot { OK, SYNCING, OFFLINE }

    /**
     * Dot from data the screen already has: not signed in = grey; the sync line says it is
     * updating or paused = amber; otherwise green. Never guesses from a missing line alone.
     */
    fun syncDot(authorized: Boolean, syncText: String?): SyncDot {
        if (!authorized) return SyncDot.OFFLINE
        val text = syncText.orEmpty()
        val updating = TelegramLibrarySyncDisplayPolicy.LIBRARY_UPDATING.trimEnd('…', '.', ' ')
        val paused = TelegramLibrarySyncDisplayPolicy.LIBRARY_PAUSED
        return if (text.contains(updating) || text.contains(paused)) SyncDot.SYNCING else SyncDot.OK
    }

    /** "Đồng bộ: 27.971 bài", or "Đồng bộ: chưa có bài" while the count is unknown or zero. */
    fun syncHeadline(total: Int?): String =
        if (total != null && total > 0) "Đồng bộ: ${MeloComponentPolicy.formatCount(total)} bài" else "Đồng bộ: chưa có bài"

    fun load(prefs: SharedPreferences): ColorTheme = ColorTheme.fromKey(prefs.getString(KEY, null))

    fun save(prefs: SharedPreferences, theme: ColorTheme) {
        prefs.edit().putString(KEY, theme.key).apply()
    }

    fun rowArtSwitch(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_ROW_ART, true)

    fun setRowArtSwitch(prefs: SharedPreferences, on: Boolean) {
        prefs.edit().putBoolean(KEY_ROW_ART, on).apply()
    }
}
