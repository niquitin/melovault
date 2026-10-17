package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import java.util.Locale

/**
 * Policy and layout rules for Theme 03: Telegram Vault Bento Grid.
 *
 * Provides responsive column calculation, source filtering, hero card logic,
 * and user display mode persistence (List vs. Bento Grid).
 * Designed for deterministic execution in both Android runtime and JVM unit tests.
 */
object TelegramVaultBentoPolicy {

    const val KEY_SOURCE_DISPLAY_MODE = "source_display_mode"
    const val MIN_CARD_WIDTH_DP = 240

    enum class SourceDisplayMode(val key: String, val displayName: String) {
        LIST("list", "Danh sách"),
        BENTO("bento", "Bento Grid");

        companion object {
            val DEFAULT = LIST

            fun fromKey(key: String?): SourceDisplayMode =
                entries.firstOrNull { it.key == key } ?: DEFAULT
        }
    }

    /**
     * Determines the number of grid columns based on available content width in dp.
     * In Theme 02 Cockpit right pane, columns are constrained to max 2 to prevent squeezing.
     */
    fun calculateGridColumns(usableWidthDp: Int, isCockpitRightPane: Boolean = false): Int {
        if (usableWidthDp <= 0) return 1
        val maxColumns = if (isCockpitRightPane) 2 else 4
        val cols = (usableWidthDp / MIN_CARD_WIDTH_DP).coerceIn(1, maxColumns)
        return cols
    }

    /**
     * Filters Telegram sources by title, case-insensitive.
     * When query is blank, all sources are returned.
     */
    fun filterSources(
        sources: List<IndexedTelegramSource>,
        query: String
    ): List<IndexedTelegramSource> {
        if (query.isBlank()) return sources
        val clean = query.trim()
        return sources.filter { it.title.contains(clean, ignoreCase = true) }
    }

    /**
     * Partitions sources into those with indexed tracks (> 0) and those with zero tracks (<= 0).
     * Returns Pair(activeSources, emptySources).
     */
    fun partitionSources(
        sources: List<IndexedTelegramSource>
    ): Pair<List<IndexedTelegramSource>, List<IndexedTelegramSource>> {
        val active = sources.filter { it.trackCount > 0 }
        val empty = sources.filter { it.trackCount <= 0 }
        return Pair(active, empty)
    }

    /**
     * Deduplicates sources by stable chatId, keeping the entry with the highest track count.
     */
    fun distinctSourcesByChatId(sources: List<IndexedTelegramSource>): List<IndexedTelegramSource> {
        return sources.groupBy { it.chatId }
            .map { (_, group) -> group.maxByOrNull { it.trackCount } ?: group.first() }
            .sortedWith(compareByDescending<IndexedTelegramSource> { it.trackCount }.thenBy { it.chatId })
    }

    /**
     * Formats track count label truthfully.
     */
    fun formatTrackCount(count: Int): String {
        return if (count <= 0) "0 bài" else String.format(Locale.US, "%,d bài", count)
    }

    /**
     * Formats header title with true channel count.
     */
    fun formatHeaderTitle(sourceCount: Int): String {
        return "NGUỒN NHẠC TELEGRAM  ·  $sourceCount nhóm/kênh/chat"
    }

    /**
     * Formats header subtitle with source count and total indexed track count.
     */
    fun formatHeaderSubtitle(sourceCount: Int, totalIndexedTracks: Int): String {
        val formattedTracks = String.format(Locale.US, "%,d", totalIndexedTracks)
        return "$sourceCount nguồn  ·  $formattedTracks bài  ·  Đã đồng bộ"
    }

    /**
     * Hero card is displayed when not filtering by search query.
     */
    fun shouldShowHeroCard(query: String): Boolean {
        return query.isBlank()
    }

    /**
     * Sorts positive-count source cards by trackCount DESC, with stable tie-break
     * by normalized title then chatId.
     */
    fun sortSources(sources: List<IndexedTelegramSource>): List<IndexedTelegramSource> {
        return sources.sortedWith(
            compareByDescending<IndexedTelegramSource> { it.trackCount }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.chatId }
        )
    }

    /**
     * Extracts the first displayable initial grapheme of a Telegram channel/group title
     * for use in placeholder avatars. Handles Unicode, emoji, and blank titles safely.
     */
    fun extractInitialGrapheme(title: String): String {
        val clean = title.trim()
        if (clean.isEmpty()) return "♫"
        val firstCodePoint = clean.codePointAt(0)
        val chars = Character.toChars(firstCodePoint)
        val str = String(chars)
        return if (str.isNotBlank()) str.uppercase(Locale.getDefault()) else "♫"
    }

    /**
     * Reads saved source display mode from preferences.
     */
    fun getSavedSourceDisplayMode(prefs: SharedPreferences): SourceDisplayMode {
        val key = prefs.getString(KEY_SOURCE_DISPLAY_MODE, null)
        return SourceDisplayMode.fromKey(key)
    }

    /**
     * Persists source display mode preference.
     */
    fun saveSourceDisplayMode(prefs: SharedPreferences, mode: SourceDisplayMode) {
        prefs.edit().putString(KEY_SOURCE_DISPLAY_MODE, mode.key).apply()
    }
}

