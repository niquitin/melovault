package com.bsdung.musicassistantcar.data.telegram

/**
 * Nhiều Playlist có tên, S1: order keys for songs inside one named playlist (no Android, no clock, no I/O).
 *
 * A key is a non-empty base-62 string ("0-9A-Za-z", which is also their ASCII order) read as a fraction
 * 0.d1d2d3... A key never ends in '0', so there is always room between two different keys. Keys are
 * compared as plain strings. [between] makes a key strictly between two others, so one song can be moved
 * by writing ONE new key, without renumbering the rest.
 */
internal object FractionalIndex {
    /** Longest key an operation may carry. Longer results make the caller [evenly] respace the playlist. */
    const val MAX_LEN = 48

    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val BASE = 62

    fun isValid(key: String): Boolean =
        key.isNotEmpty() && key.length <= MAX_LEN && key.all { digit(it) >= 0 } && key.last() != DIGITS[0]

    /** True for a key of any length that is well formed otherwise; used to check a result before its length is judged. */
    private fun isWellFormed(key: String): Boolean = key.isNotEmpty() && key.all { digit(it) >= 0 } && key.last() != DIGITS[0]

    /**
     * A key with `a < result < b`; [a] null means "before everything", [b] null "after everything".
     * Both null gives the middle key. The result can be longer than [MAX_LEN] after very many inserts in
     * one spot: the caller checks [isValid] and respaces with [evenly] then.
     */
    fun between(a: String?, b: String?): String {
        require(a == null || isWellFormed(a)) { "Bad lower key" }
        require(b == null || isWellFormed(b)) { "Bad upper key" }
        require(a == null || b == null || a < b) { "Lower key must be smaller than upper key" }
        return midpoint(a.orEmpty(), b)
    }

    /** [count] keys in increasing order, spread so that their length grows only with log(count). */
    fun evenly(count: Int): List<String> {
        require(count >= 0) { "count must not be negative" }
        val out = arrayOfNulls<String>(count)
        fill(out, 0, count, null, null)
        return out.map { checkNotNull(it) }
    }

    private fun fill(out: Array<String?>, from: Int, to: Int, a: String?, b: String?) {
        if (from >= to) return
        val mid = (from + to) ushr 1
        val key = between(a, b)
        out[mid] = key
        fill(out, from, mid, a, key)
        fill(out, mid + 1, to, key, b)
    }

    private fun digit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'A'..'Z' -> c - 'A' + 10
        in 'a'..'z' -> c - 'a' + 36
        else -> -1
    }

    /** [a] is "" for zero. Both are well formed and a < b. */
    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            var n = 0
            while (n < b.length && (if (n < a.length) a[n] else DIGITS[0]) == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.substring(minOf(n, a.length)), b.substring(n))
        }
        val da = if (a.isEmpty()) 0 else digit(a[0])
        val db = if (b != null) digit(b[0]) else BASE
        if (db - da > 1) return DIGITS[(da + db + 1) / 2].toString()
        if (b != null && b.length > 1) return b.substring(0, 1)
        return DIGITS[da].toString() + midpoint(if (a.isEmpty()) "" else a.substring(1), null)
    }
}
