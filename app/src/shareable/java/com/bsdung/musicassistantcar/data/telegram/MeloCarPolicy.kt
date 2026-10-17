package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.CarReferencePx
import kotlin.math.ceil

/**
 * Rules for Car Mode on the FYT 7870 head unit, docs/ui-spec.md step 5.
 * Pure JVM except the SharedPreferences helpers.
 */
internal object MeloCarPolicy {

    const val KEY_CAR_MODE = "car_mode"
    const val KEY_CAR_ASKED = "car_mode_asked"

    /** Spec: 5 songs per page in the list pane; Telegram cards are 2 columns × 2 rows. */
    const val ROWS_PER_PAGE = CarReferencePx.ROWS_PER_PAGE
    const val SOURCES_PER_PAGE = 4
    const val SEEK_BACK_MS = -30_000L
    const val SEEK_FORWARD_MS = 60_000L

    /** Reference widths of the three columns; the spec table adds up to the full 1920px panel. */
    const val REFERENCE_WIDTH_PX = CarReferencePx.NAV_COLUMN + CarReferencePx.PLAYER_PANE + CarReferencePx.LIST_PANE
    const val REFERENCE_HEIGHT_PX = 1200

    enum class CarSetting(val key: String, val displayName: String) {
        OFF("off", "Tắt"),
        ON("on", "Bật"),
        EMULATE_FYT_160("emulate_fyt_160", "Thử trong khung FYT 7870 (1920 × 1200, mật độ 160)"),
        EMULATE_FYT_240("emulate_fyt_240", "Thử trong khung FYT 7870 (1920 × 1200, mật độ 240)");

        val emulation: MeloScreenPolicy.Emulation?
            get() = when (this) {
                EMULATE_FYT_160 -> MeloScreenPolicy.Emulation.FYT_7870_160
                EMULATE_FYT_240 -> MeloScreenPolicy.Emulation.FYT_7870_240
                else -> null
            }

        companion object {
            val DEFAULT = OFF
            fun fromKey(key: String?): CarSetting = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    enum class Nav(val icon: String, val label: String) {
        MUSIC("♫", "Nhạc"),
        TELEGRAM("☷", "Telegram"),
        PLAYLIST("☆", "Playlist"),
        DOWNLOADED("✓", "Đã tải"),
        SEARCH("⌕", "Tìm"),
        SETTINGS("⚙", "Cài đặt")
    }

    /** Car Mode is a switch of its own and overrides every layout rule, but never before login. */
    fun shouldUseCar(setting: CarSetting, authorized: Boolean): Boolean = authorized && setting != CarSetting.OFF

    /** First launch on a wide landscape display asks once instead of switching on its own. */
    fun shouldAskCarMode(setting: CarSetting, alreadyAsked: Boolean, displayWidthDp: Int, displayHeightDp: Int, authorized: Boolean): Boolean =
        authorized && setting == CarSetting.OFF &&
            MeloScreenPolicy.shouldOfferCarMode(displayWidthDp, displayHeightDp, carMode = false, alreadyAsked = alreadyAsked)

    /**
     * Pixel scale for the Car layout: the panel's physical density relative to the spec
     * (172.8 ppi), never larger than what fits the window. Firmware that reports a wrong
     * xdpi (for example 240) would otherwise push the 1920px of columns off the screen.
     */
    fun carScale(physicalPpi: Float, widthPx: Int, heightPx: Int): Float {
        if (physicalPpi <= 0f || widthPx <= 0 || heightPx <= 0) return 1f
        return minOf(
            physicalPpi / MeloDesignTokens.CAR_REFERENCE_PPI,
            widthPx.toFloat() / REFERENCE_WIDTH_PX,
            heightPx.toFloat() / REFERENCE_HEIGHT_PX
        )
    }

    /** Density to feed MeloDesignTokens.sizes(CAR) so every Car size is scaled by [carScale]. */
    fun effectivePpi(physicalPpi: Float, widthPx: Int, heightPx: Int): Float =
        MeloDesignTokens.CAR_REFERENCE_PPI * carScale(physicalPpi, widthPx, heightPx)

    fun pageCount(itemCount: Int, perPage: Int): Int =
        maxOf(1, ceil(itemCount.toDouble() / perPage).toInt())

    fun clampPage(page: Int, itemCount: Int, perPage: Int): Int = page.coerceIn(0, pageCount(itemCount, perPage) - 1)

    fun <T> pageItems(items: List<T>, page: Int, perPage: Int): List<T> {
        val p = clampPage(page, items.size, perPage)
        return items.drop(p * perPage).take(perPage)
    }

    /** Ask for more rows when the shown page is the last loaded one and the library has more. */
    fun shouldLoadMore(page: Int, perPage: Int, loaded: Int, total: Int?): Boolean {
        if (total != null && loaded >= total) return false
        return (page + 2) * perPage > loaded
    }

    /** −30s / +60s, clamped to the track when its length is known. */
    fun seekTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
        val target = positionMs + deltaMs
        return if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
    }

    /** Offline: songs still only on Telegram are dimmed; downloaded songs stay bright. */
    fun isOfflineDimmed(online: Boolean, downloaded: Boolean): Boolean = !online && !downloaded

    fun activeNav(mode: Mode, showingSources: Boolean, searchOpen: Boolean): Nav = when {
        searchOpen -> Nav.SEARCH
        showingSources -> Nav.TELEGRAM
        mode == Mode.PLAYLIST -> Nav.PLAYLIST
        mode == Mode.DOWNLOADED -> Nav.DOWNLOADED
        else -> Nav.MUSIC
    }

    fun loadSetting(prefs: SharedPreferences): CarSetting = CarSetting.fromKey(prefs.getString(KEY_CAR_MODE, null))

    fun saveSetting(prefs: SharedPreferences, setting: CarSetting) {
        prefs.edit().putString(KEY_CAR_MODE, setting.key).apply()
    }

    fun asked(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_CAR_ASKED, false)

    fun markAsked(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_CAR_ASKED, true).apply()
    }
}
