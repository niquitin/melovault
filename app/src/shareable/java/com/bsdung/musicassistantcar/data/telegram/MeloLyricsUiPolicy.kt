package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta

/**
 * What the lyrics pane says and which buttons it offers for each [LyricsState] (docs/plans/2026-10-31-loi-nhac-p2b.md,
 * LD2/LD4/LD10), plus the stored choices (consent, tab). Pure functions: the pane only draws these answers.
 */
internal object MeloLyricsUiPolicy {

    /** Same preferences file as the rest of the Melo settings. */
    const val KEY_CONSENT = "lyrics_consent"
    const val KEY_TAB = "lyrics_tab"

    const val CONSENT_TEXT =
        "Lời nhạc lấy từ LRCLIB (dịch vụ công cộng, không thuộc MeloVault). " +
            "Ứng dụng sẽ gửi tên bài, nghệ sĩ, album và thời lượng. Bật?"

    /** Shown under the Cài đặt switch, where the owner decides without the box. */
    const val SETTINGS_SUBTITLE =
        "Gửi tên bài, nghệ sĩ, album và thời lượng tới LRCLIB (dịch vụ công cộng) khi mở tab Lời nhạc. Mặc định tắt."

    const val SOURCE_LINE = "Lời: LRCLIB"

    enum class Tab(val label: String) { QUEUE("Tiếp theo"), LYRICS("♪ Lời nhạc") }

    enum class Action(val label: String) {
        ENABLE("Bật"),
        DECLINE("Không"),
        ENABLE_AGAIN("Bật lời nhạc"),
        SEARCH("Tìm lời"),
        RETRY("Thử lại"),
        CLEAR_CHOICE("Bỏ lời đã chọn")
    }

    /** [text] null = the pane shows the lyrics themselves. */
    data class Presentation(val text: String?, val primary: Action?, val secondary: Action? = null)

    fun presentation(state: LyricsState?): Presentation = when (state) {
        null -> Presentation("Chưa có bài đang phát.", null)
        LyricsState.NeedConsent -> Presentation(CONSENT_TEXT, Action.ENABLE, Action.DECLINE)
        LyricsState.Off -> Presentation("Lời nhạc đang tắt.", Action.ENABLE_AGAIN)
        LyricsState.Loading -> Presentation("Đang tìm lời…", null)
        LyricsState.MissingMetadata -> Presentation("Thiếu tên bài hoặc nghệ sĩ để tìm lời.", Action.SEARCH)
        LyricsState.Instrumental -> Presentation("Bài không lời (nhạc cụ).", null)
        is LyricsState.NoMatch -> Presentation("Không tìm thấy lời cho bài này.", if (state.canSearch) Action.SEARCH else null)
        is LyricsState.Ambiguous -> Presentation("Chưa chắc bài nào. Chọn đúng bài:", Action.SEARCH)
        is LyricsState.TemporaryError -> Presentation(errorText(state.retryAfterMs), Action.RETRY)
        is LyricsState.Synced -> Presentation(null, if (state.manual) Action.CLEAR_CHOICE else null)
        is LyricsState.Plain -> Presentation(null, if (state.manual) Action.CLEAR_CHOICE else null)
    }

    private fun errorText(retryAfterMs: Long?): String {
        if (retryAfterMs == null || retryAfterMs <= 0) return "Chưa tải được lời. Thử lại sau."
        val seconds = (retryAfterMs + 999) / 1000
        return "Chưa tải được lời. Thử lại sau khoảng $seconds giây."
    }

    /** "Tên bài — Nghệ sĩ · 3:53 · đồng bộ" for the manual picker. */
    fun candidateLabel(candidate: Candidate): String {
        val name = listOf(candidate.trackName, candidate.artistName).filter { it.isNotBlank() }.joinToString(" — ").ifEmpty { "(không tên)" }
        val seconds = Math.round(candidate.durationSec).toInt()
        val kind = when {
            candidate.instrumental -> "nhạc cụ"
            candidate.hasSynced -> "đồng bộ"
            candidate.hasPlain -> "thường"
            else -> "không có lời"
        }
        val time = if (seconds > 0) " · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else ""
        return "$name$time · $kind"
    }

    /** What the search box starts with: title + artist, else the file name without extension. */
    fun searchPrefill(meta: Meta?, filename: String?): String {
        val title = meta?.title?.trim().orEmpty()
        val artist = meta?.artist?.trim().orEmpty()
        if (title.isNotEmpty() && artist.isNotEmpty()) return "$title $artist"
        val raw = filename?.trim().orEmpty()
        val file = (if (raw.lastIndexOf('.') > 0) raw.substringBeforeLast('.') else raw)
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (file.isNotEmpty()) return file
        return title.ifEmpty { artist }
    }

    /** The "Về dòng đang hát" button: only when the owner scrolled away and a line is being sung. */
    fun showJumpButton(autoFollow: Boolean, activeIndex: Int): Boolean = !autoFollow && activeIndex >= 0

    /** Scroll offset (px) that puts the active line in the middle of the pane, never above the top. */
    fun scrollTarget(lineTop: Int, lineHeight: Int, paneHeight: Int): Int =
        (lineTop + lineHeight / 2 - paneHeight / 2).coerceAtLeast(0)

    /** The pane's own height when it sits inside the Deck's scrolling column: room for several lines, not the whole window. */
    fun portraitPaneHeight(windowHeightPx: Int, minTouchPx: Int): Int =
        maxOf(minTouchPx * 6, windowHeightPx / 2)

    /** A button in the pane keeps the touch minimum and grows with big text (font scale 1.3 and up). */
    fun controlHeightPx(minTouchPx: Int, textPx: Float, fontScale: Float): Int =
        maxOf(minTouchPx, Math.ceil((textPx * maxOf(1f, fontScale) * 1.4f).toDouble()).toInt())

    // ---- stored choices ----

    fun consentOf(raw: String?): LyricsConsent = when (raw) {
        "granted" -> LyricsConsent.GRANTED
        "denied" -> LyricsConsent.DENIED
        else -> LyricsConsent.UNDECIDED
    }

    fun tabOf(raw: String?): Tab = Tab.entries.firstOrNull { it.name == raw } ?: Tab.QUEUE

    fun loadConsent(prefs: SharedPreferences): LyricsConsent = consentOf(prefs.getString(KEY_CONSENT, null))

    fun saveConsent(prefs: SharedPreferences, granted: Boolean) {
        prefs.edit().putString(KEY_CONSENT, if (granted) "granted" else "denied").apply()
    }

    fun loadTab(prefs: SharedPreferences): Tab = tabOf(prefs.getString(KEY_TAB, null))

    fun saveTab(prefs: SharedPreferences, tab: Tab) {
        prefs.edit().putString(KEY_TAB, tab.name).apply()
    }
}
