package com.bsdung.musicassistantcar.data.telegram

/** Pure rules for one-tap play in Downloaded; the caller does every file check and all I/O. */
internal object TelegramOfflinePlayPolicy {
    /**
     * Copy to play when a grouped row is tapped. The copy already playing comes first, then the
     * group order (newest message first). Takes the first readable one; when none is readable the
     * first candidate is returned so playback decides exactly as it does for an ungrouped song.
     * Null only when the group holds nothing playable for [accountId].
     */
    fun defaultEntry(
        group: TelegramOfflineLibrary.Group, accountId: Long, playingKey: Long?,
        readable: (TelegramOfflineLibrary.Entry) -> Boolean
    ): TelegramOfflineLibrary.Entry? {
        val candidates = group.entries.filter { it.song.accountId == accountId && !it.song.isDeleted }
        val playing = candidates.firstOrNull { it.song.songKey == playingKey }
        val ordered = if (playing == null) candidates else listOf(playing) + candidates.filter { it !== playing }
        return ordered.firstOrNull(readable) ?: ordered.firstOrNull()
    }

    /**
     * One entry per group in list order: the group holding [selectedKey] uses that copy, every
     * other group its first copy. Returns the original entries, never copies.
     */
    fun queueEntries(
        groups: List<TelegramOfflineLibrary.Group>, selectedKey: Long?
    ): List<TelegramOfflineLibrary.Entry> = groups.mapNotNull { group ->
        group.entries.firstOrNull { it.song.songKey == selectedKey } ?: group.entries.firstOrNull()
    }
}
