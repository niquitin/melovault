package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of [TelegramLibraryDao]
 * for deterministic unit tests, verification, and fast testing scenarios.
 */
class InMemoryTelegramLibraryDao : TelegramLibraryDao {

    // Key: Triple(accountId, chatId, messageId) -> IndexedTelegramSong
    private val songs = ConcurrentHashMap<Triple<Long, Long, Long>, IndexedTelegramSong>()

    // Key: Pair(accountId, chatId) -> IndexedTelegramSource
    private val sources = ConcurrentHashMap<Pair<Long, Long>, IndexedTelegramSource>()

    // Key: Pair(accountId, syncScope) -> TelegramSyncState
    private val syncStates = ConcurrentHashMap<Pair<Long, String>, TelegramSyncState>()

    override fun upsertSongs(songs: Collection<IndexedTelegramSong>): Int {
        var count = 0
        for (song in songs) {
            val key = Triple(song.accountId, song.chatId, song.messageId)
            this.songs[key] = song
            count++
        }
        return count
    }

    override fun commitPageAndCheckpoint(
        accountId: Long,
        phase: String,
        cursor: String,
        items: Collection<IndexedTelegramSong>,
        checkpoint: TelegramSyncState
    ): Int {
        synchronized(this) {
            var count = 0
            for (song in items) {
                if (song.accountId != accountId) continue
                val key = Triple(song.accountId, song.chatId, song.messageId)
                this.songs[key] = song
                count++
            }
            syncStates[Pair(checkpoint.accountId, checkpoint.syncScope)] = checkpoint
            return count
        }
    }

    override fun getSongs(
        accountId: Long,
        chatId: Long?,
        searchQuery: String?,
        offset: Int,
        limit: Int
    ): List<IndexedTelegramSong> {
        val queryLower = searchQuery?.trim()?.lowercase()
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { chatId == null || it.chatId == chatId }
            .filter { queryLower.isNullOrEmpty() || it.title.lowercase().contains(queryLower) }
            .sortedWith(compareByDescending<IndexedTelegramSong> { it.date }.thenByDescending { it.messageId })
            .drop(offset)
            .take(limit)
            .toList()
    }

    override fun getSongCount(
        accountId: Long,
        chatId: Long?,
        searchQuery: String?
    ): Int {
        val queryLower = searchQuery?.trim()?.lowercase()
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { chatId == null || it.chatId == chatId }
            .filter { queryLower.isNullOrEmpty() || it.title.lowercase().contains(queryLower) }
            .count()
    }

    override fun getSongByKey(accountId: Long, songKey: Long): IndexedTelegramSong? {
        return songs.values.firstOrNull { it.accountId == accountId && it.songKey == songKey && !it.isDeleted }
    }

    override fun updateSongFileId(accountId: Long, chatId: Long, messageId: Long, fileId: Int) {
        val key = Triple(accountId, chatId, messageId)
        val existing = songs[key]
        if (existing != null) {
            songs[key] = existing.copy(fileId = fileId)
        }
    }

    override fun getSources(accountId: Long): List<IndexedTelegramSource> {
        return sources.values
            .asSequence()
            .filter { it.accountId == accountId }
            .sortedByDescending { it.trackCount }
            .toList()
    }

    override fun upsertSources(sources: Collection<IndexedTelegramSource>): Int {
        var count = 0
        for (src in sources) {
            this.sources[Pair(src.accountId, src.chatId)] = src
            count++
        }
        return count
    }

    override fun updateSourceTitle(accountId: Long, chatId: Long, title: String) {
        val key = Pair(accountId, chatId)
        val existing = sources[key]
        if (existing != null) {
            sources[key] = existing.copy(title = title, lastUpdatedAtMs = System.currentTimeMillis())
        } else {
            sources[key] = IndexedTelegramSource(
                accountId = accountId,
                chatId = chatId,
                title = title,
                trackCount = 0
            )
        }
    }

    override fun recomputeSourceTrackCounts(accountId: Long) {
        val activeSongs = songs.values.filter { it.accountId == accountId && !it.isDeleted }
        val countsByChat = activeSongs.groupingBy { it.chatId }.eachCount()

        for ((chatId, count) in countsByChat) {
            val key = Pair(accountId, chatId)
            val existing = sources[key]
            if (existing != null) {
                sources[key] = existing.copy(trackCount = count)
            } else {
                val sampleTitle = activeSongs.firstOrNull { it.chatId == chatId }?.sourceTitle ?: "Chat $chatId"
                sources[key] = IndexedTelegramSource(
                    accountId = accountId,
                    chatId = chatId,
                    title = sampleTitle,
                    trackCount = count
                )
            }
        }
    }

    override fun getSyncState(accountId: Long, scope: String): TelegramSyncState? {
        return syncStates[Pair(accountId, scope)]
    }

    override fun updateSyncState(state: TelegramSyncState) {
        syncStates[Pair(state.accountId, state.syncScope)] = state
    }

    override fun getLatestSongDate(accountId: Long, mediaType: String?): Long {
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { mediaType == null || it.mediaType == mediaType }
            .maxOfOrNull { it.date } ?: 0L
    }

    override fun markSongsUnavailable(
        accountId: Long,
        compositeKeys: Collection<Pair<Long, Long>>
    ) {
        for ((chatId, messageId) in compositeKeys) {
            val key = Triple(accountId, chatId, messageId)
            val existing = songs[key]
            if (existing != null) {
                songs[key] = existing.copy(isDeleted = true)
            }
        }
    }

    override fun deleteAccountData(accountId: Long) {
        songs.keys.removeIf { it.first == accountId }
        sources.keys.removeIf { it.first == accountId }
        syncStates.keys.removeIf { it.first == accountId }
    }
}
