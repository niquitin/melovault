package com.bsdung.musicassistantcar.data.telegram

import java.text.Normalizer

/**
 * E96 P5B3-02: what a Telegram channel or song name looks like ON SCREEN. Display only: the original text is never changed,
 * stored or used as a key (songKey, search and the index keep the raw value).
 *
 * Why it exists. The P5B2 Sony screenshot (sony-01-app-launch-portrait.png) shows a source name ("kho nhạc miễn phí …") drawn in
 * a different, heavier typeface with tightly packed glyphs, while "Telegram · Chưa tải" in the SAME TextView is drawn in the
 * normal one. One TextView, one `Typeface.DEFAULT`, one size, one weighted slot with END ellipsis: the row layout cannot draw
 * one piece of text over another there. Android only switches typeface inside a run for characters the primary font lacks,
 * so the name must contain such characters. Telegram channel names are free text and are very often written with
 *  - Unicode "mathematical" or fullwidth lookalike letters (U+1D400..1D7FF, U+FF01..FF5E, enclosed letters), or
 *  - Vietnamese typed as base letter + COMBINING marks (NFD) instead of one precomposed letter,
 * and both are drawn by a fallback font whose marks and advances do not match the base font (doubled, narrow, overlapping
 * marks). Plain names ("SƯU TẦM NHẠC…") render normally, which is also what the screenshot shows.
 * This is a diagnosis from the screenshot and the code path; the exact code points of that channel name were not read (no
 * device or database access in this phase), so the first on-device check must compare that row before and after.
 *
 * The fix keeps every character of information: lookalike letters become the plain letters they stand for, combining marks are
 * composed (NFC), and invisible format/control characters (zero-width, bidi overrides, BOM) that also break shaping are
 * dropped. Nothing is truncated here (the TextView still ellipsizes to its width, as before).
 */
internal object DisplayTextPolicy {
    /** Characters that have no width of their own but disturb shaping and line breaking, or that reorder text. */
    private fun isInvisible(cp: Int): Boolean =
        cp in 0x200B..0x200F || cp in 0x202A..0x202E || cp in 0x2060..0x2064 || cp in 0x2066..0x206F || cp == 0xFEFF ||
            cp == 0x00AD || cp == 0x061C || cp in 0xFFF9..0xFFFB || Character.isISOControl(cp)

    /** Lookalike blocks whose compatibility decomposition is the plain letter or digit. */
    private fun isLookalike(cp: Int): Boolean =
        cp in 0x1D400..0x1D7FF || // mathematical alphanumerics (bold, italic, script, fraktur, double-struck, monospace)
            cp in 0xFF01..0xFF5E || // fullwidth ASCII
            cp in 0x2460..0x24FF || // enclosed alphanumerics
            cp in 0x1F130..0x1F189 || // squared / negative squared letters
            cp in 0x2070..0x209F // super/subscripts

    private fun isPlainAscii(text: String): Boolean = text.all { it.code in 0x20..0x7E }

    /** The text as it should be DRAWN. Blank in, blank out; a plain ASCII string comes back trimmed and otherwise unchanged. */
    fun clean(text: String): String {
        if (text.isEmpty()) return text
        if (isPlainAscii(text)) return text.trim()
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == 0x09 || cp == 0x0A || cp == 0x0D -> out.append(' ') // a line break inside a one-line name is a space
                isInvisible(cp) -> Unit
                isLookalike(cp) -> out.append(Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFKC))
                else -> out.appendCodePoint(cp)
            }
        }
        return Normalizer.normalize(out, Normalizer.Form.NFC).trim()
    }

    fun cleanOrNull(text: String?): String? = text?.let { clean(it) }

    /** True when [clean] would change what is drawn (a name that needs the fix). */
    fun needsClean(text: String): Boolean = clean(text) != text
}
