package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status

/**
 * Rules for the Gọn (Shanling M3 Ultra) layout, docs/ui-spec.md step 4.
 * Pure JVM except the SharedPreferences helpers.
 */
internal object MeloCompactPolicy {

    const val KEY_COMPACT_MODE = "compact_mode"
    const val KEY_DEFAULT_APPLIED = "compact_default_applied"

    enum class CompactSetting(val key: String, val displayName: String) {
        AUTO("auto", "Tự động (màn hình nhỏ như Shanling)"),
        ON("on", "Luôn bật"),
        OFF("off", "Tắt (giao diện cũ)"),
        EMULATE_SHANLING("emulate_shanling", "Thử trong khung Shanling (768 × 1280)");

        companion object {
            val DEFAULT = AUTO
            fun fromKey(key: String?): CompactSetting = entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    enum class Mode { ALL, DOWNLOADED, PLAYLIST }

    /**
     * AUTO looks at the physical PANEL ([panelCompact], see [MeloScreenPolicy.panelCompact]), not
     * the window, so a big head unit or tablet in split screen never turns into Gọn and rotating
     * a small player does not flip layouts. Never before login: the login screen stays the old one.
     */
    fun shouldUseCompact(setting: CompactSetting, panelCompact: Boolean, authorized: Boolean): Boolean {
        if (!authorized) return false
        return when (setting) {
            CompactSetting.OFF -> false
            CompactSetting.ON, CompactSetting.EMULATE_SHANLING -> true
            CompactSetting.AUTO -> panelCompact
        }
    }

    /** Key of a built Gọn layer; a change rebuilds it. The row-cover flag only adds ":art", so without covers it is the old key. */
    fun layerKey(settingKey: String, widthPx: Int, heightPx: Int, densityDpi: Int, fontScale: Float, rowArt: Boolean): String =
        "compact:$settingKey:${widthPx}x$heightPx:$densityDpi:$fontScale" + if (rowArt) ":art" else ""

    /** Spec: the Gọn player opens on "Đã tải" — applied once, then the owner's choice is kept. */
    fun needsDefaultDownloadedFilter(alreadyApplied: Boolean): Boolean = !alreadyApplied

    fun headerTitle(mode: Mode, sourceTitle: String?, count: Int?): String {
        val name = when {
            mode == Mode.PLAYLIST -> "Playlist"
            !sourceTitle.isNullOrBlank() -> sourceTitle.trim()
            mode == Mode.DOWNLOADED -> "Đã tải"
            else -> "Tất cả"
        }
        val prefix = if (mode == Mode.DOWNLOADED && !sourceTitle.isNullOrBlank()) "Đã tải · " else ""
        return if (count != null && count >= 0) "$prefix$name · ${MeloComponentPolicy.formatCount(count)} bài" else "$prefix$name"
    }

    /** Teal of the one-line "Đã tải" hint under the Gọn title (docs/plans/da-tai-khi-mo-app.md rework). */
    const val DOWNLOADED_LINE_COLOR: Int = 0xFF3DDC97.toInt()

    /**
     * One hint line under the Gọn title with the offline group count kept in the activity
     * (`downloadedLibraryTotal`). It is the whole-library figure, so it shows only on the
     * all-sources library and source list. Null while the count is unknown: no made-up number; 0 is valid.
     * The wording stays true for a stored figure and for a fresh scan alike ("lần kiểm trước" = the last check).
     */
    fun downloadedLine(mode: Mode, sourceTitle: String?, showingSources: Boolean, groups: Int?): String? {
        if (groups == null || groups < 0) return null
        val wholeLibrary = showingSources || (mode == Mode.ALL && sourceTitle.isNullOrBlank())
        if (!wholeLibrary) return null
        return "Đã tải: ${MeloComponentPolicy.formatCount(groups)} nhóm · lần kiểm trước"
    }

    /** [playing] = this row is the current song; [paused] = the player is paused (not just buffering). */
    fun rowStatus(downloaded: Boolean, downloadingPercent: Int?, playing: Boolean, paused: Boolean = false): Status = when {
        playing && paused -> Status.PAUSED
        playing -> Status.PLAYING
        downloadingPercent != null -> Status.DOWNLOADING
        downloaded -> Status.DOWNLOADED
        else -> Status.TELEGRAM
    }

    /** The row list must be rebuilt when the current song or its play/pause state changed. */
    fun rowsStale(shownKey: Long?, shownPaused: Boolean, nowKey: Long?, nowPaused: Boolean): Boolean =
        shownKey != nowKey || (nowKey != null && shownPaused != nowPaused)

    /** Null when the total size is unknown (never a made-up percentage). */
    fun progressPercent(readyBytes: Long, totalBytes: Long): Int? {
        if (totalBytes <= 0L || readyBytes < 0L) return null
        return ((readyBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    /** Ask for the next page a little before the end so long lists keep scrolling. */
    fun shouldLoadMore(firstVisible: Int, visibleCount: Int, totalRows: Int, threshold: Int = 20): Boolean =
        totalRows > 0 && firstVisible + visibleCount >= totalRows - threshold

    fun loadSetting(prefs: SharedPreferences): CompactSetting = CompactSetting.fromKey(prefs.getString(KEY_COMPACT_MODE, null))

    fun saveSetting(prefs: SharedPreferences, setting: CompactSetting) {
        prefs.edit().putString(KEY_COMPACT_MODE, setting.key).apply()
    }

    fun defaultApplied(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_DEFAULT_APPLIED, false)

    fun markDefaultApplied(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_DEFAULT_APPLIED, true).apply()
    }
}
