package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.lyrics.LrcParser
import com.bsdung.musicassistantcar.lyrics.LyricsLine
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Lyrics rules for Shareable (docs/plans/2026-10-31-loi-nhac-p2b.md, LD3-LD5): what is sent to the
 * lookup service, when a lookup is allowed, and which search result may be taken without asking.
 * Pure JVM, no Android. Reuses only the pure LRC parser/line type from `main`.
 */
internal object MeloLyricsPolicy {

    /** A song longer than this (a mixset) is never looked up automatically (LD5.3). */
    const val AUTO_FETCH_MAX_SEC = 900

    /** LRCLIB `/api/get` only answers within this many seconds of the real length (LD5). */
    const val DURATION_TOLERANCE_SEC = 2.0

    /** Manual search shows at most this many results (LRCLIB returns 20 and has no paging). */
    const val MAX_SEARCH_RESULTS = 20

    /** Facts about the song that is playing. Only these four fields may ever leave the device (LD3). */
    data class Meta(
        val title: String?,
        val artist: String?,
        val album: String? = null,
        val durationSec: Int? = null
    )

    enum class FetchDecision { FETCH, MISSING_METADATA, TOO_LONG }

    /** One LRCLIB record as the provider parsed it. */
    data class Candidate(
        val id: Long,
        val trackName: String,
        val artistName: String,
        val albumName: String?,
        val durationSec: Double,
        val instrumental: Boolean,
        val plainLyrics: String?,
        val syncedLyrics: String?
    ) {
        val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
        val hasPlain: Boolean get() = !plainLyrics.isNullOrBlank()
    }

    sealed class Pick {
        data class Chosen(val candidate: Candidate) : Pick()
        object None : Pick()
        data class Ambiguous(val candidates: List<Candidate>) : Pick()
    }

    private val PLACEHOLDERS = setOf(
        "", "unknown", "<unknown>", "unknown title", "unknown artist", "chưa xác định"
    )
    private val FEAT = Regex("""[\(\[]?\s*\b(?:feat|ft|featuring)\b\.?.*$""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /** NFC, lower case, single spaces: "  Sơn   Tùng " and "sơn tùng" compare equal. */
    fun normalize(value: String?): String {
        if (value == null) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(SPACES, " ")
            .trim()
    }

    /** Like [normalize] but drops a trailing "feat. X" / "(ft. X)" so a featured guest does not change the key. */
    fun normalizeArtist(value: String?): String {
        val full = normalize(value)
        val cut = full.replace(FEAT, "").trim().trimEnd('(', '[', ',', '&', '-').trim()
        return cut.ifEmpty { full }
    }

    private fun isMissing(value: String?): Boolean = normalize(value) in PLACEHOLDERS

    /** The text hashed into the cache key. Same song -> same text; other length -> other text. */
    fun signatureText(meta: Meta): String = listOf(
        normalize(meta.title),
        normalizeArtist(meta.artist),
        normalize(meta.album),
        (meta.durationSec ?: 0).coerceAtLeast(0).toString()
    ).joinToString("\u0001")

    fun signature(meta: Meta): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(signatureText(meta).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** LD4/LD5: no title or no artist -> do not guess from the file name; a mixset -> only on request. */
    fun shouldAutoFetch(meta: Meta): FetchDecision = when {
        isMissing(meta.title) || isMissing(meta.artist) -> FetchDecision.MISSING_METADATA
        (meta.durationSec ?: 0) > AUTO_FETCH_MAX_SEC -> FetchDecision.TOO_LONG
        else -> FetchDecision.FETCH
    }

    /**
     * LD5.2: take a search result without asking only when exactly one result is within
     * [DURATION_TOLERANCE_SEC] of the real length. Anything else is shown to the user, nearest first.
     * Without a known length nothing can be judged, so the user always chooses.
     */
    fun pickCandidate(results: List<Candidate>, durationSec: Int?): Pick {
        if (results.isEmpty()) return Pick.None
        val limited = results.take(MAX_SEARCH_RESULTS)
        if (durationSec == null || durationSec <= 0) return Pick.Ambiguous(limited)
        val (close, far) = limited.partition { Math.abs(it.durationSec - durationSec) <= DURATION_TOLERANCE_SEC + 1e-9 }
        return if (close.size == 1) Pick.Chosen(close[0]) else Pick.Ambiguous(close + far)
    }

    /** Index of the line being sung at [positionMs]; -1 before the first line. */
    fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int =
        LrcParser.findActiveLineIndex(lines, positionMs)

    /** `MeloVault/<version>` plus an optional contact; control characters are dropped so a header cannot be forged. */
    fun userAgent(versionName: String?, contact: String? = null): String {
        val version = clean(versionName).ifEmpty { "dev" }
        val who = clean(contact)
        return if (who.isEmpty()) "MeloVault/$version" else "MeloVault/$version ($who)"
    }

    private fun clean(value: String?): String =
        (value ?: "").filter { it.code in 0x20..0x7e }.trim()
}
