package com.bsdung.musicassistantcar.data.telegram

/** Group Telegram music by the real chat that contains each audio attachment. */
internal object TelegramSourcePolicy {
    fun sortedCounts(ids: List<Long>): List<Pair<Long, Int>> =
        ids.filter { it != 0L }.groupingBy { it }.eachCount().toList()
            .sortedWith(compareByDescending<Pair<Long, Int>> { it.second }
                .thenBy { it.first })

    fun matchesSource(title: String, query: String): Boolean =
        query.isBlank() || title.contains(query.trim(), ignoreCase = true)
}
