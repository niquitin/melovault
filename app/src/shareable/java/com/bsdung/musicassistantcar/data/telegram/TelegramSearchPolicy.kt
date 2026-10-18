package com.bsdung.musicassistantcar.data.telegram

/**
 * Accent-insensitive song search: pure logic shared by SQLite, the in-memory DAO and tests.
 * The stored key lives in telegram_songs.search_key (schema v2).
 */
internal object TelegramSearchPolicy {
    const val MAX_TERMS = 8

    class Where(val sql: String, val args: List<String>)

    fun songSearchKey(title: String?, performer: String?, filename: String?): String =
        MeloComponentPolicy.searchKey(listOfNotNull(title, performer, filename).joinToString(" "))

    /** Normalized query words (AND semantics), at most [MAX_TERMS]. Empty list = no filter. */
    fun queryTerms(query: String?): List<String> {
        val key = MeloComponentPolicy.searchKey(query.orEmpty())
        if (key.isEmpty()) return emptyList()
        return key.split(' ').filter { it.isNotEmpty() }.take(MAX_TERMS)
    }

    /** LIKE pattern for [ESCAPE] '\' with %, _ and \ escaped. */
    fun likePattern(term: String): String {
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }

    /** SQL fragment (without leading AND) or null when the query has no terms. */
    fun whereClause(query: String?): Where? {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return null
        val key = TelegramLibrarySql.COL_SEARCH_KEY
        val title = TelegramLibrarySql.COL_TITLE
        val args = mutableListOf<String>()
        val sql = terms.joinToString(" AND ") { term ->
            val pattern = likePattern(term)
            args.add(pattern)
            args.add(pattern)
            "($key LIKE ? ESCAPE '\\' OR ($key = '' AND $title LIKE ? ESCAPE '\\'))"
        }
        return Where(sql, args)
    }

    fun matches(query: String?, song: IndexedTelegramSong): Boolean =
        MeloComponentPolicy.matchesSearch(query.orEmpty(), song.title, song.performer, song.filename)
}
