package com.bsdung.musicassistantcar.data.telegram

/** Song ordering of the library list. NEWEST is the historical default. */
enum class SongSort { NEWEST, NAME }

/**
 * Pure logic of the A–Z jump bar. Letters come from the accent-insensitive search key
 * (telegram_songs.search_key), so "Đường" is under D and "Ánh" under A.
 */
internal object MeloAlphabetPolicy {
    const val HASH = '#'
    const val MIN_ROWS_FOR_BAR = 50
    const val PREF_SORT = "library_sort"

    /** '#', then 'A'..'Z'. */
    val ALL_LETTERS: List<Char> = listOf(HASH) + ('A'..'Z').toList()

    fun parseSort(raw: String?): SongSort = SongSort.entries.firstOrNull { it.name == raw } ?: SongSort.NEWEST

    fun letterOf(searchKey: String): Char {
        val first = searchKey.firstOrNull() ?: return HASH
        return if (first in 'a'..'z') first.uppercaseChar() else HASH
    }

    /** Inclusive lower bound of the search key for [letter]; null for '#' (from the very start). */
    fun lowerBound(letter: Char): String? =
        if (letter in 'A'..'Z') letter.lowercaseChar().toString() else null

    /** Same ordering as the SQL `ORDER BY search_key, chat_id, message_id`. */
    fun nameComparator(): Comparator<IndexedTelegramSong> =
        compareBy<IndexedTelegramSong> { keyOf(it) }.thenBy { it.chatId }.thenBy { it.messageId }

    fun keyOf(song: IndexedTelegramSong): String =
        TelegramSearchPolicy.songSearchKey(song.title, song.performer, song.filename)

    fun keyOf(title: String?, performer: String?, filename: String?): String =
        TelegramSearchPolicy.songSearchKey(title, performer, filename)

    /** First index whose key is >= [bound] in a sorted key list; keys.size when none, 0 for null. */
    fun firstIndexAtOrAfter(keys: List<String>, bound: String?): Int {
        if (bound == null) return 0
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < bound) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Letters drawn when [heightPx] is short: every other letter (always keeping '#' first and
     * 'Z' last) until each cell is at least [minCellPx] high. Dragging still reaches every letter
     * through [letterAt] over [ALL_LETTERS].
     */
    fun visibleLetters(heightPx: Int, minCellPx: Int): List<Char> {
        if (minCellPx <= 0 || heightPx / ALL_LETTERS.size >= minCellPx) return ALL_LETTERS
        var step = 2
        while (step < ALL_LETTERS.size) {
            val picked = pick(step)
            if (heightPx / picked.size >= minCellPx) return picked
            step++
        }
        return listOf(HASH, 'Z')
    }

    private fun pick(step: Int): List<Char> {
        val out = ALL_LETTERS.filterIndexed { i, _ -> i % step == 0 }.toMutableList()
        if (out.last() != 'Z') out.add('Z')
        return out
    }

    /** Letter under finger position [y] in a bar [height] px high; clamps outside the bar. */
    fun letterAt(y: Float, height: Int, letters: List<Char> = ALL_LETTERS): Char {
        if (letters.isEmpty() || height <= 0) return HASH
        val index = (y / height * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    fun barVisible(sort: SongSort, showingSources: Boolean, rowCount: Int, hasLowerBound: Boolean = false): Boolean =
        sort == SongSort.NAME && !showingSources && (rowCount >= MIN_ROWS_FOR_BAR || hasLowerBound)
}
